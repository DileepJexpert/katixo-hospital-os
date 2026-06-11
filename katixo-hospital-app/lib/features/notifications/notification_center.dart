import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/api/notification_models.dart';
import '../../core/theme/design_tokens.dart';

class NotificationCenter extends StatefulWidget {
  const NotificationCenter({super.key});

  @override
  State<NotificationCenter> createState() => _NotificationCenterState();
}

class _NotificationCenterState extends State<NotificationCenter> {
  List<NotificationResponse> _notifications = [];
  bool _loading = false;
  String? _error;
  int _unreadCount = 0;

  @override
  void initState() {
    super.initState();
    _loadNotifications();
  }

  Future<void> _loadNotifications() async {
    setState(() => _loading = true);
    try {
      final api = context.read<ApiClient>();
      final notifications = await api.get<List<NotificationResponse>>(
        '/api/v1/notifications/history',
        fromJson: (json) {
          if (json is List) {
            return json
                .map((n) =>
                    NotificationResponse.fromJson(n as Map<String, dynamic>))
                .toList();
          }
          return [];
        },
      );
      setState(() {
        _notifications = notifications;
        _unreadCount =
            notifications.where((n) => !n.isRead).length;
        _error = null;
      });
    } catch (e) {
      setState(() => _error = 'Failed to load notifications: $e');
    } finally {
      setState(() => _loading = false);
    }
  }

  Future<void> _markAsRead(int notificationId) async {
    try {
      final api = context.read<ApiClient>();
      await api.post(
        '/api/v1/notifications/$notificationId/read',
        {},
        fromJson: (_) => null,
      );
      _loadNotifications();
    } catch (e) {
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('Error: $e')));
    }
  }

  Future<void> _markAllAsRead() async {
    try {
      final api = context.read<ApiClient>();
      await api.post(
        '/api/v1/notifications/read-all',
        {},
        fromJson: (_) => null,
      );
      _loadNotifications();
    } catch (e) {
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('Error: $e')));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Notifications'),
        actions: [
          if (_unreadCount > 0)
            TextButton(
              onPressed: _markAllAsRead,
              child: const Text('Mark All Read'),
            ),
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _loadNotifications,
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Text(_error!),
                      const SizedBox(height: Space.md),
                      ElevatedButton(
                        onPressed: _loadNotifications,
                        child: const Text('Retry'),
                      ),
                    ],
                  ),
                )
              : _notifications.isEmpty
                  ? const Center(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(Icons.notifications_none_outlined,
                              size: 48, color: Colors.grey),
                          SizedBox(height: Space.md),
                          Text('No notifications yet'),
                        ],
                      ),
                    )
                  : ListView.builder(
                      itemCount: _notifications.length,
                      itemBuilder: (context, index) {
                        final notification = _notifications[index];
                        return _NotificationTile(
                          notification: notification,
                          onTap: notification.isRead
                              ? null
                              : () => _markAsRead(notification.id),
                          onMarkRead: () => _markAsRead(notification.id),
                        );
                      },
                    ),
    );
  }
}

class _NotificationTile extends StatelessWidget {
  final NotificationResponse notification;
  final VoidCallback? onTap;
  final VoidCallback? onMarkRead;

  const _NotificationTile({
    required this.notification,
    this.onTap,
    this.onMarkRead,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      color: notification.isRead ? Colors.transparent : Colors.blue.shade50,
      child: ListTile(
        leading: Container(
          width: 40,
          height: 40,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: notification.isRead ? Colors.grey.shade200 : Colors.blue,
          ),
          child: Center(
            child: Text(notification.icon, style: const TextStyle(fontSize: 20)),
          ),
        ),
        title: Text(
          notification.title,
          style: TextStyle(
            fontWeight:
                notification.isRead ? FontWeight.normal : FontWeight.bold,
          ),
        ),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const SizedBox(height: Space.xs),
            Text(
              notification.message,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.bodySmall,
            ),
            const SizedBox(height: Space.xs),
            Text(
              _formatTime(notification.createdAt),
              style: Theme.of(context).textTheme.labelSmall,
            ),
          ],
        ),
        trailing: !notification.isRead
            ? GestureDetector(
                onTap: onMarkRead,
                child: Container(
                  width: 10,
                  height: 10,
                  decoration: const BoxDecoration(
                    shape: BoxShape.circle,
                    color: Colors.blue,
                  ),
                ),
              )
            : null,
        isThreeLine: true,
        onTap: onTap,
      ),
    );
  }

  String _formatTime(DateTime dateTime) {
    final now = DateTime.now();
    final difference = now.difference(dateTime);

    if (difference.inMinutes < 1) {
      return 'just now';
    } else if (difference.inMinutes < 60) {
      return '${difference.inMinutes}m ago';
    } else if (difference.inHours < 24) {
      return '${difference.inHours}h ago';
    } else if (difference.inDays < 7) {
      return '${difference.inDays}d ago';
    } else {
      return dateTime.toString().split(' ')[0];
    }
  }
}

class NotificationBadge extends StatelessWidget {
  final int unreadCount;
  final VoidCallback onTap;

  const NotificationBadge({
    required this.unreadCount,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        IconButton(
          icon: const Icon(Icons.notifications_outlined),
          onPressed: onTap,
        ),
        if (unreadCount > 0)
          Positioned(
            right: 0,
            top: 0,
            child: Container(
              padding: const EdgeInsets.all(2),
              decoration: const BoxDecoration(
                color: Colors.red,
                shape: BoxShape.circle,
              ),
              constraints: const BoxConstraints(minWidth: 18, minHeight: 18),
              child: Center(
                child: Text(
                  unreadCount > 99 ? '99+' : unreadCount.toString(),
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 10,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            ),
          ),
      ],
    );
  }
}
