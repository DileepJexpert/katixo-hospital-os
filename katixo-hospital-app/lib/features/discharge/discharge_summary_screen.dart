import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/auth/auth_state.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/util/formatters.dart';
import '../../core/util/pdf_actions.dart';
import '../../core/widgets/empty_state.dart';
import '../../core/widgets/section_card.dart';
import '../../core/widgets/status_chip.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// Clinical discharge summary screen.
/// When [admissionId] is provided, shows only the summary for that admission
/// (suitable for embedding in the IPD admission detail).
/// Without it, shows a paginated list of all summaries and a creation form.
class DischargeSummaryScreen extends StatefulWidget {
  final int? admissionId;

  const DischargeSummaryScreen({super.key, this.admissionId});

  @override
  State<DischargeSummaryScreen> createState() => _DischargeSummaryScreenState();
}

class _DischargeSummaryScreenState extends State<DischargeSummaryScreen>
    with SingleTickerProviderStateMixin {
  static const _conditions = <String>[
    'STABLE', 'IMPROVED', 'CRITICAL', 'MORIBUND', 'EXPIRED', 'LAMA',
  ];

  late TabController _tabController;
  List<Map<String, dynamic>> _summaries = const [];
  Map<String, dynamic>? _singleSummary; // used when admissionId is provided
  bool _loading = false;
  String? _error;
  String? _info;

  // Form fields
  final _admissionCtrl = TextEditingController();
  final _diagnosisCtrl = TextEditingController();
  final _courseCtrl = TextEditingController();
  final _proceduresCtrl = TextEditingController();
  final _followUpCtrl = TextEditingController();
  final _medsCtrl = TextEditingController();
  final _activityCtrl = TextEditingController();
  final _dietCtrl = TextEditingController();
  String? _selectedCondition;

  String get _role => context.read<AuthState>().currentUser?.role ?? '';
  bool get _canWrite => _role == 'DOCTOR' || _role == 'ADMIN' || _role == 'SUPER_ADMIN';

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
    if (widget.admissionId != null) {
      _admissionCtrl.text = '${widget.admissionId}';
      // Pre-select admission so form is ready
    }
    WidgetsBinding.instance.addPostFrameCallback((_) => _load());
  }

  @override
  void dispose() {
    _tabController.dispose();
    _admissionCtrl.dispose();
    _diagnosisCtrl.dispose();
    _courseCtrl.dispose();
    _proceduresCtrl.dispose();
    _followUpCtrl.dispose();
    _medsCtrl.dispose();
    _activityCtrl.dispose();
    _dietCtrl.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      if (widget.admissionId != null) {
        // Load single summary for this admission
        try {
          final s = await api.get<Map<String, dynamic>>(
              '/api/v1/discharge/summaries/by-admission/${widget.admissionId}',
              fromJson: (j) {
                final d = (j as Map<String, dynamic>);
                return d['data'] as Map<String, dynamic>? ?? d;
              });
          if (mounted) setState(() => _singleSummary = s);
        } on ApiException catch (e) {
          // 404 = no summary yet — that's fine
          if (e.error.error != 'DSUM_NOT_FOUND') {
            setState(() => _error = e.error.message);
          } else {
            setState(() => _singleSummary = null);
          }
        }
      } else {
        final list = await api.get<List<Map<String, dynamic>>>(
            '/api/v1/discharge/summaries?limit=50',
            fromJson: (j) {
              final d = j as Map<String, dynamic>;
              final data = d['data'];
              if (data is List) {
                return List<Map<String, dynamic>>.from(data);
              }
              return <Map<String, dynamic>>[];
            });
        if (mounted) setState(() => _summaries = list);
      }
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  // ---- build ----

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
              Text('Discharge Summaries', style: theme.textTheme.titleLarge),
              const Spacer(),
              IconButton(
                tooltip: 'Refresh',
                onPressed: _loading ? null : _load,
                icon: const Icon(Icons.refresh),
              ),
            ],
          ),
          const SizedBox(height: Space.sm),
          if (_error != null) ...[
            MessageBanner.error(_error!),
            const SizedBox(height: Space.sm),
          ],
          if (_info != null) ...[
            MessageBanner.success(_info!),
            const SizedBox(height: Space.sm),
          ],
          TabBar(
            controller: _tabController,
            tabs: const [
              Tab(text: 'Summaries'),
              Tab(text: 'New Summary'),
            ],
          ),
          const SizedBox(height: Space.sm),
          Expanded(
            child: TabBarView(
              controller: _tabController,
              children: [
                _summariesTab(theme),
                _newSummaryTab(theme),
              ],
            ),
          ),
        ],
      ),
    );
  }

  // ---- summaries tab ----

  Widget _summariesTab(ThemeData theme) {
    if (widget.admissionId != null) {
      // Show single card for the specific admission
      if (_loading) {
        return const Center(child: CircularProgressIndicator());
      }
      if (_singleSummary == null) {
        return EmptyState(
          icon: Icons.description_outlined,
          title: 'No discharge summary yet',
          message: _canWrite ? 'Use the New Summary tab to create one.' : null,
          action: _canWrite
              ? FilledButton.icon(
                  onPressed: () => _tabController.animateTo(1),
                  icon: const Icon(Icons.add, size: 18),
                  label: const Text('Create summary'))
              : null,
        );
      }
      return SingleChildScrollView(child: _summaryCard(theme, _singleSummary!));
    }

    // Full list
    if (_summaries.isEmpty) {
      return EmptyState(
        icon: Icons.description_outlined,
        title: _loading ? 'Loading…' : 'No discharge summaries yet',
        message: _canWrite ? 'Use the New Summary tab to create one.' : null,
        action: _canWrite && !_loading
            ? FilledButton.icon(
                onPressed: () => _tabController.animateTo(1),
                icon: const Icon(Icons.add, size: 18),
                label: const Text('New summary'))
            : null,
      );
    }
    return ListView.separated(
      itemCount: _summaries.length,
      separatorBuilder: (_, __) => const SizedBox(height: Space.sm),
      itemBuilder: (context, i) {
        final s = _summaries[i];
        final status = '${s['summaryStatus']}';
        return Card(
          child: ListTile(
            onTap: () => _showDetail(context, s),
            leading: CircleAvatar(
              backgroundColor: theme.colorScheme.primaryContainer,
              child: Icon(Icons.description_outlined,
                  color: theme.colorScheme.onPrimaryContainer),
            ),
            title: Row(
              children: [
                Text('${s['summaryNumber']}', style: theme.textTheme.titleSmall),
                const SizedBox(width: Space.sm),
                StatusChip.auto(status),
              ],
            ),
            subtitle: Text(
              'Admission #${s['admissionId']}'
              '${s['conditionAtDischarge'] != null ? ' · ${s['conditionAtDischarge']}' : ''}'
              '${status == 'SIGNED' && s['signedAt'] != null ? ' · Signed ${_date(s['signedAt'])}' : ' · Draft'}'
              ,
              style: theme.textTheme.bodySmall,
            ),
            trailing: const Icon(Icons.chevron_right),
          ),
        );
      },
    );
  }

  Widget _summaryCard(ThemeData theme, Map<String, dynamic> s) {
    final status = '${s['summaryStatus']}';
    final id = s['id'] as int;
    return SectionCard(
      title: '${s['summaryNumber']}',
      icon: Icons.description_outlined,
      action: Wrap(
        spacing: Space.sm,
        children: [
          StatusChip.auto(status),
          OutlinedButton.icon(
            onPressed: _loading
                ? null
                : () => openPdf(context, context.read<ApiClient>(),
                    '/api/v1/discharge/summaries/$id/summary.pdf',
                    filename: 'discharge-summary-$id.pdf'),
            icon: const Icon(Icons.picture_as_pdf_outlined, size: 18),
            label: const Text('Open PDF'),
          ),
          if (_canWrite && status == 'DRAFT') ...[
            OutlinedButton.icon(
              onPressed: _loading ? null : () => _editDialog(s),
              icon: const Icon(Icons.edit_outlined, size: 18),
              label: const Text('Edit'),
            ),
            FilledButton.icon(
              onPressed: _loading ? null : () => _signDialog(id),
              icon: const Icon(Icons.draw_outlined, size: 18),
              label: const Text('Sign'),
            ),
          ],
        ],
      ),
      child: Wrap(
        spacing: Space.xl,
        runSpacing: Space.md,
        children: [
          _kv(theme, 'Admission', '#${s['admissionId']}'),
          _kv(theme, 'Status', status),
          if (s['conditionAtDischarge'] != null)
            _kv(theme, 'Condition at Discharge', '${s['conditionAtDischarge']}'),
          if (s['signedByDoctorName'] != null)
            _kv(theme, 'Signed by', 'Dr ${s['signedByDoctorName']}'),
          if (s['signedAt'] != null) _kv(theme, 'Signed at', _date(s['signedAt'])),
          if (s['finalDiagnosis'] != null) _kv(theme, 'Final Diagnosis', '${s['finalDiagnosis']}'),
          if (s['courseInHospital'] != null) _kv(theme, 'Course in Hospital', '${s['courseInHospital']}'),
          if (s['proceduresPerformed'] != null)
            _kv(theme, 'Procedures Performed', '${s['proceduresPerformed']}'),
          if (s['followUpInstructions'] != null)
            _kv(theme, 'Follow-up Instructions', '${s['followUpInstructions']}'),
          if (s['medicationsAtDischarge'] != null)
            _kv(theme, 'Medications', '${s['medicationsAtDischarge']}'),
          if (s['activityRestrictions'] != null)
            _kv(theme, 'Activity Restrictions', '${s['activityRestrictions']}'),
          if (s['dietAdvice'] != null) _kv(theme, 'Diet Advice', '${s['dietAdvice']}'),
        ],
      ),
    );
  }

  // ---- new summary tab ----

  Widget _newSummaryTab(ThemeData theme) {
    return SingleChildScrollView(
      child: SectionCard(
        title: 'New Discharge Summary',
        icon: Icons.add_circle_outline,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            TextField(
              controller: _admissionCtrl,
              keyboardType: TextInputType.number,
              readOnly: widget.admissionId != null,
              decoration: const InputDecoration(
                labelText: 'Admission ID *',
                hintText: 'Enter IPD admission ID',
              ),
            ),
            const SizedBox(height: Space.md),
            TextField(
              controller: _diagnosisCtrl,
              maxLines: 3,
              decoration: const InputDecoration(
                labelText: 'Final Diagnosis',
                hintText: 'Principal diagnosis (free text or ICD code)',
                alignLabelWithHint: true,
              ),
            ),
            const SizedBox(height: Space.md),
            TextField(
              controller: _courseCtrl,
              maxLines: 5,
              decoration: const InputDecoration(
                labelText: 'Course in Hospital',
                hintText: 'Chronological treatment narrative',
                alignLabelWithHint: true,
              ),
            ),
            const SizedBox(height: Space.md),
            TextField(
              controller: _proceduresCtrl,
              maxLines: 3,
              decoration: const InputDecoration(
                labelText: 'Procedures Performed',
                hintText: 'Surgical / diagnostic procedures',
                alignLabelWithHint: true,
              ),
            ),
            const SizedBox(height: Space.md),
            DropdownButtonFormField<String>(
              initialValue: _selectedCondition,
              decoration: const InputDecoration(labelText: 'Condition at Discharge'),
              hint: const Text('Select condition'),
              isExpanded: true,
              items: [
                const DropdownMenuItem(value: null, child: Text('— Not specified —')),
                for (final c in _conditions)
                  DropdownMenuItem(value: c, child: Text(c)),
              ],
              onChanged: (v) => setState(() => _selectedCondition = v),
            ),
            const SizedBox(height: Space.md),
            TextField(
              controller: _followUpCtrl,
              maxLines: 3,
              decoration: const InputDecoration(
                labelText: 'Follow-up Instructions',
                hintText: 'Clinic appointments, rest, wound care',
                alignLabelWithHint: true,
              ),
            ),
            const SizedBox(height: Space.md),
            TextField(
              controller: _medsCtrl,
              maxLines: 4,
              decoration: const InputDecoration(
                labelText: 'Medications at Discharge',
                hintText: 'Discharge prescription',
                alignLabelWithHint: true,
              ),
            ),
            const SizedBox(height: Space.md),
            TextField(
              controller: _activityCtrl,
              maxLines: 2,
              decoration: const InputDecoration(
                labelText: 'Activity Restrictions',
                hintText: 'Diet / exercise / work restrictions',
                alignLabelWithHint: true,
              ),
            ),
            const SizedBox(height: Space.md),
            TextField(
              controller: _dietCtrl,
              maxLines: 2,
              decoration: const InputDecoration(
                labelText: 'Diet Advice',
                alignLabelWithHint: true,
              ),
            ),
            const SizedBox(height: Space.lg),
            SizedBox(
              width: double.infinity,
              child: FilledButton(
                onPressed: _loading ? null : _submit,
                child: _loading
                    ? const SizedBox(
                        height: 18,
                        width: 18,
                        child: CircularProgressIndicator(strokeWidth: 2))
                    : const Text('Create Draft'),
              ),
            ),
          ],
        ),
      ),
    );
  }

  // ---- actions ----

  Future<void> _submit() async {
    final admId = int.tryParse(_admissionCtrl.text.trim());
    if (admId == null) {
      setState(() => _error = 'Enter a valid Admission ID');
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });
    try {
      final api = context.read<ApiClient>();
      await api.post<Map<String, dynamic>>(
        '/api/v1/discharge/summaries',
        {
          'admissionId': admId,
          if (_diagnosisCtrl.text.trim().isNotEmpty) 'finalDiagnosis': _diagnosisCtrl.text.trim(),
          if (_courseCtrl.text.trim().isNotEmpty) 'courseInHospital': _courseCtrl.text.trim(),
          if (_proceduresCtrl.text.trim().isNotEmpty)
            'proceduresPerformed': _proceduresCtrl.text.trim(),
          if (_selectedCondition != null) 'conditionAtDischarge': _selectedCondition,
          if (_followUpCtrl.text.trim().isNotEmpty) 'followUpInstructions': _followUpCtrl.text.trim(),
          if (_medsCtrl.text.trim().isNotEmpty) 'medicationsAtDischarge': _medsCtrl.text.trim(),
          if (_activityCtrl.text.trim().isNotEmpty) 'activityRestrictions': _activityCtrl.text.trim(),
          if (_dietCtrl.text.trim().isNotEmpty) 'dietAdvice': _dietCtrl.text.trim(),
        },
        fromJson: (j) => j as Map<String, dynamic>,
      );
      setState(() => _info = 'Discharge summary created');
      _tabController.animateTo(0);
      await _load();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _signDialog(int id) async {
    final doctorIdCtrl = TextEditingController();
    final doctorNameCtrl = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Sign Discharge Summary'),
        content: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: doctorIdCtrl,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(labelText: 'Doctor ID *'),
              ),
              const SizedBox(height: Space.sm),
              TextField(
                controller: doctorNameCtrl,
                decoration: const InputDecoration(labelText: 'Doctor Name'),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Sign')),
        ],
      ),
    );
    if (ok != true) return;
    final doctorId = int.tryParse(doctorIdCtrl.text.trim());
    if (doctorId == null) {
      setState(() => _error = 'Enter a valid Doctor ID');
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });
    try {
      if (!mounted) return;
      final api = context.read<ApiClient>();
      await api.post<Map<String, dynamic>>(
        '/api/v1/discharge/summaries/$id/sign',
        {
          'doctorId': doctorId,
          if (doctorNameCtrl.text.trim().isNotEmpty) 'doctorName': doctorNameCtrl.text.trim(),
        },
        fromJson: (j) => j as Map<String, dynamic>,
      );
      setState(() => _info = 'Discharge summary signed');
      await _load();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _editDialog(Map<String, dynamic> s) async {
    final id = s['id'] as int;
    final diagCtrl = TextEditingController(text: '${s['finalDiagnosis'] ?? ''}');
    final courseCtrl = TextEditingController(text: '${s['courseInHospital'] ?? ''}');
    final procCtrl = TextEditingController(text: '${s['proceduresPerformed'] ?? ''}');
    final fuCtrl = TextEditingController(text: '${s['followUpInstructions'] ?? ''}');
    final medsCtrl = TextEditingController(text: '${s['medicationsAtDischarge'] ?? ''}');
    final actCtrl = TextEditingController(text: '${s['activityRestrictions'] ?? ''}');
    final dietCtrl = TextEditingController(text: '${s['dietAdvice'] ?? ''}');
    String? cond = s['conditionAtDischarge'] as String?;

    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setD) => AlertDialog(
          title: const Text('Edit Discharge Summary'),
          content: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
            child: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  TextField(controller: diagCtrl, maxLines: 3,
                      decoration: const InputDecoration(labelText: 'Final Diagnosis', alignLabelWithHint: true)),
                  const SizedBox(height: Space.sm),
                  TextField(controller: courseCtrl, maxLines: 4,
                      decoration: const InputDecoration(labelText: 'Course in Hospital', alignLabelWithHint: true)),
                  const SizedBox(height: Space.sm),
                  TextField(controller: procCtrl, maxLines: 3,
                      decoration: const InputDecoration(labelText: 'Procedures Performed', alignLabelWithHint: true)),
                  const SizedBox(height: Space.sm),
                  DropdownButtonFormField<String>(
                    initialValue: cond,
                    isExpanded: true,
                    decoration: const InputDecoration(labelText: 'Condition at Discharge'),
                    items: [
                      const DropdownMenuItem(value: null, child: Text('— Not specified —')),
                      for (final c in _conditions) DropdownMenuItem(value: c, child: Text(c)),
                    ],
                    onChanged: (v) => setD(() => cond = v),
                  ),
                  const SizedBox(height: Space.sm),
                  TextField(controller: fuCtrl, maxLines: 3,
                      decoration: const InputDecoration(labelText: 'Follow-up Instructions', alignLabelWithHint: true)),
                  const SizedBox(height: Space.sm),
                  TextField(controller: medsCtrl, maxLines: 3,
                      decoration: const InputDecoration(labelText: 'Medications at Discharge', alignLabelWithHint: true)),
                  const SizedBox(height: Space.sm),
                  TextField(controller: actCtrl, maxLines: 2,
                      decoration: const InputDecoration(labelText: 'Activity Restrictions', alignLabelWithHint: true)),
                  const SizedBox(height: Space.sm),
                  TextField(controller: dietCtrl, maxLines: 2,
                      decoration: const InputDecoration(labelText: 'Diet Advice', alignLabelWithHint: true)),
                ],
              ),
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
            FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Save')),
          ],
        ),
      ),
    );
    if (ok != true) return;
    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });
    try {
      if (!mounted) return;
      final api = context.read<ApiClient>();
      await api.put<Map<String, dynamic>>(
        '/api/v1/discharge/summaries/$id',
        {
          'finalDiagnosis': diagCtrl.text.trim().isEmpty ? null : diagCtrl.text.trim(),
          'courseInHospital': courseCtrl.text.trim().isEmpty ? null : courseCtrl.text.trim(),
          'proceduresPerformed': procCtrl.text.trim().isEmpty ? null : procCtrl.text.trim(),
          'conditionAtDischarge': cond,
          'followUpInstructions': fuCtrl.text.trim().isEmpty ? null : fuCtrl.text.trim(),
          'medicationsAtDischarge': medsCtrl.text.trim().isEmpty ? null : medsCtrl.text.trim(),
          'activityRestrictions': actCtrl.text.trim().isEmpty ? null : actCtrl.text.trim(),
          'dietAdvice': dietCtrl.text.trim().isEmpty ? null : dietCtrl.text.trim(),
        },
        fromJson: (j) => j as Map<String, dynamic>,
      );
      setState(() => _info = 'Discharge summary updated');
      await _load();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _showDetail(BuildContext context, Map<String, dynamic> s) {
    final id = s['id'] as int;
    final status = '${s['summaryStatus']}';
    final theme = Theme.of(context);
    showDialog(
      context: context,
      builder: (ctx) => Dialog(
        child: ConstrainedBox(
          constraints: BoxConstraints(
            maxWidth: 700,
            maxHeight: MediaQuery.of(ctx).size.height * 0.85,
          ),
          child: Padding(
            padding: const EdgeInsets.all(Space.lg),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Text('${s['summaryNumber']}', style: theme.textTheme.titleMedium),
                    const SizedBox(width: Space.sm),
                    StatusChip.auto(status),
                    const Spacer(),
                    IconButton(
                      icon: const Icon(Icons.close),
                      onPressed: () => Navigator.pop(ctx),
                    ),
                  ],
                ),
                const Divider(),
                Expanded(
                  child: SingleChildScrollView(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Wrap(spacing: Space.xl, runSpacing: Space.md, children: [
                          _kv(theme, 'Admission', '#${s['admissionId']}'),
                          if (s['conditionAtDischarge'] != null)
                            _kv(theme, 'Condition', '${s['conditionAtDischarge']}'),
                          if (s['signedByDoctorName'] != null)
                            _kv(theme, 'Signed by', 'Dr ${s['signedByDoctorName']}'),
                          if (s['signedAt'] != null)
                            _kv(theme, 'Signed at', _date(s['signedAt'])),
                        ]),
                        if (s['finalDiagnosis'] != null) ...[
                          const SizedBox(height: Space.md),
                          _section(theme, 'Final Diagnosis', '${s['finalDiagnosis']}'),
                        ],
                        if (s['courseInHospital'] != null) ...[
                          const SizedBox(height: Space.sm),
                          _section(theme, 'Course in Hospital', '${s['courseInHospital']}'),
                        ],
                        if (s['proceduresPerformed'] != null) ...[
                          const SizedBox(height: Space.sm),
                          _section(theme, 'Procedures Performed', '${s['proceduresPerformed']}'),
                        ],
                        if (s['followUpInstructions'] != null) ...[
                          const SizedBox(height: Space.sm),
                          _section(theme, 'Follow-up Instructions', '${s['followUpInstructions']}'),
                        ],
                        if (s['medicationsAtDischarge'] != null) ...[
                          const SizedBox(height: Space.sm),
                          _section(theme, 'Medications at Discharge', '${s['medicationsAtDischarge']}'),
                        ],
                        if (s['activityRestrictions'] != null) ...[
                          const SizedBox(height: Space.sm),
                          _section(theme, 'Activity Restrictions', '${s['activityRestrictions']}'),
                        ],
                        if (s['dietAdvice'] != null) ...[
                          const SizedBox(height: Space.sm),
                          _section(theme, 'Diet Advice', '${s['dietAdvice']}'),
                        ],
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: Space.md),
                Row(
                  mainAxisAlignment: MainAxisAlignment.end,
                  children: [
                    OutlinedButton.icon(
                      onPressed: () => openPdf(context, context.read<ApiClient>(),
                          '/api/v1/discharge/summaries/$id/summary.pdf',
                          filename: 'discharge-summary-$id.pdf'),
                      icon: const Icon(Icons.picture_as_pdf_outlined, size: 18),
                      label: const Text('Open PDF'),
                    ),
                    if (_canWrite && status == 'DRAFT') ...[
                      const SizedBox(width: Space.sm),
                      OutlinedButton.icon(
                        onPressed: () {
                          Navigator.pop(ctx);
                          _editDialog(s);
                        },
                        icon: const Icon(Icons.edit_outlined, size: 18),
                        label: const Text('Edit'),
                      ),
                      const SizedBox(width: Space.sm),
                      FilledButton.icon(
                        onPressed: () {
                          Navigator.pop(ctx);
                          _signDialog(id);
                        },
                        icon: const Icon(Icons.draw_outlined, size: 18),
                        label: const Text('Sign'),
                      ),
                    ],
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _section(ThemeData theme, String title, String body) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title,
            style: theme.textTheme.titleSmall
                ?.copyWith(color: theme.colorScheme.primary)),
        const SizedBox(height: Space.xs),
        Text(body, style: theme.textTheme.bodyMedium),
      ],
    );
  }

  Widget _kv(ThemeData theme, String k, String v) {
    return SizedBox(
      width: 200,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(k,
              style: theme.textTheme.bodySmall
                  ?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
          Text(v, style: theme.textTheme.titleSmall),
        ],
      ),
    );
  }

  String _date(Object? iso) => formatDate(iso, ifEmpty: '—');
}
