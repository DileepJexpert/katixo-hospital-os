import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/status_chip.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// Lab report viewer: look up a lab order by ID and show the patient header
/// plus each test's result, unit, reference range and status. Abnormal results
/// are flagged red. Body widget — host supplies the AppShell.
class LabReportScreen extends StatefulWidget {
  const LabReportScreen({super.key});

  @override
  State<LabReportScreen> createState() => _LabReportScreenState();
}

class _LabReportScreenState extends State<LabReportScreen> {
  final _orderIdCtrl = TextEditingController();

  Map<String, dynamic>? _report;
  bool _loading = false;
  String? _error;

  @override
  void dispose() {
    _orderIdCtrl.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    final id = int.tryParse(_orderIdCtrl.text.trim());
    if (id == null) {
      setState(() => _error = 'Enter a valid lab order ID');
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
      _report = null;
    });
    try {
      final api = context.read<ApiClient>();
      final report = await api.get<Map<String, dynamic>>(
        '/api/v1/lab/orders/$id/report',
        fromJson: (json) => json as Map<String, dynamic>,
      );
      if (mounted) setState(() => _report = report);
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
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Lab Report', style: theme.textTheme.titleLarge),
          const SizedBox(height: Space.md),
          if (_error != null) ...[
            MessageBanner.error(_error!),
            const SizedBox(height: Space.md),
          ],
          Card(
            child: Padding(
              padding: const EdgeInsets.all(Space.lg),
              child: Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _orderIdCtrl,
                      keyboardType: TextInputType.number,
                      decoration:
                          const InputDecoration(labelText: 'Lab order ID *'),
                      onSubmitted: (_) => _load(),
                    ),
                  ),
                  const SizedBox(width: Space.md),
                  FilledButton.icon(
                    onPressed: _loading ? null : _load,
                    icon: const Icon(Icons.search, size: 18),
                    label: const Text('Load'),
                  ),
                ],
              ),
            ),
          ),
          if (_report != null) ...[
            const SizedBox(height: Space.lg),
            Expanded(child: _reportView(theme, _report!)),
          ],
        ],
      ),
    );
  }

  Widget _reportView(ThemeData theme, Map<String, dynamic> report) {
    final results =
        List<Map<String, dynamic>>.from(report['results'] as List? ?? const []);
    return SingleChildScrollView(
      child: Card(
        child: Padding(
          padding: const EdgeInsets.all(Space.lg),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Text('${report['orderNumber']}',
                      style: theme.textTheme.titleMedium),
                  const SizedBox(width: Space.sm),
                  StatusChip.auto('${report['orderStatus']}'),
                ],
              ),
              const SizedBox(height: Space.xs),
              Text(
                '${report['patientName']} · UHID ${report['uhid']} · '
                '${'${report['orderDate']}'.split('T').first}',
                style: theme.textTheme.bodySmall
                    ?.copyWith(color: theme.colorScheme.onSurfaceVariant),
              ),
              const SizedBox(height: Space.md),
              const Divider(),
              Row(
                children: [
                  Expanded(flex: 3, child: _h(theme, 'Test')),
                  Expanded(flex: 2, child: _h(theme, 'Result')),
                  Expanded(flex: 1, child: _h(theme, 'Unit')),
                  Expanded(flex: 2, child: _h(theme, 'Reference')),
                  Expanded(flex: 2, child: _h(theme, 'Status')),
                ],
              ),
              const Divider(),
              for (final r in results) _resultRow(theme, r),
            ],
          ),
        ),
      ),
    );
  }

  Widget _resultRow(ThemeData theme, Map<String, dynamic> r) {
    final abnormal = r['isAbnormal'] == true;
    final resultStyle = theme.textTheme.bodyMedium?.copyWith(
      color: abnormal ? theme.colorScheme.error : null,
      fontWeight: abnormal ? FontWeight.bold : null,
    );
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: Space.sm),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
              flex: 3,
              child: Text('${r['testName']}',
                  style: theme.textTheme.bodyMedium)),
          Expanded(
              flex: 2,
              child: Row(
                children: [
                  Flexible(child: Text('${r['result']}', style: resultStyle)),
                  if (abnormal) ...[
                    const SizedBox(width: Space.xxs),
                    Icon(Icons.warning_amber,
                        size: 14, color: theme.colorScheme.error),
                  ],
                ],
              )),
          Expanded(flex: 1, child: Text('${r['unit']}')),
          Expanded(flex: 2, child: Text('${r['referenceRange']}')),
          Expanded(flex: 2, child: StatusChip.auto('${r['status']}')),
        ],
      ),
    );
  }

  Widget _h(ThemeData theme, String label) {
    return Text(label,
        style: theme.textTheme.labelSmall?.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
            fontWeight: FontWeight.bold));
  }
}
