import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/api/models.dart';
import '../../core/api/opd_models.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/app_shell.dart';
import '../../core/widgets/status_chip.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// Front-desk appointment desk (body only — lives inside FrontDeskHome shell):
/// search patient by UHID, book a doctor slot, then cancel or check-in from
/// the patient's appointment list. Check-in issues a queue token, merging the
/// appointment into the doctor's walk-in worklist.
class AppointmentBookingScreen extends StatefulWidget {
  const AppointmentBookingScreen({super.key});

  @override
  State<AppointmentBookingScreen> createState() =>
      _AppointmentBookingScreenState();
}

class _AppointmentBookingScreenState extends State<AppointmentBookingScreen> {
  final _uhidCtrl = TextEditingController();
  final _notesCtrl = TextEditingController();

  List<StaffMember> _doctors = [];
  PatientResponse? _patient;
  List<OpdAppointment> _appointments = [];

  int? _doctorId;
  DateTime? _date;
  TimeOfDay? _slotStart;
  int _durationMin = 15;

  bool _searching = false;
  bool _busy = false;
  String? _error;
  String? _success;

  @override
  void initState() {
    super.initState();
    _loadDoctors();
  }

  @override
  void dispose() {
    _uhidCtrl.dispose();
    _notesCtrl.dispose();
    super.dispose();
  }

  Future<void> _loadDoctors() async {
    try {
      final api = context.read<ApiClient>();
      final doctors = await api.get<List<StaffMember>>(
        '/api/v1/staff?role=DOCTOR',
        fromJson: (json) => (json as List)
            .map((e) => StaffMember.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
      if (mounted) setState(() => _doctors = doctors);
    } catch (_) {
      // Doctor list failure surfaces when booking is attempted.
    }
  }

  Future<void> _searchPatient() async {
    final uhid = _uhidCtrl.text.trim();
    if (uhid.isEmpty) {
      setState(() => _error = 'Enter a UHID to search');
      return;
    }
    setState(() {
      _searching = true;
      _error = null;
      _success = null;
      _patient = null;
      _appointments = [];
    });
    try {
      final api = context.read<ApiClient>();
      final patient = await api.get<PatientResponse>(
        '/api/v1/patients/uhid/$uhid',
        fromJson: (json) =>
            PatientResponse.fromJson(json as Map<String, dynamic>),
      );
      setState(() => _patient = patient);
      await _loadAppointments();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Patient lookup failed: $e');
    } finally {
      if (mounted) setState(() => _searching = false);
    }
  }

  Future<void> _loadAppointments() async {
    final patient = _patient;
    if (patient == null) return;
    try {
      final api = context.read<ApiClient>();
      final appointments = await api.get<List<OpdAppointment>>(
        '/api/v1/opd/appointments/patient/${patient.id}',
        fromJson: (json) => (json as List? ?? [])
            .map((e) => OpdAppointment.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
      if (mounted) setState(() => _appointments = appointments);
    } catch (_) {
      // List failure is non-blocking; booking still works.
    }
  }

  String _fmtDate(DateTime d) =>
      '${d.year}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

  String _fmtTime(TimeOfDay t) =>
      '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}:00';

  Future<void> _book() async {
    final patient = _patient;
    if (patient == null ||
        _doctorId == null ||
        _date == null ||
        _slotStart == null) {
      setState(() => _error = 'Patient, doctor, date and slot are required');
      return;
    }
    final startMinutes = _slotStart!.hour * 60 + _slotStart!.minute;
    final endMinutes = startMinutes + _durationMin;
    final slotEnd =
        TimeOfDay(hour: endMinutes ~/ 60 % 24, minute: endMinutes % 60);

    setState(() {
      _busy = true;
      _error = null;
      _success = null;
    });
    try {
      final api = context.read<ApiClient>();
      await api.post<OpdAppointment>(
        '/api/v1/opd/appointments',
        {
          'patientId': patient.id,
          'doctorId': _doctorId,
          'appointmentDate': _fmtDate(_date!),
          'slotStart': _fmtTime(_slotStart!),
          'slotEnd': _fmtTime(slotEnd),
          'notes':
              _notesCtrl.text.trim().isEmpty ? null : _notesCtrl.text.trim(),
        },
        fromJson: (json) =>
            OpdAppointment.fromJson(json as Map<String, dynamic>),
      );
      setState(() {
        _success = 'Appointment booked for ${patient.fullName}';
        _date = null;
        _slotStart = null;
        _notesCtrl.clear();
      });
      await _loadAppointments();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Booking failed: $e');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _checkIn(OpdAppointment apt) async {
    setState(() {
      _busy = true;
      _error = null;
      _success = null;
    });
    try {
      final api = context.read<ApiClient>();
      final visit = await api.post<Map<String, dynamic>>(
        '/api/v1/opd/appointments/${apt.id}/check-in',
        const {},
        fromJson: (json) => json as Map<String, dynamic>,
      );
      setState(() =>
          _success = 'Checked in — visit ${visit['visitNumber'] ?? ''} queued');
      await _loadAppointments();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Check-in failed: $e');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _cancel(OpdAppointment apt) async {
    final ctrl = TextEditingController();
    final reason = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Cancel Appointment'),
        content: SizedBox(
          width: 380,
          child: TextField(
            controller: ctrl,
            autofocus: true,
            decoration:
                const InputDecoration(labelText: 'Cancellation reason *'),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Keep'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, ctrl.text.trim()),
            child: const Text('Cancel Appointment'),
          ),
        ],
      ),
    );
    if (reason == null || reason.isEmpty) return;

    setState(() {
      _busy = true;
      _error = null;
      _success = null;
    });
    try {
      final api = context.read<ApiClient>();
      await api.post<OpdAppointment>(
        '/api/v1/opd/appointments/${apt.id}/cancel',
        {'reason': reason},
        fromJson: (json) =>
            OpdAppointment.fromJson(json as Map<String, dynamic>),
      );
      setState(() => _success = 'Appointment cancelled');
      await _loadAppointments();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return PageContainer(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Appointments', style: theme.textTheme.titleLarge),
          const SizedBox(height: Space.md),
          if (_error != null) ...[
            MessageBanner.error(_error!),
            const SizedBox(height: Space.md),
          ],
          if (_success != null) ...[
            MessageBanner.success(_success!),
            const SizedBox(height: Space.md),
          ],

          // Patient lookup
          Row(
            children: [
              Expanded(
                child: TextField(
                  controller: _uhidCtrl,
                  enabled: !_searching,
                  decoration: const InputDecoration(
                    labelText: 'Patient UHID',
                    prefixIcon: Icon(Icons.search, size: 20),
                  ),
                  onSubmitted: (_) => _searchPatient(),
                ),
              ),
              const SizedBox(width: Space.md),
              FilledButton(
                onPressed: _searching ? null : _searchPatient,
                child: const Text('Search'),
              ),
            ],
          ),

          if (_patient != null) ...[
            const SizedBox(height: Space.md),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(Space.md),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Text(_patient!.fullName,
                            style: theme.textTheme.titleMedium),
                        const SizedBox(width: Space.sm),
                        Text('${_patient!.uhid} • ${_patient!.mobile}',
                            style: theme.textTheme.bodySmall?.copyWith(
                                color: theme.colorScheme.onSurfaceVariant)),
                      ],
                    ),
                    const SizedBox(height: Space.md),
                    Row(
                      children: [
                        Expanded(
                          flex: 2,
                          child: DropdownButtonFormField<int>(
                            value: _doctorId,
                            decoration:
                                const InputDecoration(labelText: 'Doctor *'),
                            items: [
                              for (final d in _doctors)
                                DropdownMenuItem(
                                  value: d.id,
                                  child: Text(
                                    d.specialisation == null
                                        ? d.name
                                        : '${d.name} (${d.specialisation})',
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                ),
                            ],
                            onChanged: (v) => setState(() => _doctorId = v),
                          ),
                        ),
                        const SizedBox(width: Space.md),
                        Expanded(
                          child: OutlinedButton.icon(
                            onPressed: () async {
                              final picked = await showDatePicker(
                                context: context,
                                initialDate: DateTime.now(),
                                firstDate: DateTime.now(),
                                lastDate: DateTime.now()
                                    .add(const Duration(days: 60)),
                              );
                              if (picked != null) {
                                setState(() => _date = picked);
                              }
                            },
                            icon: const Icon(Icons.calendar_today_outlined,
                                size: 16),
                            label:
                                Text(_date == null ? 'Date *' : _fmtDate(_date!)),
                          ),
                        ),
                        const SizedBox(width: Space.md),
                        Expanded(
                          child: OutlinedButton.icon(
                            onPressed: () async {
                              final picked = await showTimePicker(
                                context: context,
                                initialTime: TimeOfDay.now(),
                              );
                              if (picked != null) {
                                setState(() => _slotStart = picked);
                              }
                            },
                            icon: const Icon(Icons.access_time, size: 16),
                            label: Text(_slotStart == null
                                ? 'Slot start *'
                                : _slotStart!.format(context)),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: Space.md),
                    Row(
                      children: [
                        Text('Duration', style: theme.textTheme.labelSmall),
                        const SizedBox(width: Space.sm),
                        for (final m in const [10, 15, 20, 30]) ...[
                          ChoiceChip(
                            label: Text('$m min',
                                style: theme.textTheme.labelSmall),
                            selected: _durationMin == m,
                            visualDensity: VisualDensity.compact,
                            onSelected: (_) =>
                                setState(() => _durationMin = m),
                          ),
                          const SizedBox(width: Space.xs),
                        ],
                        const SizedBox(width: Space.sm),
                        Expanded(
                          child: TextField(
                            controller: _notesCtrl,
                            decoration: const InputDecoration(
                                labelText: 'Notes (optional)'),
                          ),
                        ),
                        const SizedBox(width: Space.md),
                        FilledButton(
                          onPressed: _busy ? null : _book,
                          child: Text(_busy ? 'Working…' : 'Book Slot'),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),

            if (_appointments.isNotEmpty) ...[
              const SizedBox(height: Space.lg),
              Text('Appointments for ${_patient!.fullName}',
                  style: theme.textTheme.titleMedium),
              const SizedBox(height: Space.sm),
              Card(
                child: Column(
                  children: [
                    for (var i = 0; i < _appointments.length; i++) ...[
                      if (i > 0) const Divider(height: 1),
                      _appointmentRow(_appointments[i], theme),
                    ],
                  ],
                ),
              ),
            ],
          ],
        ],
      ),
    );
  }

  Widget _appointmentRow(OpdAppointment apt, ThemeData theme) {
    String? doctor;
    for (final d in _doctors) {
      if (d.id == apt.doctorId) {
        doctor = d.name;
        break;
      }
    }
    return Padding(
      padding: const EdgeInsets.symmetric(
          horizontal: Space.md, vertical: Space.xs),
      child: Row(
        children: [
          SizedBox(
            width: 170,
            child: Text(
              '${apt.appointmentDate}  ${OpdAppointment.shortTime(apt.slotStart)}–${OpdAppointment.shortTime(apt.slotEnd)}',
              style: theme.textTheme.bodySmall
                  ?.copyWith(fontWeight: FontWeight.w600),
            ),
          ),
          const SizedBox(width: Space.md),
          Expanded(
            child: Text(
              doctor == null ? 'Doctor #${apt.doctorId}' : doctor,
              style: theme.textTheme.bodySmall
                  ?.copyWith(color: theme.colorScheme.onSurfaceVariant),
              overflow: TextOverflow.ellipsis,
            ),
          ),
          StatusChip.auto(apt.appointmentStatus),
          if (apt.canCheckIn) ...[
            const SizedBox(width: Space.sm),
            TextButton(
              onPressed: _busy ? null : () => _checkIn(apt),
              child: const Text('Check-in'),
            ),
          ],
          if (apt.canCancel)
            IconButton(
              tooltip: 'Cancel appointment',
              iconSize: 18,
              icon: const Icon(Icons.close),
              onPressed: _busy ? null : () => _cancel(apt),
            ),
        ],
      ),
    );
  }
}
