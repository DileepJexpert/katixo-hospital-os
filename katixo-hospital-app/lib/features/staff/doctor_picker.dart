import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/theme/design_tokens.dart';

/// Opens a doctor picker (staff with role DOCTOR). Returns the selected doctor
/// map (id, name, specialisation) or null. Reuse wherever a doctor is chosen.
Future<Map<String, dynamic>?> showDoctorPicker(BuildContext context) {
  return showDialog<Map<String, dynamic>>(
    context: context,
    builder: (_) => const _DoctorPickerDialog(),
  );
}

class _DoctorPickerDialog extends StatefulWidget {
  const _DoctorPickerDialog();

  @override
  State<_DoctorPickerDialog> createState() => _DoctorPickerDialogState();
}

class _DoctorPickerDialogState extends State<_DoctorPickerDialog> {
  final _q = TextEditingController();
  List<Map<String, dynamic>> _all = const [];
  bool _loading = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _load());
  }

  @override
  void dispose() {
    _q.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      final list = await api.get<List<Map<String, dynamic>>>(
        '/api/v1/staff?role=DOCTOR',
        fromJson: (json) =>
            List<Map<String, dynamic>>.from(json as List? ?? const []),
      );
      if (mounted) setState(() => _all = list);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  List<Map<String, dynamic>> get _filtered {
    final q = _q.text.trim().toLowerCase();
    if (q.isEmpty) return _all;
    return _all
        .where((d) =>
            '${d['name'] ?? ''}'.toLowerCase().contains(q) ||
            '${d['specialisation'] ?? ''}'.toLowerCase().contains(q))
        .toList();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final list = _filtered;
    return AlertDialog(
      title: const Text('Select doctor'),
      content: SizedBox(
        width: 460,
        height: 420,
        child: Column(
          children: [
            TextField(
              controller: _q,
              autofocus: true,
              decoration: const InputDecoration(
                labelText: 'Search doctor / specialisation',
                prefixIcon: Icon(Icons.search, size: 18),
              ),
              onChanged: (_) => setState(() {}),
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
              child: list.isEmpty
                  ? Center(
                      child: Text(_loading ? 'Loading…' : 'No doctors found',
                          style: theme.textTheme.bodyMedium?.copyWith(
                              color: theme.colorScheme.onSurfaceVariant)))
                  : ListView.separated(
                      itemCount: list.length,
                      separatorBuilder: (_, __) => const Divider(height: 1),
                      itemBuilder: (context, i) {
                        final d = list[i];
                        return ListTile(
                          dense: true,
                          leading: const CircleAvatar(
                              child: Icon(Icons.medical_services_outlined,
                                  size: 18)),
                          title: Text('${d['name'] ?? ''}'),
                          subtitle: Text('${d['specialisation'] ?? 'General'}',
                              style: theme.textTheme.bodySmall),
                          onTap: () => Navigator.pop(context, d),
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
