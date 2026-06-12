import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/discharge_models.dart';
import '../../core/api/http_client.dart';
import '../../core/responsive/responsive_builder.dart';
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
  int? _loadedAdmissionId;
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
      _loadedAdmissionId = admissionId;
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
            const SizedBox(height: Space.md),
            DischargeChecklistPanel(
              key: ValueKey(_loadedAdmissionId),
              admissionId: _loadedAdmissionId!,
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


/// Discharge readiness checklist for one admission. Items the policy engine
/// marks as blocking show a "BLOCKS" tag; finalize is refused server-side
/// while any of those are open. Warn-only items can stay open.
class DischargeChecklistPanel extends StatefulWidget {
  const DischargeChecklistPanel({required this.admissionId, super.key});

  final int admissionId;

  @override
  State<DischargeChecklistPanel> createState() =>
      _DischargeChecklistPanelState();
}

class _DischargeChecklistPanelState extends State<DischargeChecklistPanel> {
  Map<String, dynamic>? _checklist;
  bool _busy = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  List<Map<String, dynamic>> get _items =>
      ((_checklist?['items'] as List?) ?? const [])
          .cast<Map<String, dynamic>>();

  Future<void> _load() async {
    try {
      final api = context.read<ApiClient>();
      final checklist = await api.get<Map<String, dynamic>>(
        '/api/v1/discharge/checklist/admission/${widget.admissionId}',
        fromJson: (json) => json as Map<String, dynamic>,
      );
      if (mounted) {
        setState(() {
          _checklist = checklist;
          _error = null;
        });
      }
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.error.message);
    } catch (e) {
      if (mounted) setState(() => _error = 'Checklist load failed: $e');
    }
  }

  Future<void> _toggle(Map<String, dynamic> item) async {
    final completed = item['completed'] == true;
    setState(() => _busy = true);
    try {
      final api = context.read<ApiClient>();
      final checklist = await api.post<Map<String, dynamic>>(
        '/api/v1/discharge/checklist/items/${item['id']}/${completed ? 'reopen' : 'complete'}',
        const {},
        fromJson: (json) => json as Map<String, dynamic>,
      );
      if (mounted) {
        setState(() {
          _checklist = checklist;
          _error = null;
        });
      }
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final blocked = _checklist?['dischargeBlocked'] == true;

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(Space.md),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Text('Discharge Checklist', style: theme.textTheme.titleMedium),
                const Spacer(),
                if (_checklist != null)
                  StatusChip(
                    blocked ? 'DISCHARGE BLOCKED' : 'READY',
                    kind: blocked ? StatusKind.danger : StatusKind.success,
                  ),
              ],
            ),
            if (_error != null) ...[
              const SizedBox(height: Space.sm),
              MessageBanner.error(_error!),
            ],
            const SizedBox(height: Space.sm),
            if (_checklist == null && _error == null)
              const Center(
                child: Padding(
                  padding: EdgeInsets.all(Space.md),
                  child: CircularProgressIndicator(),
                ),
              )
            else
              for (final item in _items)
                Padding(
                  padding: const EdgeInsets.only(bottom: Space.xxs),
                  child: Row(
                    children: [
                      SizedBox(
                        height: 28,
                        width: 28,
                        child: Checkbox(
                          value: item['completed'] == true,
                          visualDensity: VisualDensity.compact,
                          onChanged: _busy ? null : (_) => _toggle(item),
                        ),
                      ),
                      const SizedBox(width: Space.sm),
                      Expanded(
                        child: Text(
                          item['itemName'] as String? ?? '',
                          style: theme.textTheme.bodySmall?.copyWith(
                            decoration: item['completed'] == true
                                ? TextDecoration.lineThrough
                                : null,
                            color: item['completed'] == true
                                ? theme.colorScheme.onSurfaceVariant
                                : null,
                          ),
                        ),
                      ),
                      if (item['blocking'] == true)
                        const StatusChip('BLOCKS', kind: StatusKind.warning),
                    ],
                  ),
                ),
          ],
        ),
      ),
    );
  }
}
