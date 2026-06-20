import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/status_chip.dart';

/// Opens a searchable OPD visit picker (search by patient name / mobile / UHID
/// / visit number). Returns the selected visit map (visitId, visitNumber,
/// patientId, patientName, …) or null if cancelled. Reuse anywhere a visit must
/// be chosen (e.g. lab order creation).
Future<Map<String, dynamic>?> showVisitPicker(BuildContext context) {
  return showDialog<Map<String, dynamic>>(
    context: context,
    builder: (_) => const _VisitPickerDialog(),
  );
}

class _VisitPickerDialog extends StatefulWidget {
  const _VisitPickerDialog();

  @override
  State<_VisitPickerDialog> createState() => _VisitPickerDialogState();
}

class _VisitPickerDialogState extends State<_VisitPickerDialog> {
  final _q = TextEditingController();
  Timer? _debounce;
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
    _debounce?.cancel();
    _q.dispose();
    super.dispose();
  }

  void _onChanged(String _) {
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 300), _search);
  }

  Future<void> _search() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      final q = _q.text.trim();
      final list = await api.get<List<Map<String, dynamic>>>(
        '/api/v1/opd/visit-search?limit=50'
        '${q.isEmpty ? '' : '&q=${Uri.encodeQueryComponent(q)}'}',
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
      title: const Text('Select visit'),
      content: SizedBox(
        width: 480,
        height: 440,
        child: Column(
          children: [
            TextField(
              controller: _q,
              autofocus: true,
              decoration: const InputDecoration(
                labelText: 'Search patient / visit no',
                prefixIcon: Icon(Icons.search, size: 18),
              ),
              onChanged: _onChanged,
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
                      child: Text(_loading ? 'Searching…' : 'No visits found',
                          style: theme.textTheme.bodyMedium?.copyWith(
                              color: theme.colorScheme.onSurfaceVariant)))
                  : ListView.separated(
                      itemCount: _results.length,
                      separatorBuilder: (_, __) => const Divider(height: 1),
                      itemBuilder: (context, i) {
                        final v = _results[i];
                        final sub = [
                          if (v['uhid'] != null) '${v['uhid']}',
                          if (v['mobile'] != null) '${v['mobile']}',
                          if (v['age'] != null) '${v['age']}y',
                        ].join(' · ');
                        return ListTile(
                          dense: true,
                          title: Text(
                              '${v['patientName'] ?? 'Patient #${v['patientId']}'}'),
                          subtitle: Text(
                            '${v['visitNumber'] ?? v['visitId']}'
                            '${sub.isEmpty ? '' : ' · $sub'}',
                            style: theme.textTheme.bodySmall,
                          ),
                          trailing: StatusChip.auto('${v['visitStatus']}'),
                          onTap: () => Navigator.pop(context, v),
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
