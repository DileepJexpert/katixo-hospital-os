import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/theme/design_tokens.dart';

/// Opens a searchable patient picker. Returns the selected patient map
/// (id, uhid, fullName, mobile, age, gender) or null if cancelled.
/// Reuse anywhere a patient must be chosen (IPD admit, TPA case, billing, lab).
Future<Map<String, dynamic>?> showPatientPicker(BuildContext context) {
  return showDialog<Map<String, dynamic>>(
    context: context,
    builder: (_) => const _PatientPickerDialog(),
  );
}

class _PatientPickerDialog extends StatefulWidget {
  const _PatientPickerDialog();

  @override
  State<_PatientPickerDialog> createState() => _PatientPickerDialogState();
}

class _PatientPickerDialogState extends State<_PatientPickerDialog> {
  final _q = TextEditingController();
  List<Map<String, dynamic>> _results = const [];
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
    return AlertDialog(
      title: const Text('Select patient'),
      content: SizedBox(
        width: 480,
        height: 440,
        child: Column(
          children: [
            TextField(
              controller: _q,
              autofocus: true,
              decoration: InputDecoration(
                labelText: 'Search name / mobile / UHID',
                prefixIcon: const Icon(Icons.search, size: 18),
                suffixIcon: IconButton(
                  icon: const Icon(Icons.arrow_forward, size: 18),
                  onPressed: _loading ? null : _search,
                ),
              ),
              onSubmitted: (_) => _search(),
            ),
            const SizedBox(height: Space.sm),
            if (_error != null)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: Space.sm),
                child: Text(_error!,
                    style: theme.textTheme.bodySmall
                        ?.copyWith(color: theme.colorScheme.error)),
              ),
            Expanded(
              child: _results.isEmpty
                  ? Center(
                      child: Text(_loading ? 'Searching…' : 'No patients found',
                          style: theme.textTheme.bodyMedium?.copyWith(
                              color: theme.colorScheme.onSurfaceVariant)))
                  : ListView.separated(
                      itemCount: _results.length,
                      separatorBuilder: (_, __) => const Divider(height: 1),
                      itemBuilder: (context, i) {
                        final p = _results[i];
                        return ListTile(
                          dense: true,
                          title: Text('${p['fullName'] ?? ''}'),
                          subtitle: Text(
                            'UHID ${p['uhid'] ?? '—'} · ${p['mobile'] ?? '—'}'
                            '${p['age'] != null ? ' · ${p['age']}y' : ''}'
                            '${p['gender'] != null ? ' · ${p['gender']}' : ''}',
                            style: theme.textTheme.bodySmall,
                          ),
                          onTap: () => Navigator.pop(context, p),
                        );
                      },
                    ),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Cancel'),
        ),
      ],
    );
  }
}
