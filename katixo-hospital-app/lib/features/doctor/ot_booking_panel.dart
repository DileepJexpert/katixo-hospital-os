import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/api/opd_models.dart';
import '../../core/api/ot_models.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/status_chip.dart';

/// Panel for booking OT during consultation.
class OTBookingPanel extends StatefulWidget {
  final int visitId;
  const OTBookingPanel({required this.visitId, super.key});

  @override
  State<OTBookingPanel> createState() => _OTBookingPanelState();
}

class _OTBookingPanelState extends State<OTBookingPanel> {
  List<OTRoom> _rooms = [];
  List<StaffMember> _doctors = [];
  bool _loading = false;
  String? _error;
  String? _success;

  int? _selectedRoomId;
  int? _selectedAnesthesiaId;
  int _selectedDurationMins = 60;
  final _procedureCtrl = TextEditingController();

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  @override
  void dispose() {
    _procedureCtrl.dispose();
    super.dispose();
  }

  Future<void> _loadData() async {
    try {
      final api = context.read<ApiClient>();
      final rooms = await api.get<List<OTRoom>>(
        '/api/v1/ot/rooms',
        fromJson: (json) => (json as List)
            .map((e) => OTRoom.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
      final doctors = await api.get<List<StaffMember>>(
        '/api/v1/staff?role=ANESTHESIOLOGIST',
        fromJson: (json) => (json as List)
            .map((e) => StaffMember.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
      if (mounted) setState(() {
        _rooms = rooms;
        _doctors = doctors;
      });
    } catch (e) {
      setState(() => _error = 'Failed to load OT data: $e');
    }
  }

  Future<void> _bookOT() async {
    if (_selectedRoomId == null || _procedureCtrl.text.isEmpty) {
      setState(() => _error = 'Select OT room and enter procedure name');
      return;
    }

    setState(() {
      _loading = true;
      _error = null;
      _success = null;
    });

    try {
      final api = context.read<ApiClient>();
      final request = BookOTRequest(
        patientId: 0, // Will be resolved from visit context on backend
        sourceType: 'OPD_VISIT',
        sourceId: widget.visitId,
        otRoomId: _selectedRoomId!,
        surgeonId: 0, // Current user's staff ID
        anesthesiologistId: _selectedAnesthesiaId,
        scheduledAt: DateTime.now().add(Duration(hours: 1)).toIso8601String(),
        estimatedDurationMins: _selectedDurationMins,
        procedureName: _procedureCtrl.text.trim(),
      );

      final booking = await api.post<OTBooking>(
        '/api/v1/ot/bookings',
        request,
        fromJson: (json) => OTBooking.fromJson(json as Map<String, dynamic>),
      );

      setState(() {
        _success = 'OT booked: ${booking.bookingNumber}';
        _procedureCtrl.clear();
        _selectedRoomId = null;
        _selectedAnesthesiaId = null;
        _selectedDurationMins = 60;
      });
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'OT booking failed: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('Operating Theatre', style: theme.textTheme.titleMedium),
        const SizedBox(height: Space.md),
        if (_error != null) ...[
          Container(
            padding: const EdgeInsets.all(Space.md),
            decoration: BoxDecoration(
              color: StatusColors.danger.withValues(alpha: 0.12),
              borderRadius: Corners.smRadius,
            ),
            child: Text(_error!, style: TextStyle(color: StatusColors.danger)),
          ),
          const SizedBox(height: Space.md),
        ],
        if (_success != null) ...[
          Container(
            padding: const EdgeInsets.all(Space.md),
            decoration: BoxDecoration(
              color: StatusColors.success.withValues(alpha: 0.12),
              borderRadius: Corners.smRadius,
            ),
            child: Text(_success!, style: TextStyle(color: StatusColors.success)),
          ),
          const SizedBox(height: Space.md),
        ],
        DropdownButtonFormField<int>(
          value: _selectedRoomId,
          decoration: const InputDecoration(labelText: 'OT Room *'),
          items: [
            for (final room in _rooms)
              DropdownMenuItem(
                value: room.id,
                child: Text('${room.roomNumber} • ${room.roomName}'),
              ),
          ],
          onChanged: _loading ? null : (v) => setState(() => _selectedRoomId = v),
        ),
        const SizedBox(height: Space.md),
        TextField(
          controller: _procedureCtrl,
          enabled: !_loading,
          decoration: const InputDecoration(labelText: 'Procedure Name *'),
        ),
        const SizedBox(height: Space.md),
        Row(
          children: [
            Expanded(
              child: Slider(
                value: _selectedDurationMins.toDouble(),
                min: 30,
                max: 480,
                divisions: 9,
                label: '${_selectedDurationMins} min',
                onChanged: _loading ? null : (v) => setState(() => _selectedDurationMins = v.toInt()),
              ),
            ),
          ],
        ),
        const SizedBox(height: Space.sm),
        Text('Duration: ${_selectedDurationMins} minutes',
            style: theme.textTheme.bodySmall),
        const SizedBox(height: Space.md),
        DropdownButtonFormField<int>(
          value: _selectedAnesthesiaId,
          decoration:
              const InputDecoration(labelText: 'Anesthesiologist (optional)'),
          items: [
            for (final doc in _doctors)
              DropdownMenuItem(
                value: doc.id,
                child: Text(doc.name),
              ),
          ],
          onChanged:
              _loading ? null : (v) => setState(() => _selectedAnesthesiaId = v),
        ),
        const SizedBox(height: Space.lg),
        SizedBox(
          width: double.infinity,
          child: FilledButton.icon(
            onPressed: _loading ? null : _bookOT,
            icon: const Icon(Icons.calendar_today, size: 18),
            label: _loading ? const SizedBox(
                width: 20,
                height: 20,
                child: CircularProgressIndicator(strokeWidth: 2),
              ) : const Text('Book OT'),
          ),
        ),
      ],
    );
  }
}
