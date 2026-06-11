import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/appointment_models.dart';
import '../../core/api/http_client.dart';
import '../../core/theme/design_tokens.dart';

class AppointmentBookingScreen extends StatefulWidget {
  const AppointmentBookingScreen({super.key});

  @override
  State<AppointmentBookingScreen> createState() =>
      _AppointmentBookingScreenState();
}

class _AppointmentBookingScreenState extends State<AppointmentBookingScreen> {
  int? _selectedDoctorId;
  DateTime? _selectedDate;
  TimeOfDay? _selectedTime;
  String _selectedType = 'CONSULTATION';
  bool _isOnline = false;
  late TextEditingController _reasonController;
  late TextEditingController _notesController;
  bool _isLoading = false;
  List<AppointmentResponse> _upcomingAppointments = [];

  @override
  void initState() {
    super.initState();
    _reasonController = TextEditingController();
    _notesController = TextEditingController();
    _loadUpcomingAppointments();
  }

  @override
  void dispose() {
    _reasonController.dispose();
    _notesController.dispose();
    super.dispose();
  }

  Future<void> _loadUpcomingAppointments() async {
    try {
      final api = context.read<ApiClient>();
      // Placeholder - in real app would load from API
      // final appointments = await api.get(...);
    } catch (e) {
      // Handle error
    }
  }

  Future<void> _bookAppointment() async {
    if (_selectedDoctorId == null ||
        _selectedDate == null ||
        _selectedTime == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please fill in all required fields')),
      );
      return;
    }

    final appointmentDateTime = DateTime(
      _selectedDate!.year,
      _selectedDate!.month,
      _selectedDate!.day,
      _selectedTime!.hour,
      _selectedTime!.minute,
    );

    setState(() => _isLoading = true);
    try {
      final api = context.read<ApiClient>();
      final request = BookAppointmentRequest(
        patientId: 1, // In real app, get from AuthState
        doctorId: _selectedDoctorId!,
        appointmentDateTime: appointmentDateTime,
        reason: _reasonController.text,
        appointmentType: _selectedType,
        isOnline: _isOnline,
        notes: _notesController.text,
      );

      await api.post(
        '/api/v1/appointments',
        request.toJson(),
        fromJson: (json) =>
            AppointmentResponse.fromJson(json as Map<String, dynamic>),
      );

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Appointment booked successfully')),
        );
        _resetForm();
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('Error: $e')));
      }
    } finally {
      setState(() => _isLoading = false);
    }
  }

  void _resetForm() {
    setState(() {
      _selectedDoctorId = null;
      _selectedDate = null;
      _selectedTime = null;
      _selectedType = 'CONSULTATION';
      _isOnline = false;
      _reasonController.clear();
      _notesController.clear();
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Book Appointment')),
      body: SingleChildScrollView(
        child: Padding(
          padding: const EdgeInsets.all(Space.md),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Select Doctor',
                  style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: Space.sm),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: Space.md),
                decoration: BoxDecoration(
                  border: Border.all(color: Colors.grey.shade300),
                  borderRadius: BorderRadius.circular(Corners.sm),
                ),
                child: DropdownButton<int>(
                  value: _selectedDoctorId,
                  isExpanded: true,
                  hint: const Text('Select a doctor'),
                  items: const [
                    DropdownMenuItem(value: 1, child: Text('Dr. John Smith')),
                    DropdownMenuItem(value: 2, child: Text('Dr. Sarah Jones')),
                    DropdownMenuItem(value: 3, child: Text('Dr. Mike Brown')),
                  ],
                  onChanged: (value) => setState(() => _selectedDoctorId = value),
                ),
              ),
              const SizedBox(height: Space.lg),
              Text('Select Date',
                  style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: Space.sm),
              OutlinedButton(
                onPressed: () async {
                  final date = await showDatePicker(
                    context: context,
                    initialDate: DateTime.now(),
                    firstDate: DateTime.now(),
                    lastDate: DateTime.now().add(const Duration(days: 30)),
                  );
                  if (date != null) {
                    setState(() => _selectedDate = date);
                  }
                },
                child: Padding(
                  padding: const EdgeInsets.all(Space.md),
                  child: Row(
                    children: [
                      const Icon(Icons.calendar_today),
                      const SizedBox(width: Space.md),
                      Text(_selectedDate == null
                          ? 'Select Date'
                          : _selectedDate.toString().split(' ')[0]),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: Space.lg),
              Text('Select Time',
                  style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: Space.sm),
              OutlinedButton(
                onPressed: () async {
                  final time = await showTimePicker(
                    context: context,
                    initialTime: TimeOfDay.now(),
                  );
                  if (time != null) {
                    setState(() => _selectedTime = time);
                  }
                },
                child: Padding(
                  padding: const EdgeInsets.all(Space.md),
                  child: Row(
                    children: [
                      const Icon(Icons.access_time),
                      const SizedBox(width: Space.md),
                      Text(_selectedTime == null
                          ? 'Select Time'
                          : _selectedTime!.format(context)),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: Space.lg),
              Text('Appointment Type',
                  style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: Space.sm),
              Wrap(
                spacing: Space.sm,
                children: ['CONSULTATION', 'FOLLOW_UP', 'CHECK_UP', 'PROCEDURE']
                    .map((type) => FilterChip(
                          label: Text(type),
                          selected: _selectedType == type,
                          onSelected: (selected) {
                            if (selected) {
                              setState(() => _selectedType = type);
                            }
                          },
                        ))
                    .toList(),
              ),
              const SizedBox(height: Space.lg),
              CheckboxListTile(
                title: const Text('Online Appointment'),
                value: _isOnline,
                onChanged: (value) => setState(() => _isOnline = value ?? false),
              ),
              const SizedBox(height: Space.lg),
              TextField(
                controller: _reasonController,
                decoration: const InputDecoration(
                  labelText: 'Reason for Visit',
                  border: OutlineInputBorder(),
                ),
                maxLines: 2,
              ),
              const SizedBox(height: Space.md),
              TextField(
                controller: _notesController,
                decoration: const InputDecoration(
                  labelText: 'Additional Notes (Optional)',
                  border: OutlineInputBorder(),
                ),
                maxLines: 2,
              ),
              const SizedBox(height: Space.lg),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  onPressed: _isLoading ? null : _bookAppointment,
                  child: _isLoading
                      ? const SizedBox(
                          height: 20,
                          width: 20,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Text('Book Appointment'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class AppointmentListScreen extends StatefulWidget {
  final int patientId;

  const AppointmentListScreen({required this.patientId, super.key});

  @override
  State<AppointmentListScreen> createState() => _AppointmentListScreenState();
}

class _AppointmentListScreenState extends State<AppointmentListScreen> {
  List<AppointmentResponse> _appointments = [];
  bool _loading = false;

  @override
  void initState() {
    super.initState();
    _loadAppointments();
  }

  Future<void> _loadAppointments() async {
    setState(() => _loading = true);
    try {
      final api = context.read<ApiClient>();
      final appointments = await api.get<List<AppointmentResponse>>(
        '/api/v1/appointments/patient/${widget.patientId}',
        fromJson: (json) {
          if (json is List) {
            return json
                .map((a) =>
                    AppointmentResponse.fromJson(a as Map<String, dynamic>))
                .toList();
          }
          return [];
        },
      );
      setState(() => _appointments = appointments);
    } catch (e) {
      // Handle error
    } finally {
      setState(() => _loading = false);
    }
  }

  Future<void> _cancelAppointment(int appointmentId) async {
    final reason = await showDialog<String>(
      context: context,
      builder: (context) {
        String reason = '';
        return AlertDialog(
          title: const Text('Cancel Appointment'),
          content: TextField(
            onChanged: (value) => reason = value,
            decoration:
                const InputDecoration(labelText: 'Cancellation Reason'),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Keep'),
            ),
            ElevatedButton(
              onPressed: () => Navigator.pop(context, reason),
              child: const Text('Cancel'),
            ),
          ],
        );
      },
    );

    if (reason != null && reason.isNotEmpty) {
      try {
        final api = context.read<ApiClient>();
        await api.post(
          '/api/v1/appointments/$appointmentId/cancel',
          CancelAppointmentRequest(reason: reason).toJson(),
          fromJson: (json) =>
              AppointmentResponse.fromJson(json as Map<String, dynamic>),
        );
        _loadAppointments();
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Appointment cancelled')),
          );
        }
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context)
              .showSnackBar(SnackBar(content: Text('Error: $e')));
        }
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('My Appointments'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _loadAppointments,
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _appointments.isEmpty
              ? const Center(child: Text('No appointments scheduled'))
              : ListView.builder(
                  itemCount: _appointments.length,
                  itemBuilder: (context, index) {
                    final apt = _appointments[index];
                    return Card(
                      margin: const EdgeInsets.all(Space.sm),
                      child: ListTile(
                        title: Text('${apt.appointmentDateTime.toLocal()}'
                            .split('.')[0]),
                        subtitle: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const SizedBox(height: Space.xs),
                            Text('Reason: ${apt.reason ?? 'General checkup'}'),
                            Text('Type: ${apt.appointmentType}'),
                            const SizedBox(height: Space.xs),
                            Chip(
                              label: Text(apt.statusDisplay),
                              backgroundColor: apt.appointmentStatus == 'CONFIRMED'
                                  ? Colors.green
                                  : Colors.blue,
                              labelStyle:
                                  const TextStyle(color: Colors.white),
                            ),
                          ],
                        ),
                        trailing: apt.appointmentStatus == 'SCHEDULED' &&
                                apt.isUpcoming
                            ? PopupMenuButton(
                                itemBuilder: (context) => [
                                  PopupMenuItem(
                                    child: const Text('Cancel'),
                                    onTap: () =>
                                        _cancelAppointment(apt.id),
                                  ),
                                ],
                              )
                            : null,
                        isThreeLine: true,
                      ),
                    );
                  },
                ),
      floatingActionButton: FloatingActionButton(
        onPressed: () => Navigator.push(
          context,
          MaterialPageRoute(
            builder: (context) => const AppointmentBookingScreen(),
          ),
        ).then((_) => _loadAppointments()),
        child: const Icon(Icons.add),
      ),
    );
  }
}
