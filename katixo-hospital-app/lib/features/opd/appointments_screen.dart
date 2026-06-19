import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/util/formatters.dart';
import '../../core/widgets/empty_state.dart';
import '../../core/widgets/status_chip.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;
import '../patient/patient_picker.dart';
import '../staff/doctor_picker.dart';

/// Appointment booking + day view: pick a doctor and date to see the schedule,
/// book a slot for a patient, check a patient in (→ OPD queue token) or cancel.
/// Body widget. FRONT_DESK/ADMIN book/check-in/cancel; clinical roles can view.
class AppointmentsScreen extends StatefulWidget {
  const AppointmentsScreen({super.key});

  @override
  State<AppointmentsScreen> createState() => _AppointmentsScreenState();
}

class _AppointmentsScreenState extends State<AppointmentsScreen> {
  int? _doctorId;
  String _doctorName = '';
  DateTime _date = DateTime.now();
  List<Map<String, dynamic>> _slots = const [];
  bool _available = true; // doctor not on leave that day
  int _slotMinutes = 15;
  bool _loading = false;
  String? _error;
  String? _info;

  String _isoDate(DateTime d) => formatDate(d);
  String _hhmmss(TimeOfDay t) =>
      '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}:00';

  Future<void> _load() async {
    final id = _doctorId;
    if (id == null) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      final day = await api.get<Map<String, dynamic>>(
        '/api/v1/opd/doctor/$id/slots?date=${_isoDate(_date)}',
        fromJson: (json) => Map<String, dynamic>.from(json as Map? ?? const {}),
      );
      if (mounted) {
        setState(() {
          _available = day['available'] == true;
          _slotMinutes = (day['slotMinutes'] as num?)?.toInt() ?? 15;
          _slots = List<Map<String, dynamic>>.from(day['slots'] as List? ?? const []);
        });
      }
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Could not load schedule: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _pickDoctor() async {
    final d = await showDoctorPicker(context);
    if (d == null) return;
    setState(() {
      _doctorId = d['id'] as int?;
      _doctorName = '${d['name'] ?? ''}';
    });
    await _load();
  }

  Future<void> _pickDate() async {
    final p = await showDatePicker(
      context: context,
      initialDate: _date,
      firstDate: DateTime(2020),
      lastDate: DateTime(2100),
    );
    if (p != null) {
      setState(() => _date = p);
      await _load();
    }
  }

  Future<void> _bookDialog() async {
    final id = _doctorId;
    if (id == null) return;
    final patient = await showPatientPicker(context);
    if (patient == null) return;
    final patientId = patient['id'] as int?;
    if (patientId == null) return;

    TimeOfDay start = const TimeOfDay(hour: 9, minute: 0);
    TimeOfDay end = const TimeOfDay(hour: 9, minute: 15);
    bool tentative = false;
    final notesCtrl = TextEditingController();

    if (!mounted) return;
    final proceed = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setLocal) => AlertDialog(
          title: Text('Book — ${patient['fullName'] ?? 'patient'}'),
          content: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text('Dr $_doctorName · ${_isoDate(_date)}',
                    style: Theme.of(context).textTheme.bodySmall),
                const SizedBox(height: Space.md),
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
                  decoration: const InputDecoration(labelText: 'Notes / chief complaint'),
                ),
                CheckboxListTile(
                  contentPadding: EdgeInsets.zero,
                  dense: true,
                  value: tentative,
                  onChanged: (v) => setLocal(() => tentative = v ?? false),
                  title: const Text('Tentative (unconfirmed) booking'),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
            FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Book')),
          ],
        ),
      ),
    );
    if (proceed != true) return;
    // Compare as minutes-from-midnight to validate the slot.
    final startMin = start.hour * 60 + start.minute;
    final endMin = end.hour * 60 + end.minute;
    if (endMin <= startMin) {
      setState(() => _error = 'End time must be after start time');
      return;
    }
    await _act(() async {
      final api = context.read<ApiClient>();
      await api.post<dynamic>('/api/v1/opd/appointments', {
        'patientId': patientId,
        'doctorId': id,
        'appointmentDate': _isoDate(_date),
        'slotStart': _hhmmss(start),
        'slotEnd': _hhmmss(end),
        'notes': (tentative ? '[TENTATIVE] ' : '') + notesCtrl.text.trim(),
      }, fromJson: (j) => j);
    }, tentative ? 'Tentative appointment booked' : 'Appointment booked');
  }

  /// Book a specific grid slot (start/end already known) for a patient.
  Future<void> _bookSlot(Map<String, dynamic> slot) async {
    final id = _doctorId;
    if (id == null) return;
    final patient = await showPatientPicker(context);
    if (patient == null) return;
    final patientId = patient['id'] as int?;
    if (patientId == null) return;

    bool tentative = false;
    final notesCtrl = TextEditingController();
    if (!mounted) return;
    final proceed = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setLocal) => AlertDialog(
          title: Text('Book ${slot['start']}–${slot['end']}'),
          content: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('${patient['fullName'] ?? 'Patient'} · Dr $_doctorName · ${_isoDate(_date)}',
                    style: Theme.of(context).textTheme.bodySmall),
                const SizedBox(height: Space.sm),
                TextField(
                  controller: notesCtrl,
                  decoration: const InputDecoration(labelText: 'Notes / chief complaint'),
                ),
                CheckboxListTile(
                  contentPadding: EdgeInsets.zero,
                  dense: true,
                  value: tentative,
                  onChanged: (v) => setLocal(() => tentative = v ?? false),
                  title: const Text('Tentative (unconfirmed) booking'),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
            FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Book')),
          ],
        ),
      ),
    );
    if (proceed != true) return;
    await _act(() async {
      final api = context.read<ApiClient>();
      await api.post<dynamic>('/api/v1/opd/appointments', {
        'patientId': patientId,
        'doctorId': id,
        'appointmentDate': _isoDate(_date),
        'slotStart': '${slot['start']}:00',
        'slotEnd': '${slot['end']}:00',
        'notes': (tentative ? '[TENTATIVE] ' : '') + notesCtrl.text.trim(),
      }, fromJson: (j) => j);
    }, tentative ? 'Tentative appointment booked' : 'Appointment booked');
  }

  /// Ad-hoc urgent case: no slot needed — register a priority walk-in that goes
  /// straight onto the doctor's OPD queue.
  Future<void> _urgentWalkIn() async {
    final id = _doctorId;
    if (id == null) return;
    final patient = await showPatientPicker(context);
    if (patient == null) return;
    final patientId = patient['id'] as int?;
    if (patientId == null) return;

    final complaintCtrl = TextEditingController();
    final reasonCtrl = TextEditingController();
    if (!mounted) return;
    final proceed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        icon: const Icon(Icons.priority_high, color: StatusColors.danger),
        title: const Text('Urgent walk-in'),
        content: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('${patient['fullName'] ?? 'Patient'} → Dr $_doctorName',
                  style: Theme.of(context).textTheme.bodySmall),
              const SizedBox(height: Space.sm),
              TextField(
                controller: complaintCtrl,
                decoration: const InputDecoration(labelText: 'Chief complaint'),
              ),
              TextField(
                controller: reasonCtrl,
                decoration: const InputDecoration(labelText: 'Priority reason'),
              ),
              const SizedBox(height: Space.sm),
              Text('Adds a high-priority token to the live queue now — no slot required.',
                  style: Theme.of(context).textTheme.bodySmall),
            ],
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: StatusColors.danger),
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Add to queue'),
          ),
        ],
      ),
    );
    if (proceed != true) return;
    await _act(() async {
      final api = context.read<ApiClient>();
      await api.post<dynamic>('/api/v1/opd/visits', {
        'patientId': patientId,
        'doctorId': id,
        'chiefComplaint': complaintCtrl.text.trim(),
        'priority': 1,
        'priorityReason': reasonCtrl.text.trim(),
      }, fromJson: (j) => j);
    }, 'Urgent walk-in added to queue');
  }

  Future<void> _checkIn(int appointmentId) async {
    await _act(() async {
      final api = context.read<ApiClient>();
      await api.post<dynamic>('/api/v1/opd/appointments/$appointmentId/check-in',
          const <String, dynamic>{}, fromJson: (j) => j);
    }, 'Checked in — token issued');
  }

  Future<void> _cancel(int appointmentId) async {
    await _act(() async {
      final api = context.read<ApiClient>();
      await api.post<dynamic>('/api/v1/opd/appointments/$appointmentId/cancel',
          const <String, dynamic>{}, fromJson: (j) => j);
    }, 'Appointment cancelled');
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
      await _load();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Action failed: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

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
              Text('Appointments', style: theme.textTheme.titleLarge),
              const Spacer(),
              IconButton(
                tooltip: 'Refresh',
                onPressed: _loading || _doctorId == null ? null : _load,
                icon: const Icon(Icons.refresh),
              ),
            ],
          ),
          const SizedBox(height: Space.md),
          Wrap(
            spacing: Space.md,
            runSpacing: Space.sm,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              OutlinedButton.icon(
                onPressed: _loading ? null : _pickDoctor,
                icon: const Icon(Icons.person_search_outlined, size: 18),
                label: Text(_doctorId == null ? 'Select doctor' : 'Dr $_doctorName'),
              ),
              OutlinedButton.icon(
                onPressed: _loading ? null : _pickDate,
                icon: const Icon(Icons.calendar_today, size: 16),
                label: Text(_isoDate(_date)),
              ),
              if (_doctorId != null) ...[
                FilledButton.icon(
                  onPressed: _loading ? null : _bookDialog,
                  icon: const Icon(Icons.add, size: 18),
                  label: const Text('Book (other time)'),
                ),
                OutlinedButton.icon(
                  onPressed: _loading ? null : _urgentWalkIn,
                  icon: const Icon(Icons.priority_high, size: 18),
                  label: const Text('Urgent walk-in'),
                ),
              ],
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
          Expanded(child: _body(theme)),
        ],
      ),
    );
  }

  Widget _body(ThemeData theme) {
    if (_doctorId == null) {
      return const EmptyState(
        icon: Icons.event_note_outlined,
        title: 'Select a doctor',
        message: 'Pick a doctor and date to see free and booked slots.',
      );
    }
    if (!_available) {
      return EmptyState(
        icon: Icons.event_busy_outlined,
        title: 'Doctor unavailable',
        message: 'Dr $_doctorName is on leave on ${_isoDate(_date)}. '
            'Use "Urgent walk-in" for emergencies.',
      );
    }
    if (_slots.isEmpty) {
      return EmptyState(
        icon: Icons.schedule_outlined,
        title: _loading ? 'Loading…' : 'No slots configured',
        message: 'No working hours found for this day.',
      );
    }
    final free = _slots.where((s) => s['status'] == 'FREE').length;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            _legendDot(theme.colorScheme.primary, 'Free ($free)'),
            const SizedBox(width: Space.md),
            _legendDot(theme.colorScheme.surfaceContainerHighest, 'Booked'),
            const Spacer(),
            Text('$_slotMinutes-min slots', style: theme.textTheme.bodySmall),
          ],
        ),
        const SizedBox(height: Space.sm),
        Expanded(
          child: SingleChildScrollView(
            child: Wrap(
              spacing: Space.sm,
              runSpacing: Space.sm,
              children: [for (final s in _slots) _slotTile(theme, s)],
            ),
          ),
        ),
      ],
    );
  }

  Widget _legendDot(Color color, String label) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(width: 12, height: 12, decoration: BoxDecoration(color: color, shape: BoxShape.circle)),
        const SizedBox(width: Space.xs),
        Text(label),
      ],
    );
  }

  Widget _slotTile(ThemeData theme, Map<String, dynamic> s) {
    final booked = s['status'] == 'BOOKED';
    final time = '${s['start']}–${s['end']}';
    return SizedBox(
      width: 168,
      child: Card(
        margin: EdgeInsets.zero,
        color: booked ? theme.colorScheme.surfaceContainerHighest : null,
        child: InkWell(
          onTap: _loading
              ? null
              : booked
                  ? () => _bookedSlotActions(s)
                  : () => _bookSlot(s),
          borderRadius: BorderRadius.circular(Corners.md),
          child: Padding(
            padding: const EdgeInsets.all(Space.sm),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Row(
                  children: [
                    Icon(booked ? Icons.event_busy : Icons.event_available,
                        size: 16,
                        color: booked
                            ? theme.colorScheme.onSurfaceVariant
                            : theme.colorScheme.primary),
                    const SizedBox(width: Space.xs),
                    Text(time, style: theme.textTheme.titleSmall),
                  ],
                ),
                const SizedBox(height: Space.xxs),
                if (booked) ...[
                  Text('${s['patientName'] ?? 'Patient #${s['patientId']}'}',
                      maxLines: 1, overflow: TextOverflow.ellipsis,
                      style: theme.textTheme.bodySmall),
                  StatusChip.auto('${s['appointmentStatus']}'),
                ] else
                  Text('Tap to book',
                      style: theme.textTheme.bodySmall
                          ?.copyWith(color: theme.colorScheme.primary)),
              ],
            ),
          ),
        ),
      ),
    );
  }

  /// Actions for a booked slot: check-in (→ queue token) or cancel.
  Future<void> _bookedSlotActions(Map<String, dynamic> s) async {
    final apptId = s['appointmentId'] as int?;
    if (apptId == null) return;
    final status = '${s['appointmentStatus']}';
    final canAct = status == 'BOOKED' || status == 'CONFIRMED';
    final action = await showModalBottomSheet<String>(
      context: context,
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              title: Text('${s['start']}–${s['end']} · ${s['patientName'] ?? 'Patient'}'),
              subtitle: Text('Status: $status'),
            ),
            if (canAct) ...[
              ListTile(
                leading: const Icon(Icons.login),
                title: const Text('Check in (issue queue token)'),
                onTap: () => Navigator.pop(context, 'checkin'),
              ),
              ListTile(
                leading: const Icon(Icons.block),
                title: const Text('Cancel appointment'),
                onTap: () => Navigator.pop(context, 'cancel'),
              ),
            ] else
              const ListTile(
                leading: Icon(Icons.info_outline),
                title: Text('No actions available'),
              ),
          ],
        ),
      ),
    );
    if (action == 'checkin') await _checkIn(apptId);
    if (action == 'cancel') await _cancel(apptId);
  }
}
