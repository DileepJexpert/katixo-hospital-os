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
  List<Map<String, dynamic>> _appointments = const [];
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
      final list = await api.get<List<Map<String, dynamic>>>(
        '/api/v1/opd/appointments?doctorId=$id&date=${_isoDate(_date)}',
        fromJson: (json) =>
            List<Map<String, dynamic>>.from(json as List? ?? const []),
      );
      if (mounted) setState(() => _appointments = list);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Could not load appointments: $e');
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
        'notes': notesCtrl.text.trim(),
      }, fromJson: (j) => j);
    }, 'Appointment booked');
  }

  Future<void> _checkIn(Map<String, dynamic> a) async {
    await _act(() async {
      final api = context.read<ApiClient>();
      await api.post<dynamic>('/api/v1/opd/appointments/${a['id']}/check-in',
          const <String, dynamic>{}, fromJson: (j) => j);
    }, 'Checked in — token issued');
  }

  Future<void> _cancel(Map<String, dynamic> a) async {
    await _act(() async {
      final api = context.read<ApiClient>();
      await api.post<dynamic>('/api/v1/opd/appointments/${a['id']}/cancel',
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
              if (_doctorId != null)
                FilledButton.icon(
                  onPressed: _loading ? null : _bookDialog,
                  icon: const Icon(Icons.add, size: 18),
                  label: const Text('Book appointment'),
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
        message: 'Pick a doctor and date to see and book appointments.',
      );
    }
    if (_appointments.isEmpty) {
      return EmptyState(
        icon: Icons.event_available_outlined,
        title: _loading ? 'Loading…' : 'No appointments',
        message: 'Dr $_doctorName has no appointments on ${_isoDate(_date)}.',
      );
    }
    return Card(
      child: ListView.separated(
        itemCount: _appointments.length,
        separatorBuilder: (_, __) => const Divider(height: 1),
        itemBuilder: (context, i) {
          final a = _appointments[i];
          final status = '${a['appointmentStatus']}';
          final canAct = status == 'BOOKED' || status == 'CONFIRMED';
          return ListTile(
            leading: const Icon(Icons.schedule_outlined),
            title: Row(
              children: [
                Text('${_hhmm(a['slotStart'])}–${_hhmm(a['slotEnd'])}',
                    style: theme.textTheme.titleSmall),
                const SizedBox(width: Space.sm),
                StatusChip.auto(status),
              ],
            ),
            subtitle: Text(
              'Patient #${a['patientId']}'
              '${(a['notes'] ?? '').toString().isNotEmpty ? ' · ${a['notes']}' : ''}',
              style: theme.textTheme.bodySmall,
            ),
            trailing: canAct
                ? Wrap(
                    spacing: Space.xs,
                    children: [
                      FilledButton(
                        onPressed: _loading ? null : () => _checkIn(a),
                        child: const Text('Check in'),
                      ),
                      IconButton(
                        tooltip: 'Cancel',
                        icon: const Icon(Icons.block, size: 20),
                        onPressed: _loading ? null : () => _cancel(a),
                      ),
                    ],
                  )
                : (a['visitId'] != null
                    ? Text('Visit #${a['visitId']}', style: theme.textTheme.bodySmall)
                    : null),
          );
        },
      ),
    );
  }

  String _hhmm(Object? time) {
    final s = '${time ?? ''}';
    final parts = s.split(':');
    return parts.length >= 2 ? '${parts[0]}:${parts[1]}' : s;
  }
}
