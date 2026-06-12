import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/discharge_models.dart';
import '../../core/api/http_client.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/app_shell.dart';
import '../../core/widgets/status_chip.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// Doctor discharge summary workflow (body only — lives inside DoctorHome
/// shell): load by admission ID, draft the summary, submit → approve → finalize.
class DischargeSummaryScreen extends StatefulWidget {
  const DischargeSummaryScreen({super.key});

  @override
  State<DischargeSummaryScreen> createState() => _DischargeSummaryScreenState();
}

class _DischargeSummaryScreenState extends State<DischargeSummaryScreen> {
  final _admissionCtrl = TextEditingController();
  final _patientCtrl = TextEditingController();
  final _diagnosisCtrl = TextEditingController();
  final _treatmentCtrl = TextEditingController();
  final _medicationsCtrl = TextEditingController();
  final _followUpCtrl = TextEditingController();

  DischargeSummaryResponse? _summary;
  String _dischargeType = 'NORMAL';
  bool _loading = false;
  bool _notFound = false;
  String? _error;
  String? _success;

  static const _dischargeTypes = ['NORMAL', 'LAMA', 'DEATH', 'REFERRED'];

  bool get _isDraft => _summary?.dischargeStatus == 'DRAFT';

  @override
  void dispose() {
    _admissionCtrl.dispose();
    _patientCtrl.dispose();
    _diagnosisCtrl.dispose();
    _treatmentCtrl.dispose();
    _medicationsCtrl.dispose();
    _followUpCtrl.dispose();
    super.dispose();
  }

  void _fillControllers(DischargeSummaryResponse s) {
    _diagnosisCtrl.text = s.diagnosis ?? '';
    _treatmentCtrl.text = s.treatmentSummary ?? '';
    _medicationsCtrl.text = s.medications ?? '';
    _followUpCtrl.text = s.followUpInstructions ?? '';
    _dischargeType = s.dischargeType;
  }

  Future<void> _load() async {
    final admissionId = int.tryParse(_admissionCtrl.text.trim());
    if (admissionId == null) {
      setState(() => _error = 'Enter a valid admission ID');
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
      _success = null;
      _summary = null;
      _notFound = false;
    });
    try {
      final api = context.read<ApiClient>();
      final summary = await api.get<DischargeSummaryResponse>(
        '/api/v1/discharge/admissions/$admissionId',
        fromJson: (json) =>
            DischargeSummaryResponse.fromJson(json as Map<String, dynamic>),
      );
      setState(() {
        _summary = summary;
        _fillControllers(summary);
      });
    } on ApiException {
      // No summary yet for this admission — show the create form.
      setState(() => _notFound = true);
    } catch (e) {
      setState(() => _error = 'Load failed: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _create() async {
    final admissionId = int.tryParse(_admissionCtrl.text.trim());
    final patientId = int.tryParse(_patientCtrl.text.trim());
    if (admissionId == null || patientId == null) {
      setState(() => _error = 'Admission ID and patient ID are required');
      return;
    }
    await _send(
      'POST',
      '/api/v1/discharge/summaries',
      {
        'admissionId': admissionId,
        'patientId': patientId,
        'diagnosis': _diagnosisCtrl.text.trim(),
        'treatmentSummary': _treatmentCtrl.text.trim(),
        'medications': _medicationsCtrl.text.trim(),
        'followUpInstructions': _followUpCtrl.text.trim(),
        'dischargeType': _dischargeType,
      },
      'Draft created',
    );
  }

  Future<void> _saveDraft() async {
    final s = _summary;
    if (s == null) return;
    await _send(
      'PUT',
      '/api/v1/discharge/summaries/${s.id}',
      {
        'diagnosis': _diagnosisCtrl.text.trim(),
        'treatmentSummary': _treatmentCtrl.text.trim(),
        'medications': _medicationsCtrl.text.trim(),
        'followUpInstructions': _followUpCtrl.text.trim(),
      },
      'Draft saved',
    );
  }

  Future<void> _transition(String action, String successMsg) async {
    final s = _summary;
    if (s == null) return;
    await _send('POST', '/api/v1/discharge/summaries/${s.id}/$action',
        const {}, successMsg);
  }

  Future<void> _send(
      String method, String path, Object body, String successMsg) async {
    setState(() {
      _loading = true;
      _error = null;
      _success = null;
    });
    try {
      final api = context.read<ApiClient>();
      DischargeSummaryResponse parse(dynamic json) =>
          DischargeSummaryResponse.fromJson(json as Map<String, dynamic>);
      final summary = method == 'PUT'
          ? await api.put<DischargeSummaryResponse>(path, body,
              fromJson: parse)
          : await api.post<DischargeSummaryResponse>(path, body,
              fromJson: parse);
      setState(() {
        _summary = summary;
        _notFound = false;
        _success = successMsg;
        _fillControllers(summary);
      });
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
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Discharge Summary', style: theme.textTheme.titleLarge),
          const SizedBox(height: Space.md),
          if (_error != null) ...[
            MessageBanner.error(_error!),
            const SizedBox(height: Space.md),
          ],
          if (_success != null) ...[
            MessageBanner.success(_success!),
            const SizedBox(height: Space.md),
          ],

          // Admission lookup
          Row(
            children: [
              SizedBox(
                width: 220,
                child: TextField(
                  controller: _admissionCtrl,
                  enabled: !_loading,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(
                    labelText: 'Admission ID',
                    prefixIcon: Icon(Icons.search, size: 20),
                  ),
                  onSubmitted: (_) => _load(),
                ),
              ),
              const SizedBox(width: Space.md),
              FilledButton(
                onPressed: _loading ? null : _load,
                child: const Text('Load'),
              ),
            ],
          ),

          if (_summary != null || _notFound) ...[
            const SizedBox(height: Space.md),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(Space.md),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Text(
                          _summary == null
                              ? 'New discharge summary'
                              : 'Admission #${_summary!.admissionId} • Patient #${_summary!.patientId}',
                          style: theme.textTheme.titleMedium,
                        ),
                        const Spacer(),
                        if (_summary != null)
                          StatusChip.auto(_summary!.dischargeStatus),
                      ],
                    ),
                    const SizedBox(height: Space.md),

                    if (_notFound) ...[
                      SizedBox(
                        width: 220,
                        child: TextField(
                          controller: _patientCtrl,
                          keyboardType: TextInputType.number,
                          decoration:
                              const InputDecoration(labelText: 'Patient ID *'),
                        ),
                      ),
                      const SizedBox(height: Space.md),
                    ],

                    // Discharge type — editable only while drafting/creating
                    if (_notFound || _isDraft) ...[
                      Wrap(
                        spacing: Space.sm,
                        children: [
                          for (final t in _dischargeTypes)
                            ChoiceChip(
                              label: Text(t,
                                  style: theme.textTheme.labelSmall),
                              selected: _dischargeType == t,
                              visualDensity: VisualDensity.compact,
                              onSelected: _notFound
                                  ? (_) => setState(() => _dischargeType = t)
                                  : null,
                            ),
                        ],
                      ),
                      const SizedBox(height: Space.md),
                    ],

                    _field('Diagnosis', _diagnosisCtrl, lines: 2),
                    const SizedBox(height: Space.md),
                    _field('Treatment summary', _treatmentCtrl, lines: 3),
                    const SizedBox(height: Space.md),
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Expanded(
                            child: _field('Medications', _medicationsCtrl,
                                lines: 2)),
                        const SizedBox(width: Space.md),
                        Expanded(
                            child: _field(
                                'Follow-up instructions', _followUpCtrl,
                                lines: 2)),
                      ],
                    ),
                    const SizedBox(height: Space.md),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.end,
                      children: _actions(),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }

  List<Widget> _actions() {
    if (_notFound) {
      return [
        FilledButton(
          onPressed: _loading ? null : _create,
          child: const Text('Create Draft'),
        ),
      ];
    }
    return switch (_summary?.dischargeStatus) {
      'DRAFT' => [
          TextButton(
            onPressed: _loading ? null : _saveDraft,
            child: const Text('Save Draft'),
          ),
          const SizedBox(width: Space.sm),
          FilledButton(
            onPressed: _loading
                ? null
                : () => _transition('submit', 'Submitted for approval'),
            child: const Text('Submit for Approval'),
          ),
        ],
      'PENDING_APPROVAL' => [
          FilledButton(
            onPressed: _loading
                ? null
                : () => _transition('approve', 'Summary approved'),
            child: const Text('Approve'),
          ),
        ],
      'APPROVED' => [
          FilledButton(
            onPressed: _loading
                ? null
                : () => _transition('finalize', 'Summary finalized'),
            child: const Text('Finalize'),
          ),
        ],
      _ => const [],
    };
  }

  Widget _field(String label, TextEditingController ctrl, {int lines = 1}) {
    final editable = _notFound || _isDraft;
    return TextField(
      controller: ctrl,
      maxLines: lines,
      readOnly: !editable,
      decoration: InputDecoration(labelText: label),
    );
  }
}
