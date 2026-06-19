import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/api/rx_models.dart';
import '../../core/theme/design_tokens.dart';

/// Opens the pick-don't-type medicine dialog and returns the chosen line
/// (or null if cancelled). Medicine comes from the item master (search), and
/// dosage / frequency / instructions are standard dropdowns (1-0-1 BD, etc.)
/// so the common case is zero typing. Shared by the consultation panel and the
/// prescription edit flow.
Future<RxItemInput?> showMedicinePicker(BuildContext context) {
  return showDialog<RxItemInput>(
    context: context,
    builder: (_) => MedicinePickerDialog(api: context.read<ApiClient>()),
  );
}

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

class MedicinePickerDialog extends StatefulWidget {
  const MedicinePickerDialog({super.key, required this.api});

  final ApiClient api;

  @override
  State<MedicinePickerDialog> createState() => _MedicinePickerDialogState();
}

class _MedicinePickerDialogState extends State<MedicinePickerDialog> {
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

  /// qty = doses/day × duration, unless an explicit quantity was typed.
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
