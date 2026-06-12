import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/api/tpa_models.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/app_shell.dart';
import '../../core/widgets/status_chip.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// Admin TPA insurance case management (body only — lives inside OwnerDashboard shell).
/// Dense list of cases with inline expandable detail; no full-screen pushes.
class TPAManagementScreen extends StatefulWidget {
  const TPAManagementScreen({super.key});

  @override
  State<TPAManagementScreen> createState() => _TPAManagementScreenState();
}

class _TPAManagementScreenState extends State<TPAManagementScreen> {
  static const _statuses = [
    'REGISTERED',
    'PREAUTH_PENDING',
    'PREAUTH_APPROVED',
    'CLAIM_SUBMITTED',
    'CLAIM_APPROVED',
  ];

  List<TPACase> _cases = [];
  bool _loading = false;
  String _status = 'REGISTERED';
  String? _error;
  String? _success;

  @override
  void initState() {
    super.initState();
    _loadCases();
  }

  Future<void> _loadCases() async {
    setState(() => _loading = true);
    try {
      final api = context.read<ApiClient>();
      final cases = await api.get<List<TPACase>>(
        '/api/v1/tpa/cases/status/$_status',
        fromJson: (json) => (json as List? ?? [])
            .map((e) => TPACase.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
      if (mounted) {
        setState(() {
          _cases = cases;
          _error = null;
        });
      }
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.error.message);
    } catch (e) {
      if (mounted) setState(() => _error = 'Failed to load cases: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _post(String path, Object body, String successMsg) async {
    setState(() {
      _loading = true;
      _error = null;
      _success = null;
    });
    try {
      final api = context.read<ApiClient>();
      await api.post<TPACase>(
        path,
        body,
        fromJson: (json) => TPACase.fromJson(json as Map<String, dynamic>),
      );
      setState(() => _success = successMsg);
      await _loadCases();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Action failed: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<String?> _promptText(String title, String label) {
    final ctrl = TextEditingController();
    return showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        content: SizedBox(
          width: 380,
          child: TextField(
            controller: ctrl,
            autofocus: true,
            decoration: InputDecoration(labelText: label),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, ctrl.text.trim()),
            child: const Text('Submit'),
          ),
        ],
      ),
    );
  }

  Future<void> _submitPreauth(TPACase c) async {
    final ref = await _promptText('Submit Preauth', 'Preauth reference number');
    if (ref == null || ref.isEmpty) return;
    await _post('/api/v1/tpa/cases/${c.id}/preauth',
        {'preauthRefNumber': ref}, 'Preauth submitted for ${c.caseNumber}');
  }

  Future<void> _approvePreauth(TPACase c) async {
    final amt = await _promptText('Approve Preauth', 'Approved amount (₹)');
    if (amt == null || amt.isEmpty) return;
    await _post('/api/v1/tpa/cases/${c.id}/preauth/approve',
        {'approvedAmount': double.tryParse(amt) ?? 0},
        'Preauth approved for ${c.caseNumber}');
  }

  Future<void> _rejectPreauth(TPACase c) async {
    final reason = await _promptText('Reject Preauth', 'Rejection reason');
    if (reason == null || reason.isEmpty) return;
    await _post('/api/v1/tpa/cases/${c.id}/preauth/reject',
        {'reason': reason}, 'Preauth rejected for ${c.caseNumber}');
  }

  Future<void> _submitClaim(TPACase c) async {
    final num = await _promptText('Submit Claim', 'Claim number');
    if (num == null || num.isEmpty) return;
    final amt = await _promptText('Submit Claim', 'Claim amount (₹)');
    if (amt == null || amt.isEmpty) return;
    await _post('/api/v1/tpa/cases/${c.id}/claim',
        {'claimNumber': num, 'claimAmount': double.tryParse(amt) ?? 0},
        'Claim submitted for ${c.caseNumber}');
  }

  Future<void> _approveClaim(TPACase c) async {
    final amt = await _promptText('Approve Claim', 'Approved amount (₹)');
    if (amt == null || amt.isEmpty) return;
    await _post('/api/v1/tpa/cases/${c.id}/claim/approve',
        {'approvedAmount': double.tryParse(amt) ?? 0},
        'Claim approved for ${c.caseNumber}');
  }

  Future<void> _rejectClaim(TPACase c) async {
    final reason = await _promptText('Reject Claim', 'Rejection reason');
    if (reason == null || reason.isEmpty) return;
    await _post('/api/v1/tpa/cases/${c.id}/claim/reject',
        {'reason': reason}, 'Claim rejected for ${c.caseNumber}');
  }

  void _showRegisterDialog() {
    showDialog(
      context: context,
      builder: (context) => _RegisterCaseDialog(onSubmit: (body) async {
        Navigator.pop(context);
        await _post('/api/v1/tpa/cases', body, 'TPA case registered');
      }),
    );
  }

  List<Widget> _actionsFor(TPACase c) {
    Widget action(String label, VoidCallback onTap, {bool danger = false}) =>
        TextButton(
          onPressed: _loading ? null : onTap,
          style: danger
              ? TextButton.styleFrom(foregroundColor: StatusColors.danger)
              : null,
          child: Text(label),
        );

    return switch (c.caseStatus) {
      'REGISTERED' => [action('Submit Preauth', () => _submitPreauth(c))],
      'PREAUTH_PENDING' => [
          action('Approve', () => _approvePreauth(c)),
          action('Reject', () => _rejectPreauth(c), danger: true),
        ],
      'PREAUTH_APPROVED' => [action('Submit Claim', () => _submitClaim(c))],
      'CLAIM_SUBMITTED' => [
          action('Approve', () => _approveClaim(c)),
          action('Reject', () => _rejectClaim(c), danger: true),
        ],
      _ => const [],
    };
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
              Text('TPA Insurance Cases', style: theme.textTheme.titleLarge),
              const Spacer(),
              IconButton(
                tooltip: 'Refresh',
                onPressed: _loading ? null : _loadCases,
                icon: const Icon(Icons.refresh),
              ),
              const SizedBox(width: Space.sm),
              FilledButton.icon(
                onPressed: _loading ? null : _showRegisterDialog,
                icon: const Icon(Icons.add, size: 18),
                label: const Text('Register Case'),
              ),
            ],
          ),
          const SizedBox(height: Space.md),
          Wrap(
            spacing: Space.sm,
            children: [
              for (final s in _statuses)
                ChoiceChip(
                  label: Text(s.replaceAll('_', ' '),
                      style: theme.textTheme.labelSmall),
                  selected: _status == s,
                  visualDensity: VisualDensity.compact,
                  onSelected: (_) {
                    setState(() => _status = s);
                    _loadCases();
                  },
                ),
            ],
          ),
          const SizedBox(height: Space.md),
          if (_error != null) ...[
            MessageBanner.error(_error!),
            const SizedBox(height: Space.md),
          ],
          if (_success != null) ...[
            MessageBanner.success(_success!),
            const SizedBox(height: Space.md),
          ],
          if (_cases.isEmpty && !_loading)
            Card(
              child: Padding(
                padding: const EdgeInsets.all(Space.xl),
                child: Center(
                  child: Text('No ${_status.replaceAll('_', ' ').toLowerCase()} cases',
                      style: theme.textTheme.bodyMedium?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant)),
                ),
              ),
            )
          else
            Expanded(
              child: Card(
                child: ListView.separated(
                  itemCount: _cases.length,
                  separatorBuilder: (_, __) => const Divider(height: 1),
                  itemBuilder: (context, i) => _caseRow(_cases[i], theme),
                ),
              ),
            ),
        ],
      ),
    );
  }

  Widget _caseRow(TPACase c, ThemeData theme) {
    final pendingDocs = c.pendingDocumentCount;
    return ExpansionTile(
      dense: true,
      tilePadding: const EdgeInsets.symmetric(horizontal: Space.md),
      childrenPadding: const EdgeInsets.fromLTRB(Space.lg, 0, Space.lg, Space.md),
      title: Row(
        children: [
          Text(c.caseNumber, style: theme.textTheme.bodyMedium
              ?.copyWith(fontWeight: FontWeight.w600)),
          const SizedBox(width: Space.md),
          Expanded(
            child: Text('${c.insurerName} • ${c.policyNumber}',
                style: theme.textTheme.bodySmall?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant),
                overflow: TextOverflow.ellipsis),
          ),
          if (pendingDocs > 0) ...[
            Text('$pendingDocs docs pending',
                style: theme.textTheme.labelSmall
                    ?.copyWith(color: StatusColors.warning)),
            const SizedBox(width: Space.md),
          ],
          StatusChip.auto(c.caseStatus),
        ],
      ),
      children: [
        _kv(theme, 'Admission', '#${c.admissionId}'),
        if (c.policyHolderName != null)
          _kv(theme, 'Policy holder', c.policyHolderName!),
        if (c.sumInsured != null)
          _kv(theme, 'Sum insured', '₹${c.sumInsured!.toStringAsFixed(0)}'),
        if (c.approvedAmount != null)
          _kv(theme, 'Approved', '₹${c.approvedAmount!.toStringAsFixed(0)}'),
        if (c.preauthRefNumber != null)
          _kv(theme, 'Preauth ref', c.preauthRefNumber!),
        if (c.claimNumber != null) _kv(theme, 'Claim no.', c.claimNumber!),
        if (c.documents.isNotEmpty) ...[
          const SizedBox(height: Space.sm),
          Wrap(
            spacing: Space.sm,
            runSpacing: Space.xs,
            children: [
              for (final d in c.documents)
                Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      d.submitted
                          ? Icons.check_circle_outline
                          : Icons.radio_button_unchecked,
                      size: 14,
                      color: d.submitted
                          ? StatusColors.success
                          : StatusColors.warning,
                    ),
                    const SizedBox(width: Space.xs),
                    Text(d.documentType, style: theme.textTheme.labelSmall),
                  ],
                ),
            ],
          ),
        ],
        const SizedBox(height: Space.sm),
        Row(
          mainAxisAlignment: MainAxisAlignment.end,
          children: _actionsFor(c),
        ),
      ],
    );
  }

  Widget _kv(ThemeData theme, String k, String v) {
    return Padding(
      padding: const EdgeInsets.only(bottom: Space.xxs),
      child: Row(
        children: [
          SizedBox(
            width: 120,
            child: Text(k,
                style: theme.textTheme.labelSmall
                    ?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
          ),
          Text(v, style: theme.textTheme.bodySmall),
        ],
      ),
    );
  }
}

/// Compact registration dialog: required fields only, single column.
class _RegisterCaseDialog extends StatefulWidget {
  const _RegisterCaseDialog({required this.onSubmit});

  final void Function(Map<String, dynamic> body) onSubmit;

  @override
  State<_RegisterCaseDialog> createState() => _RegisterCaseDialogState();
}

class _RegisterCaseDialogState extends State<_RegisterCaseDialog> {
  final _admissionCtrl = TextEditingController();
  final _patientCtrl = TextEditingController();
  final _insurerCtrl = TextEditingController();
  final _policyCtrl = TextEditingController();
  final _sumInsuredCtrl = TextEditingController();
  String? _error;

  @override
  void dispose() {
    _admissionCtrl.dispose();
    _patientCtrl.dispose();
    _insurerCtrl.dispose();
    _policyCtrl.dispose();
    _sumInsuredCtrl.dispose();
    super.dispose();
  }

  void _submit() {
    final admissionId = int.tryParse(_admissionCtrl.text.trim());
    final patientId = int.tryParse(_patientCtrl.text.trim());
    if (admissionId == null ||
        patientId == null ||
        _insurerCtrl.text.trim().isEmpty ||
        _policyCtrl.text.trim().isEmpty) {
      setState(() => _error = 'All fields except sum insured are required');
      return;
    }
    widget.onSubmit({
      'admissionId': admissionId,
      'patientId': patientId,
      'insurerName': _insurerCtrl.text.trim(),
      'policyNumber': _policyCtrl.text.trim(),
      'sumInsured': double.tryParse(_sumInsuredCtrl.text.trim()),
      'requiredDocuments': [
        'Discharge Summary',
        'Lab Reports',
        'Imaging',
        'Final Bill',
      ],
    });
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Register TPA Case'),
      content: SizedBox(
        width: 380,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (_error != null) ...[
              MessageBanner.error(_error!),
              const SizedBox(height: Space.md),
            ],
            TextField(
              controller: _admissionCtrl,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(labelText: 'Admission ID *'),
            ),
            const SizedBox(height: Space.md),
            TextField(
              controller: _patientCtrl,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(labelText: 'Patient ID *'),
            ),
            const SizedBox(height: Space.md),
            TextField(
              controller: _insurerCtrl,
              decoration: const InputDecoration(labelText: 'Insurer name *'),
            ),
            const SizedBox(height: Space.md),
            TextField(
              controller: _policyCtrl,
              decoration: const InputDecoration(labelText: 'Policy number *'),
            ),
            const SizedBox(height: Space.md),
            TextField(
              controller: _sumInsuredCtrl,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(labelText: 'Sum insured (₹)'),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Cancel'),
        ),
        FilledButton(onPressed: _submit, child: const Text('Register')),
      ],
    );
  }
}
