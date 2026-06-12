import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/api/notification_models.dart';
import '../../core/theme/design_tokens.dart';

/// Bell icon with unread badge for the AppShell actions row.
/// Opens a compact anchored panel — not a full screen.
class NotificationBell extends StatefulWidget {
  const NotificationBell({super.key});

  @override
  State<NotificationBell> createState() => _NotificationBellState();
}

class _NotificationBellState extends State<NotificationBell> {
  int _unread = 0;
  Timer? _pollTimer;

  @override
  void initState() {
    super.initState();
    _loadUnreadCount();
    _pollTimer = Timer.periodic(
        const Duration(seconds: 30), (_) => _loadUnreadCount());
  }

  @override
  void dispose() {
    _pollTimer?.cancel();
    super.dispose();
  }

  Future<void> _loadUnreadCount() async {
    try {
      final api = context.read<ApiClient>();
      final unread = await api.get<List<NotificationResponse>>(
        '/api/v1/notifications/unread',
        fromJson: (json) => (json as List? ?? [])
            .map((e) =>
                NotificationResponse.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
      if (mounted) setState(() => _unread = unread.length);
    } catch (_) {
      // Silent on poll errors.
    }
  }

  void _openPanel() {
    showDialog(
      context: context,
      barrierColor: Colors.transparent,
      builder: (context) => Align(
        alignment: Alignment.topRight,
        child: Padding(
          padding: const EdgeInsets.only(
              top: Metrics.appBarHeight + Space.xs, right: Space.md),
          child: Material(
            elevation: Elevations.dialog,
            borderRadius: Corners.mdRadius,
            clipBehavior: Clip.antiAlias,
            child: const SizedBox(
              width: 380,
              height: 440,
              child: NotificationPanel(),
            ),
          ),
        ),
      ),
    ).then((_) => _loadUnreadCount());
  }

  @override
  Widget build(BuildContext context) {
    return Stack(
      clipBehavior: Clip.none,
      children: [
        IconButton(
          tooltip: 'Notifications',
          icon: const Icon(Icons.notifications_outlined),
          onPressed: _openPanel,
        ),
        if (_unread > 0)
          Positioned(
            right: 6,
            top: 6,
            child: IgnorePointer(
              child: Container(
                padding: const EdgeInsets.symmetric(
                    horizontal: Space.xs, vertical: 1),
                decoration: BoxDecoration(
                  color: StatusColors.danger,
                  borderRadius: Corners.smRadius,
                ),
                constraints: const BoxConstraints(minWidth: 16),
                child: Text(
                  _unread > 99 ? '99+' : '$_unread',
                  textAlign: TextAlign.center,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 9,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ),
          ),
      ],
    );
  }
}

/// Compact notification list: dense rows, unread dot, mark-all-read.
class NotificationPanel extends StatefulWidget {
  const NotificationPanel({super.key});

  @override
  State<NotificationPanel> createState() => _NotificationPanelState();
}

class _NotificationPanelState extends State<NotificationPanel> {
  List<NotificationResponse> _notifications = [];
  bool _loading = true;
  String? _error;

  int get _unreadCount => _notifications.where((n) => !n.isRead).length;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final api = context.read<ApiClient>();
      final notifications = await api.get<List<NotificationResponse>>(
        '/api/v1/notifications/history?limit=30',
        fromJson: (json) => (json as List? ?? [])
            .map((e) =>
                NotificationResponse.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
      if (mounted) {
        setState(() {
          _notifications = notifications;
          _error = null;
          _loading = false;
        });
      }
    } on ApiException catch (e) {
      if (mounted) {
        setState(() {
          _error = e.error.message;
          _loading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = 'Failed to load notifications';
          _loading = false;
        });
      }
    }
  }

  Future<void> _markRead(NotificationResponse n) async {
    try {
      final api = context.read<ApiClient>();
      await api.post('/api/v1/notifications/${n.id}/read', const {},
          fromJson: (_) => null);
      await _load();
    } catch (_) {}
  }

  Future<void> _markAllRead() async {
    try {
      final api = context.read<ApiClient>();
      await api.post('/api/v1/notifications/read-all', const {},
          fromJson: (_) => null);
      await _load();
    } catch (_) {}
  }

  IconData _iconFor(String type) => switch (type) {
        'APPOINTMENT_REMINDER' ||
        'APPOINTMENT_CONFIRMED' ||
        'APPOINTMENT_CANCELLED' =>
          Icons.event_outlined,
        'DISCHARGE_SCHEDULED' ||
        'DISCHARGE_COMPLETED' =>
          Icons.exit_to_app_outlined,
        'BILL_GENERATED' ||
        'PAYMENT_RECEIVED' ||
        'REFUND_PROCESSED' =>
          Icons.receipt_long_outlined,
        'TEST_RESULTS_READY' => Icons.science_outlined,
        'PRESCRIPTION_READY' => Icons.medication_outlined,
        'SYSTEM_ALERT' => Icons.warning_amber_outlined,
        _ => Icons.info_outline,
      };

  String _relativeTime(DateTime t) {
    final d = DateTime.now().difference(t);
    if (d.inMinutes < 1) return 'now';
    if (d.inMinutes < 60) return '${d.inMinutes}m';
    if (d.inHours < 24) return '${d.inHours}h';
    if (d.inDays < 7) return '${d.inDays}d';
    return '${t.day}/${t.month}';
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(Space.md, Space.sm, Space.xs, 0),
          child: Row(
            children: [
              Text('Notifications', style: theme.textTheme.titleMedium),
              const Spacer(),
              if (_unreadCount > 0)
                TextButton(
                  onPressed: _markAllRead,
                  child: Text('Mark all read',
                      style: theme.textTheme.labelSmall),
                ),
              IconButton(
                tooltip: 'Refresh',
                iconSize: 18,
                icon: const Icon(Icons.refresh),
                onPressed: _load,
              ),
            ],
          ),
        ),
        const Divider(height: 1),
        Expanded(
          child: _loading
              ? const Center(child: CircularProgressIndicator())
              : _error != null
                  ? Center(
                      child: Text(_error!,
                          style: theme.textTheme.bodySmall?.copyWith(
                              color: theme.colorScheme.onSurfaceVariant)))
                  : _notifications.isEmpty
                      ? Center(
                          child: Text('No notifications',
                              style: theme.textTheme.bodySmall?.copyWith(
                                  color:
                                      theme.colorScheme.onSurfaceVariant)))
                      : ListView.separated(
                          itemCount: _notifications.length,
                          separatorBuilder: (_, __) =>
                              const Divider(height: 1),
                          itemBuilder: (context, i) {
                            final n = _notifications[i];
                            return InkWell(
                              onTap: n.isRead ? null : () => _markRead(n),
                              child: Padding(
                                padding: const EdgeInsets.symmetric(
                                    horizontal: Space.md,
                                    vertical: Space.sm),
                                child: Row(
                                  crossAxisAlignment:
                                      CrossAxisAlignment.start,
                                  children: [
                                    Icon(_iconFor(n.notificationType),
                                        size: 18,
                                        color: n.isRead
                                            ? theme.colorScheme
                                                .onSurfaceVariant
                                            : theme.colorScheme.primary),
                                    const SizedBox(width: Space.sm),
                                    Expanded(
                                      child: Column(
                                        crossAxisAlignment:
                                            CrossAxisAlignment.start,
                                        children: [
                                          Text(n.title,
                                              style: theme
                                                  .textTheme.bodySmall
                                                  ?.copyWith(
                                                      fontWeight: n.isRead
                                                          ? FontWeight.w400
                                                          : FontWeight
                                                              .w600)),
                                          Text(n.message,
                                              maxLines: 2,
                                              overflow:
                                                  TextOverflow.ellipsis,
                                              style: theme
                                                  .textTheme.labelSmall
                                                  ?.copyWith(
                                                      color: theme
                                                          .colorScheme
                                                          .onSurfaceVariant)),
                                        ],
                                      ),
                                    ),
                                    const SizedBox(width: Space.sm),
                                    Column(
                                      crossAxisAlignment:
                                          CrossAxisAlignment.end,
                                      children: [
                                        Text(_relativeTime(n.createdAt),
                                            style: theme
                                                .textTheme.labelSmall
                                                ?.copyWith(
                                                    color: theme
                                                        .colorScheme
                                                        .onSurfaceVariant)),
                                        if (!n.isRead)
                                          Padding(
                                            padding: const EdgeInsets.only(
                                                top: Space.xs),
                                            child: Container(
                                              width: 7,
                                              height: 7,
                                              decoration:
                                                  const BoxDecoration(
                                                color: StatusColors.info,
                                                shape: BoxShape.circle,
                                              ),
                                            ),
                                          ),
                                      ],
                                    ),
                                  ],
                                ),
                              ),
                            );
                          },
                        ),
        ),
      ],
    );
  }
}
