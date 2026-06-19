import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/auth/auth_state.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/empty_state.dart';
import '../../core/widgets/section_card.dart';
import '../../core/widgets/status_chip.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;
import '../patient/patient_picker.dart';

/// Patient consent: capture a signed consent (from a template or free-form) for
/// an encounter, list a patient's consents, and withdraw one. ADMIN manages the
/// template master. Body widget. Clinical roles capture; DOCTOR/ADMIN withdraw.
class ConsentScreen extends StatefulWidget {
  const ConsentScreen({super.key});

  @override
  State<ConsentScreen> createState() => _ConsentScreenState();
}

class _ConsentScreenState extends State<ConsentScreen> {
  static const _types = <String>[
    'SURGERY', 'ANAESTHESIA', 'PROCEDURE', 'ADMISSION', 'BLOOD_TRANSFUSION',
    'HIV_TEST', 'DNR', 'RESEARCH', 'PHOTOGRAPHY', 'GENERAL',
  ];
  static const _signatories = <String>[
    'PATIENT', 'GUARDIAN', 'NEXT_OF_KIN', 'SPOUSE', 'PARENT', 'OTHER',
  ];
  static const _sources = <String>['', 'OPD_VISIT', 'IPD_ADMISSION', 'OT_BOOKING', 'GENERAL'];

  String _tab = 'records';
  List<Map<String, dynamic>> _records = const [];
  List<Map<String, dynamic>> _templates = const [];
  bool _loading = false;
  String? _error;
  String? _info;

  String get _role => context.read<AuthState>().currentUser?.role ?? '';
  bool get _isAdmin => _role == 'ADMIN' || _role == 'SUPER_ADMIN';
  bool get _canWithdraw => _isAdmin || _role == 'DOCTOR';

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
      final records = await api.get<List<Map<String, dynamic>>>(
        '/api/v1/consent/records?limit=50',
        fromJson: (j) => List<Map<String, dynamic>>.from(j as List? ?? const []),
      );
      // Templates are needed both for the Templates tab and the capture dialog.
      final templates = await api.get<List<Map<String, dynamic>>>(
        '/api/v1/consent/templates',
        fromJson: (j) => List<Map<String, dynamic>>.from(j as List? ?? const []),
      );
      if (mounted) {
        setState(() {
          _records = records;
          _templates = templates;
        });
      }
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Could not load consents: $e');
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

  // ---------------- capture ----------------

  Future<void> _captureDialog() async {
    final patient = await showPatientPicker(context);
    if (patient == null || !mounted) return;

    int? templateId; // null = free-form
    String type = 'GENERAL';
    String signatory = 'PATIENT';
    String source = '';
    bool refused = false;
    final titleCtrl = TextEditingController();
    final bodyCtrl = TextEditingController();
    final signatoryNameCtrl = TextEditingController(text: '${patient['fullName'] ?? ''}');
    final relationCtrl = TextEditingController();
    final witnessCtrl = TextEditingController();
    final sourceIdCtrl = TextEditingController();

    final proceed = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setLocal) {
          final freeForm = templateId == null;
          return AlertDialog(
            title: Text('Consent — ${patient['fullName']}'),
            content: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
              child: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    DropdownButtonFormField<int?>(
                      initialValue: templateId,
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
                        initialValue: type,
                        isExpanded: true,
                        decoration: const InputDecoration(labelText: 'Consent type'),
                        items: [for (final t in _types) DropdownMenuItem(value: t, child: Text(_title(t)))],
                        onChanged: (v) => setLocal(() => type = v ?? 'GENERAL'),
                      ),
                      TextField(controller: titleCtrl,
                          decoration: const InputDecoration(labelText: 'Title *')),
                      TextField(controller: bodyCtrl, maxLines: 4,
                          decoration: const InputDecoration(labelText: 'Consent text *')),
                      const SizedBox(height: Space.sm),
                    ],
                    DropdownButtonFormField<String>(
                      initialValue: signatory,
                      isExpanded: true,
                      decoration: const InputDecoration(labelText: 'Signed by'),
                      items: [for (final s in _signatories) DropdownMenuItem(value: s, child: Text(_title(s)))],
                      onChanged: (v) => setLocal(() => signatory = v ?? 'PATIENT'),
                    ),
                    TextField(controller: signatoryNameCtrl,
                        decoration: const InputDecoration(labelText: 'Signatory name *')),
                    if (signatory != 'PATIENT')
                      TextField(controller: relationCtrl,
                          decoration: const InputDecoration(labelText: 'Relation to patient *')),
                    TextField(controller: witnessCtrl,
                        decoration: const InputDecoration(labelText: 'Witness name')),
                    const SizedBox(height: Space.sm),
                    DropdownButtonFormField<String>(
                      initialValue: source,
                      isExpanded: true,
                      decoration: const InputDecoration(labelText: 'Linked encounter (optional)'),
                      items: [
                        for (final s in _sources)
                          DropdownMenuItem(value: s, child: Text(s.isEmpty ? 'None' : _title(s))),
                      ],
                      onChanged: (v) => setLocal(() => source = v ?? ''),
                    ),
                    if (source.isNotEmpty)
                      TextField(controller: sourceIdCtrl, keyboardType: TextInputType.number,
                          decoration: const InputDecoration(labelText: 'Encounter ID')),
                    const SizedBox(height: Space.sm),
                    CheckboxListTile(
                      contentPadding: EdgeInsets.zero,
                      title: const Text('Patient refused (record a declined consent)'),
                      value: refused,
                      onChanged: (v) => setLocal(() => refused = v ?? false),
                    ),
                  ],
                ),
              ),
            ),
            actions: [
              TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
              FilledButton(onPressed: () => Navigator.pop(context, true),
                  child: Text(refused ? 'Record refusal' : 'Capture consent')),
            ],
          );
        },
      ),
    );
    if (proceed != true) return;
    if (signatoryNameCtrl.text.trim().isEmpty) {
      setState(() => _error = 'Signatory name is required');
      return;
    }
    if (templateId == null && (titleCtrl.text.trim().isEmpty || bodyCtrl.text.trim().isEmpty)) {
      setState(() => _error = 'A free-form consent needs a title and text');
      return;
    }
    if (signatory != 'PATIENT' && relationCtrl.text.trim().isEmpty) {
      setState(() => _error = 'Relationship to patient is required for a non-patient signatory');
      return;
    }
    await _act(() async {
      final api = context.read<ApiClient>();
      await api.post<dynamic>('/api/v1/consent/records', {
        'patientId': patient['id'],
        if (templateId != null) 'templateId': templateId,
        if (templateId == null) 'consentType': type,
        if (templateId == null) 'title': titleCtrl.text.trim(),
        if (templateId == null) 'bodyText': bodyCtrl.text.trim(),
        if (source.isNotEmpty) 'sourceType': source,
        if (source.isNotEmpty && sourceIdCtrl.text.trim().isNotEmpty)
          'sourceId': int.tryParse(sourceIdCtrl.text.trim()),
        'signatory': signatory,
        'signatoryName': signatoryNameCtrl.text.trim(),
        if (signatory != 'PATIENT') 'relationToPatient': relationCtrl.text.trim(),
        if (witnessCtrl.text.trim().isNotEmpty) 'witnessName': witnessCtrl.text.trim(),
        'consentStatus': refused ? 'REFUSED' : 'GIVEN',
      }, fromJson: (j) => j);
    }, refused ? 'Refusal recorded' : 'Consent captured');
  }

  Future<void> _withdrawDialog(Map<String, dynamic> r) async {
    final reasonCtrl = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Withdraw ${r['recordNumber']}'),
        content: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
          child: TextField(controller: reasonCtrl, maxLines: 3,
              decoration: const InputDecoration(labelText: 'Reason for withdrawal *')),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Withdraw')),
        ],
      ),
    );
    if (ok != true || reasonCtrl.text.trim().isEmpty) return;
    await _act(() async {
      await context.read<ApiClient>().post<dynamic>(
          '/api/v1/consent/records/${r['id']}/withdraw',
          {'reason': reasonCtrl.text.trim()}, fromJson: (j) => j);
    }, 'Consent withdrawn');
  }

  Future<void> _viewRecord(Map<String, dynamic> r) async {
    await showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('${r['recordNumber']} · ${r['title']}'),
        content: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('${_title('${r['consentType']}')} · ${r['consentStatus']}',
                    style: Theme.of(context).textTheme.labelLarge),
                const SizedBox(height: Space.sm),
                Text('${r['bodyText'] ?? ''}'),
                const SizedBox(height: Space.md),
                Text('Signed by: ${_title('${r['signatory']}')} — ${r['signatoryName'] ?? ''}'
                    '${(r['relationToPatient'] ?? '').toString().isNotEmpty ? ' (${r['relationToPatient']})' : ''}'),
                if ((r['witnessName'] ?? '').toString().isNotEmpty) Text('Witness: ${r['witnessName']}'),
                if ((r['withdrawnReason'] ?? '').toString().isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.only(top: Space.sm),
                    child: Text('Withdrawn: ${r['withdrawnReason']}',
                        style: const TextStyle(color: StatusColors.danger)),
                  ),
              ],
            ),
          ),
        ),
        actions: [TextButton(onPressed: () => Navigator.pop(context), child: const Text('Close'))],
      ),
    );
  }

  // ---------------- templates ----------------

  Future<void> _addTemplateDialog() async {
    final codeCtrl = TextEditingController();
    final titleCtrl = TextEditingController();
    final bodyCtrl = TextEditingController();
    final langCtrl = TextEditingController(text: 'ENGLISH');
    String type = 'SURGERY';
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setLocal) => AlertDialog(
          title: const Text('Add consent template'),
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
                    initialValue: type,
                    isExpanded: true,
                    decoration: const InputDecoration(labelText: 'Consent type'),
                    items: [for (final t in _types) DropdownMenuItem(value: t, child: Text(_title(t)))],
                    onChanged: (v) => setLocal(() => type = v ?? 'SURGERY'),
                  ),
                  TextField(controller: bodyCtrl, maxLines: 5,
                      decoration: const InputDecoration(labelText: 'Consent text *')),
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
      setState(() => _error = 'Code, title and consent text are required');
      return;
    }
    await _act(() async {
      await context.read<ApiClient>().post<dynamic>('/api/v1/consent/templates', {
        'code': codeCtrl.text.trim(),
        'title': titleCtrl.text.trim(),
        'consentType': type,
        'bodyText': bodyCtrl.text.trim(),
        'language': langCtrl.text.trim(),
      }, fromJson: (j) => j);
    }, 'Template added');
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final tabs = <(String, String)>[
      ('records', 'Consents'),
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
              Text('Patient Consent', style: theme.textTheme.titleLarge),
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
        Align(
          alignment: Alignment.centerRight,
          child: FilledButton.icon(
            onPressed: _loading ? null : _captureDialog,
            icon: const Icon(Icons.add, size: 18),
            label: const Text('Capture consent'),
          ),
        ),
        const SizedBox(height: Space.md),
        Expanded(
          child: _records.isEmpty
              ? EmptyState(
                  icon: Icons.assignment_turned_in_outlined,
                  title: _loading ? 'Loading…' : 'No consents',
                  message: 'Captured patient consents appear here.')
              : ListView(children: [for (final r in _records) _recordTile(theme, r)]),
        ),
      ],
    );
  }

  Widget _recordTile(ThemeData theme, Map<String, dynamic> r) {
    final status = '${r['consentStatus']}';
    return SectionCard(
      title: '${r['recordNumber']} · ${r['title']}',
      icon: Icons.assignment_turned_in_outlined,
      subtitle: '${_title('${r['consentType']}')} · patient #${r['patientId']}'
          ' · ${_title('${r['signatory']}')}: ${r['signatoryName'] ?? ''}',
      action: StatusChip.auto(status),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if ((r['sourceType'] ?? '').toString().isNotEmpty)
            Text('For ${_title('${r['sourceType']}')}'
                '${r['sourceId'] != null ? ' #${r['sourceId']}' : ''}',
                style: theme.textTheme.bodySmall),
          const SizedBox(height: Space.sm),
          Wrap(
            spacing: Space.sm,
            children: [
              OutlinedButton(
                onPressed: () => _viewRecord(r),
                child: const Text('View'),
              ),
              if (_canWithdraw && status == 'GIVEN')
                FilledButton(
                  onPressed: _loading ? null : () => _withdrawDialog(r),
                  child: const Text('Withdraw'),
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
                  message: 'Add standard consent wordings to reuse at capture.')
              : ListView(children: [for (final t in _templates) _templateTile(theme, t)]),
        ),
      ],
    );
  }

  Widget _templateTile(ThemeData theme, Map<String, dynamic> t) {
    return SectionCard(
      title: '${t['code']} · ${t['title']}',
      icon: Icons.description_outlined,
      subtitle: '${_title('${t['consentType']}')}'
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
