import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/auth/auth_state.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;
import 'patient_credit_panel.dart';

/// Patient search + detail. Look up by name / mobile / UHID and view the record.
/// Body widget — the host home supplies the AppShell.
class PatientsScreen extends StatefulWidget {
  const PatientsScreen({super.key});

  @override
  State<PatientsScreen> createState() => _PatientsScreenState();
}

class _PatientsScreenState extends State<PatientsScreen> {
  final _q = TextEditingController();
  List<Map<String, dynamic>> _results = const [];
  Map<String, dynamic>? _selected;
  bool _loading = false;
  String? _error;
  String _role = '';

  bool get _canEdit =>
      _role == 'FRONT_DESK' || _role == 'ADMIN' || _role == 'SUPER_ADMIN';
  bool get _canSeeCredit =>
      _role == 'BILLING' || _role == 'DOCTOR' || _role == 'ADMIN' ||
      _role == 'SUPER_ADMIN';

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      _role = context.read<AuthState>().currentUser?.role ?? '';
      _search();
    });
  }

  /// Re-fetch the open patient after an edit so the detail reflects the change.
  Future<void> _reloadSelected() async {
    final id = _selected?['id'];
    if (id == null) return;
    try {
      final api = context.read<ApiClient>();
      final fresh = await api.get<Map<String, dynamic>>(
        '/api/v1/patients/$id',
        fromJson: (json) => json as Map<String, dynamic>,
      );
      if (mounted) setState(() => _selected = fresh);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    }
  }

  @override
  void dispose() {
    _q.dispose();
    super.dispose();
  }

  Future<void> _search() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      final term = _q.text.trim();
      final list = await api.get<List<Map<String, dynamic>>>(
        '/api/v1/patients${term.isEmpty ? '' : '?q=$term'}',
        fromJson: (json) =>
            List<Map<String, dynamic>>.from(json as List? ?? const []),
      );
      if (mounted) setState(() => _results = list);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return PageContainer(
      scrollable: false,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Patients', style: theme.textTheme.titleLarge),
          const SizedBox(height: Space.md),
          if (_error != null) ...[
            MessageBanner.error(_error!),
            const SizedBox(height: Space.md),
          ],
          Row(
            children: [
              Expanded(
                child: TextField(
                  controller: _q,
                  decoration: const InputDecoration(
                    labelText: 'Search name / mobile / UHID',
                    prefixIcon: Icon(Icons.search, size: 18),
                  ),
                  onSubmitted: (_) => _search(),
                ),
              ),
              const SizedBox(width: Space.md),
              FilledButton.icon(
                onPressed: _loading ? null : _search,
                icon: const Icon(Icons.search, size: 18),
                label: const Text('Search'),
              ),
            ],
          ),
          const SizedBox(height: Space.md),
          Expanded(child: _selected != null ? _detail(theme, _selected!) : _list(theme)),
        ],
      ),
    );
  }

  Widget _list(ThemeData theme) {
    if (_results.isEmpty) {
      return Center(
        child: Text(_loading ? 'Searching…' : 'No patients found',
            style: theme.textTheme.bodyMedium
                ?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
      );
    }
    return ListView.separated(
      itemCount: _results.length,
      separatorBuilder: (_, __) => const Divider(height: 1),
      itemBuilder: (context, i) {
        final p = _results[i];
        return ListTile(
          onTap: () => setState(() => _selected = p),
          leading: CircleAvatar(child: Text(_initials(p))),
          title: Text('${p['fullName'] ?? ''}', style: theme.textTheme.titleSmall),
          subtitle: Text(
            'UHID ${p['uhid'] ?? '—'} · ${p['mobile'] ?? '—'}'
            '${p['age'] != null ? ' · ${p['age']}y' : ''}'
            '${p['gender'] != null ? ' · ${p['gender']}' : ''}',
            style: theme.textTheme.bodySmall,
          ),
          trailing: const Icon(Icons.chevron_right),
        );
      },
    );
  }

  Widget _detail(ThemeData theme, Map<String, dynamic> p) {
    return SingleChildScrollView(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          TextButton.icon(
            onPressed: () => setState(() => _selected = null),
            icon: const Icon(Icons.arrow_back, size: 18),
            label: const Text('Back to results'),
          ),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(Space.lg),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      CircleAvatar(radius: 22, child: Text(_initials(p))),
                      const SizedBox(width: Space.md),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text('${p['fullName'] ?? ''}',
                                style: theme.textTheme.titleLarge),
                            Text('UHID ${p['uhid'] ?? '—'} · ID #${p['id']}',
                                style: theme.textTheme.bodySmall?.copyWith(
                                    color: theme.colorScheme.onSurfaceVariant)),
                          ],
                        ),
                      ),
                      if (_canEdit)
                        OutlinedButton.icon(
                          onPressed: () => _editDialog(p),
                          icon: const Icon(Icons.edit_outlined, size: 18),
                          label: const Text('Edit'),
                        ),
                    ],
                  ),
                  const Divider(height: Space.xl),
                  Wrap(
                    spacing: Space.xl,
                    runSpacing: Space.md,
                    children: [
                      _kv(theme, 'Mobile', '${p['mobile'] ?? '—'}'),
                      _kv(theme, 'Age', p['age'] != null ? '${p['age']}y' : '—'),
                      _kv(theme, 'Gender', '${p['gender'] ?? '—'}'),
                      _kv(theme, 'Blood group', '${p['bloodGroup'] ?? '—'}'),
                      _kv(theme, 'DOB', _date(p['dateOfBirth'])),
                      _kv(theme, 'Email', '${p['email'] ?? '—'}'),
                      _kv(theme, 'City', '${p['city'] ?? '—'}'),
                      _kv(theme, 'Emergency', '${p['emergencyContactName'] ?? '—'} '
                          '${p['emergencyContactPhone'] ?? ''}'),
                    ],
                  ),
                  if ((p['allergies'] ?? '').toString().isNotEmpty) ...[
                    const SizedBox(height: Space.md),
                    Text('Allergies: ${p['allergies']}',
                        style: theme.textTheme.bodyMedium
                            ?.copyWith(color: theme.colorScheme.error)),
                  ],
                  if ((p['chronicConditions'] ?? '').toString().isNotEmpty)
                    Padding(
                      padding: const EdgeInsets.only(top: Space.xs),
                      child: Text('Chronic: ${p['chronicConditions']}',
                          style: theme.textTheme.bodyMedium),
                    ),
                ],
              ),
            ),
          ),
          if (_canSeeCredit && p['id'] != null) ...[
            const SizedBox(height: Space.md),
            PatientCreditPanel(
              key: ValueKey('credit-${p['id']}'),
              patientId: p['id'] as int,
              role: _role,
            ),
          ],
        ],
      ),
    );
  }

  Future<void> _editDialog(Map<String, dynamic> p) async {
    final ctrls = <String, TextEditingController>{
      for (final f in [
        'firstName', 'middleName', 'lastName', 'mobile', 'email',
        'bloodGroup', 'occupation', 'addressLine1', 'addressLine2',
        'city', 'state', 'pincode', 'emergencyContactName',
        'emergencyContactPhone', 'emergencyContactRelation',
        'allergies', 'chronicConditions', 'medications', 'notes',
      ])
        f: TextEditingController(text: '${p[f] ?? ''}'),
    };
    String? gender = p['gender'] as String?;
    String? marital = p['maritalStatus'] as String?;

    Widget field(String key, String label, {int maxLines = 1}) => Padding(
          padding: const EdgeInsets.only(bottom: Space.sm),
          child: TextField(
            controller: ctrls[key],
            maxLines: maxLines,
            decoration: InputDecoration(labelText: label),
          ),
        );

    final save = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Edit ${p['fullName'] ?? 'patient'}'),
        content: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                field('firstName', 'First name'),
                field('middleName', 'Middle name'),
                field('lastName', 'Last name'),
                field('mobile', 'Mobile'),
                field('email', 'Email'),
                StatefulBuilder(
                  builder: (context, setLocal) => Row(
                    children: [
                      Expanded(
                        child: DropdownButtonFormField<String>(
                          value: gender,
                          decoration: const InputDecoration(labelText: 'Gender'),
                          items: const [
                            DropdownMenuItem(value: 'MALE', child: Text('Male')),
                            DropdownMenuItem(value: 'FEMALE', child: Text('Female')),
                            DropdownMenuItem(value: 'OTHER', child: Text('Other')),
                            DropdownMenuItem(
                                value: 'PREFER_NOT_TO_SAY', child: Text('Prefer not to say')),
                          ],
                          onChanged: (v) => setLocal(() => gender = v),
                        ),
                      ),
                      const SizedBox(width: Space.md),
                      Expanded(
                        child: DropdownButtonFormField<String>(
                          value: marital,
                          decoration: const InputDecoration(labelText: 'Marital status'),
                          items: const [
                            DropdownMenuItem(value: 'SINGLE', child: Text('Single')),
                            DropdownMenuItem(value: 'MARRIED', child: Text('Married')),
                            DropdownMenuItem(value: 'DIVORCED', child: Text('Divorced')),
                            DropdownMenuItem(value: 'WIDOWED', child: Text('Widowed')),
                            DropdownMenuItem(
                                value: 'PREFER_NOT_TO_SAY', child: Text('Prefer not to say')),
                          ],
                          onChanged: (v) => setLocal(() => marital = v),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: Space.sm),
                field('bloodGroup', 'Blood group'),
                field('occupation', 'Occupation'),
                field('addressLine1', 'Address line 1'),
                field('addressLine2', 'Address line 2'),
                field('city', 'City'),
                field('state', 'State'),
                field('pincode', 'Pincode'),
                field('emergencyContactName', 'Emergency contact name'),
                field('emergencyContactPhone', 'Emergency contact phone'),
                field('emergencyContactRelation', 'Emergency contact relation'),
                field('allergies', 'Allergies', maxLines: 2),
                field('chronicConditions', 'Chronic conditions', maxLines: 2),
                field('medications', 'Medications', maxLines: 2),
                field('notes', 'Notes', maxLines: 2),
              ],
            ),
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Save')),
        ],
      ),
    );
    if (save != true) return;

    // Only send non-empty values; the backend update is null-safe (partial).
    final body = <String, dynamic>{};
    ctrls.forEach((k, c) {
      final v = c.text.trim();
      if (v.isNotEmpty) body[k] = v;
    });
    if (gender != null) body['gender'] = gender;
    if (marital != null) body['maritalStatus'] = marital;

    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      await api.put<Map<String, dynamic>>(
        '/api/v1/patients/${p['id']}',
        body,
        fromJson: (json) => json as Map<String, dynamic>,
      );
      await _reloadSelected();
      await _search();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
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

  String _initials(Map<String, dynamic> p) {
    final name = '${p['fullName'] ?? ''}'.trim();
    if (name.isEmpty) return '?';
    final parts = name.split(RegExp(r'\s+'));
    final first = parts.first.isNotEmpty ? parts.first[0] : '';
    final last = parts.length > 1 && parts.last.isNotEmpty ? parts.last[0] : '';
    return (first + last).toUpperCase();
  }

  String _date(Object? iso) {
    if (iso == null) return '—';
    final s = '$iso';
    return s.contains('T') ? s.split('T').first : s;
  }
}
