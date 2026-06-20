import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/status_chip.dart';

/// Opens a searchable IPD admission picker (current inpatients by default).
/// Returns the selected admission map (id, admissionNumber, patientId,
/// patientName, currentBedId, admissionStatus) or null if cancelled.
/// Reuse anywhere an admission must be chosen (discharge summary, lab order).
Future<Map<String, dynamic>?> showAdmissionPicker(BuildContext context,
    {String status = 'ADMITTED'}) {
  return showDialog<Map<String, dynamic>>(
    context: context,
    builder: (_) => _AdmissionPickerDialog(status: status),
  );
}

class _AdmissionPickerDialog extends StatefulWidget {
  const _AdmissionPickerDialog({required this.status});

  final String status;

  @override
  State<_AdmissionPickerDialog> createState() => _AdmissionPickerDialogState();
}

class _AdmissionPickerDialogState extends State<_AdmissionPickerDialog> {
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
        '/api/v1/ipd/admissions?status=${widget.status}',
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
    return _all.where((a) {
      final hay = '${a['admissionNumber'] ?? ''} ${a['patientName'] ?? ''} '
              '${a['patientId'] ?? ''} ${a['currentBedId'] ?? ''}'
          .toLowerCase();
      return hay.contains(q);
    }).toList();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final items = _filtered;
    return AlertDialog(
      title: const Text('Select admission'),
      content: SizedBox(
        width: 480,
        height: 440,
        child: Column(
          children: [
            TextField(
              controller: _q,
              autofocus: true,
              decoration: const InputDecoration(
                labelText: 'Search admission no / patient / bed',
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
              child: items.isEmpty
                  ? Center(
                      child: Text(_loading ? 'Loading…' : 'No admissions',
                          style: theme.textTheme.bodyMedium?.copyWith(
                              color: theme.colorScheme.onSurfaceVariant)))
                  : ListView.separated(
                      itemCount: items.length,
                      separatorBuilder: (_, __) => const Divider(height: 1),
                      itemBuilder: (context, i) {
                        final a = items[i];
                        return ListTile(
                          dense: true,
                          title: Text(
                              '${a['patientName']?.toString().isNotEmpty == true ? a['patientName'] : 'Patient #${a['patientId']}'}'),
                          subtitle: Text(
                            '${a['admissionNumber'] ?? ''} · bed #${a['currentBedId'] ?? '-'}',
                            style: theme.textTheme.bodySmall,
                          ),
                          trailing: StatusChip.auto('${a['admissionStatus']}'),
                          onTap: () => Navigator.pop(context, a),
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
