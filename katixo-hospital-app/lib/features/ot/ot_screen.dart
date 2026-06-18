import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/auth/auth_state.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/util/formatters.dart';
import '../../core/widgets/empty_state.dart';
import '../../core/widgets/status_chip.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;
import '../patient/patient_picker.dart';
import '../staff/doctor_picker.dart';

/// Operating-theatre scheduling: day schedule (book / start / complete / cancel)
/// and OT room master. Body widget. DOCTOR/ADMIN manage; others view.
class OtScreen extends StatefulWidget {
  const OtScreen({super.key});

  @override
  State<OtScreen> createState() => _OtScreenState();
}

class _OtScreenState extends State<OtScreen> {
  String _tab = 'schedule';
  DateTime _date = DateTime.now();
  List<Map<String, dynamic>> _bookings = const [];
  List<Map<String, dynamic>> _rooms = const [];
  bool _loading = false;
  String? _error;
  String? _info;

  String get _role => context.read<AuthState>().currentUser?.role ?? '';
  bool get _isAdmin => _role == 'ADMIN' || _role == 'SUPER_ADMIN';
  bool get _canManage => _role == 'DOCTOR' || _isAdmin;

  String _isoDate(DateTime d) => formatDate(d);
  String _hhmmss(TimeOfDay t) =>
      '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}:00';
  String _hhmm(Object? t) {
    final s = '${t ?? ''}'.split(':');
    return s.length >= 2 ? '${s[0]}:${s[1]}' : '$t';
  }

  @override
  void initState() {
    super.initState();
    _loadAll();
  }

  Future<void> _loadAll() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      final rooms = await api.get<List<Map<String, dynamic>>>(
        '/api/v1/ot/rooms',
        fromJson: (j) => List<Map<String, dynamic>>.from(j as List? ?? const []),
      );
      final bookings = await api.get<List<Map<String, dynamic>>>(
        '/api/v1/ot/bookings?date=${_isoDate(_date)}',
        fromJson: (j) => List<Map<String, dynamic>>.from(j as List? ?? const []),
      );
      if (mounted) {
        setState(() {
          _rooms = rooms;
          _bookings = bookings;
        });
      }
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Could not load OT data: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _act(Future<void> Function() action, String okMsg) async {
    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });
    try {
      await action();
      setState(() => _info = okMsg);
      await _loadAll();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Action failed: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  String _roomName(Object? id) {
    final r = _rooms.firstWhere((e) => e['id'] == id, orElse: () => const {});
    return '${r['name'] ?? 'Room #$id'}';
  }

  // ---------------- book ----------------

  Future<void> _bookDialog() async {
    if (_rooms.isEmpty) {
      setState(() => _error = 'Add an OT room first (Rooms tab)');
      return;
    }
    int? roomId = _rooms.first['id'] as int?;
    final patient = await showPatientPicker(context);
    if (patient == null) return;
    final surgeon = await showDoctorPicker(context);
    if (surgeon == null) return;
    final procCtrl = TextEditingController();
    final notesCtrl = TextEditingController();
    TimeOfDay start = const TimeOfDay(hour: 9, minute: 0);
    TimeOfDay end = const TimeOfDay(hour: 11, minute: 0);

    final proceed = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setLocal) => AlertDialog(
          title: const Text('Schedule surgery'),
          content: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
            child: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('${patient['fullName']} · Dr ${surgeon['name']} · ${_isoDate(_date)}',
                      style: Theme.of(context).textTheme.bodySmall),
                  const SizedBox(height: Space.sm),
                  DropdownButtonFormField<int?>(
                    value: roomId,
                    isExpanded: true,
                    decoration: const InputDecoration(labelText: 'OT room'),
                    items: [
                      for (final r in _rooms)
                        DropdownMenuItem<int?>(value: r['id'] as int?, child: Text('${r['name']}')),
                    ],
                    onChanged: (v) => setLocal(() => roomId = v),
                  ),
                  const SizedBox(height: Space.sm),
                  TextField(
                    controller: procCtrl,
                    decoration: const InputDecoration(labelText: 'Procedure *'),
                  ),
                  const SizedBox(height: Space.sm),
                  Row(
                    children: [
                      Expanded(
                        child: OutlinedButton(
                          onPressed: () async {
                            final t = await showTimePicker(context: context, initialTime: start);
                            if (t != null) setLocal(() => start = t);
                          },
                          child: Text('Start ${start.format(context)}'),
                        ),
                      ),
                      const SizedBox(width: Space.sm),
                      Expanded(
                        child: OutlinedButton(
                          onPressed: () async {
                            final t = await showTimePicker(context: context, initialTime: end);
                            if (t != null) setLocal(() => end = t);
                          },
                          child: Text('End ${end.format(context)}'),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: Space.sm),
                  TextField(
                    controller: notesCtrl,
                    decoration: const InputDecoration(labelText: 'Notes'),
                  ),
                ],
              ),
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
            FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Schedule')),
          ],
        ),
      ),
    );
    if (proceed != true) return;
    if (roomId == null || procCtrl.text.trim().isEmpty) {
      setState(() => _error = 'Room and procedure are required');
      return;
    }
    if (end.hour * 60 + end.minute <= start.hour * 60 + start.minute) {
      setState(() => _error = 'End time must be after start time');
      return;
    }
    await _act(() async {
      final api = context.read<ApiClient>();
      await api.post<dynamic>('/api/v1/ot/bookings', {
        'otRoomId': roomId,
        'patientId': patient['id'],
        'surgeonId': surgeon['id'],
        'procedureName': procCtrl.text.trim(),
        'scheduledDate': _isoDate(_date),
        'startTime': _hhmmss(start),
        'endTime': _hhmmss(end),
        'notes': notesCtrl.text.trim(),
      }, fromJson: (j) => j);
    }, 'Surgery scheduled');
  }

  Future<void> _completeDialog(Map<String, dynamic> b) async {
    final notesCtrl = TextEditingController(text: '${b['surgeryNotes'] ?? ''}');
    final proceed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Complete — ${b['procedureName']}'),
        content: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
          child: TextField(
            controller: notesCtrl,
            maxLines: 4,
            decoration: const InputDecoration(labelText: 'Operative / post-op note'),
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Complete')),
        ],
      ),
    );
    if (proceed != true) return;
    await _act(() async {
      final api = context.read<ApiClient>();
      await api.post<dynamic>('/api/v1/ot/bookings/${b['id']}/complete',
          {'surgeryNotes': notesCtrl.text.trim()}, fromJson: (j) => j);
    }, 'Surgery completed');
  }

  Future<void> _addRoomDialog() async {
    final nameCtrl = TextEditingController();
    final locCtrl = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Add OT room'),
        content: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(controller: nameCtrl, decoration: const InputDecoration(labelText: 'Name *')),
              TextField(controller: locCtrl, decoration: const InputDecoration(labelText: 'Location')),
            ],
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Add')),
        ],
      ),
    );
    if (ok != true || nameCtrl.text.trim().isEmpty) return;
    await _act(() async {
      final api = context.read<ApiClient>();
      await api.post<dynamic>('/api/v1/ot/rooms',
          {'name': nameCtrl.text.trim(), 'location': locCtrl.text.trim()}, fromJson: (j) => j);
    }, 'OT room added');
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final tabs = <(String, String)>[
      ('schedule', 'Schedule'),
      if (_isAdmin) ('rooms', 'Rooms'),
    ];
    if (!tabs.any((t) => t.$1 == _tab)) _tab = 'schedule';
    return PageContainer(
      scrollable: false,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text('Operating Theatre', style: theme.textTheme.titleLarge),
              const Spacer(),
              IconButton(
                tooltip: 'Refresh',
                onPressed: _loading ? null : _loadAll,
                icon: const Icon(Icons.refresh),
              ),
            ],
          ),
          const SizedBox(height: Space.md),
          if (tabs.length > 1) ...[
            Wrap(
              spacing: Space.sm,
              children: [
                for (final t in tabs)
                  ChoiceChip(
                    label: Text(t.$2),
                    selected: _tab == t.$1,
                    onSelected: (_) => setState(() => _tab = t.$1),
                  ),
              ],
            ),
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
          Expanded(child: _tab == 'rooms' ? _roomsTab(theme) : _scheduleTab(theme)),
        ],
      ),
    );
  }

  Widget _scheduleTab(ThemeData theme) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            OutlinedButton.icon(
              onPressed: _loading
                  ? null
                  : () async {
                      final p = await showDatePicker(
                        context: context,
                        initialDate: _date,
                        firstDate: DateTime(2020),
                        lastDate: DateTime(2100));
                      if (p != null) {
                        setState(() => _date = p);
                        await _loadAll();
                      }
                    },
              icon: const Icon(Icons.calendar_today, size: 16),
              label: Text(_isoDate(_date)),
            ),
            const Spacer(),
            if (_canManage)
              FilledButton.icon(
                onPressed: _loading ? null : _bookDialog,
                icon: const Icon(Icons.add, size: 18),
                label: const Text('Schedule'),
              ),
          ],
        ),
        const SizedBox(height: Space.md),
        Expanded(
          child: _bookings.isEmpty
              ? EmptyState(
                  icon: Icons.event_outlined,
                  title: _loading ? 'Loading…' : 'No surgeries scheduled',
                  message: 'Nothing booked for ${_isoDate(_date)}.')
              : Card(
                  child: ListView.separated(
                    itemCount: _bookings.length,
                    separatorBuilder: (_, __) => const Divider(height: 1),
                    itemBuilder: (context, i) => _bookingTile(theme, _bookings[i]),
                  ),
                ),
        ),
      ],
    );
  }

  Widget _bookingTile(ThemeData theme, Map<String, dynamic> b) {
    final status = '${b['otStatus']}';
    return ListTile(
      leading: const Icon(Icons.medical_services_outlined),
      title: Row(
        children: [
          Flexible(child: Text('${b['procedureName']}', style: theme.textTheme.titleSmall)),
          const SizedBox(width: Space.sm),
          StatusChip.auto(status),
        ],
      ),
      subtitle: Text(
        '${_hhmm(b['startTime'])}–${_hhmm(b['endTime'])} · ${_roomName(b['otRoomId'])}'
        ' · patient #${b['patientId']} · surgeon #${b['surgeonId']}'
        '${(b['surgeryNotes'] ?? '').toString().isNotEmpty ? '\nNote: ${b['surgeryNotes']}' : ''}',
        style: theme.textTheme.bodySmall,
      ),
      isThreeLine: (b['surgeryNotes'] ?? '').toString().isNotEmpty,
      trailing: !_canManage
          ? null
          : Wrap(
              spacing: Space.xs,
              children: [
                if (status == 'SCHEDULED')
                  FilledButton(
                    onPressed: _loading ? null : () => _act(() async {
                      await context.read<ApiClient>().post<dynamic>(
                          '/api/v1/ot/bookings/${b['id']}/start', const {}, fromJson: (j) => j);
                    }, 'Surgery started'),
                    child: const Text('Start'),
                  ),
                if (status == 'SCHEDULED' || status == 'IN_PROGRESS')
                  OutlinedButton(
                    onPressed: _loading ? null : () => _completeDialog(b),
                    child: const Text('Complete'),
                  ),
                if (status == 'SCHEDULED' || status == 'IN_PROGRESS')
                  IconButton(
                    tooltip: 'Cancel',
                    icon: const Icon(Icons.block, size: 20),
                    onPressed: _loading ? null : () => _act(() async {
                      await context.read<ApiClient>().post<dynamic>(
                          '/api/v1/ot/bookings/${b['id']}/cancel', const {}, fromJson: (j) => j);
                    }, 'Booking cancelled'),
                  ),
              ],
            ),
    );
  }

  Widget _roomsTab(ThemeData theme) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Align(
          alignment: Alignment.centerRight,
          child: FilledButton.icon(
            onPressed: _loading ? null : _addRoomDialog,
            icon: const Icon(Icons.add, size: 18),
            label: const Text('Add room'),
          ),
        ),
        const SizedBox(height: Space.md),
        Expanded(
          child: _rooms.isEmpty
              ? const EmptyState(
                  icon: Icons.meeting_room_outlined,
                  title: 'No OT rooms',
                  message: 'Add operating theatres to schedule surgeries into.')
              : Card(
                  child: ListView.separated(
                    itemCount: _rooms.length,
                    separatorBuilder: (_, __) => const Divider(height: 1),
                    itemBuilder: (context, i) {
                      final r = _rooms[i];
                      return ListTile(
                        leading: const Icon(Icons.meeting_room_outlined),
                        title: Text('${r['name']}'),
                        subtitle: Text('${r['location'] ?? '—'}'),
                        trailing: r['active'] == false ? StatusChip.auto('INACTIVE') : null,
                      );
                    },
                  ),
                ),
        ),
      ],
    );
  }
}
