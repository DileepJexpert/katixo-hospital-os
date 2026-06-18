import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/util/file_picker.dart';
import '../../core/util/pdf_actions.dart';
import '../../core/widgets/empty_state.dart';
import '../../core/widgets/section_card.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// Reusable, embeddable attachments panel: lists the files linked to
/// (entityType, entityId), lets the user upload a new one (browser file dialog),
/// open any file in a new tab, and delete with confirmation. Drop this into any
/// detail screen — it owns its own loading/state.
class DocumentsPanel extends StatefulWidget {
  const DocumentsPanel({
    super.key,
    required this.entityType,
    required this.entityId,
    this.title = 'Attachments',
  });

  final String entityType;
  final int? entityId;
  final String title;

  @override
  State<DocumentsPanel> createState() => _DocumentsPanelState();
}

class _DocumentsPanelState extends State<DocumentsPanel> {
  List<Map<String, dynamic>> _docs = const [];
  bool _loading = true;
  bool _busy = false;
  String? _error;
  String? _success;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void didUpdateWidget(covariant DocumentsPanel oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.entityType != widget.entityType || oldWidget.entityId != widget.entityId) {
      _load();
    }
  }

  String get _listPath {
    final q = StringBuffer('/api/v1/documents?entityType=${Uri.encodeQueryComponent(widget.entityType)}');
    if (widget.entityId != null) q.write('&entityId=${widget.entityId}');
    return q.toString();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      final list = await api.get<List<dynamic>>(_listPath, fromJson: (d) => d as List<dynamic>);
      if (!mounted) return;
      setState(() {
        _docs = list.cast<Map<String, dynamic>>();
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = _msg(e);
        _loading = false;
      });
    }
  }

  Future<void> _upload() async {
    final picked = await pickFile();
    if (picked == null || !mounted) return;
    setState(() {
      _busy = true;
      _error = null;
      _success = null;
    });
    try {
      final api = context.read<ApiClient>();
      await api.uploadBytes(
        '/api/v1/documents',
        bytes: picked.bytes,
        filename: picked.name,
        contentType: picked.contentType,
        fields: {
          'entityType': widget.entityType,
          if (widget.entityId != null) 'entityId': '${widget.entityId}',
        },
      );
      if (!mounted) return;
      setState(() {
        _busy = false;
        _success = 'Uploaded ${picked.name}';
      });
      await _load();
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _busy = false;
        _error = _msg(e);
      });
    }
  }

  Future<void> _open(Map<String, dynamic> doc) async {
    final id = doc['id'];
    final name = (doc['fileName'] as String?) ?? 'file';
    final type = (doc['contentType'] as String?) ?? 'application/octet-stream';
    await openDocument(context, context.read<ApiClient>(),
        '/api/v1/documents/$id/download',
        filename: name, mimeType: type);
  }

  Future<void> _delete(Map<String, dynamic> doc) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Delete attachment'),
        content: Text('Delete "${doc['fileName']}"? This cannot be undone.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Delete')),
        ],
      ),
    );
    if (confirm != true || !mounted) return;
    setState(() {
      _busy = true;
      _error = null;
      _success = null;
    });
    try {
      final api = context.read<ApiClient>();
      await api.delete<dynamic>('/api/v1/documents/${doc['id']}', fromJson: (d) => d);
      if (!mounted) return;
      setState(() {
        _busy = false;
        _success = 'Deleted ${doc['fileName']}';
      });
      await _load();
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _busy = false;
        _error = _msg(e);
      });
    }
  }

  String _msg(Object e) => e is ApiException ? e.error.message : e.toString();

  String _humanSize(Object? bytes) {
    final n = bytes is num ? bytes.toInt() : int.tryParse('$bytes') ?? 0;
    if (n < 1024) return '$n B';
    if (n < 1024 * 1024) return '${(n / 1024).toStringAsFixed(1)} KB';
    return '${(n / (1024 * 1024)).toStringAsFixed(1)} MB';
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return SectionCard(
      title: widget.title,
      icon: Icons.attach_file,
      subtitle: widget.entityId == null
          ? widget.entityType
          : '${widget.entityType} #${widget.entityId}',
      action: FilledButton.icon(
        onPressed: _busy ? null : _upload,
        icon: const Icon(Icons.upload_file, size: 16),
        label: const Text('Upload'),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (_error != null) ...[
            MessageBanner.error(_error!),
            const SizedBox(height: Space.sm),
          ],
          if (_success != null) ...[
            MessageBanner.success(_success!),
            const SizedBox(height: Space.sm),
          ],
          if (_loading)
            const Padding(
              padding: EdgeInsets.all(Space.lg),
              child: Center(child: CircularProgressIndicator()),
            )
          else if (_docs.isEmpty)
            const EmptyState(
              icon: Icons.folder_off_outlined,
              title: 'No attachments',
              message: 'Upload a scanned report, consent form, ID proof or document.',
            )
          else
            ListView.separated(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              itemCount: _docs.length,
              separatorBuilder: (_, __) => const Divider(height: 1),
              itemBuilder: (context, i) {
                final doc = _docs[i];
                return ListTile(
                  contentPadding: EdgeInsets.zero,
                  dense: true,
                  leading: const Icon(Icons.insert_drive_file_outlined),
                  title: Text(doc['fileName']?.toString() ?? 'file'),
                  subtitle: Text(
                    '${_humanSize(doc['sizeBytes'])} · '
                    '${doc['uploadedByName'] ?? 'unknown'} · '
                    '${(doc['uploadedAt']?.toString() ?? '').split('T').first}',
                    style: theme.textTheme.bodySmall,
                  ),
                  trailing: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      IconButton(
                        tooltip: 'Open',
                        icon: const Icon(Icons.open_in_new, size: 18),
                        onPressed: _busy ? null : () => _open(doc),
                      ),
                      IconButton(
                        tooltip: 'Delete',
                        icon: const Icon(Icons.delete_outline, size: 18),
                        onPressed: _busy ? null : () => _delete(doc),
                      ),
                    ],
                  ),
                );
              },
            ),
        ],
      ),
    );
  }
}
