import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/api/models.dart';
import '../../core/api/opd_models.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/status_chip.dart';

/// Walk-in OPD visit: look up patient by UHID, pick doctor, issue token.
class WalkInScreen extends StatefulWidget {
  const WalkInScreen({super.key});

  @override
  State<WalkInScreen> createState() => _WalkInScreenState();
}

class _WalkInScreenState extends State<WalkInScreen> {
  final _uhidCtrl = TextEditingController();
  final _complaintCtrl = TextEditingController();
  final _priorityReasonCtrl = TextEditingController();

  PatientResponse? _patient;
  List<StaffMember> _doctors = [];
  int? _selectedDoctorId;
  bool _urgent = false;

  bool _searching = false;
  bool _creating = false;
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
    _complaintCtrl.dispose();
    _priorityReasonCtrl.dispose();
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
      // The doctor dropdown can't be populated — tell the user rather than
      // leaving an unexplained empty list they must assign from.
      if (mounted && _doctors.isEmpty) {
        setState(() => _error = 'Could not load the doctor list — refresh to retry.');
      }
    }
  }

  Future<void> _searchPatient() async {
    final uhid = _uhidCtrl.text.trim();
    if (uhid.isEmpty) return;

    setState(() {
      _searching = true;
      _error = null;
      _patient = null;
    });

    try {
      final api = context.read<ApiClient>();
      final patient = await api.get<PatientResponse>(
        '/api/v1/patients/uhid/$uhid',
        fromJson: (json) =>
            PatientResponse.fromJson(json as Map<String, dynamic>),
      );
      setState(() => _patient = patient);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Search failed: $e');
    } finally {
      if (mounted) setState(() => _searching = false);
    }
  }

  Future<void> _createVisit() async {
    if (_patient == null || _selectedDoctorId == null) {
      setState(() => _error = 'Select a patient and doctor first');
      return;
    }
    if (_urgent && _priorityReasonCtrl.text.trim().isEmpty) {
      setState(() => _error = 'Urgent visits require a reason (audited)');
      return;
    }

    setState(() {
      _creating = true;
      _error = null;
      _success = null;
    });

    try {
      final api = context.read<ApiClient>();
      final visit = await api.post<VisitResponse>(
        '/api/v1/opd/visits',
        CreateWalkInRequest(
          patientId: _patient!.id,
          doctorId: _selectedDoctorId!,
          chiefComplaint: _complaintCtrl.text.trim().isEmpty
              ? null
              : _complaintCtrl.text.trim(),
          priority: _urgent ? 1 : null,
          priorityReason:
              _urgent ? _priorityReasonCtrl.text.trim() : null,
        ),
        fromJson: (json) =>
            VisitResponse.fromJson(json as Map<String, dynamic>),
      );

      setState(() {
        _success =
            'Visit ${visit.visitNumber} created — fee ₹${visit.consultationFee} (${visit.feeType})';
        _patient = null;
        _uhidCtrl.clear();
        _complaintCtrl.clear();
        _priorityReasonCtrl.clear();
        _urgent = false;
        _selectedDoctorId = null;
      });
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Visit creation failed: $e');
    } finally {
      if (mounted) setState(() => _creating = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return PageContainer(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Walk-in Visit', style: theme.textTheme.titleLarge),
          const SizedBox(height: Space.md),

          if (_error != null) ...[
            _banner(context, _error!, StatusColors.danger, Icons.error_outline),
            const SizedBox(height: Space.md),
          ],
          if (_success != null) ...[
            _banner(context, _success!, StatusColors.success,
                Icons.check_circle_outline),
            const SizedBox(height: Space.md),
          ],

          // Step 1: find patient
          Card(
            child: Padding(
              padding: const EdgeInsets.all(Space.lg),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('1. Find Patient', style: theme.textTheme.titleMedium),
                  const SizedBox(height: Space.md),
                  Row(
                    children: [
                      Expanded(
                        child: TextField(
                          controller: _uhidCtrl,
                          enabled: !_searching,
                          decoration: const InputDecoration(
                            labelText: 'UHID',
                            hintText: 'HOS-1-100001',
                            prefixIcon: Icon(Icons.qr_code_outlined),
                          ),
                          onSubmitted: (_) => _searchPatient(),
                        ),
                      ),
                      const SizedBox(width: Space.md),
                      FilledButton.icon(
                        onPressed: _searching ? null : _searchPatient,
                        icon: const Icon(Icons.search, size: 18),
                        label: Text(_searching ? 'Searching…' : 'Search'),
                      ),
                    ],
                  ),
                  if (_patient != null) ...[
                    const SizedBox(height: Space.md),
                    ListTile(
                      leading: const Icon(Icons.person_outline),
                      title: Text(_patient!.fullName),
                      subtitle: Text(
                          '${_patient!.uhid} · ${_patient!.gender ?? ''} · ${_patient!.age ?? '-'} yrs · ${_patient!.mobile}'),
                      trailing: StatusChip.auto('ACTIVE'),
                    ),
                  ],
                ],
              ),
            ),
          ),
          const SizedBox(height: Space.md),

          // Step 2: visit details
          Card(
            child: Padding(
              padding: const EdgeInsets.all(Space.lg),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('2. Visit Details', style: theme.textTheme.titleMedium),
                  const SizedBox(height: Space.md),
                  DropdownButtonFormField<int>(
                    value: _selectedDoctorId,
                    decoration: const InputDecoration(labelText: 'Doctor *'),
                    items: [
                      for (final d in _doctors)
                        DropdownMenuItem(
                          value: d.id,
                          child: Text(d.specialisation == null
                              ? d.name
                              : '${d.name} — ${d.specialisation}'),
                        ),
                    ],
                    onChanged: _creating
                        ? null
                        : (v) => setState(() => _selectedDoctorId = v),
                  ),
                  const SizedBox(height: Space.md),
                  TextField(
                    controller: _complaintCtrl,
                    enabled: !_creating,
                    decoration:
                        const InputDecoration(labelText: 'Chief Complaint'),
                  ),
                  const SizedBox(height: Space.sm),
                  CheckboxListTile(
                    enabled: !_creating,
                    value: _urgent,
                    onChanged: (v) => setState(() => _urgent = v ?? false),
                    controlAffinity: ListTileControlAffinity.leading,
                    title: Text('Urgent (priority token — reason audited)',
                        style: theme.textTheme.bodyMedium),
                  ),
                  if (_urgent) ...[
                    const SizedBox(height: Space.sm),
                    TextField(
                      controller: _priorityReasonCtrl,
                      enabled: !_creating,
                      decoration: const InputDecoration(
                          labelText: 'Priority Reason *'),
                    ),
                  ],
                  const SizedBox(height: Space.lg),
                  SizedBox(
                    width: double.infinity,
                    height: Metrics.buttonHeight,
                    child: FilledButton(
                      onPressed: _creating ? null : _createVisit,
                      child: _creating
                          ? const SizedBox(
                              width: 20,
                              height: 20,
                              child:
                                  CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Text('Create Visit & Issue Token'),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _banner(
      BuildContext context, String message, Color color, IconData icon) {
    return Container(
      padding: const EdgeInsets.all(Space.md),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: Corners.smRadius,
        border: Border.all(color: color.withValues(alpha: 0.3)),
      ),
      child: Row(
        children: [
          Icon(icon, size: 20, color: color),
          const SizedBox(width: Space.sm),
          Expanded(
            child: Text(message,
                style: Theme.of(context)
                    .textTheme
                    .bodySmall
                    ?.copyWith(color: color)),
          ),
        ],
      ),
    );
  }
}
