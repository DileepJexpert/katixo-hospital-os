import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/auth/auth_state.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/util/pdf_actions.dart';
import '../../core/widgets/empty_state.dart';
import '../../core/widgets/section_card.dart';
import '../../core/widgets/status_chip.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;
import '../patient/patient_picker.dart';

/// Medical certificates: issue a certificate (from a template or free-form) to a
/// patient, list/print (PDF) certificates, and revoke one. ADMIN manages the
/// template master. Body widget. DOCTOR/ADMIN issue + revoke; clinical roles view.
class CertificateScreen extends StatefulWidget {
  const CertificateScreen({super.key});

  @override
  State<CertificateScreen> createState() => _CertificateScreenState();
}

class _CertificateScreenState extends State<CertificateScreen> {
  static const _types = <String>[
    'FITNESS', 'MEDICAL', 'SICKNESS', 'BIRTH', 'DEATH', 'MLC', 'DISABILITY', 'VACCINATION', 'OTHER',
  ];

  String _tab = 'records';
  List<Map<String, dynamic>> _certificates = const [];
  List<Map<String, dynamic>> _templates = const [];
  bool _loading = false;
  String? _error;
  String? _info;

  String get _role => context.read<AuthState>().currentUser?.role ?? '';
  bool get _isAdmin => _role == 'ADMIN' || _role == 'SUPER_ADMIN';
  bool get _canIssue => _isAdmin || _role == 'DOCTOR';

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      final certs = await api.get<List<Map<String, dynamic>>>(
        '/api/v1/certificates?limit=50',
        fromJson: (j) => List<Map<String, dynamic>>.from(j as List? ?? const []),
      );
      final templates = await api.get<List<Map<String, dynamic>>>(
        '/api/v1/certificates/templates',
        fromJson: (j) => List<Map<String, dynamic>>.from(j as List? ?? const []),
      );
      if (mounted) {
        setState(() {
          _certificates = certs;
          _templates = templates;
        });
      }
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Could not load certificates: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _act(Future<void> Function() action, String okMsg) async {
    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });
    try {
      await action();
      setState(() => _info = okMsg);
      await _load();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Action failed: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  // ---------------- issue ----------------

  Future<void> _issueDialog() async {
    final patient = await showPatientPicker(context);
    if (patient == null || !mounted) return;

    int? templateId;
    String type = 'FITNESS';
    DateTime issueDate = DateTime.now();
    DateTime? validFrom;
    DateTime? validTo;
    final titleCtrl = TextEditingController();
    final bodyCtrl = TextEditingController();
    final doctorNameCtrl = TextEditingController(
        text: _role == 'DOCTOR' ? (context.read<AuthState>().currentUser?.name ?? '') : '');
    final remarksCtrl = TextEditingController();
    String iso(DateTime d) => d.toIso8601String().split('T').first;

    final proceed = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setLocal) {
          final freeForm = templateId == null;
          return AlertDialog(
            title: Text('Certificate — ${patient['fullName']}'),
            content: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
              child: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    DropdownButtonFormField<int?>(
                      value: templateId,
                      isExpanded: true,
                      decoration: const InputDecoration(labelText: 'Template'),
                      items: [
                        const DropdownMenuItem<int?>(value: null, child: Text('Free-form (enter below)')),
                        for (final t in _templates)
                          DropdownMenuItem<int?>(value: t['id'] as int, child: Text('${t['title']}')),
                      ],
                      onChanged: (v) => setLocal(() => templateId = v),
                    ),
                    const SizedBox(height: Space.sm),
                    if (freeForm) ...[
                      DropdownButtonFormField<String>(
                        value: type,
                        isExpanded: true,
                        decoration: const InputDecoration(labelText: 'Certificate type'),
                        items: [for (final t in _types) DropdownMenuItem(value: t, child: Text(_title(t)))],
                        onChanged: (v) => setLocal(() => type = v ?? 'FITNESS'),
                      ),
                      TextField(controller: titleCtrl,
                          decoration: const InputDecoration(labelText: 'Title *')),
                      TextField(controller: bodyCtrl, maxLines: 5,
                          decoration: const InputDecoration(labelText: 'Certificate text *')),
                      const SizedBox(height: Space.sm),
                    ],
                    TextField(controller: doctorNameCtrl,
                        decoration: const InputDecoration(labelText: 'Issuing doctor')),
                    const SizedBox(height: Space.sm),
                    OutlinedButton.icon(
                      onPressed: () async {
                        final p = await showDatePicker(
                            context: context, initialDate: issueDate,
                            firstDate: DateTime(2020), lastDate: DateTime.now());
                        if (p != null) setLocal(() => issueDate = p);
                      },
                      icon: const Icon(Icons.calendar_today, size: 16),
                      label: Text('Issue date ${iso(issueDate)}'),
                    ),
                    const SizedBox(height: Space.xs),
                    Row(
                      children: [
                        Expanded(
                          child: OutlinedButton(
                            onPressed: () async {
                              final p = await showDatePicker(
                                  context: context, initialDate: validFrom ?? issueDate,
                                  firstDate: DateTime(2020), lastDate: DateTime(2030));
                              if (p != null) setLocal(() => validFrom = p);
                            },
                            child: Text(validFrom == null ? 'Valid from' : 'From ${iso(validFrom!)}'),
                          ),
                        ),
                        const SizedBox(width: Space.sm),
                        Expanded(
                          child: OutlinedButton(
                            onPressed: () async {
                              final p = await showDatePicker(
                                  context: context, initialDate: validTo ?? issueDate,
                                  firstDate: DateTime(2020), lastDate: DateTime(2030));
                              if (p != null) setLocal(() => validTo = p);
                            },
                            child: Text(validTo == null ? 'Valid to' : 'To ${iso(validTo!)}'),
                          ),
                        ),
                      ],
                    ),
                    TextField(controller: remarksCtrl,
                        decoration: const InputDecoration(labelText: 'Remarks')),
                  ],
                ),
              ),
            ),
            actions: [
              TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
              FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Issue')),
            ],
          );
        },
      ),
    );
    if (proceed != true) return;
    if (templateId == null && (titleCtrl.text.trim().isEmpty || bodyCtrl.text.trim().isEmpty)) {
      setState(() => _error = 'A free-form certificate needs a title and text');
      return;
    }
    await _act(() async {
      final api = context.read<ApiClient>();
      await api.post<dynamic>('/api/v1/certificates', {
        'patientId': patient['id'],
        if (templateId != null) 'templateId': templateId,
        if (templateId == null) 'certificateType': type,
        if (templateId == null) 'title': titleCtrl.text.trim(),
        if (templateId == null) 'bodyText': bodyCtrl.text.trim(),
        if (doctorNameCtrl.text.trim().isNotEmpty) 'issuingDoctorName': doctorNameCtrl.text.trim(),
        'issueDate': iso(issueDate),
        if (validFrom != null) 'validFrom': iso(validFrom!),
        if (validTo != null) 'validTo': iso(validTo!),
        if (remarksCtrl.text.trim().isNotEmpty) 'remarks': remarksCtrl.text.trim(),
      }, fromJson: (j) => j);
    }, 'Certificate issued');
  }

  Future<void> _revokeDialog(Map<String, dynamic> c) async {
    final reasonCtrl = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Revoke ${c['certificateNumber']}'),
        content: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
          child: TextField(controller: reasonCtrl, maxLines: 3,
              decoration: const InputDecoration(labelText: 'Reason for revocation *')),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Revoke')),
        ],
      ),
    );
    if (ok != true || reasonCtrl.text.trim().isEmpty) return;
    await _act(() async {
      await context.read<ApiClient>().post<dynamic>(
          '/api/v1/certificates/${c['id']}/revoke',
          {'reason': reasonCtrl.text.trim()}, fromJson: (j) => j);
    }, 'Certificate revoked');
  }

  Future<void> _openPdf(Map<String, dynamic> c) async {
    await openPdf(
      context,
      context.read<ApiClient>(),
      '/api/v1/certificates/${c['id']}/certificate.pdf',
      filename: 'certificate-${c['certificateNumber']}.pdf',
    );
  }

  // ---------------- templates ----------------

  Future<void> _addTemplateDialog() async {
    final codeCtrl = TextEditingController();
    final titleCtrl = TextEditingController();
    final bodyCtrl = TextEditingController();
    final langCtrl = TextEditingController(text: 'ENGLISH');
    String type = 'FITNESS';
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setLocal) => AlertDialog(
          title: const Text('Add certificate template'),
          content: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
            child: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  TextField(controller: codeCtrl, decoration: const InputDecoration(labelText: 'Code *')),
                  TextField(controller: titleCtrl, decoration: const InputDecoration(labelText: 'Title *')),
                  const SizedBox(height: Space.sm),
                  DropdownButtonFormField<String>(
                    value: type,
                    isExpanded: true,
                    decoration: const InputDecoration(labelText: 'Certificate type'),
                    items: [for (final t in _types) DropdownMenuItem(value: t, child: Text(_title(t)))],
                    onChanged: (v) => setLocal(() => type = v ?? 'FITNESS'),
                  ),
                  TextField(controller: bodyCtrl, maxLines: 5,
                      decoration: const InputDecoration(labelText: 'Certificate text *')),
                  TextField(controller: langCtrl, decoration: const InputDecoration(labelText: 'Language')),
                ],
              ),
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
            FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Add')),
          ],
        ),
      ),
    );
    if (ok != true) return;
    if (codeCtrl.text.trim().isEmpty || titleCtrl.text.trim().isEmpty || bodyCtrl.text.trim().isEmpty) {
      setState(() => _error = 'Code, title and certificate text are required');
      return;
    }
    await _act(() async {
      await context.read<ApiClient>().post<dynamic>('/api/v1/certificates/templates', {
        'code': codeCtrl.text.trim(),
        'title': titleCtrl.text.trim(),
        'certificateType': type,
        'bodyText': bodyCtrl.text.trim(),
        'language': langCtrl.text.trim(),
      }, fromJson: (j) => j);
    }, 'Template added');
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final tabs = <(String, String)>[
      ('records', 'Certificates'),
      if (_isAdmin) ('templates', 'Templates'),
    ];
    if (!tabs.any((t) => t.$1 == _tab)) _tab = 'records';
    return PageContainer(
      scrollable: false,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text('Certificates', style: theme.textTheme.titleLarge),
              const Spacer(),
              IconButton(
                tooltip: 'Refresh',
                onPressed: _loading ? null : _load,
                icon: const Icon(Icons.refresh),
              ),
            ],
          ),
          const SizedBox(height: Space.md),
          if (tabs.length > 1) ...[
            Wrap(
              spacing: Space.sm,
              children: [
                for (final t in tabs)
                  ChoiceChip(
                    label: Text(t.$2),
                    selected: _tab == t.$1,
                    onSelected: (_) => setState(() => _tab = t.$1),
                  ),
              ],
            ),
            const SizedBox(height: Space.md),
          ],
          if (_error != null) ...[
            MessageBanner.error(_error!),
            const SizedBox(height: Space.md),
          ],
          if (_info != null) ...[
            MessageBanner.success(_info!),
            const SizedBox(height: Space.md),
          ],
          Expanded(child: _tab == 'templates' ? _templatesTab(theme) : _recordsTab(theme)),
        ],
      ),
    );
  }

  Widget _recordsTab(ThemeData theme) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (_canIssue)
          Align(
            alignment: Alignment.centerRight,
            child: FilledButton.icon(
              onPressed: _loading ? null : _issueDialog,
              icon: const Icon(Icons.add, size: 18),
              label: const Text('Issue certificate'),
            ),
          ),
        const SizedBox(height: Space.md),
        Expanded(
          child: _certificates.isEmpty
              ? EmptyState(
                  icon: Icons.workspace_premium_outlined,
                  title: _loading ? 'Loading…' : 'No certificates',
                  message: 'Issued certificates appear here.')
              : ListView(children: [for (final c in _certificates) _recordTile(theme, c)]),
        ),
      ],
    );
  }

  Widget _recordTile(ThemeData theme, Map<String, dynamic> c) {
    final status = '${c['certificateStatus']}';
    return SectionCard(
      title: '${c['certificateNumber']} · ${c['title']}',
      icon: Icons.workspace_premium_outlined,
      subtitle: '${_title('${c['certificateType']}')} · patient #${c['patientId']}'
          ' · ${c['issueDate'] ?? ''}',
      action: StatusChip.auto(status),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if ((c['validFrom'] ?? '').toString().isNotEmpty || (c['validTo'] ?? '').toString().isNotEmpty)
            Text('Valid: ${c['validFrom'] ?? '—'} to ${c['validTo'] ?? '—'}',
                style: theme.textTheme.bodySmall),
          if ((c['issuingDoctorName'] ?? '').toString().isNotEmpty)
            Text('Issued by Dr ${c['issuingDoctorName']}', style: theme.textTheme.bodySmall),
          if ((c['revokedReason'] ?? '').toString().isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(top: Space.xs),
              child: Text('Revoked: ${c['revokedReason']}',
                  style: const TextStyle(color: StatusColors.danger, fontSize: 12)),
            ),
          const SizedBox(height: Space.sm),
          Wrap(
            spacing: Space.sm,
            children: [
              OutlinedButton.icon(
                onPressed: () => _openPdf(c),
                icon: const Icon(Icons.picture_as_pdf_outlined, size: 16),
                label: const Text('PDF'),
              ),
              if (_canIssue && status == 'ISSUED')
                FilledButton(
                  onPressed: _loading ? null : () => _revokeDialog(c),
                  child: const Text('Revoke'),
                ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _templatesTab(ThemeData theme) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Align(
          alignment: Alignment.centerRight,
          child: FilledButton.icon(
            onPressed: _loading ? null : _addTemplateDialog,
            icon: const Icon(Icons.add, size: 18),
            label: const Text('Add template'),
          ),
        ),
        const SizedBox(height: Space.md),
        Expanded(
          child: _templates.isEmpty
              ? const EmptyState(
                  icon: Icons.description_outlined,
                  title: 'No templates',
                  message: 'Add standard certificate wordings to reuse at issue.')
              : ListView(children: [for (final t in _templates) _templateTile(theme, t)]),
        ),
      ],
    );
  }

  Widget _templateTile(ThemeData theme, Map<String, dynamic> t) {
    return SectionCard(
      title: '${t['code']} · ${t['title']}',
      icon: Icons.description_outlined,
      subtitle: '${_title('${t['certificateType']}')}'
          '${(t['language'] ?? '').toString().isNotEmpty ? ' · ${t['language']}' : ''}',
      child: Text('${t['bodyText'] ?? ''}',
          maxLines: 3, overflow: TextOverflow.ellipsis, style: theme.textTheme.bodySmall),
    );
  }

  String _title(String s) => s
      .toLowerCase()
      .split('_')
      .map((w) => w.isEmpty ? w : '${w[0].toUpperCase()}${w.substring(1)}')
      .join(' ');
}
