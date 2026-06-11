import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/api/ipd_models.dart';
import '../../core/api/models.dart';
import '../../core/api/opd_models.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/status_chip.dart';
import 'registration_screen.dart' show MessageBanner;

/// IPD admission: search patient, select doctor, pick vacant bed, admit.
class IPDAdmissionScreen extends StatefulWidget {
  const IPDAdmissionScreen({super.key});

  @override
  State<IPDAdmissionScreen> createState() => _IPDAdmissionScreenState();
}

class _IPDAdmissionScreenState extends State<IPDAdmissionScreen> {
  final _uhidCtrl = TextEditingController();
  final _diagnosisCtrl = TextEditingController();
  final _notesCtrl = TextEditingController();

  PatientResponse? _patient;
  List<StaffMember> _doctors = [];
  int? _selectedDoctorId;
  List<BedView> _beds = [];
  int? _selectedBedId;

  bool _searching = false;
  bool _admitting = false;
  String? _error;
  String? _success;

  @override
  void initState() {
    super.initState();
    Future.wait([_loadDoctors(), _loadBeds()]);
  }

  @override
  void dispose() {
    _uhidCtrl.dispose();
    _diagnosisCtrl.dispose();
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
      // Doctor list failure surfaces when user opens the dropdown empty.
    }
  }

  Future<void> _loadBeds() async {
    try {
      final api = context.read<ApiClient>();
      final beds = await api.get<List<BedView>>(
        '/api/v1/ipd/beds',
        fromJson: (json) => (json as List)
            .map((e) => BedView.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
      if (mounted) setState(() => _beds = beds);
    } catch (_) {
      // Bed list failure is not critical; shown when user opens dropdown.
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

  Future<void> _admit() async {
    if (_patient == null || _selectedDoctorId == null || _selectedBedId == null) {
      setState(() => _error = 'Select patient, doctor, and bed first');
      return;
    }

    setState(() {
      _admitting = true;
      _error = null;
      _success = null;
    });

    try {
      final api = context.read<ApiClient>();
      final request = AdmitRequest(
        patientId: _patient!.id,
        doctorId: _selectedDoctorId!,
        bedId: _selectedBedId!,
        diagnosis:
            _diagnosisCtrl.text.trim().isEmpty ? null : _diagnosisCtrl.text.trim(),
        notes: _notesCtrl.text.trim().isEmpty ? null : _notesCtrl.text.trim(),
      );

      final admission = await api.post<AdmissionView>(
        '/api/v1/ipd/admissions',
        request,
        fromJson: (json) =>
            AdmissionView.fromJson(json as Map<String, dynamic>),
      );

      setState(() {
        _success =
            '${_patient!.fullName} admitted — ${admission.admissionNumber}';
        _patient = null;
        _uhidCtrl.clear();
        _diagnosisCtrl.clear();
        _notesCtrl.clear();
        _selectedDoctorId = null;
        _selectedBedId = null;
      });
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Admission failed: $e');
    } finally {
      if (mounted) setState(() => _admitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final vacant = _beds.where((b) => b.bedStatus == 'VACANT').toList();

    return PageContainer(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('IPD Admission', style: theme.textTheme.titleLarge),
          const SizedBox(height: Space.md),

          if (_error != null) ...[
            MessageBanner.error(_error!),
            const SizedBox(height: Space.md),
          ],
          if (_success != null) ...[
            MessageBanner.success(_success!),
            const SizedBox(height: Space.md),
          ],

          // Step 1: Find patient
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
                          enabled: !_searching && !_admitting,
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
                        onPressed: _searching || _admitting ? null : _searchPatient,
                        icon: const Icon(Icons.search, size: 18),
                        label:
                            Text(_searching ? 'Searching…' : 'Search'),
                      ),
                    ],
                  ),
                  if (_patient != null) ...[
                    const SizedBox(height: Space.md),
                    ListTile(
                      leading: const Icon(Icons.person_outline),
                      title: Text(_patient!.fullName),
                      subtitle: Text(
                          '${_patient!.uhid} • ${_patient!.gender ?? ''} • ${_patient!.age ?? '-'} yrs • ${_patient!.mobile}'),
                      trailing: StatusChip.auto('ACTIVE'),
                    ),
                  ],
                ],
              ),
            ),
          ),
          const SizedBox(height: Space.md),

          // Step 2: Admission details
          Card(
            child: Padding(
              padding: const EdgeInsets.all(Space.lg),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('2. Admission Details',
                      style: theme.textTheme.titleMedium),
                  const SizedBox(height: Space.md),
                  DropdownButtonFormField<int>(
                    value: _selectedDoctorId,
                    decoration:
                        const InputDecoration(labelText: 'Doctor (Admitting) *'),
                    items: [
                      for (final d in _doctors)
                        DropdownMenuItem(
                          value: d.id,
                          child: Text(d.specialisation == null
                              ? d.name
                              : '${d.name} — ${d.specialisation}'),
                        ),
                    ],
                    onChanged: _admitting
                        ? null
                        : (v) => setState(() => _selectedDoctorId = v),
                  ),
                  const SizedBox(height: Space.md),
                  DropdownButtonFormField<int>(
                    value: _selectedBedId,
                    decoration: InputDecoration(
                      labelText: 'Bed (Vacant: ${vacant.length}) *',
                    ),
                    items: [
                      for (final b in vacant)
                        DropdownMenuItem(
                          value: b.id,
                          child: Text(
                              'Bed ${b.bedNumber} • ${b.chargeModel} • ₹${b.tariffRate ?? 0}'),
                        ),
                    ],
                    onChanged: _admitting
                        ? null
                        : (v) => setState(() => _selectedBedId = v),
                  ),
                  const SizedBox(height: Space.md),
                  TextField(
                    controller: _diagnosisCtrl,
                    enabled: !_admitting,
                    maxLines: 2,
                    decoration:
                        const InputDecoration(labelText: 'Diagnosis (optional)'),
                  ),
                  const SizedBox(height: Space.md),
                  TextField(
                    controller: _notesCtrl,
                    enabled: !_admitting,
                    maxLines: 2,
                    decoration:
                        const InputDecoration(labelText: 'Notes (optional)'),
                  ),
                  const SizedBox(height: Space.lg),
                  SizedBox(
                    width: double.infinity,
                    height: Metrics.buttonHeight,
                    child: FilledButton(
                      onPressed: _admitting ? null : _admit,
                      child: _admitting
                          ? const SizedBox(
                              width: 20,
                              height: 20,
                              child:
                                  CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Text('Admit Patient'),
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
}
