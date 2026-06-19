import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/api/rx_models.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/util/pdf_actions.dart';
import '../../core/widgets/status_chip.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// Prescription entry inside an active consultation.
/// Handles the allergy-guard flow: a blocked create surfaces an
/// override dialog where the doctor must give an audited reason.
class PrescriptionPanel extends StatefulWidget {
  const PrescriptionPanel({
    super.key,
    required this.visitId,
    this.onCreated,
  });

  final int visitId;
  final ValueChanged<RxResponse>? onCreated;

  @override
  State<PrescriptionPanel> createState() => _PrescriptionPanelState();
}

class _PrescriptionPanelState extends State<PrescriptionPanel> {
  final List<RxItemInput> _items = [];
  final _notesCtrl = TextEditingController();

  bool _saving = false;
  bool _sendingToPharmacy = false;
  String? _error;
  RxResponse? _created;

  @override
  void dispose() {
    _notesCtrl.dispose();
    super.dispose();
  }

  Future<void> _addItemDialog() async {
    final item = await showDialog<RxItemInput>(
      context: context,
      builder: (_) => _AddMedicineDialog(api: context.read<ApiClient>()),
    );
    if (item != null) {
      setState(() => _items.add(item));
    }
  }

  Future<void> _create({bool override = false, String? reason}) async {
    if (_items.isEmpty) {
      setState(() => _error = 'Add at least one medicine');
      return;
    }

    setState(() {
      _saving = true;
      _error = null;
    });

    try {
      final api = context.read<ApiClient>();
      final rx = await api.post<RxResponse>(
        '/api/v1/prescriptions',
        CreateRxRequest(
          visitId: widget.visitId,
          items: _items,
          notes: _notesCtrl.text.trim().isEmpty ? null : _notesCtrl.text.trim(),
          overrideAllergy: override,
          allergyOverrideReason: reason,
        ),
        fromJson: (json) => RxResponse.fromJson(json as Map<String, dynamic>),
      );
      setState(() => _created = rx);
      widget.onCreated?.call(rx);
    } on ApiException catch (e) {
      if (e.error.error == 'ALLERGY_CONFLICT') {
        await _allergyOverrideDialog(e.error.message);
      } else {
        setState(() => _error = e.error.message);
      }
    } catch (e) {
      setState(() => _error = 'Prescription failed: $e');
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _allergyOverrideDialog(String conflictMessage) async {
    final reasonCtrl = TextEditingController();

    final proceed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        icon: const Icon(Icons.warning_amber_rounded,
            color: StatusColors.danger, size: 32),
        title: const Text('Allergy Conflict'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(conflictMessage),
            const SizedBox(height: Space.lg),
            const Text('To proceed anyway, give a clinical reason. '
                'The override is recorded in the audit log.'),
            const SizedBox(height: Space.md),
            TextField(
              controller: reasonCtrl,
              decoration:
                  const InputDecoration(labelText: 'Override Reason *'),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(
                backgroundColor: StatusColors.danger),
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Override & Prescribe'),
          ),
        ],
      ),
    );

    if (proceed == true && reasonCtrl.text.trim().isNotEmpty) {
      await _create(override: true, reason: reasonCtrl.text.trim());
    } else if (proceed == true) {
      setState(() => _error = 'Override reason is required');
    }
  }

  Future<void> _sendToPharmacy() async {
    final rx = _created;
    if (rx == null) return;

    setState(() {
      _sendingToPharmacy = true;
      _error = null;
    });

    try {
      final api = context.read<ApiClient>();
      await api.post<dynamic>(
        '/api/v1/pharmacy/queue/send',
        {'prescriptionId': rx.id},
        fromJson: (json) => json,
      );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('${rx.prescriptionNumber} sent to pharmacy')),
        );
      }
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _sendingToPharmacy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    // After creation: summary + send-to-pharmacy.
    if (_created != null) {
      final rx = _created!;
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text('Prescription ${rx.prescriptionNumber}',
                  style: theme.textTheme.titleMedium),
              const SizedBox(width: Space.sm),
              StatusChip.auto(rx.status),
            ],
          ),
          const SizedBox(height: Space.sm),
          for (final item in rx.items)
            Padding(
              padding: const EdgeInsets.only(bottom: Space.xs),
              child: Text(
                  '• ${item.medicineName} ${item.dosage ?? ''} ${item.frequency ?? ''} ×${item.quantity ?? 1}',
                  style: theme.textTheme.bodyMedium),
            ),
          const SizedBox(height: Space.md),
          if (_error != null) ...[
            MessageBanner.error(_error!),
            const SizedBox(height: Space.md),
          ],
          Wrap(
            spacing: Space.sm,
            runSpacing: Space.sm,
            children: [
              OutlinedButton.icon(
                onPressed: _sendingToPharmacy ? null : _sendToPharmacy,
                icon: const Icon(Icons.local_pharmacy_outlined, size: 18),
                label: Text(
                    _sendingToPharmacy ? 'Sending…' : 'Send to Pharmacy Queue'),
              ),
              OutlinedButton.icon(
                onPressed: () => openPdf(
                  context,
                  context.read<ApiClient>(),
                  '/api/v1/prescriptions/${rx.id}/print.pdf',
                  filename: 'prescription-${rx.id}.pdf',
                ),
                icon: const Icon(Icons.print_outlined, size: 18),
                label: const Text('Print'),
              ),
            ],
          ),
        ],
      );
    }

    // Entry form.
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Text('Prescription', style: theme.textTheme.titleMedium),
            const Spacer(),
            OutlinedButton.icon(
              onPressed: _saving ? null : _addItemDialog,
              icon: const Icon(Icons.add, size: 18),
              label: const Text('Add Medicine'),
            ),
          ],
        ),
        const SizedBox(height: Space.sm),

        if (_error != null) ...[
          MessageBanner.error(_error!),
          const SizedBox(height: Space.sm),
        ],

        if (_items.isEmpty)
          Text('No medicines added',
              style: theme.textTheme.bodySmall
                  ?.copyWith(color: theme.colorScheme.onSurfaceVariant))
        else
          for (var i = 0; i < _items.length; i++)
            ListTile(
              dense: true,
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.medication_outlined, size: 20),
              title: Text(
                  '${_items[i].medicineName} (${_items[i].medicineCode})'),
              subtitle: Text(
                  '${_items[i].dosage ?? '-'} · ${_items[i].frequency ?? '-'} · qty ${_items[i].quantity}'),
              trailing: IconButton(
                icon: const Icon(Icons.close, size: 18),
                onPressed: () => setState(() => _items.removeAt(i)),
              ),
            ),

        const SizedBox(height: Space.sm),
        TextField(
          controller: _notesCtrl,
          enabled: !_saving,
          decoration: const InputDecoration(labelText: 'Notes'),
        ),
        const SizedBox(height: Space.md),
        FilledButton.icon(
          onPressed: _saving || _items.isEmpty ? null : () => _create(),
          icon: const Icon(Icons.receipt_long_outlined, size: 18),
          label: Text(_saving ? 'Creating…' : 'Create Prescription'),
        ),
      ],
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────
//  Add-medicine dialog: pick-don't-type.
//  Medicine comes from the item master (search), and dosage / frequency /
//  instructions are standard dropdowns so the common case is zero typing.
// ─────────────────────────────────────────────────────────────────────────

/// A medicine from the item master returned by /api/v1/inventory/items.
class _MasterMedicine {
  const _MasterMedicine({required this.code, required this.name, this.manufacturer});

  final String code;
  final String name;
  final String? manufacturer;

  factory _MasterMedicine.fromJson(Map<String, dynamic> json) => _MasterMedicine(
        code: (json['code'] ?? '').toString(),
        name: (json['name'] ?? '').toString(),
        manufacturer: json['manufacturer']?.toString(),
      );
}

/// Standard OPD dosing pattern (morning-noon-night) with doses/day used to
/// auto-calculate the dispense quantity. [dosesPerDay] == 0 means "don't
/// auto-calc" (SOS / STAT / custom).
class _FreqOption {
  const _FreqOption(this.code, this.label, this.dosesPerDay);
  final String code;
  final String label;
  final int dosesPerDay;
}

const String _customSentinel = '__custom__';

const List<_FreqOption> _freqOptions = [
  _FreqOption('1-0-1', 'Twice daily · BD (morning + night)', 2),
  _FreqOption('1-1-1', 'Thrice daily · TDS', 3),
  _FreqOption('1-0-0', 'Once daily · morning (OD)', 1),
  _FreqOption('0-0-1', 'Once at night · HS', 1),
  _FreqOption('0-1-0', 'Once daily · afternoon', 1),
  _FreqOption('1-1-1-1', 'Four times daily · QID', 4),
  _FreqOption('SOS', 'As needed · SOS', 0),
  _FreqOption('STAT', 'Immediately, once · STAT', 0),
];

const List<String> _dosageOptions = [
  '1 tab', '2 tab', '½ tab', '1 cap', '5 ml', '10 ml', '1 tsp (5 ml)',
  '1 sachet', '1 drop', '2 drops', '1 puff', 'Apply locally',
];

const List<String> _instructionOptions = [
  'After food', 'Before food', 'Empty stomach', 'With water', 'At bedtime',
];

class _AddMedicineDialog extends StatefulWidget {
  const _AddMedicineDialog({required this.api});

  final ApiClient api;

  @override
  State<_AddMedicineDialog> createState() => _AddMedicineDialogState();
}

class _AddMedicineDialogState extends State<_AddMedicineDialog> {
  final _searchCtrl = TextEditingController();
  final _qtyCtrl = TextEditingController();
  final _durationCtrl = TextEditingController();
  final _dosageCustomCtrl = TextEditingController();
  final _freqCustomCtrl = TextEditingController();
  final _instrCustomCtrl = TextEditingController();

  Timer? _debounce;
  bool _searching = false;
  List<_MasterMedicine> _results = [];
  _MasterMedicine? _selected;

  String _dosage = _dosageOptions.first;
  String _freq = _freqOptions.first.code;
  String? _instruction; // null = no special instruction
  bool _qtyEdited = false;

  @override
  void dispose() {
    _debounce?.cancel();
    _searchCtrl.dispose();
    _qtyCtrl.dispose();
    _durationCtrl.dispose();
    _dosageCustomCtrl.dispose();
    _freqCustomCtrl.dispose();
    _instrCustomCtrl.dispose();
    super.dispose();
  }

  void _onSearchChanged(String q) {
    _debounce?.cancel();
    _debounce =
        Timer(const Duration(milliseconds: 300), () => _runSearch(q.trim()));
  }

  Future<void> _runSearch(String q) async {
    if (q.length < 2) {
      setState(() => _results = []);
      return;
    }
    setState(() => _searching = true);
    try {
      final res = await widget.api.get<List<_MasterMedicine>>(
        '/api/v1/inventory/items?search=${Uri.encodeQueryComponent(q)}',
        fromJson: (data) => (data as List)
            .map((e) => _MasterMedicine.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
      if (mounted) setState(() => _results = res);
    } catch (_) {
      if (mounted) setState(() => _results = []);
    } finally {
      if (mounted) setState(() => _searching = false);
    }
  }

  int get _dosesPerDay => _freqOptions
      .firstWhere((f) => f.code == _freq,
          orElse: () => const _FreqOption(_customSentinel, '', 0))
      .dosesPerDay;

  /// qty = doses/day × duration, unless the doctor typed an explicit quantity.
  void _recalcQty() {
    if (_qtyEdited) return;
    final days = int.tryParse(_durationCtrl.text) ?? 0;
    final dpd = _dosesPerDay;
    if (dpd > 0 && days > 0) _qtyCtrl.text = (dpd * days).toString();
  }

  void _select(_MasterMedicine m) {
    setState(() {
      _selected = m;
      _searchCtrl.text = m.name;
      _results = [];
    });
  }

  void _submit() {
    final m = _selected;
    if (m == null) return;
    final dosage = _dosage == _customSentinel
        ? _dosageCustomCtrl.text.trim()
        : _dosage;
    final freq =
        _freq == _customSentinel ? _freqCustomCtrl.text.trim() : _freq;
    final instr = _instruction == _customSentinel
        ? _instrCustomCtrl.text.trim()
        : _instruction;
    Navigator.pop(
      context,
      RxItemInput(
        medicineCode: m.code,
        medicineName: m.name,
        dosage: dosage.isEmpty ? null : dosage,
        frequency: freq.isEmpty ? null : freq,
        durationDays: int.tryParse(_durationCtrl.text),
        quantity: int.tryParse(_qtyCtrl.text) ?? 1,
        instructions: (instr == null || instr.isEmpty) ? null : instr,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final q = _searchCtrl.text.trim();
    return AlertDialog(
      title: const Text('Add Medicine'),
      content: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // ── Medicine search / pick ──
              TextField(
                controller: _searchCtrl,
                autofocus: true,
                decoration: InputDecoration(
                  labelText: 'Medicine *',
                  hintText: 'Type to search the item master…',
                  prefixIcon: const Icon(Icons.search),
                  suffixIcon: _selected != null
                      ? IconButton(
                          icon: const Icon(Icons.close, size: 18),
                          tooltip: 'Change',
                          onPressed: () => setState(() {
                            _selected = null;
                            _searchCtrl.clear();
                            _results = [];
                          }),
                        )
                      : (_searching
                          ? const Padding(
                              padding: EdgeInsets.all(12),
                              child: SizedBox(
                                  width: 16,
                                  height: 16,
                                  child: CircularProgressIndicator(
                                      strokeWidth: 2)),
                            )
                          : null),
                ),
                onChanged: (v) {
                  if (_selected != null) setState(() => _selected = null);
                  _onSearchChanged(v);
                },
              ),
              if (_selected != null)
                Padding(
                  padding: const EdgeInsets.only(top: Space.xs),
                  child: Text(
                    'Code ${_selected!.code}'
                    '${_selected!.manufacturer != null ? ' · ${_selected!.manufacturer}' : ''}',
                    style: theme.textTheme.bodySmall
                        ?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                  ),
                ),
              if (_selected == null && q.length >= 2) _buildResults(theme, q),

              const SizedBox(height: Space.md),

              // ── Dosage + Frequency ──
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: DropdownButtonFormField<String>(
                      initialValue: _dosage,
                      isExpanded: true,
                      decoration: const InputDecoration(labelText: 'Dosage'),
                      items: [
                        for (final d in _dosageOptions)
                          DropdownMenuItem(value: d, child: Text(d)),
                        const DropdownMenuItem(
                            value: _customSentinel, child: Text('Custom…')),
                      ],
                      onChanged: (v) =>
                          setState(() => _dosage = v ?? _dosageOptions.first),
                    ),
                  ),
                  const SizedBox(width: Space.md),
                  Expanded(
                    child: DropdownButtonFormField<String>(
                      initialValue: _freq,
                      isExpanded: true,
                      decoration: const InputDecoration(labelText: 'Frequency'),
                      items: [
                        for (final f in _freqOptions)
                          DropdownMenuItem(
                              value: f.code,
                              child: Text('${f.code}  ·  ${f.label}',
                                  overflow: TextOverflow.ellipsis)),
                        const DropdownMenuItem(
                            value: _customSentinel, child: Text('Custom…')),
                      ],
                      onChanged: (v) => setState(() {
                        _freq = v ?? _freqOptions.first.code;
                        _recalcQty();
                      }),
                    ),
                  ),
                ],
              ),
              if (_dosage == _customSentinel || _freq == _customSentinel)
                Padding(
                  padding: const EdgeInsets.only(top: Space.sm),
                  child: Row(
                    children: [
                      if (_dosage == _customSentinel)
                        Expanded(
                          child: TextField(
                            controller: _dosageCustomCtrl,
                            decoration: const InputDecoration(
                                labelText: 'Custom dosage',
                                hintText: 'e.g. 1.5 ml'),
                          ),
                        ),
                      if (_dosage == _customSentinel &&
                          _freq == _customSentinel)
                        const SizedBox(width: Space.md),
                      if (_freq == _customSentinel)
                        Expanded(
                          child: TextField(
                            controller: _freqCustomCtrl,
                            decoration: const InputDecoration(
                                labelText: 'Custom frequency',
                                hintText: 'e.g. Q6H'),
                          ),
                        ),
                    ],
                  ),
                ),

              const SizedBox(height: Space.md),

              // ── Duration + Quantity ──
              Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _durationCtrl,
                      keyboardType: TextInputType.number,
                      inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                      decoration: const InputDecoration(
                          labelText: 'Duration (days)', hintText: 'e.g. 5'),
                      onChanged: (_) => setState(_recalcQty),
                    ),
                  ),
                  const SizedBox(width: Space.md),
                  Expanded(
                    child: TextField(
                      controller: _qtyCtrl,
                      keyboardType: TextInputType.number,
                      inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                      decoration: InputDecoration(
                        labelText: 'Quantity',
                        helperText: _dosesPerDay > 0 ? 'Auto from days' : null,
                      ),
                      onChanged: (_) => _qtyEdited = true,
                    ),
                  ),
                ],
              ),

              const SizedBox(height: Space.md),

              // ── Instructions ──
              DropdownButtonFormField<String?>(
                initialValue: _instruction,
                isExpanded: true,
                decoration: const InputDecoration(labelText: 'Instructions'),
                items: [
                  const DropdownMenuItem<String?>(
                      value: null, child: Text('No special instruction')),
                  for (final i in _instructionOptions)
                    DropdownMenuItem<String?>(value: i, child: Text(i)),
                  const DropdownMenuItem<String?>(
                      value: _customSentinel, child: Text('Custom…')),
                ],
                onChanged: (v) => setState(() => _instruction = v),
              ),
              if (_instruction == _customSentinel)
                Padding(
                  padding: const EdgeInsets.only(top: Space.sm),
                  child: TextField(
                    controller: _instrCustomCtrl,
                    decoration: const InputDecoration(
                        labelText: 'Custom instruction'),
                  ),
                ),
            ],
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Cancel'),
        ),
        FilledButton(
          onPressed: _selected == null ? null : _submit,
          child: const Text('Add'),
        ),
      ],
    );
  }

  Widget _buildResults(ThemeData theme, String q) {
    if (_searching) return const SizedBox.shrink();
    if (_results.isEmpty) {
      // Fresh tenant / no match: let the doctor use the typed text directly.
      final code = q.toUpperCase().replaceAll(RegExp(r'[^A-Z0-9]'), '');
      return Card(
        margin: const EdgeInsets.only(top: Space.sm),
        child: ListTile(
          dense: true,
          leading: const Icon(Icons.edit_note, size: 20),
          title: Text('Use "$q"'),
          subtitle: const Text('Not in item master'),
          onTap: () => _select(_MasterMedicine(
              code: code.isEmpty ? 'RX' : code, name: q)),
        ),
      );
    }
    return Container(
      margin: const EdgeInsets.only(top: Space.sm),
      constraints: const BoxConstraints(maxHeight: 180),
      decoration: BoxDecoration(
        border: Border.all(color: theme.dividerColor),
        borderRadius: BorderRadius.circular(Corners.md),
      ),
      child: ListView.builder(
        shrinkWrap: true,
        itemCount: _results.length,
        itemBuilder: (_, i) {
          final m = _results[i];
          return ListTile(
            dense: true,
            leading: const Icon(Icons.medication_outlined, size: 20),
            title: Text(m.name),
            subtitle: Text(
                '${m.code}${m.manufacturer != null ? ' · ${m.manufacturer}' : ''}'),
            onTap: () => _select(m),
          );
        },
      ),
    );
  }
}
