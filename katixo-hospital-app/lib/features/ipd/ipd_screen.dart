import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/auth/auth_state.dart';
import '../../core/realtime/board_socket.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/util/step_up.dart';
import '../../core/widgets/empty_state.dart';
import '../../core/widgets/section_card.dart';
import '../../core/widgets/status_chip.dart';
import '../discharge/discharge_summary_screen.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;
import '../nursing/vitals_screen.dart';
import '../patient/patient_picker.dart';
import '../staff/doctor_picker.dart';

/// IPD inpatient management: occupancy overview, current inpatients, a
/// ward-grouped bed board, admission detail (transfer/discharge) and bed-master
/// setup. Role-aware (backend also enforces). Body widget — host supplies AppShell.
class IpdScreen extends StatefulWidget {
  const IpdScreen({super.key});

  @override
  State<IpdScreen> createState() => _IpdScreenState();
}

class _IpdScreenState extends State<IpdScreen> {
  int _tab = 0; // 0 inpatients, 1 bed board, 2 setup

  List<Map<String, dynamic>> _inpatients = const [];
  List<Map<String, dynamic>> _beds = const [];
  List<Map<String, dynamic>> _wards = const [];
  List<Map<String, dynamic>> _rooms = const [];
  Map<String, dynamic>? _selected; // admission detail
  List<Map<String, dynamic>> _allocations = const [];

  bool _loading = false;
  String? _error;
  String? _info;
  String _role = '';
  BoardSocket? _socket;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      _role = context.read<AuthState>().currentUser?.role ?? '';
      _loadAll();
      // Real-time bed-board nudge (admit / transfer / discharge).
      _socket = BoardSocket(
        baseUrl: context.read<ApiClient>().baseUrl,
        token: context.read<AuthState>().token ?? '',
        onTopic: (t) {
          if (t == 'beds' && mounted) _loadAll();
        },
      )..connect();
    });
  }

  @override
  void dispose() {
    _socket?.dispose();
    super.dispose();
  }

  bool get _isAdmin => _role == 'ADMIN';
  bool get _canAdmit => _role == 'FRONT_DESK' || _role == 'ADMIN';
  bool get _canTransfer => _role == 'FRONT_DESK' || _role == 'NURSE' || _role == 'ADMIN';
  bool get _canDischarge => _role == 'DOCTOR' || _role == 'ADMIN';
  bool get _canViewVitals =>
      _role == 'NURSE' || _role == 'DOCTOR' || _role == 'FRONT_DESK' || _role == 'ADMIN' || _role == 'SUPER_ADMIN';

  Future<void> _loadAll() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      List<Map<String, dynamic>> l(dynamic j) =>
          List<Map<String, dynamic>>.from(j as List? ?? const []);
      final beds = await api.get('/api/v1/ipd/beds', fromJson: l);
      final wards = await api.get('/api/v1/ipd/wards', fromJson: l);
      final rooms = await api.get('/api/v1/ipd/rooms', fromJson: l);
      final inp = await api.get('/api/v1/ipd/admissions?status=ADMITTED', fromJson: l);
      if (mounted) {
        setState(() {
          _beds = beds;
          _wards = wards;
          _rooms = rooms;
          _inpatients = inp;
        });
      }
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _openAdmission(int id) async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      final a = await api.get<Map<String, dynamic>>('/api/v1/ipd/admissions/$id',
          fromJson: (j) => j as Map<String, dynamic>);
      final allocs = await api.get<List<Map<String, dynamic>>>(
          '/api/v1/ipd/admissions/$id/allocations',
          fromJson: (j) => List<Map<String, dynamic>>.from(j as List? ?? const []));
      if (mounted) setState(() {
        _selected = a;
        _allocations = allocs;
      });
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  List<Map<String, dynamic>> get _vacantBeds =>
      _beds.where((b) => '${b['bedStatus']}' == 'VACANT').toList();

  int _countStatus(String s) =>
      _beds.where((b) => '${b['bedStatus']}' == s).length;

  // ---------------- build ----------------

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final segments = <ButtonSegment<int>>[
      const ButtonSegment(value: 0, label: Text('Inpatients'), icon: Icon(Icons.people_alt_outlined)),
      const ButtonSegment(value: 1, label: Text('Bed board'), icon: Icon(Icons.grid_view_outlined)),
      if (_isAdmin)
        const ButtonSegment(value: 2, label: Text('Setup'), icon: Icon(Icons.settings_outlined)),
    ];
    final tab = (_tab == 2 && !_isAdmin) ? 0 : _tab;

    return PageContainer(
      scrollable: false,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text('IPD — Inpatient Management', style: theme.textTheme.titleLarge),
              const Spacer(),
              IconButton(
                tooltip: 'Refresh',
                onPressed: _loading ? null : _loadAll,
                icon: const Icon(Icons.refresh),
              ),
            ],
          ),
          const SizedBox(height: Space.md),
          _occupancyStrip(theme),
          const SizedBox(height: Space.md),
          SegmentedButton<int>(
            segments: segments,
            selected: {tab},
            showSelectedIcon: false,
            onSelectionChanged: (s) => setState(() => _tab = s.first),
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
          Expanded(
            child: switch (tab) {
              1 => _bedBoardTab(theme),
              2 => _setupTab(theme),
              _ => _inpatientsTab(theme),
            },
          ),
        ],
      ),
    );
  }

  Widget _occupancyStrip(ThemeData theme) {
    final total = _beds.length;
    final occ = _countStatus('OCCUPIED');
    final pct = total == 0 ? 0 : (occ * 100 / total).round();
    return Wrap(
      spacing: Space.md,
      runSpacing: Space.sm,
      children: [
        _stat(theme, 'Total beds', '$total', theme.colorScheme.primary, Icons.hotel_outlined),
        _stat(theme, 'Occupied', '$occ', theme.colorScheme.primary, Icons.bed),
        _stat(theme, 'Vacant', '${_countStatus('VACANT')}', StatusColors.success, Icons.bed_outlined),
        _stat(theme, 'Isolation', '${_countStatus('ISOLATION')}', StatusColors.danger, Icons.coronavirus_outlined),
        _stat(theme, 'Occupancy', '$pct%', pct >= 85 ? StatusColors.danger : StatusColors.info, Icons.pie_chart_outline),
        _stat(theme, 'Inpatients', '${_inpatients.length}', theme.colorScheme.primary, Icons.people_alt_outlined),
      ],
    );
  }

  Widget _stat(ThemeData theme, String label, String value, Color color, IconData icon) {
    return Container(
      width: 168,
      padding: const EdgeInsets.all(Space.md),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.08),
        borderRadius: Corners.mdRadius,
        border: Border.all(color: color.withValues(alpha: 0.25)),
      ),
      child: Row(
        children: [
          Icon(icon, size: 22, color: color),
          const SizedBox(width: Space.sm),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(label,
                    style: theme.textTheme.labelSmall
                        ?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
                Text(value, style: theme.textTheme.titleLarge),
              ],
            ),
          ),
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
            Text('Current inpatients', style: theme.textTheme.titleMedium),
            const Spacer(),
            if (_canAdmit)
              FilledButton.icon(
                onPressed: _loading ? null : _admitDialog,
                icon: const Icon(Icons.person_add_alt_1, size: 18),
                label: const Text('Admit patient'),
              ),
          ],
        ),
        const SizedBox(height: Space.sm),
        Expanded(
          child: _inpatients.isEmpty
              ? EmptyState(
                  icon: Icons.bed_outlined,
                  title: _loading ? 'Loading…' : 'No current inpatients',
                  message: _canAdmit ? 'Admit a patient to get started.' : null,
                  action: _canAdmit && !_loading
                      ? FilledButton.icon(
                          onPressed: _admitDialog,
                          icon: const Icon(Icons.person_add_alt_1, size: 18),
                          label: const Text('Admit patient'))
                      : null,
                )
              : ListView.separated(
                  itemCount: _inpatients.length,
                  separatorBuilder: (_, __) => const SizedBox(height: Space.sm),
                  itemBuilder: (context, i) {
                    final a = _inpatients[i];
                    return Card(
                      child: ListTile(
                        onTap: () => _openAdmission(a['id'] as int),
                        leading: CircleAvatar(
                          backgroundColor: theme.colorScheme.primaryContainer,
                          child: Icon(Icons.personal_injury_outlined,
                              color: theme.colorScheme.onPrimaryContainer),
                        ),
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
                          'bed #${a['currentBedId']} · since ${_date(a['admittedAt'])}',
                          style: theme.textTheme.bodySmall,
                        ),
                        trailing: const Icon(Icons.chevron_right),
                      ),
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
    return SingleChildScrollView(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          TextButton.icon(
            onPressed: () => setState(() => _selected = null),
            icon: const Icon(Icons.arrow_back, size: 18),
            label: const Text('Back to inpatients'),
          ),
          SectionCard(
            title: '${a['admissionNumber']}',
            icon: Icons.assignment_outlined,
            action: Wrap(
              spacing: Space.sm,
              children: [
                StatusChip.auto('${a['admissionStatus']}'),
                if (isAdmitted && _canTransfer)
                  OutlinedButton.icon(
                    onPressed: _loading ? null : () => _transferDialog(id),
                    icon: const Icon(Icons.swap_horiz, size: 18),
                    label: const Text('Transfer'),
                  ),
                if (_canViewVitals)
                  OutlinedButton.icon(
                    onPressed: () => _openVitals(id, a['patientId'] as int?),
                    icon: const Icon(Icons.monitor_heart_outlined, size: 18),
                    label: const Text('Vitals'),
                  ),
                if (_canDischarge)
                  OutlinedButton.icon(
                    onPressed: () => _openDischargeSummary(id),
                    icon: const Icon(Icons.description_outlined, size: 18),
                    label: const Text('Discharge Summary'),
                  ),
                if (isAdmitted && _canDischarge)
                  FilledButton.icon(
                    onPressed: _loading ? null : () => _dischargeDialog(id),
                    icon: const Icon(Icons.logout, size: 18),
                    label: const Text('Discharge'),
                  ),
              ],
            ),
            child: Wrap(
              spacing: Space.xl,
              runSpacing: Space.md,
              children: [
                _kv(theme, 'Patient', '#${a['patientId']}'),
                _kv(theme, 'Doctor', '#${a['admittingDoctorId']}'),
                _kv(theme, 'Current bed', '#${a['currentBedId']}'),
                _kv(theme, 'Admitted', _date(a['admittedAt'])),
                if (a['dischargedAt'] != null) _kv(theme, 'Discharged', _date(a['dischargedAt'])),
                if (a['dischargeType'] != null) _kv(theme, 'Discharge type', '${a['dischargeType']}'),
                _kv(theme, 'Bed charge', '₹${a['totalBedCharge'] ?? 0}'),
                if (a['diagnosis'] != null) _kv(theme, 'Diagnosis', '${a['diagnosis']}'),
              ],
            ),
          ),
          const SizedBox(height: Space.md),
          SectionCard(
            title: 'Bed allocations',
            icon: Icons.timeline_outlined,
            child: _allocations.isEmpty
                ? Text('No allocations',
                    style: theme.textTheme.bodySmall
                        ?.copyWith(color: theme.colorScheme.onSurfaceVariant))
                : Column(
                    children: [
                      for (final al in _allocations)
                        ListTile(
                          dense: true,
                          contentPadding: EdgeInsets.zero,
                          leading: Icon(al['isActive'] == true ? Icons.bed : Icons.bed_outlined,
                              color: al['isActive'] == true ? theme.colorScheme.primary : null),
                          title: Text('Bed #${al['bedId']} · ${al['chargeModel']} @ ₹${al['tariffRate']}'),
                          subtitle: Text(
                            '${_date(al['allocatedAt'])}'
                            '${al['releasedAt'] != null ? ' → ${_date(al['releasedAt'])}' : ' (current)'}'
                            ' · ${al['unitsCharged'] ?? 0} units',
                            style: theme.textTheme.bodySmall,
                          ),
                          trailing: Text('₹${al['allocationCharge'] ?? 0}',
                              style: theme.textTheme.titleSmall),
                        ),
                    ],
                  ),
          ),
        ],
      ),
    );
  }

  // ---------------- bed board ----------------

  Widget _bedBoardTab(ThemeData theme) {
    if (_beds.isEmpty) {
      return EmptyState(
        icon: Icons.grid_view_outlined,
        title: _loading ? 'Loading…' : 'No beds configured',
        message: _isAdmin ? 'Use the Setup tab to add wards, rooms and beds.' : null,
        action: _isAdmin && !_loading
            ? FilledButton.icon(
                onPressed: () => setState(() => _tab = 2),
                icon: const Icon(Icons.settings_outlined, size: 18),
                label: const Text('Open setup'))
            : null,
      );
    }
    final roomById = {for (final r in _rooms) '${r['id']}': r};
    final wardById = {for (final w in _wards) '${w['id']}': w};
    // group beds by ward id (via room), '' = unassigned
    final byWard = <String, List<Map<String, dynamic>>>{};
    for (final b in _beds) {
      final room = roomById['${b['roomId']}'];
      final wardId = room == null ? '' : '${room['wardId']}';
      byWard.putIfAbsent(wardId, () => []).add(b);
    }
    final wardIds = byWard.keys.toList()
      ..sort((a, c) => '${wardById[a]?['name'] ?? 'zzz'}'
          .compareTo('${wardById[c]?['name'] ?? 'zzz'}'));

    return SingleChildScrollView(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _legend(theme),
          const SizedBox(height: Space.md),
          for (final wid in wardIds) ...[
            SectionCard(
              title: wardById[wid] == null
                  ? 'Unassigned'
                  : '${wardById[wid]!['name']} · ${wardById[wid]!['wardType']}',
              icon: Icons.meeting_room_outlined,
              subtitle: '${byWard[wid]!.length} beds',
              child: Wrap(
                spacing: Space.md,
                runSpacing: Space.md,
                children: [
                  for (final b in byWard[wid]!) _bedTile(theme, b, roomById),
                ],
              ),
            ),
            const SizedBox(height: Space.md),
          ],
        ],
      ),
    );
  }

  Widget _legend(ThemeData theme) {
    Widget dot(String label, Color c) => Row(mainAxisSize: MainAxisSize.min, children: [
          Container(width: 10, height: 10, decoration: BoxDecoration(color: c, shape: BoxShape.circle)),
          const SizedBox(width: Space.xs),
          Text(label, style: theme.textTheme.bodySmall),
        ]);
    return Wrap(spacing: Space.lg, runSpacing: Space.xs, children: [
      dot('Vacant', StatusColors.success),
      dot('Occupied', theme.colorScheme.primary),
      dot('Isolation', StatusColors.danger),
      dot('Maintenance', StatusColors.warning),
      dot('Reserved', StatusColors.info),
    ]);
  }

  Widget _bedTile(ThemeData theme, Map<String, dynamic> b, Map<String, Map<String, dynamic>> roomById) {
    final status = '${b['bedStatus']}';
    final color = _bedColor(theme, status);
    final room = roomById['${b['roomId']}'];
    return Container(
      width: 156,
      padding: const EdgeInsets.all(Space.md),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.06),
        borderRadius: Corners.mdRadius,
        border: Border.all(color: color.withValues(alpha: 0.5)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(_bedIcon(status), size: 18, color: color),
              const SizedBox(width: Space.xs),
              Text('Bed ${b['bedNumber']}', style: theme.textTheme.titleSmall),
            ],
          ),
          if (room != null)
            Text('Room ${room['roomNumber']}',
                style: theme.textTheme.bodySmall
                    ?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
          const SizedBox(height: Space.xs),
          StatusChip.auto(status),
          const SizedBox(height: Space.xs),
          Text('${b['chargeModel']} · ₹${b['tariffRate'] ?? 0}',
              style: theme.textTheme.bodySmall
                  ?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
        ],
      ),
    );
  }

  IconData _bedIcon(String s) => switch (s) {
        'VACANT' => Icons.bed_outlined,
        'OCCUPIED' => Icons.bed,
        'ISOLATION' => Icons.coronavirus_outlined,
        'MAINTENANCE' => Icons.build_outlined,
        'RESERVED' => Icons.event_seat_outlined,
        _ => Icons.event_seat_outlined,
      };

  Color _bedColor(ThemeData theme, String s) => switch (s) {
        'VACANT' => StatusColors.success,
        'OCCUPIED' => theme.colorScheme.primary,
        'ISOLATION' => StatusColors.danger,
        'MAINTENANCE' => StatusColors.warning,
        'RESERVED' => StatusColors.info,
        _ => theme.colorScheme.onSurfaceVariant,
      };

  // ---------------- setup (admin) ----------------

  Widget _setupTab(ThemeData theme) {
    return SingleChildScrollView(
      child: Column(
        children: [
          SectionCard(
            title: 'Wards',
            icon: Icons.apartment_outlined,
            subtitle: '${_wards.length} configured',
            action: FilledButton.icon(
              onPressed: _loading ? null : _addWardDialog,
              icon: const Icon(Icons.add, size: 18),
              label: const Text('Add ward'),
            ),
            child: _wards.isEmpty
                ? Text('No wards yet',
                    style: theme.textTheme.bodySmall
                        ?.copyWith(color: theme.colorScheme.onSurfaceVariant))
                : Wrap(
                    spacing: Space.sm,
                    runSpacing: Space.sm,
                    children: [
                      for (final w in _wards)
                        Chip(
                          avatar: const Icon(Icons.apartment, size: 16),
                          label: Text('${w['name']} · ${w['wardType']}'),
                        ),
                    ],
                  ),
          ),
          const SizedBox(height: Space.md),
          SectionCard(
            title: 'Rooms',
            icon: Icons.meeting_room_outlined,
            subtitle: '${_rooms.length} configured',
            action: FilledButton.icon(
              onPressed: _loading || _wards.isEmpty ? null : _addRoomDialog,
              icon: const Icon(Icons.add, size: 18),
              label: const Text('Add room'),
            ),
            child: _rooms.isEmpty
                ? Text(_wards.isEmpty ? 'Add a ward first' : 'No rooms yet',
                    style: theme.textTheme.bodySmall
                        ?.copyWith(color: theme.colorScheme.onSurfaceVariant))
                : Wrap(
                    spacing: Space.sm,
                    runSpacing: Space.sm,
                    children: [
                      for (final r in _rooms)
                        Chip(
                          avatar: const Icon(Icons.meeting_room, size: 16),
                          label: Text('Room ${r['roomNumber']}'),
                        ),
                    ],
                  ),
          ),
          const SizedBox(height: Space.md),
          SectionCard(
            title: 'Beds',
            icon: Icons.hotel_outlined,
            subtitle: '${_beds.length} configured',
            action: FilledButton.icon(
              onPressed: _loading || _rooms.isEmpty ? null : _addBedDialog,
              icon: const Icon(Icons.add, size: 18),
              label: const Text('Add bed'),
            ),
            child: _beds.isEmpty
                ? Text(_rooms.isEmpty ? 'Add a room first' : 'No beds yet',
                    style: theme.textTheme.bodySmall
                        ?.copyWith(color: theme.colorScheme.onSurfaceVariant))
                : Text('${_countStatus('VACANT')} vacant · ${_countStatus('OCCUPIED')} occupied',
                    style: theme.textTheme.bodyMedium),
          ),
        ],
      ),
    );
  }

  // ---------------- actions ----------------

  Future<void> _admitDialog() async {
    if (_vacantBeds.isEmpty) {
      setState(() => _error = 'No vacant beds available');
      return;
    }
    Map<String, dynamic>? patient;
    Map<String, dynamic>? doctor;
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
                  _pickRow(theme: Theme.of(context), label: 'Patient', value: patient?['fullName'],
                      sub: patient == null ? null : 'UHID ${patient!['uhid']}',
                      onPick: () async {
                        final p = await showPatientPicker(context);
                        if (p != null) setD(() => patient = p);
                      }),
                  const SizedBox(height: Space.sm),
                  _pickRow(theme: Theme.of(context), label: 'Doctor', value: doctor?['name'],
                      sub: doctor == null ? null : '${doctor!['specialisation'] ?? 'General'}',
                      onPick: () async {
                        final d = await showDoctorPicker(context);
                        if (d != null) setD(() => doctor = d);
                      }),
                  const SizedBox(height: Space.sm),
                  DropdownButtonFormField<String>(
                    value: bedId,
                    isExpanded: true,
                    decoration: const InputDecoration(labelText: 'Bed *'),
                    items: [
                      for (final b in _vacantBeds)
                        DropdownMenuItem(
                          value: '${b['id']}',
                          child: Text('Bed ${b['bedNumber']} · ${b['chargeModel']} · ₹${b['tariffRate'] ?? 0}'),
                        ),
                    ],
                    onChanged: (v) => setD(() => bedId = v ?? bedId),
                  ),
                  const SizedBox(height: Space.sm),
                  TextField(controller: diagnosis,
                      decoration: const InputDecoration(labelText: 'Diagnosis')),
                  const SizedBox(height: Space.sm),
                  TextField(controller: notes,
                      decoration: const InputDecoration(labelText: 'Notes')),
                ],
              ),
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
            FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Admit')),
          ],
        ),
      ),
    );
    if (ok != true) return;
    if (patient == null || doctor == null) {
      setState(() => _error = 'Select both a patient and a doctor');
      return;
    }
    await _post('/api/v1/ipd/admissions', {
      'patientId': patient!['id'],
      'doctorId': doctor!['id'],
      'bedId': int.parse(bedId),
      if (diagnosis.text.trim().isNotEmpty) 'diagnosis': diagnosis.text.trim(),
      if (notes.text.trim().isNotEmpty) 'notes': notes.text.trim(),
    }, 'Patient admitted');
  }

  Widget _pickRow({
    required ThemeData theme,
    required String label,
    required Object? value,
    String? sub,
    required Future<void> Function() onPick,
  }) {
    if (value == null) {
      return Align(
        alignment: Alignment.centerLeft,
        child: OutlinedButton.icon(
          onPressed: onPick,
          icon: const Icon(Icons.search, size: 18),
          label: Text('Select $label *'),
        ),
      );
    }
    return Container(
      decoration: BoxDecoration(
        borderRadius: Corners.smRadius,
        border: Border.all(color: theme.colorScheme.outlineVariant),
      ),
      child: ListTile(
        dense: true,
        title: Text('$value'),
        subtitle: sub == null ? null : Text(sub),
        trailing: TextButton(onPressed: onPick, child: const Text('Change')),
      ),
    );
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
                DropdownMenuItem(value: '${b['id']}',
                    child: Text('Bed ${b['bedNumber']} · ${b['chargeModel']}')),
            ],
            onChanged: (v) => setD(() => bedId = v ?? bedId),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
            FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Transfer')),
          ],
        ),
      ),
    );
    if (ok != true) return;
    await _post('/api/v1/ipd/admissions/$id/transfer', {'newBedId': int.parse(bedId)},
        'Bed transferred', reloadDetailId: id);
  }

  String _checklistLabel(String code) => code
      .toLowerCase()
      .split('_')
      .map((w) => w.isEmpty ? w : '${w[0].toUpperCase()}${w.substring(1)}')
      .join(' ');

  Future<void> _dischargeDialog(int id) async {
    // Pull the hospital's policy-driven checklist so the dialog renders the right items.
    List<String> blocking = const [];
    List<String> warning = const [];
    try {
      final api = context.read<ApiClient>();
      final cl = await api.get<Map<String, dynamic>>('/api/v1/ipd/discharge-checklist',
          fromJson: (j) => j as Map<String, dynamic>);
      blocking = List<String>.from(cl['blockingItems'] as List? ?? const []);
      warning = List<String>.from(cl['warningItems'] as List? ?? const []);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
      return;
    }

    String type = 'NORMAL';
    final ticked = <String, bool>{
      for (final c in blocking) c: false,
      for (final c in warning) c: false,
    };

    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setD) {
          // Blocking items only gate a NORMAL discharge; LAMA/DEATH bypass (server-enforced too).
          final blockingSatisfied =
              type != 'NORMAL' || blocking.every((c) => ticked[c] == true);
          return AlertDialog(
            title: const Text('Discharge patient'),
            content: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  DropdownButtonFormField<String>(
                    value: type,
                    decoration: const InputDecoration(labelText: 'Discharge type *'),
                    items: const [
                      DropdownMenuItem(value: 'NORMAL', child: Text('Normal')),
                      DropdownMenuItem(value: 'LAMA', child: Text('LAMA (against advice)')),
                      DropdownMenuItem(value: 'DEATH', child: Text('Death')),
                    ],
                    onChanged: (v) => setD(() => type = v ?? 'NORMAL'),
                  ),
                  if (blocking.isNotEmpty) ...[
                    const SizedBox(height: Space.md),
                    Text('Required before discharge',
                        style: Theme.of(context).textTheme.labelLarge),
                    if (type != 'NORMAL')
                      Padding(
                        padding: const EdgeInsets.only(top: Space.xs),
                        child: Text('Not enforced for $type — acknowledge if applicable',
                            style: Theme.of(context).textTheme.bodySmall),
                      ),
                    for (final c in blocking)
                      CheckboxListTile(
                        contentPadding: EdgeInsets.zero,
                        dense: true,
                        title: Text(_checklistLabel(c)),
                        value: ticked[c],
                        onChanged: (v) => setD(() => ticked[c] = v ?? false),
                      ),
                  ],
                  if (warning.isNotEmpty) ...[
                    const SizedBox(height: Space.sm),
                    Text('Advisory', style: Theme.of(context).textTheme.labelLarge),
                    for (final c in warning)
                      CheckboxListTile(
                        contentPadding: EdgeInsets.zero,
                        dense: true,
                        title: Text(_checklistLabel(c)),
                        value: ticked[c],
                        onChanged: (v) => setD(() => ticked[c] = v ?? false),
                      ),
                  ],
                ],
              ),
            ),
            actions: [
              TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
              FilledButton(
                onPressed: blockingSatisfied ? () => Navigator.pop(context, true) : null,
                child: const Text('Discharge'),
              ),
            ],
          );
        },
      ),
    );
    if (ok != true) return;
    final acknowledged = [
      for (final e in ticked.entries)
        if (e.value) e.key
    ];
    await _post('/api/v1/ipd/admissions/$id/discharge', {
      'dischargeType': type,
      'acknowledgedChecklistItems': acknowledged,
    }, 'Patient discharged', reloadDetailId: id);
  }

  Future<void> _addWardDialog() async {
    final name = TextEditingController();
    String type = 'GENERAL';
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setD) => AlertDialog(
          title: const Text('Add ward'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(controller: name, decoration: const InputDecoration(labelText: 'Ward name *')),
              const SizedBox(height: Space.sm),
              DropdownButtonFormField<String>(
                value: type,
                decoration: const InputDecoration(labelText: 'Ward type'),
                items: const [
                  DropdownMenuItem(value: 'GENERAL', child: Text('General')),
                  DropdownMenuItem(value: 'ICU', child: Text('ICU')),
                  DropdownMenuItem(value: 'PRIVATE', child: Text('Private')),
                  DropdownMenuItem(value: 'SEMI_PRIVATE', child: Text('Semi-private')),
                  DropdownMenuItem(value: 'EMERGENCY', child: Text('Emergency')),
                ],
                onChanged: (v) => setD(() => type = v ?? 'GENERAL'),
              ),
            ],
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
            FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Save')),
          ],
        ),
      ),
    );
    if (ok != true || name.text.trim().isEmpty) return;
    await _post('/api/v1/ipd/wards', {'name': name.text.trim(), 'wardType': type}, 'Ward created');
  }

  Future<void> _addRoomDialog() async {
    String wardId = '${_wards.first['id']}';
    final roomNumber = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setD) => AlertDialog(
          title: const Text('Add room'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              DropdownButtonFormField<String>(
                value: wardId,
                isExpanded: true,
                decoration: const InputDecoration(labelText: 'Ward *'),
                items: [
                  for (final w in _wards)
                    DropdownMenuItem(value: '${w['id']}', child: Text('${w['name']}')),
                ],
                onChanged: (v) => setD(() => wardId = v ?? wardId),
              ),
              const SizedBox(height: Space.sm),
              TextField(controller: roomNumber, decoration: const InputDecoration(labelText: 'Room number *')),
            ],
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
            FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Save')),
          ],
        ),
      ),
    );
    if (ok != true || roomNumber.text.trim().isEmpty) return;
    await _post('/api/v1/ipd/rooms',
        {'wardId': int.parse(wardId), 'roomNumber': roomNumber.text.trim()}, 'Room created');
  }

  Future<void> _addBedDialog() async {
    String roomId = '${_rooms.first['id']}';
    final bedNumber = TextEditingController();
    final tariff = TextEditingController();
    String model = 'DAILY';
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setD) => AlertDialog(
          title: const Text('Add bed'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              DropdownButtonFormField<String>(
                value: roomId,
                isExpanded: true,
                decoration: const InputDecoration(labelText: 'Room *'),
                items: [
                  for (final r in _rooms)
                    DropdownMenuItem(value: '${r['id']}', child: Text('Room ${r['roomNumber']}')),
                ],
                onChanged: (v) => setD(() => roomId = v ?? roomId),
              ),
              const SizedBox(height: Space.sm),
              TextField(controller: bedNumber, decoration: const InputDecoration(labelText: 'Bed number *')),
              const SizedBox(height: Space.sm),
              DropdownButtonFormField<String>(
                value: model,
                decoration: const InputDecoration(labelText: 'Charge model'),
                items: const [
                  DropdownMenuItem(value: 'DAILY', child: Text('Daily')),
                  DropdownMenuItem(value: 'HOURLY', child: Text('Hourly')),
                  DropdownMenuItem(value: 'PACKAGE', child: Text('Package')),
                ],
                onChanged: (v) => setD(() => model = v ?? 'DAILY'),
              ),
              const SizedBox(height: Space.sm),
              TextField(controller: tariff, keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: 'Tariff rate', prefixText: '₹ ')),
            ],
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
            FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Save')),
          ],
        ),
      ),
    );
    if (ok != true || bedNumber.text.trim().isEmpty) return;
    await _post('/api/v1/ipd/beds', {
      'roomId': int.parse(roomId),
      'bedNumber': bedNumber.text.trim(),
      'chargeModel': model,
      if (tariff.text.trim().isNotEmpty) 'tariffRate': double.tryParse(tariff.text.trim()),
    }, 'Bed created');
  }

  void _openDischargeSummary(int admissionId) {
    showDialog(
      context: context,
      builder: (ctx) => Dialog(
        child: ConstrainedBox(
          constraints: BoxConstraints(
            maxWidth: 700,
            maxHeight: MediaQuery.of(ctx).size.height * 0.85,
          ),
          child: DischargeSummaryScreen(admissionId: admissionId),
        ),
      ),
    );
  }

  void _openVitals(int admissionId, int? patientId) {
    showDialog(
      context: context,
      builder: (ctx) => Dialog(
        child: ConstrainedBox(
          constraints: BoxConstraints(
            maxWidth: 700,
            maxHeight: MediaQuery.of(ctx).size.height * 0.85,
          ),
          child: VitalsScreen(admissionId: admissionId, patientId: patientId),
        ),
      ),
    );
  }

  Future<void> _post(String path, Map<String, dynamic> body, String ok, {int? reloadDetailId}) async {
    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });
    try {
      final api = context.read<ApiClient>();
      // Discharge sign-off is gated by step-up MFA; withStepUp re-issues with
      // the authenticator code if the server challenges. Other IPD posts are
      // never challenged, so this is transparent for them.
      final done = await withStepUp(context, (headers) => api.post<Map<String, dynamic>>(
          path, body, fromJson: (j) => j as Map<String, dynamic>, headers: headers));
      if (done == null) return; // user cancelled the step-up prompt
      setState(() => _info = ok);
      await _loadAll();
      if (reloadDetailId != null) await _openAdmission(reloadDetailId);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Widget _kv(ThemeData theme, String k, String v) {
    return SizedBox(
      width: 200,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(k, style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
          Text(v, style: theme.textTheme.titleSmall),
        ],
      ),
    );
  }

  String _date(Object? iso) {
    if (iso == null) return '—';
    final s = '$iso';
    return s.contains('T') ? s.split('T').first : s;
  }
}
