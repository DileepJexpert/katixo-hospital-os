import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/auth/auth_state.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/util/pdf_actions.dart';
import '../../core/widgets/empty_state.dart';
import '../../core/widgets/section_card.dart';
import '../../core/widgets/status_chip.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// Doctor's prescriptions hub: "patients I've seen" (searchable by name /
/// mobile / visit no), per-visit prescriptions with version history, edit
/// (ACTIVE only), cancel (doctor), dispense (pharmacist) and a printable Rx.
/// Body widget. Read for clinical roles; mutations gated server-side.
class PrescriptionsScreen extends StatefulWidget {
  const PrescriptionsScreen({super.key});

  @override
  State<PrescriptionsScreen> createState() => _PrescriptionsScreenState();
}

class _PrescriptionsScreenState extends State<PrescriptionsScreen> {
  final _searchCtrl = TextEditingController();
  List<Map<String, dynamic>> _visits = const []; // patients/visits seen
  Map<String, dynamic>? _stats; // headline counts
  int? _openVisitId; // visit whose prescriptions are shown
  String? _openVisitLabel;
  List<Map<String, dynamic>> _list = const []; // prescriptions for open visit
  List<Map<String, dynamic>>? _history; // version history for one rx
  bool _loading = false;
  String? _error;
  String? _info;

  String get _role => context.read<AuthState>().currentUser?.role ?? '';
  bool get _isDoctor => _role == 'DOCTOR' || _role == 'ADMIN' || _role == 'SUPER_ADMIN';
  bool get _isPharmacist => _role == 'PHARMACIST' || _role == 'ADMIN' || _role == 'SUPER_ADMIN';
  int? get _doctorId => context.read<AuthState>().currentUser?.staffId;

  @override
  void initState() {
    super.initState();
    _loadStats();
    _loadVisits();
  }

  @override
  void dispose() {
    _searchCtrl.dispose();
    super.dispose();
  }

  Future<void> _loadStats() async {
    final id = _doctorId;
    if (id == null) return;
    try {
      final api = context.read<ApiClient>();
      final stats = await api.get<Map<String, dynamic>>(
        '/api/v1/opd/doctor/$id/stats',
        fromJson: (json) => Map<String, dynamic>.from(json as Map? ?? const {}),
      );
      if (mounted) setState(() => _stats = stats);
    } catch (_) {
      // stats are best-effort; never block the page
    }
  }

  /// Loads the doctor's "patients I've seen" list, optionally filtered by the
  /// search box (name / mobile / UHID / visit number).
  Future<void> _loadVisits() async {
    final id = _doctorId;
    if (id == null) return;
    setState(() {
      _loading = true;
      _error = null;
      _history = null;
      _openVisitId = null;
    });
    try {
      final api = context.read<ApiClient>();
      final q = _searchCtrl.text.trim();
      final visits = await api.get<List<Map<String, dynamic>>>(
        '/api/v1/opd/visits?doctorId=$id&limit=100'
        '${q.isEmpty ? '' : '&q=${Uri.encodeQueryComponent(q)}'}',
        fromJson: (json) =>
            List<Map<String, dynamic>>.from(json as List? ?? const []),
      );
      if (mounted) setState(() => _visits = visits);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  /// Opens a single visit's prescriptions.
  Future<void> _openVisit(int visitId, String label) async {
    setState(() {
      _loading = true;
      _error = null;
      _history = null;
      _openVisitId = visitId;
      _openVisitLabel = label;
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

  /// Reloads the currently-open visit's prescriptions (after edit/cancel/etc).
  Future<void> _reloadOpenVisit() async {
    final id = _openVisitId;
    if (id != null) await _openVisit(id, _openVisitLabel ?? 'Visit $id');
  }

  Future<void> _openByIdDialog() async {
    final ctrl = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Open by Visit ID'),
        content: TextField(
          controller: ctrl,
          autofocus: true,
          keyboardType: TextInputType.number,
          decoration: const InputDecoration(labelText: 'Visit ID'),
          onSubmitted: (_) => Navigator.pop(context, true),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Open')),
        ],
      ),
    );
    final id = int.tryParse(ctrl.text.trim());
    if (ok == true && id != null) _openVisit(id, 'Visit $id');
  }

  Future<void> _print(int rxId) async {
    if (!mounted) return;
    await openPdf(
      context,
      context.read<ApiClient>(),
      '/api/v1/prescriptions/$rxId/print.pdf',
      filename: 'prescription-$rxId.pdf',
    );
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
      await _reloadOpenVisit();
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
      await _reloadOpenVisit();
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
          Text('My Patients & Prescriptions', style: theme.textTheme.titleLarge),
          const SizedBox(height: Space.md),
          if (_stats != null) ...[
            _statsStrip(theme),
            const SizedBox(height: Space.md),
          ],
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
                  controller: _searchCtrl,
                  decoration: const InputDecoration(
                    labelText: 'Search patient — name, mobile, UHID or visit no',
                    prefixIcon: Icon(Icons.search, size: 18),
                  ),
                  onSubmitted: (_) => _loadVisits(),
                ),
              ),
              const SizedBox(width: Space.md),
              FilledButton.icon(
                onPressed: _loading ? null : _loadVisits,
                icon: const Icon(Icons.search, size: 18),
                label: const Text('Search'),
              ),
              const SizedBox(width: Space.sm),
              TextButton.icon(
                onPressed: _loading ? null : _openByIdDialog,
                icon: const Icon(Icons.tag, size: 18),
                label: const Text('By Visit ID'),
              ),
            ],
          ),
          const SizedBox(height: Space.md),
          Expanded(
            child: _history != null
                ? _historyView(theme)
                : _openVisitId != null
                    ? _openVisitView(theme)
                    : _visitsListView(theme),
          ),
        ],
      ),
    );
  }

  Widget _statsStrip(ThemeData theme) {
    final s = _stats!;
    Widget tile(String label, Object? value, IconData icon) => Expanded(
          child: Card(
            margin: EdgeInsets.zero,
            child: Padding(
              padding: const EdgeInsets.all(Space.md),
              child: Row(
                children: [
                  Icon(icon, size: 22, color: theme.colorScheme.primary),
                  const SizedBox(width: Space.sm),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text('${value ?? 0}', style: theme.textTheme.titleLarge),
                      Text(label,
                          style: theme.textTheme.bodySmall?.copyWith(
                              color: theme.colorScheme.onSurfaceVariant)),
                    ],
                  ),
                ],
              ),
            ),
          ),
        );
    return Row(
      children: [
        tile('Patients seen', s['distinctPatients'], Icons.groups_outlined),
        const SizedBox(width: Space.sm),
        tile('Consultations', s['visitsCompleted'], Icons.assignment_turned_in_outlined),
        const SizedBox(width: Space.sm),
        tile('Completed today', s['completedToday'], Icons.today_outlined),
      ],
    );
  }

  Widget _visitsListView(ThemeData theme) {
    if (_loading && _visits.isEmpty) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_visits.isEmpty) {
      return EmptyState(
        icon: Icons.groups_outlined,
        title: _searchCtrl.text.trim().isEmpty
            ? 'No patients yet'
            : 'No matches',
        message: _searchCtrl.text.trim().isEmpty
            ? 'Patients you consult will appear here.'
            : 'Try a different name, mobile or visit number.',
      );
    }
    return ListView.builder(
      itemCount: _visits.length,
      itemBuilder: (_, i) {
        final v = _visits[i];
        final name = '${v['patientName'] ?? 'Patient'}';
        final age = v['age'];
        final sub = [
          if (v['uhid'] != null) '${v['uhid']}',
          if (v['mobile'] != null) '${v['mobile']}',
          if (age != null) '$age yrs',
        ].join(' · ');
        final visitNo = '${v['visitNumber'] ?? v['visitId']}';
        return Card(
          margin: const EdgeInsets.symmetric(vertical: Space.xxs),
          child: ListTile(
            leading: const Icon(Icons.person_outline),
            title: Text('$name${sub.isEmpty ? '' : '  ·  $sub'}'),
            subtitle: Text(
              '$visitNo'
              '${(v['diagnosis'] ?? '').toString().isNotEmpty ? '  ·  ${v['diagnosis']}' : ''}'
              '${(v['chiefComplaint'] ?? '').toString().isNotEmpty ? '  ·  ${v['chiefComplaint']}' : ''}',
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
            trailing: Wrap(
              crossAxisAlignment: WrapCrossAlignment.center,
              spacing: Space.xs,
              children: [
                StatusChip.auto('${v['visitStatus']}'),
                const Icon(Icons.chevron_right),
              ],
            ),
            onTap: () => _openVisit(v['visitId'] as int, '$name · $visitNo'),
          ),
        );
      },
    );
  }

  Widget _openVisitView(ThemeData theme) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            TextButton.icon(
              onPressed: () => setState(() => _openVisitId = null),
              icon: const Icon(Icons.arrow_back, size: 18),
              label: const Text('Back to patients'),
            ),
            const SizedBox(width: Space.sm),
            Expanded(
              child: Text(_openVisitLabel ?? '',
                  style: theme.textTheme.titleMedium,
                  overflow: TextOverflow.ellipsis),
            ),
          ],
        ),
        const SizedBox(height: Space.sm),
        Expanded(child: _listView(theme)),
      ],
    );
  }

  Widget _listView(ThemeData theme) {
    if (_loading && _list.isEmpty) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_list.isEmpty) {
      return const EmptyState(
        icon: Icons.medical_information_outlined,
        title: 'No prescriptions for this visit',
        message: 'Create one from the consultation worklist.',
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
                onPressed: _loading ? null : () => _print(rx['id'] as int),
                icon: const Icon(Icons.print_outlined, size: 18),
                label: const Text('Print'),
              ),
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
