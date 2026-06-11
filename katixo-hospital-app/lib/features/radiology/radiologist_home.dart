import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/api/radiology_models.dart';
import '../../core/auth/auth_state.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/app_shell.dart';
import '../../core/widgets/status_chip.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// Radiologist role home: imaging report worklist.
class RadiologistHome extends StatefulWidget {
  const RadiologistHome({super.key});

  @override
  State<RadiologistHome> createState() => _RadiologistHomeState();
}

class _RadiologistHomeState extends State<RadiologistHome> {
  List<RadiologyOrderItem> _worklist = [];
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
      final items = await api.get<List<RadiologyOrderItem>>(
        '/api/v1/radiology/worklist',
        fromJson: (json) => (json as List)
            .map((e) => RadiologyOrderItem.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
      if (mounted) setState(() => _worklist = items);
    } catch (_) {
      // Silent on poll errors.
    }
  }

  Future<void> _enterReport(RadiologyOrderItem item) async {
    final reportCtrl = TextEditingController();

    final proceed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Enter Radiology Report'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('Imaging: ${item.testName}',
                style: Theme.of(context).textTheme.bodyMedium),
            const SizedBox(height: Space.md),
            TextField(
              controller: reportCtrl,
              decoration: const InputDecoration(
                labelText: 'Findings & Conclusion *',
                hintText: 'Describe imaging findings and diagnostic impression',
              ),
              maxLines: 5,
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: reportCtrl.text.trim().isNotEmpty
                ? () => Navigator.pop(context, true)
                : null,
            child: const Text('Save Report'),
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
          '/api/v1/radiology/order-items/${item.itemId}/report',
          {
            'reportText': reportCtrl.text.trim(),
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
      title: 'Radiology',
      destinations: const [
        ShellDestination(
          label: 'Worklist',
          icon: Icons.image_outlined,
          selectedIcon: Icons.image,
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
                Text('Radiology Worklist', style: theme.textTheme.titleLarge),
                const Spacer(),
                StatusChip(
                  '${_worklist.where((i) => i.itemStatus == 'IMAGING_DONE').length} pending',
                  kind: StatusKind.warning,
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
                    child: Text('No pending reports',
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
                      final pendingReport =
                          item.itemStatus == 'IMAGING_DONE';

                      return ListTile(
                        leading: Icon(
                          Icons.image_outlined,
                          color: Theme.of(context).colorScheme.primary,
                        ),
                        title: Text(item.testName),
                        subtitle: Text(item.itemStatus),
                        trailing: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            StatusChip.auto(item.itemStatus),
                            const SizedBox(width: Space.sm),
                            if (pendingReport)
                              FilledButton(
                                onPressed: _loading
                                    ? null
                                    : () => _enterReport(item),
                                child: const Text('Report',
                                    style: TextStyle(fontSize: 12)),
                              )
                            else
                              Text(
                                item.reportStatus ?? 'COMPLETED',
                                style: theme.textTheme.labelSmall,
                              ),
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
