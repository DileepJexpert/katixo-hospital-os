import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/auth/auth_state.dart';
import '../../core/realtime/board_socket.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/status_chip.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// FIFO dispense queue with audited priority override. Body widget.
class PharmacyQueueScreen extends StatefulWidget {
  const PharmacyQueueScreen({super.key});

  @override
  State<PharmacyQueueScreen> createState() => _PharmacyQueueScreenState();
}

class _PharmacyQueueScreenState extends State<PharmacyQueueScreen> {
  List<Map<String, dynamic>> _items = [];
  bool _loading = false;
  String? _error;
  Timer? _refreshTimer;
  BoardSocket? _socket;

  @override
  void initState() {
    super.initState();
    _loadQueue();
    // Real-time nudge on pharmacy changes; the timer is a safety net.
    _socket = BoardSocket(
      baseUrl: context.read<ApiClient>().baseUrl,
      token: context.read<AuthState>().token ?? '',
      onTopic: (t) {
        if (t == 'pharmacy' && mounted) _loadQueue();
      },
    )..connect();
    _refreshTimer = Timer.periodic(const Duration(seconds: 30), (_) => _loadQueue());
  }

  @override
  void dispose() {
    _refreshTimer?.cancel();
    _socket?.dispose();
    super.dispose();
  }

  Future<void> _loadQueue() async {
    try {
      final api = context.read<ApiClient>();
      final page = await api.get<Map<String, dynamic>>(
        '/api/v1/pharmacy/queue?page=0&size=50',
        fromJson: (json) => json as Map<String, dynamic>,
      );
      if (mounted) {
        setState(() {
          _items = List<Map<String, dynamic>>.from(page['content'] as List? ?? []);
          _error = null;
        });
      }
    } catch (_) {
      // Stay quiet on transient poll failures once the queue is on screen;
      // surface only when the first load failed (nothing to show).
      if (mounted && _items.isEmpty) {
        setState(() => _error = 'Could not load the dispense queue — check your connection.');
      }
    }
  }

  Future<void> _action(String path, {Object body = const <String, dynamic>{}}) async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      await api.post<dynamic>(path, body, fromJson: (json) => json);
      await _loadQueue();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Action failed: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _priorityDialog(Map<String, dynamic> item) async {
    final priorityCtrl = TextEditingController(text: '0');
    final reasonCtrl = TextEditingController();
    final apply = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Priority Override — ${item['medicineName']}'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('Lower number = dispensed sooner. Override is recorded in the audit log.'),
            const SizedBox(height: Space.md),
            TextField(
              controller: priorityCtrl,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(labelText: 'New Priority *'),
            ),
            const SizedBox(height: Space.md),
            TextField(controller: reasonCtrl, decoration: const InputDecoration(labelText: 'Reason *')),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Apply')),
        ],
      ),
    );
    if (apply == true && reasonCtrl.text.trim().isNotEmpty) {
      await _action(
        '/api/v1/pharmacy/queue-items/${item['itemId']}/priority-override',
        body: {'newPriority': int.tryParse(priorityCtrl.text) ?? 0, 'reason': reasonCtrl.text.trim()},
      );
    }
  }

  Future<void> _rejectDialog(Map<String, dynamic> item) async {
    final reasonCtrl = TextEditingController();
    final reject = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Reject — ${item['medicineName']}'),
        content: TextField(controller: reasonCtrl, decoration: const InputDecoration(labelText: 'Reason *')),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: StatusColors.danger),
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Reject'),
          ),
        ],
      ),
    );
    if (reject == true && reasonCtrl.text.trim().isNotEmpty) {
      await _action('/api/v1/pharmacy/queue-items/${item['itemId']}/reject',
          body: {'reason': reasonCtrl.text.trim()});
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return PageContainer(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text('Dispense Queue', style: theme.textTheme.titleLarge),
              const SizedBox(width: Space.sm),
              StatusChip('${_items.length} pending', kind: StatusKind.info),
              const Spacer(),
              IconButton(
                tooltip: 'Refresh',
                onPressed: _loading ? null : _loadQueue,
                icon: const Icon(Icons.refresh),
              ),
            ],
          ),
          const SizedBox(height: Space.md),
          if (_error != null) ...[
            MessageBanner.error(_error!),
            const SizedBox(height: Space.md),
          ],
          if (_items.isEmpty)
            Card(
              child: Padding(
                padding: const EdgeInsets.all(Space.xl),
                child: Center(
                  child: Text('Queue is empty',
                      style: theme.textTheme.bodyMedium
                          ?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
                ),
              ),
            )
          else
            Card(
              child: Column(
                children: [
                  for (var i = 0; i < _items.length; i++) ...[
                    _QueueItemTile(
                      item: _items[i],
                      loading: _loading,
                      onStart: () => _action('/api/v1/pharmacy/queue-items/${_items[i]['itemId']}/start'),
                      onComplete: () => _action('/api/v1/pharmacy/queue-items/${_items[i]['itemId']}/complete'),
                      onPriority: () => _priorityDialog(_items[i]),
                      onReject: () => _rejectDialog(_items[i]),
                    ),
                    if (i < _items.length - 1) const Divider(),
                  ],
                ],
              ),
            ),
        ],
      ),
    );
  }
}

class _QueueItemTile extends StatelessWidget {
  const _QueueItemTile({
    required this.item,
    required this.loading,
    required this.onStart,
    required this.onComplete,
    required this.onPriority,
    required this.onReject,
  });

  final Map<String, dynamic> item;
  final bool loading;
  final VoidCallback onStart;
  final VoidCallback onComplete;
  final VoidCallback onPriority;
  final VoidCallback onReject;

  @override
  Widget build(BuildContext context) {
    final status = item['queueStatus'] as String? ?? 'PENDING';
    final overridden = item['isPriorityOverridden'] == true;

    return ListTile(
      leading: const Icon(Icons.medication_outlined),
      title: Text('${item['medicineName']} ×${item['quantity']}${overridden ? '  ⚡' : ''}'),
      subtitle: Text('Rx #${item['prescriptionId']} · patient #${item['patientId']} · '
          '${item['dosage'] ?? '-'} ${item['frequency'] ?? ''}'),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          StatusChip.auto(status),
          const SizedBox(width: Space.sm),
          if (status == 'PENDING') ...[
            IconButton(
              tooltip: 'Priority override',
              icon: const Icon(Icons.low_priority, size: 20),
              onPressed: loading ? null : onPriority,
            ),
            OutlinedButton(onPressed: loading ? null : onStart, child: const Text('Start')),
          ],
          if (status == 'IN_PROGRESS')
            FilledButton(onPressed: loading ? null : onComplete, child: const Text('Dispense')),
          if (status != 'DISPENSED') ...[
            const SizedBox(width: Space.xs),
            IconButton(
              tooltip: 'Reject',
              icon: const Icon(Icons.block, size: 20),
              onPressed: loading ? null : onReject,
            ),
          ],
        ],
      ),
    );
  }
}
