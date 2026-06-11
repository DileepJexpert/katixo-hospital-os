import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/api/ipd_models.dart';
import '../../core/api/nursing_models.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/status_chip.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// Nursing vitals & rounds: record patient vital signs during ward rounds.
class NursingVitalsScreen extends StatefulWidget {
  const NursingVitalsScreen({super.key});

  @override
  State<NursingVitalsScreen> createState() => _NursingVitalsScreenState();
}

class _NursingVitalsScreenState extends State<NursingVitalsScreen> {
  List<ActiveAdmission> _admissions = [];
  List<NursingVital> _vitals = [];
  bool _loading = false;
  String? _error;
  String? _success;
  Timer? _refreshTimer;

  // Form state for recording vitals
  ActiveAdmission? _selectedAdmission;
  final _tempCtrl = TextEditingController();
  final _hrCtrl = TextEditingController();
  final _rrCtrl = TextEditingController();
  final _sysBpCtrl = TextEditingController();
  final _diaBpCtrl = TextEditingController();
  final _spo2Ctrl = TextEditingController();
  final _glucoseCtrl = TextEditingController();
  final _observationsCtrl = TextEditingController();
  final _complaintsCtrl = TextEditingController();
  int? _painLevel;
  String _nutritionStatus = 'Good';

  @override
  void initState() {
    super.initState();
    _loadAdmissions();
    _refreshTimer =
        Timer.periodic(const Duration(seconds: 15), (_) => _loadAdmissions());
  }

  @override
  void dispose() {
    _refreshTimer?.cancel();
    _tempCtrl.dispose();
    _hrCtrl.dispose();
    _rrCtrl.dispose();
    _sysBpCtrl.dispose();
    _diaBpCtrl.dispose();
    _spo2Ctrl.dispose();
    _glucoseCtrl.dispose();
    _observationsCtrl.dispose();
    _complaintsCtrl.dispose();
    super.dispose();
  }

  Future<void> _loadAdmissions() async {
    try {
      final api = context.read<ApiClient>();
      final rows = await api.get<List<ActiveAdmission>>(
        '/api/v1/ipd/admissions',
        fromJson: (json) => (json as List)
            .map((e) => ActiveAdmission.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
      if (mounted) setState(() => _admissions = rows);
    } catch (_) {
      // Silent on poll errors.
    }
  }

  Future<void> _loadVitalHistory(int admissionId) async {
    try {
      final api = context.read<ApiClient>();
      final vitals = await api.get<List<NursingVital>>(
        '/api/v1/nursing/vitals/admission/$admissionId',
        fromJson: (json) => (json as List)
            .map((e) => NursingVital.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
      if (mounted) setState(() => _vitals = vitals);
    } catch (_) {
      // Ignore errors loading history
    }
  }

  Future<void> _recordVital() async {
    if (_selectedAdmission == null) {
      setState(() => _error = 'Select a patient first');
      return;
    }

    setState(() {
      _loading = true;
      _error = null;
      _success = null;
    });

    try {
      final api = context.read<ApiClient>();
      final request = RecordVitalRequest(
        admissionId: _selectedAdmission!.admissionId,
        patientId: _selectedAdmission!.patientId,
        temperatureCelsius:
            _tempCtrl.text.isEmpty ? null : double.tryParse(_tempCtrl.text),
        heartRateBpm: _hrCtrl.text.isEmpty ? null : int.tryParse(_hrCtrl.text),
        respiratoryRate:
            _rrCtrl.text.isEmpty ? null : int.tryParse(_rrCtrl.text),
        systolicBp:
            _sysBpCtrl.text.isEmpty ? null : int.tryParse(_sysBpCtrl.text),
        diastolicBp:
            _diaBpCtrl.text.isEmpty ? null : int.tryParse(_diaBpCtrl.text),
        spo2Percent:
            _spo2Ctrl.text.isEmpty ? null : double.tryParse(_spo2Ctrl.text),
        bloodGlucose: _glucoseCtrl.text.isEmpty
            ? null
            : double.tryParse(_glucoseCtrl.text),
        observations: _observationsCtrl.text.trim().isEmpty
            ? null
            : _observationsCtrl.text.trim(),
        complaints: _complaintsCtrl.text.trim().isEmpty
            ? null
            : _complaintsCtrl.text.trim(),
        painLevel: _painLevel,
        nutritionStatus: _nutritionStatus,
      );

      final vital = await api.post<NursingVital>(
        '/api/v1/nursing/vitals',
        request,
        fromJson: (json) =>
            NursingVital.fromJson(json as Map<String, dynamic>),
      );

      setState(() {
        _success = vital.isAbnormal
            ? 'Vitals recorded - ABNORMAL readings detected'
            : 'Vitals recorded successfully';
        _tempCtrl.clear();
        _hrCtrl.clear();
        _rrCtrl.clear();
        _sysBpCtrl.clear();
        _diaBpCtrl.clear();
        _spo2Ctrl.clear();
        _glucoseCtrl.clear();
        _observationsCtrl.clear();
        _complaintsCtrl.clear();
        _painLevel = null;
        _nutritionStatus = 'Good';
      });

      await _loadVitalHistory(_selectedAdmission!.admissionId);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Failed to record vitals: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return PageContainer(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Nursing Vitals & Rounds', style: theme.textTheme.titleLarge),
          const SizedBox(height: Space.md),

          if (_error != null) ...[
            MessageBanner.error(_error!),
            const SizedBox(height: Space.md),
          ],
          if (_success != null) ...[
            MessageBanner.success(_success!),
            const SizedBox(height: Space.md),
          ],

          // Patient selection
          Card(
            child: Padding(
              padding: const EdgeInsets.all(Space.lg),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Select Patient for Vitals',
                      style: theme.textTheme.titleMedium),
                  const SizedBox(height: Space.md),
                  DropdownButtonFormField<ActiveAdmission>(
                    value: _selectedAdmission,
                    decoration: const InputDecoration(labelText: 'Patient'),
                    items: [
                      for (final adm in _admissions)
                        DropdownMenuItem(
                          value: adm,
                          child: Text('${adm.patientName} - Bed ${adm.bedNumber}'),
                        ),
                    ],
                    onChanged: _loading
                        ? null
                        : (adm) {
                            if (adm != null) {
                              setState(() => _selectedAdmission = adm);
                              _loadVitalHistory(adm.admissionId);
                            }
                          },
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: Space.md),

          // Vitals form
          if (_selectedAdmission != null) ...[
            Card(
              child: Padding(
                padding: const EdgeInsets.all(Space.lg),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Record Vitals', style: theme.textTheme.titleMedium),
                    const SizedBox(height: Space.md),
                    Row(
                      children: [
                        Expanded(
                          child: TextField(
                            controller: _tempCtrl,
                            enabled: !_loading,
                            keyboardType: TextInputType.numberWithOptions(decimal: true),
                            decoration: const InputDecoration(
                              labelText: 'Temp (°C)',
                              hintText: '36.5',
                            ),
                          ),
                        ),
                        const SizedBox(width: Space.sm),
                        Expanded(
                          child: TextField(
                            controller: _hrCtrl,
                            enabled: !_loading,
                            keyboardType: TextInputType.number,
                            decoration: const InputDecoration(
                              labelText: 'Heart Rate',
                              hintText: '75',
                            ),
                          ),
                        ),
                        const SizedBox(width: Space.sm),
                        Expanded(
                          child: TextField(
                            controller: _rrCtrl,
                            enabled: !_loading,
                            keyboardType: TextInputType.number,
                            decoration: const InputDecoration(
                              labelText: 'RR',
                              hintText: '16',
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: Space.md),
                    Row(
                      children: [
                        Expanded(
                          child: TextField(
                            controller: _sysBpCtrl,
                            enabled: !_loading,
                            keyboardType: TextInputType.number,
                            decoration: const InputDecoration(
                              labelText: 'Sys BP',
                              hintText: '120',
                            ),
                          ),
                        ),
                        const SizedBox(width: Space.sm),
                        Expanded(
                          child: TextField(
                            controller: _diaBpCtrl,
                            enabled: !_loading,
                            keyboardType: TextInputType.number,
                            decoration: const InputDecoration(
                              labelText: 'Dia BP',
                              hintText: '80',
                            ),
                          ),
                        ),
                        const SizedBox(width: Space.sm),
                        Expanded(
                          child: TextField(
                            controller: _spo2Ctrl,
                            enabled: !_loading,
                            keyboardType: TextInputType.numberWithOptions(decimal: true),
                            decoration: const InputDecoration(
                              labelText: 'SpO2 %',
                              hintText: '98',
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: Space.md),
                    TextField(
                      controller: _glucoseCtrl,
                      enabled: !_loading,
                      keyboardType: TextInputType.numberWithOptions(decimal: true),
                      decoration: const InputDecoration(
                        labelText: 'Blood Glucose (mg/dL) - optional',
                      ),
                    ),
                    const SizedBox(height: Space.md),
                    Row(
                      children: [
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text('Pain Level (0-10)',
                                  style: theme.textTheme.bodySmall),
                              Slider(
                                value: _painLevel?.toDouble() ?? 0,
                                min: 0,
                                max: 10,
                                divisions: 10,
                                label: '${_painLevel ?? 0}',
                                onChanged: _loading
                                    ? null
                                    : (v) => setState(() => _painLevel = v.toInt()),
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(width: Space.md),
                        Expanded(
                          child: DropdownButtonFormField<String>(
                            value: _nutritionStatus,
                            decoration:
                                const InputDecoration(labelText: 'Nutrition'),
                            items: const [
                              DropdownMenuItem(value: 'Good', child: Text('Good')),
                              DropdownMenuItem(value: 'Fair', child: Text('Fair')),
                              DropdownMenuItem(value: 'Poor', child: Text('Poor')),
                            ],
                            onChanged: _loading
                                ? null
                                : (v) => setState(
                                    () => _nutritionStatus = v ?? 'Good'),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: Space.md),
                    TextField(
                      controller: _observationsCtrl,
                      enabled: !_loading,
                      maxLines: 2,
                      decoration: const InputDecoration(
                        labelText: 'Observations (optional)',
                      ),
                    ),
                    const SizedBox(height: Space.md),
                    TextField(
                      controller: _complaintsCtrl,
                      enabled: !_loading,
                      maxLines: 2,
                      decoration: const InputDecoration(
                        labelText: 'Patient Complaints (optional)',
                      ),
                    ),
                    const SizedBox(height: Space.lg),
                    SizedBox(
                      width: double.infinity,
                      child: FilledButton.icon(
                        onPressed: _loading ? null : _recordVital,
                        icon: const Icon(Icons.medical_information_outlined,
                            size: 18),
                        label: _loading
                            ? const SizedBox(
                                width: 20,
                                height: 20,
                                child: CircularProgressIndicator(
                                    strokeWidth: 2),
                              )
                            : const Text('Record Vitals'),
                      ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: Space.md),

            // Vital history
            if (_vitals.isNotEmpty) ...[
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(Space.lg),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Vital History (Last 10)',
                          style: theme.textTheme.titleMedium),
                      const SizedBox(height: Space.md),
                      for (var i = 0; i < _vitals.take(10).length; i++)
                        _buildVitalCard(_vitals[i], theme),
                    ],
                  ),
                ),
              ),
            ],
          ],
        ],
      ),
    );
  }

  Widget _buildVitalCard(NursingVital vital, ThemeData theme) {
    return Padding(
      padding: const EdgeInsets.only(bottom: Space.md),
      child: Container(
        padding: const EdgeInsets.all(Space.md),
        decoration: BoxDecoration(
          color: vital.isAbnormal
              ? Colors.red.withValues(alpha: 0.05)
              : Colors.grey.withValues(alpha: 0.05),
          border: Border.all(
            color: vital.isAbnormal ? Colors.red : Colors.grey,
            width: 1,
          ),
          borderRadius: Corners.smRadius,
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Text(vital.recordedAt,
                    style: theme.textTheme.labelSmall),
                const Spacer(),
                if (vital.isAbnormal)
                  StatusChip.auto('ABNORMAL'),
              ],
            ),
            const SizedBox(height: Space.sm),
            Text(
              [
                if (vital.temperatureCelsius != null)
                  '${vital.temperatureCelsius}°C',
                if (vital.heartRateBpm != null)
                  '${vital.heartRateBpm} bpm',
                if (vital.systolicBp != null && vital.diastolicBp != null)
                  '${vital.systolicBp}/${vital.diastolicBp}',
                if (vital.spo2Percent != null)
                  '${vital.spo2Percent}% O₂',
              ].join('  •  '),
              style: theme.textTheme.bodySmall,
            ),
            if (vital.abnormalityNotes != null) ...[
              const SizedBox(height: Space.xs),
              Text(vital.abnormalityNotes!,
                  style: theme.textTheme.bodySmall
                      ?.copyWith(color: Colors.red, fontStyle: FontStyle.italic)),
            ],
          ],
        ),
      ),
    );
  }
}
