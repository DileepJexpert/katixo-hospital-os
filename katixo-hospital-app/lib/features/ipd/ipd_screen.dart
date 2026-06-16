import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/auth/auth_state.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/status_chip.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;
import '../patient/patient_picker.dart';

/// IPD module: current inpatients, bed board, admit, and admission detail with
/// bed transfer + discharge. Role-aware actions (backend also enforces).
/// Body widget — the host home supplies the AppShell.
class IpdScreen extends StatefulWidget {
  const IpdScreen({super.key});

  @override
  State<IpdScreen> createState() => _IpdScreenState();
}

class _IpdScreenState extends State<IpdScreen> {
  int _tab = 0; // 0 = inpatients, 1 = bed board

  List<Map<String, dynamic>> _inpatients = const [];
  List<Map<String, dynamic>> _beds = const [];
  Map<String, dynamic>? _selected; // admission detail
  List<Map<String, dynamic>> _allocations = const [];

  bool _loading = false;
  String? _error;
  String? _info;

  String _role = '';

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _role = context.read<AuthState>().currentUser?.role ?? '';
      _loadAll();
    });
  }

  bool get _canAdmit => _role == 'FRONT_DESK' || _role == 'ADMIN';
  bool get _canTransfer => _role == 'FRONT_DESK' || _role == 'NURSE' || _role == 'ADMIN';
  bool get _canDischarge => _role == 'DOCTOR' || _role == 'ADMIN';

  Future<void> _loadAll() async {
    await _loadInpatients();
    await _loadBeds();
  }

  Future<void> _loadInpatients() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      final list = await api.get<List<Map<String, dynamic>>>(
        '/api/v1/ipd/admissions?status=ADMITTED',
        fromJson: (json) =>
            List<Map<String, dynamic>>.from(json as List? ?? const []),
      );
      if (mounted) setState(() => _inpatients = list);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _loadBeds() async {
    try {
      final api = context.read<ApiClient>();
      final list = await api.get<List<Map<String, dynamic>>>(
        '/api/v1/ipd/beds',
        fromJson: (json) =>
            List<Map<String, dynamic>>.from(json as List? ?? const []),
      );
      if (mounted) setState(() => _beds = list);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    }
  }

  Future<void> _openAdmission(int id) async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      final a = await api.get<Map<String, dynamic>>(
        '/api/v1/ipd/admissions/$id',
        fromJson: (json) => json as Map<String, dynamic>,
      );
      final allocs = await api.get<List<Map<String, dynamic>>>(
        '/api/v1/ipd/admissions/$id/allocations',
        fromJson: (json) =>
            List<Map<String, dynamic>>.from(json as List? ?? const []),
      );
      if (mounted) {
        setState(() {
          _selected = a;
          _allocations = allocs;
        });
      }
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  List<Map<String, dynamic>> get _vacantBeds =>
      _beds.where((b) => '${b['bedStatus']}' == 'VACANT').toList();

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return PageContainer(
      scrollable: false,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text('IPD', style: theme.textTheme.titleLarge),
              const Spacer(),
              SegmentedButton<int>(
                segments: const [
                  ButtonSegment(value: 0, label: Text('Inpatients')),
                  ButtonSegment(value: 1, label: Text('Bed board')),
                ],
                selected: {_tab},
                onSelectionChanged: (s) => setState(() => _tab = s.first),
              ),
            ],
          ),
          const SizedBox(height: Space.md),
          if (_error != null) ...[
            MessageBanner.error(_error!),
            const SizedBox(height: Space.md),
          ],
          if (_info != null) ...[
            MessageBanner.success(_info!),
            const SizedBox(height: Space.md),
          ],
          Expanded(child: _tab == 0 ? _inpatientsTab(theme) : _bedBoardTab(theme)),
        ],
      ),
    );
  }

  // ---------------- inpatients ----------------

  Widget _inpatientsTab(ThemeData theme) {
    if (_selected != null) return _admissionDetail(theme, _selected!);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Text('${_inpatients.length} current inpatients',
                style: theme.textTheme.titleMedium),
            const Spacer(),
            IconButton(
              tooltip: 'Refresh',
              onPressed: _loading ? null : _loadAll,
              icon: const Icon(Icons.refresh, size: 20),
            ),
            if (_canAdmit)
              FilledButton.icon(
                onPressed: _loading ? null : _admitDialog,
                icon: const Icon(Icons.person_add_alt_1, size: 18),
                label: const Text('Admit'),
              ),
          ],
        ),
        const SizedBox(height: Space.sm),
        Expanded(
          child: _inpatients.isEmpty
              ? Center(
                  child: Text(_loading ? 'Loading…' : 'No current inpatients',
                      style: theme.textTheme.bodyMedium?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant)))
              : ListView.separated(
                  itemCount: _inpatients.length,
                  separatorBuilder: (_, __) => const Divider(height: 1),
                  itemBuilder: (context, i) {
                    final a = _inpatients[i];
                    return ListTile(
                      onTap: () => _openAdmission(a['id'] as int),
                      title: Row(
                        children: [
                          Text('${a['admissionNumber']}',
                              style: theme.textTheme.titleSmall),
                          const SizedBox(width: Space.sm),
                          StatusChip.auto('${a['admissionStatus']}'),
                        ],
                      ),
                      subtitle: Text(
                        'Patient #${a['patientId']} · Dr #${a['admittingDoctorId']} · '
                        'bed #${a['currentBedId']} · ${_date(a['admittedAt'])}',
                        style: theme.textTheme.bodySmall,
                      ),
                      trailing: const Icon(Icons.chevron_right),
                    );
                  },
                ),
        ),
      ],
    );
  }

  Widget _admissionDetail(ThemeData theme, Map<String, dynamic> a) {
    final id = a['id'] as int;
    final isAdmitted = '${a['admissionStatus']}' == 'ADMITTED';
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        TextButton.icon(
          onPressed: () => setState(() => _selected = null),
          icon: const Icon(Icons.arrow_back, size: 18),
          label: const Text('Back'),
        ),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(Space.lg),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Text('${a['admissionNumber']}',
                        style: theme.textTheme.titleMedium),
                    const SizedBox(width: Space.sm),
                    StatusChip.auto('${a['admissionStatus']}'),
                    const Spacer(),
                    if (isAdmitted && _canTransfer) ...[
                      OutlinedButton(
                        onPressed: _loading ? null : () => _transferDialog(id),
                        child: const Text('Transfer'),
                      ),
                      const SizedBox(width: Space.sm),
                    ],
                    if (isAdmitted && _canDischarge)
                      FilledButton(
                        onPressed: _loading ? null : () => _dischargeDialog(id),
                        child: const Text('Discharge'),
                      ),
                  ],
                ),
                const SizedBox(height: Space.sm),
                Wrap(
                  spacing: Space.lg,
                  runSpacing: Space.sm,
                  children: [
                    _kv(theme, 'Patient', '#${a['patientId']}'),
                    _kv(theme, 'Doctor', '#${a['admittingDoctorId']}'),
                    _kv(theme, 'Bed', '#${a['currentBedId']}'),
                    _kv(theme, 'Admitted', _date(a['admittedAt'])),
                    if (a['dischargedAt'] != null)
                      _kv(theme, 'Discharged', _date(a['dischargedAt'])),
                    if (a['dischargeType'] != null)
                      _kv(theme, 'Type', '${a['dischargeType']}'),
                    _kv(theme, 'Bed charge', '₹${a['totalBedCharge'] ?? 0}'),
                  ],
                ),
                if (a['diagnosis'] != null) ...[
                  const SizedBox(height: Space.sm),
                  Text('Diagnosis: ${a['diagnosis']}',
                      style: theme.textTheme.bodyMedium),
                ],
              ],
            ),
          ),
        ),
        const SizedBox(height: Space.md),
        Text('Bed allocations', style: theme.textTheme.titleMedium),
        const SizedBox(height: Space.sm),
        Expanded(
          child: _allocations.isEmpty
              ? Center(
                  child: Text('No allocations',
                      style: theme.textTheme.bodySmall?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant)))
              : ListView.separated(
                  itemCount: _allocations.length,
                  separatorBuilder: (_, __) => const Divider(height: 1),
                  itemBuilder: (context, i) {
                    final al = _allocations[i];
                    final active = al['isActive'] == true;
                    return ListTile(
                      dense: true,
                      leading: Icon(active ? Icons.bed : Icons.bed_outlined,
                          color: active ? theme.colorScheme.primary : null),
                      title: Text('Bed #${al['bedId']} · ${al['chargeModel']} '
                          '@ ₹${al['tariffRate']}'),
                      subtitle: Text(
                        '${_date(al['allocatedAt'])}'
                        '${al['releasedAt'] != null ? ' → ${_date(al['releasedAt'])}' : ' (current)'}'
                        ' · ${al['unitsCharged'] ?? 0} units',
                        style: theme.textTheme.bodySmall,
                      ),
                      trailing: Text('₹${al['allocationCharge'] ?? 0}',
                          style: theme.textTheme.titleSmall),
                    );
                  },
                ),
        ),
      ],
    );
  }

  // ---------------- bed board ----------------

  Widget _bedBoardTab(ThemeData theme) {
    if (_beds.isEmpty) {
      return Center(
        child: Text(_loading ? 'Loading…' : 'No beds configured',
            style: theme.textTheme.bodyMedium
                ?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
      );
    }
    return SingleChildScrollView(
      child: Wrap(
        spacing: Space.md,
        runSpacing: Space.md,
        children: [
          for (final b in _beds)
            SizedBox(
              width: 180,
              child: Card(
                child: Padding(
                  padding: const EdgeInsets.all(Space.md),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Icon(_bedIcon('${b['bedStatus']}'), size: 18,
                              color: _bedColor(theme, '${b['bedStatus']}')),
                          const SizedBox(width: Space.xs),
                          Text('Bed ${b['bedNumber']}',
                              style: theme.textTheme.titleSmall),
                        ],
                      ),
                      const SizedBox(height: Space.xs),
                      StatusChip.auto('${b['bedStatus']}'),
                      const SizedBox(height: Space.xs),
                      Text('${b['chargeModel']} · ₹${b['tariffRate'] ?? 0}',
                          style: theme.textTheme.bodySmall?.copyWith(
                              color: theme.colorScheme.onSurfaceVariant)),
                    ],
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }

  IconData _bedIcon(String status) => switch (status) {
        'VACANT' => Icons.bed_outlined,
        'OCCUPIED' => Icons.bed,
        'ISOLATION' => Icons.coronavirus_outlined,
        'MAINTENANCE' => Icons.build_outlined,
        _ => Icons.event_seat_outlined,
      };

  Color? _bedColor(ThemeData theme, String status) => switch (status) {
        'VACANT' => StatusColors.success,
        'OCCUPIED' => theme.colorScheme.primary,
        'ISOLATION' => StatusColors.danger,
        'MAINTENANCE' => StatusColors.warning,
        _ => theme.colorScheme.onSurfaceVariant,
      };

  // ---------------- actions ----------------

  Future<void> _admitDialog() async {
    if (_vacantBeds.isEmpty) {
      setState(() => _error = 'No vacant beds available');
      return;
    }
    Map<String, dynamic>? selectedPatient;
    final doctorId = TextEditingController();
    final diagnosis = TextEditingController();
    final notes = TextEditingController();
    String bedId = '${_vacantBeds.first['id']}';
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setD) => AlertDialog(
          title: const Text('Admit patient'),
          content: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
            child: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  selectedPatient == null
                      ? Align(
                          alignment: Alignment.centerLeft,
                          child: OutlinedButton.icon(
                            onPressed: () async {
                              final p = await showPatientPicker(context);
                              if (p != null) setD(() => selectedPatient = p);
                            },
                            icon: const Icon(Icons.search, size: 18),
                            label: const Text('Select patient *'),
                          ),
                        )
                      : ListTile(
                          contentPadding: EdgeInsets.zero,
                          title: Text('${selectedPatient!['fullName']}'),
                          subtitle: Text('UHID ${selectedPatient!['uhid']}'),
                          trailing: TextButton(
                            onPressed: () async {
                              final p = await showPatientPicker(context);
                              if (p != null) setD(() => selectedPatient = p);
                            },
                            child: const Text('Change'),
                          ),
                        ),
                  _field(doctorId, 'Doctor ID *', number: true),
                  DropdownButtonFormField<String>(
                    value: bedId,
                    isExpanded: true,
                    decoration: const InputDecoration(labelText: 'Bed *'),
                    items: [
                      for (final b in _vacantBeds)
                        DropdownMenuItem(
                          value: '${b['id']}',
                          child: Text('Bed ${b['bedNumber']} · ${b['chargeModel']} '
                              '· ₹${b['tariffRate'] ?? 0}'),
                        ),
                    ],
                    onChanged: (v) => setD(() => bedId = v ?? bedId),
                  ),
                  _field(diagnosis, 'Diagnosis'),
                  _field(notes, 'Notes'),
                ],
              ),
            ),
          ),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(context, false),
                child: const Text('Cancel')),
            FilledButton(
                onPressed: () => Navigator.pop(context, true),
                child: const Text('Admit')),
          ],
        ),
      ),
    );
    if (ok != true) return;
    final did = int.tryParse(doctorId.text.trim());
    if (selectedPatient == null || did == null) {
      setState(() => _error = 'Select a patient and enter a valid doctor ID');
      return;
    }
    await _post('/api/v1/ipd/admissions', {
      'patientId': selectedPatient!['id'],
      'doctorId': did,
      'bedId': int.parse(bedId),
      if (diagnosis.text.trim().isNotEmpty) 'diagnosis': diagnosis.text.trim(),
      if (notes.text.trim().isNotEmpty) 'notes': notes.text.trim(),
    }, 'Patient admitted', reloadDetailId: null);
  }

  Future<void> _transferDialog(int id) async {
    if (_vacantBeds.isEmpty) {
      setState(() => _error = 'No vacant beds available');
      return;
    }
    String bedId = '${_vacantBeds.first['id']}';
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setD) => AlertDialog(
          title: const Text('Transfer bed'),
          content: DropdownButtonFormField<String>(
            value: bedId,
            isExpanded: true,
            decoration: const InputDecoration(labelText: 'New bed *'),
            items: [
              for (final b in _vacantBeds)
                DropdownMenuItem(
                    value: '${b['id']}',
                    child: Text('Bed ${b['bedNumber']} · ${b['chargeModel']}')),
            ],
            onChanged: (v) => setD(() => bedId = v ?? bedId),
          ),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(context, false),
                child: const Text('Cancel')),
            FilledButton(
                onPressed: () => Navigator.pop(context, true),
                child: const Text('Transfer')),
          ],
        ),
      ),
    );
    if (ok != true) return;
    await _post('/api/v1/ipd/admissions/$id/transfer', {'newBedId': int.parse(bedId)},
        'Bed transferred', reloadDetailId: id);
  }

  Future<void> _dischargeDialog(int id) async {
    String type = 'NORMAL';
    var billCleared = false;
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setD) => AlertDialog(
          title: const Text('Discharge patient'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              DropdownButtonFormField<String>(
                value: type,
                decoration: const InputDecoration(labelText: 'Discharge type *'),
                items: const [
                  DropdownMenuItem(value: 'NORMAL', child: Text('Normal')),
                  DropdownMenuItem(value: 'LAMA', child: Text('LAMA')),
                  DropdownMenuItem(value: 'DEATH', child: Text('Death')),
                ],
                onChanged: (v) => setD(() => type = v ?? 'NORMAL'),
              ),
              const SizedBox(height: Space.sm),
              CheckboxListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('Final bill cleared'),
                value: billCleared,
                onChanged: (v) => setD(() => billCleared = v ?? false),
              ),
            ],
          ),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(context, false),
                child: const Text('Cancel')),
            FilledButton(
                onPressed: () => Navigator.pop(context, true),
                child: const Text('Discharge')),
          ],
        ),
      ),
    );
    if (ok != true) return;
    await _post('/api/v1/ipd/admissions/$id/discharge', {
      'dischargeType': type,
      'acknowledgedChecklistItems':
          billCleared ? ['FINAL_BILL_CLEARED'] : <String>[],
    }, 'Patient discharged', reloadDetailId: id);
  }

  Future<void> _post(String path, Map<String, dynamic> body, String ok,
      {int? reloadDetailId}) async {
    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });
    try {
      final api = context.read<ApiClient>();
      await api.post<Map<String, dynamic>>(path, body,
          fromJson: (json) => json as Map<String, dynamic>);
      setState(() => _info = ok);
      await _loadAll();
      if (reloadDetailId != null) {
        await _openAdmission(reloadDetailId);
      }
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  // ---------------- helpers ----------------

  Widget _kv(ThemeData theme, String k, String v) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(k,
            style: theme.textTheme.bodySmall
                ?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
        Text(v, style: theme.textTheme.titleSmall),
      ],
    );
  }

  Widget _field(TextEditingController c, String label, {bool number = false}) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: Space.xs),
      child: TextField(
        controller: c,
        keyboardType: number ? TextInputType.number : TextInputType.text,
        decoration: InputDecoration(labelText: label),
      ),
    );
  }

  String _date(Object? iso) {
    if (iso == null) return '—';
    final s = '$iso';
    return s.contains('T') ? s.split('T').first : s;
  }
}
