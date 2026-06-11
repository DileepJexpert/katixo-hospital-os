import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/api/lab_models.dart';
import '../../core/auth/auth_state.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/app_shell.dart';
import '../../core/widgets/status_chip.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// Lab Technician role home: sample collection and result entry worklist.
class LabTechHome extends StatefulWidget {
  const LabTechHome({super.key});

  @override
  State<LabTechHome> createState() => _LabTechHomeState();
}

class _LabTechHomeState extends State<LabTechHome> {
  List<LabOrderItem> _worklist = [];
  bool _loading = false;
  String? _error;
  Timer? _refreshTimer;

  @override
  void initState() {
    super.initState();
    _loadWorklist();
    _refreshTimer =
        Timer.periodic(const Duration(seconds: 10), (_) => _loadWorklist());
  }

  @override
  void dispose() {
    _refreshTimer?.cancel();
    super.dispose();
  }

  Future<void> _loadWorklist() async {
    try {
      final api = context.read<ApiClient>();
      final items = await api.get<List<LabOrderItem>>(
        '/api/v1/lab/worklist',
        fromJson: (json) => (json as List)
            .map((e) => LabOrderItem.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
      if (mounted) setState(() => _worklist = items);
    } catch (_) {
      // Silent on poll errors.
    }
  }

  Future<void> _collectSample(LabOrderItem item) async {
    final notesCtrl = TextEditingController();
    final proceed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Collect Sample'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('Test: ${item.testName}',
                style: Theme.of(context).textTheme.bodyMedium),
            const SizedBox(height: Space.md),
            Text('Specimen: ${item.specimenType}',
                style: Theme.of(context).textTheme.bodySmall),
            const SizedBox(height: Space.md),
            TextField(
              controller: notesCtrl,
              decoration: const InputDecoration(
                labelText: 'Collection Notes (optional)',
              ),
              maxLines: 2,
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Confirm Collection'),
          ),
        ],
      ),
    );

    if (proceed == true) {
      setState(() {
        _loading = true;
        _error = null;
      });

      try {
        final api = context.read<ApiClient>();
        await api.post<dynamic>(
          '/api/v1/lab/order-items/${item.itemId}/collect-sample',
          if (notesCtrl.text.trim().isNotEmpty)
            {'notes': notesCtrl.text.trim()}
          else
            {},
          fromJson: (json) => json,
        );
        await _loadWorklist();
      } on ApiException catch (e) {
        setState(() => _error = e.error.message);
      } finally {
        if (mounted) setState(() => _loading = false);
      }
    }
  }

  Future<void> _enterResult(LabOrderItem item) async {
    final resultCtrl = TextEditingController();
    final isAbnormal = ValueNotifier<bool>(false);

    final proceed = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: const Text('Enter Lab Result'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('Test: ${item.testName}',
                  style: Theme.of(context).textTheme.bodyMedium),
              const SizedBox(height: Space.md),
              TextField(
                controller: resultCtrl,
                decoration: const InputDecoration(
                  labelText: 'Result Value *',
                  hintText: 'e.g., 7.2 mg/dL',
                ),
              ),
              const SizedBox(height: Space.md),
              CheckboxListTile(
                value: isAbnormal.value,
                onChanged: (v) =>
                    setDialogState(() => isAbnormal.value = v ?? false),
                title: const Text('Abnormal Result'),
                dense: true,
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Cancel'),
            ),
            FilledButton(
              onPressed: resultCtrl.text.trim().isNotEmpty
                  ? () => Navigator.pop(context, true)
                  : null,
              child: const Text('Save Result'),
            ),
          ],
        ),
      ),
    );

    if (proceed == true) {
      setState(() {
        _loading = true;
        _error = null;
      });

      try {
        final api = context.read<ApiClient>();
        await api.post<dynamic>(
          '/api/v1/lab/order-items/${item.itemId}/result',
          {
            'resultValue': resultCtrl.text.trim(),
            'isAbnormal': isAbnormal.value,
          },
          fromJson: (json) => json,
        );
        await _loadWorklist();
      } on ApiException catch (e) {
        setState(() => _error = e.error.message);
      } finally {
        if (mounted) setState(() => _loading = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final authState = context.watch<AuthState>();
    final theme = Theme.of(context);

    return AppShell(
      title: 'Laboratory',
      destinations: const [
        ShellDestination(
          label: 'Worklist',
          icon: Icons.assignment_outlined,
          selectedIcon: Icons.assignment,
        ),
      ],
      selectedIndex: 0,
      onDestinationSelected: (_) {},
      actions: [
        if (authState.currentUser != null)
          Center(
            child: Padding(
              padding: const EdgeInsets.only(right: Space.sm),
              child: Text(authState.currentUser!.name,
                  style: theme.textTheme.labelLarge),
            ),
          ),
        IconButton(
          tooltip: 'Sign out',
          icon: const Icon(Icons.logout_outlined),
          onPressed: () => authState.logout(),
        ),
      ],
      body: PageContainer(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Text('Lab Worklist', style: theme.textTheme.titleLarge),
                const Spacer(),
                StatusChip(
                  '${_worklist.where((i) => i.itemStatus == 'SAMPLE_PENDING').length} pending',
                  kind: StatusKind.warning,
                ),
                const SizedBox(width: Space.sm),
                StatusChip(
                  '${_worklist.where((i) => i.itemStatus == 'IN_PROGRESS').length} in progress',
                  kind: StatusKind.info,
                ),
                const SizedBox(width: Space.sm),
                IconButton(
                  tooltip: 'Refresh',
                  onPressed: _loading ? null : _loadWorklist,
                  icon: const Icon(Icons.refresh),
                ),
              ],
            ),
            const SizedBox(height: Space.md),

            if (_error != null) ...[
              MessageBanner.error(_error!),
              const SizedBox(height: Space.md),
            ],

            if (_worklist.isEmpty)
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(Space.xl),
                  child: Center(
                    child: Text('No pending tests',
                        style: theme.textTheme.bodyMedium?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant)),
                  ),
                ),
              )
            else
              Expanded(
                child: Card(
                  child: ListView.separated(
                    itemCount: _worklist.length,
                    separatorBuilder: (_, __) => const Divider(),
                    itemBuilder: (context, i) {
                      final item = _worklist[i];
                      final pendingSample = item.itemStatus == 'SAMPLE_PENDING';
                      final resultPending = item.itemStatus == 'IN_PROGRESS';

                      return ListTile(
                        leading: Icon(
                          Icons.science_outlined,
                          color: Theme.of(context).colorScheme.primary,
                        ),
                        title: Text(item.testName),
                        subtitle: Text(
                          'Specimen: ${item.specimenType}',
                        ),
                        trailing: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            StatusChip.auto(item.itemStatus),
                            const SizedBox(width: Space.sm),
                            if (pendingSample) ...[
                              OutlinedButton(
                                onPressed: _loading ? null : () => _collectSample(item),
                                child: const Text('Collect', style: TextStyle(fontSize: 12)),
                              ),
                            ] else if (resultPending) ...[
                              FilledButton(
                                onPressed: _loading ? null : () => _enterResult(item),
                                child: const Text('Result', style: TextStyle(fontSize: 12)),
                              ),
                            ] else ...[
                              Text(
                                item.itemStatus,
                                style: theme.textTheme.labelSmall,
                              ),
                            ],
                          ],
                        ),
                      );
                    },
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
