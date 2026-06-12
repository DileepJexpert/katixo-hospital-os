import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/api/ot_models.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// Admin screen for OT room management (create, edit, delete).
class OTRoomManagementScreen extends StatefulWidget {
  const OTRoomManagementScreen({super.key});

  @override
  State<OTRoomManagementScreen> createState() => _OTRoomManagementScreenState();
}

class _OTRoomManagementScreenState extends State<OTRoomManagementScreen> {
  List<OTRoom> _rooms = [];
  bool _loading = false;
  String? _error;
  String? _success;
  Timer? _refreshTimer;

  final _roomNumberCtrl = TextEditingController();
  final _roomNameCtrl = TextEditingController();
  final _roomTypeCtrl = TextEditingController();
  final _capacityCtrl = TextEditingController();

  @override
  void initState() {
    super.initState();
    _loadRooms();
    _refreshTimer =
        Timer.periodic(const Duration(seconds: 10), (_) => _loadRooms());
  }

  @override
  void dispose() {
    _refreshTimer?.cancel();
    _roomNumberCtrl.dispose();
    _roomNameCtrl.dispose();
    _roomTypeCtrl.dispose();
    _capacityCtrl.dispose();
    super.dispose();
  }

  Future<void> _loadRooms() async {
    try {
      final api = context.read<ApiClient>();
      final rooms = await api.get<List<OTRoom>>(
        '/api/v1/ot/rooms',
        fromJson: (json) => (json as List)
            .map((e) => OTRoom.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
      if (mounted) setState(() => _rooms = rooms);
    } catch (_) {
      // Silent on poll errors.
    }
  }

  void _showCreateDialog() {
    _roomNumberCtrl.clear();
    _roomNameCtrl.clear();
    _roomTypeCtrl.clear();
    _capacityCtrl.clear();

    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Create OT Room'),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: _roomNumberCtrl,
                decoration: const InputDecoration(labelText: 'Room Number *'),
              ),
              const SizedBox(height: Space.md),
              TextField(
                controller: _roomNameCtrl,
                decoration: const InputDecoration(labelText: 'Room Name *'),
              ),
              const SizedBox(height: Space.md),
              TextField(
                controller: _roomTypeCtrl,
                decoration:
                    const InputDecoration(labelText: 'Type (General, Cardiac, Neuro...)'),
              ),
              const SizedBox(height: Space.md),
              TextField(
                controller: _capacityCtrl,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(labelText: 'Capacity'),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => _createRoom(context),
            child: const Text('Create'),
          ),
        ],
      ),
    );
  }

  Future<void> _createRoom(BuildContext context) async {
    if (_roomNumberCtrl.text.isEmpty || _roomNameCtrl.text.isEmpty) {
      setState(() => _error = 'Room number and name are required');
      return;
    }

    setState(() {
      _loading = true;
      _error = null;
      _success = null;
    });

    try {
      final api = context.read<ApiClient>();
      final request = {
        'roomNumber': _roomNumberCtrl.text.trim(),
        'roomName': _roomNameCtrl.text.trim(),
        'roomType': _roomTypeCtrl.text.trim().isEmpty ? null : _roomTypeCtrl.text.trim(),
        'capacity': _capacityCtrl.text.isEmpty ? null : int.tryParse(_capacityCtrl.text),
      };

      await api.post<OTRoom>(
        '/api/v1/ot/rooms',
        request,
        fromJson: (json) => OTRoom.fromJson(json as Map<String, dynamic>),
      );

      setState(() => _success = 'OT room created: ${_roomNumberCtrl.text}');
      if (mounted) Navigator.pop(context);
      await _loadRooms();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Failed: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _deleteRoom(OTRoom room) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Delete OT Room'),
        content:
            Text('Delete ${room.roomNumber}? This cannot be undone.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Delete'),
          ),
        ],
      ),
    );

    if (confirmed == true) {
      setState(() {
        _loading = true;
        _error = null;
      });

      try {
        final api = context.read<ApiClient>();
        await api.delete('/api/v1/ot/rooms/${room.id}',
            fromJson: (_) => null);
        setState(() => _success = 'OT room deleted');
        await _loadRooms();
      } on ApiException catch (e) {
        setState(() => _error = e.error.message);
      } finally {
        if (mounted) setState(() => _loading = false);
      }
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
              Text('OT Room Management', style: theme.textTheme.titleLarge),
              const Spacer(),
              FilledButton.icon(
                onPressed: _loading ? null : _showCreateDialog,
                icon: const Icon(Icons.add, size: 18),
                label: const Text('Add Room'),
              ),
            ],
          ),
          const SizedBox(height: Space.md),
          if (_error != null) ...[
            MessageBanner.error(_error!),
            const SizedBox(height: Space.md),
          ],
          if (_success != null) ...[
            MessageBanner.success(_success!),
            const SizedBox(height: Space.md),
          ],
          if (_rooms.isEmpty)
            Card(
              child: Padding(
                padding: const EdgeInsets.all(Space.xl),
                child: Center(
                  child: Text('No OT rooms configured',
                      style: theme.textTheme.bodyMedium?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant)),
                ),
              ),
            )
          else
            Expanded(
              child: Card(
                child: ListView.separated(
                  itemCount: _rooms.length,
                  separatorBuilder: (_, __) => const Divider(height: 1),
                  itemBuilder: (context, i) {
                    final room = _rooms[i];
                    return ListTile(
                      leading: const Icon(Icons.meeting_room_outlined),
                      title: Text('${room.roomNumber} • ${room.roomName}'),
                      subtitle: Text(room.roomType ?? 'General'),
                      trailing: IconButton(
                        icon: const Icon(Icons.delete_outline),
                        onPressed: _loading ? null : () => _deleteRoom(room),
                      ),
                    );
                  },
                ),
              ),
            ),
        ],
      ),
    );
  }
}
