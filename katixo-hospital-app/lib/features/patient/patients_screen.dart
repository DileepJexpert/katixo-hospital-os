import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

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

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _search());
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
        ],
      ),
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
