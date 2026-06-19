import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/auth/auth_state.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/empty_state.dart';
import '../../core/widgets/section_card.dart';
import '../../core/widgets/status_chip.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// Prescription management: look up by visit, view items + version history,
/// edit (ACTIVE only), cancel (doctor), and mark dispensed (pharmacist).
/// Body widget. Read for clinical roles; mutations gated server-side.
class PrescriptionsScreen extends StatefulWidget {
  const PrescriptionsScreen({super.key});

  @override
  State<PrescriptionsScreen> createState() => _PrescriptionsScreenState();
}

class _PrescriptionsScreenState extends State<PrescriptionsScreen> {
  final _visitCtrl = TextEditingController();
  List<Map<String, dynamic>> _list = const [];
  List<Map<String, dynamic>>? _history; // version history for one rx
  bool _loading = false;
  String? _error;
  String? _info;

  String get _role => context.read<AuthState>().currentUser?.role ?? '';
  bool get _isDoctor => _role == 'DOCTOR' || _role == 'ADMIN' || _role == 'SUPER_ADMIN';
  bool get _isPharmacist => _role == 'PHARMACIST' || _role == 'ADMIN' || _role == 'SUPER_ADMIN';

  @override
  void dispose() {
    _visitCtrl.dispose();
    super.dispose();
  }

  Future<void> _loadVisit() async {
    final visitId = int.tryParse(_visitCtrl.text.trim());
    if (visitId == null) {
      setState(() => _error = 'Enter a valid visit ID');
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
      _history = null;
    });
    try {
      final api = context.read<ApiClient>();
      final list = await api.get<List<Map<String, dynamic>>>(
        '/api/v1/prescriptions/visit/$visitId',
        fromJson: (json) =>
            List<Map<String, dynamic>>.from(json as List? ?? const []),
      );
      if (mounted) setState(() => _list = list);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _showHistory(int id) async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      final list = await api.get<List<Map<String, dynamic>>>(
        '/api/v1/prescriptions/$id/history',
        fromJson: (json) =>
            List<Map<String, dynamic>>.from(json as List? ?? const []),
      );
      if (mounted) setState(() => _history = list);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _act(String path, String okMsg) async {
    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });
    try {
      final api = context.read<ApiClient>();
      await api.put<dynamic>(path, const <String, dynamic>{}, fromJson: (j) => j);
      setState(() => _info = okMsg);
      await _loadVisit();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _editDialog(Map<String, dynamic> rx) async {
    final notesCtrl = TextEditingController(text: '${rx['notes'] ?? ''}');
    // Deep-copy items into editable rows.
    final items = [
      for (final i in List<Map<String, dynamic>>.from(rx['items'] as List? ?? const []))
        <String, dynamic>{
          'medicineCode': '${i['medicineCode'] ?? ''}',
          'medicineName': '${i['medicineName'] ?? ''}',
          'dosage': '${i['dosage'] ?? ''}',
          'frequency': '${i['frequency'] ?? ''}',
          'durationDays': i['durationDays'],
          'quantity': i['quantity'] ?? 1,
          'instructions': '${i['instructions'] ?? ''}',
        }
    ];

    final saved = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setLocal) => AlertDialog(
          title: Text('Edit ${rx['prescriptionNumber']}'),
          content: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
            child: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  TextField(
                    controller: notesCtrl,
                    decoration: const InputDecoration(labelText: 'Notes'),
                  ),
                  const SizedBox(height: Space.sm),
                  for (var i = 0; i < items.length; i++)
                    Card(
                      margin: const EdgeInsets.symmetric(vertical: Space.xxs),
                      child: Padding(
                        padding: const EdgeInsets.all(Space.sm),
                        child: Column(
                          children: [
                            Row(
                              children: [
                                Expanded(
                                  child: Text('${items[i]['medicineName']} (${items[i]['medicineCode']})',
                                      style: Theme.of(context).textTheme.titleSmall),
                                ),
                                IconButton(
                                  tooltip: 'Remove',
                                  icon: const Icon(Icons.delete_outline, size: 20),
                                  onPressed: () => setLocal(() => items.removeAt(i)),
                                ),
                              ],
                            ),
                            Row(
                              children: [
                                Expanded(child: _miniField('Dosage', items[i], 'dosage')),
                                const SizedBox(width: Space.sm),
                                Expanded(child: _miniField('Frequency', items[i], 'frequency')),
                              ],
                            ),
                            Row(
                              children: [
                                Expanded(child: _miniField('Days', items[i], 'durationDays', number: true)),
                                const SizedBox(width: Space.sm),
                                Expanded(child: _miniField('Qty', items[i], 'quantity', number: true)),
                              ],
                            ),
                            _miniField('Instructions', items[i], 'instructions'),
                          ],
                        ),
                      ),
                    ),
                  TextButton.icon(
                    onPressed: () async {
                      final added = await _addItemDialog();
                      if (added != null) setLocal(() => items.add(added));
                    },
                    icon: const Icon(Icons.add, size: 18),
                    label: const Text('Add medicine'),
                  ),
                ],
              ),
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
            FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Save')),
          ],
        ),
      ),
    );
    if (saved != true) return;
    if (items.isEmpty) {
      setState(() => _error = 'A prescription needs at least one medicine');
      return;
    }

    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });
    try {
      if (!mounted) return;
      final api = context.read<ApiClient>();
      await api.put<dynamic>(
        '/api/v1/prescriptions/${rx['id']}',
        {
          'notes': notesCtrl.text.trim(),
          'items': [
            for (final it in items)
              {
                'medicineCode': it['medicineCode'],
                'medicineName': it['medicineName'],
                if ('${it['dosage']}'.isNotEmpty) 'dosage': it['dosage'],
                if ('${it['frequency']}'.isNotEmpty) 'frequency': it['frequency'],
                if (it['durationDays'] != null) 'durationDays': _toInt(it['durationDays']),
                'quantity': _toInt(it['quantity']) ?? 1,
                if ('${it['instructions']}'.isNotEmpty) 'instructions': it['instructions'],
              }
          ],
        },
        fromJson: (j) => j,
      );
      setState(() => _info = 'Prescription updated (new version)');
      await _loadVisit();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<Map<String, dynamic>?> _addItemDialog() async {
    final code = TextEditingController();
    final name = TextEditingController();
    final dosage = TextEditingController();
    final freq = TextEditingController();
    final days = TextEditingController();
    final qty = TextEditingController(text: '1');
    final instr = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Add medicine'),
        content: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(controller: code, decoration: const InputDecoration(labelText: 'Medicine code *')),
                TextField(controller: name, decoration: const InputDecoration(labelText: 'Medicine name *')),
                TextField(controller: dosage, decoration: const InputDecoration(labelText: 'Dosage')),
                TextField(controller: freq, decoration: const InputDecoration(labelText: 'Frequency')),
                TextField(controller: days, keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'Duration (days)')),
                TextField(controller: qty, keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'Quantity')),
                TextField(controller: instr, decoration: const InputDecoration(labelText: 'Instructions')),
              ],
            ),
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Add')),
        ],
      ),
    );
    if (ok != true || code.text.trim().isEmpty || name.text.trim().isEmpty) return null;
    return {
      'medicineCode': code.text.trim(),
      'medicineName': name.text.trim(),
      'dosage': dosage.text.trim(),
      'frequency': freq.text.trim(),
      'durationDays': int.tryParse(days.text.trim()),
      'quantity': int.tryParse(qty.text.trim()) ?? 1,
      'instructions': instr.text.trim(),
    };
  }

  Widget _miniField(String label, Map<String, dynamic> row, String key, {bool number = false}) {
    return TextField(
      controller: TextEditingController(text: '${row[key] ?? ''}'),
      keyboardType: number ? TextInputType.number : null,
      decoration: InputDecoration(labelText: label, isDense: true),
      onChanged: (v) => row[key] = number ? int.tryParse(v) : v,
    );
  }

  int? _toInt(Object? v) => v == null ? null : int.tryParse('$v');

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return PageContainer(
      scrollable: false,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Prescriptions', style: theme.textTheme.titleLarge),
          const SizedBox(height: Space.md),
          if (_error != null) ...[
            MessageBanner.error(_error!),
            const SizedBox(height: Space.md),
          ],
          if (_info != null) ...[
            MessageBanner.success(_info!),
            const SizedBox(height: Space.md),
          ],
          Row(
            children: [
              Expanded(
                child: TextField(
                  controller: _visitCtrl,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(
                    labelText: 'Visit ID',
                    prefixIcon: Icon(Icons.search, size: 18),
                  ),
                  onSubmitted: (_) => _loadVisit(),
                ),
              ),
              const SizedBox(width: Space.md),
              FilledButton.icon(
                onPressed: _loading ? null : _loadVisit,
                icon: const Icon(Icons.search, size: 18),
                label: const Text('Load'),
              ),
            ],
          ),
          const SizedBox(height: Space.md),
          Expanded(child: _history != null ? _historyView(theme) : _listView(theme)),
        ],
      ),
    );
  }

  Widget _listView(ThemeData theme) {
    if (_list.isEmpty) {
      return const EmptyState(
        icon: Icons.medical_information_outlined,
        title: 'No prescriptions loaded',
        message: 'Enter a visit ID to view its prescriptions.',
      );
    }
    return ListView(
      children: [for (final rx in _list) _rxCard(theme, rx)],
    );
  }

  Widget _rxCard(ThemeData theme, Map<String, dynamic> rx) {
    final items = List<Map<String, dynamic>>.from(rx['items'] as List? ?? const []);
    final status = '${rx['prescriptionStatus']}';
    final isActive = status == 'ACTIVE';
    return SectionCard(
      title: '${rx['prescriptionNumber']} · v${rx['version']}',
      icon: Icons.medical_information_outlined,
      subtitle: 'Visit ${rx['visitId']} · patient ${rx['patientId']}',
      action: StatusChip.auto(status),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if ((rx['notes'] ?? '').toString().isNotEmpty) ...[
            Text('Notes: ${rx['notes']}', style: theme.textTheme.bodyMedium),
            const SizedBox(height: Space.sm),
          ],
          for (final i in items)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: Space.xxs),
              child: Text(
                '• ${i['medicineName']} — ${i['dosage'] ?? ''} ${i['frequency'] ?? ''}'
                '${i['durationDays'] != null ? ' × ${i['durationDays']}d' : ''}'
                ' (qty ${i['quantity']})'
                '${(i['instructions'] ?? '').toString().isNotEmpty ? ' · ${i['instructions']}' : ''}',
                style: theme.textTheme.bodyMedium,
              ),
            ),
          const SizedBox(height: Space.sm),
          Wrap(
            spacing: Space.sm,
            children: [
              OutlinedButton.icon(
                onPressed: _loading ? null : () => _showHistory(rx['id'] as int),
                icon: const Icon(Icons.history, size: 18),
                label: const Text('History'),
              ),
              if (isActive && _isDoctor)
                OutlinedButton.icon(
                  onPressed: _loading ? null : () => _editDialog(rx),
                  icon: const Icon(Icons.edit_outlined, size: 18),
                  label: const Text('Edit'),
                ),
              if (isActive && _isPharmacist)
                FilledButton.icon(
                  onPressed: _loading ? null : () => _act('/api/v1/prescriptions/${rx['id']}/dispense', 'Marked dispensed'),
                  icon: const Icon(Icons.check, size: 18),
                  label: const Text('Dispense'),
                ),
              if (isActive && _isDoctor)
                OutlinedButton.icon(
                  onPressed: _loading ? null : () => _act('/api/v1/prescriptions/${rx['id']}/cancel', 'Cancelled'),
                  icon: const Icon(Icons.block, size: 18),
                  label: const Text('Cancel'),
                ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _historyView(ThemeData theme) {
    final hist = _history ?? const [];
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        TextButton.icon(
          onPressed: () => setState(() => _history = null),
          icon: const Icon(Icons.arrow_back, size: 18),
          label: const Text('Back'),
        ),
        Expanded(
          child: hist.isEmpty
              ? const EmptyState(icon: Icons.history, title: 'No history')
              : ListView(children: [for (final rx in hist) _rxCard(theme, rx)]),
        ),
      ],
    );
  }
}
