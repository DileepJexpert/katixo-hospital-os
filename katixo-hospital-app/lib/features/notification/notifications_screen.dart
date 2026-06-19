import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/empty_state.dart';
import '../../core/widgets/section_card.dart';
import '../../core/widgets/status_chip.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// Notifications admin console: SMS/WhatsApp provider settings, per-type
/// templates, a manual send tool, and the delivery log. Backed by
/// `/api/v1/notifications`. Body widget (mounted by Admin/SuperAdmin homes).
class NotificationsScreen extends StatefulWidget {
  const NotificationsScreen({super.key});

  @override
  State<NotificationsScreen> createState() => _NotificationsScreenState();
}

class _NotificationsScreenState extends State<NotificationsScreen> {
  static const _types = ['WALK_IN', 'APPOINTMENT', 'REPORT_READY', 'BILL', 'GENERIC'];
  static const _channels = ['SMS', 'WHATSAPP'];

  String _tab = 'settings';
  bool _loading = false;
  String? _error;
  String? _info;

  // ---- settings ----
  bool _settingsInit = false;
  bool _smsEnabled = false;
  String _smsProvider = 'MSG91';
  bool _smsKeyConfigured = false;
  final _smsApiKey = TextEditingController();
  final _smsSenderId = TextEditingController();
  final _smsCustomUrl = TextEditingController();
  bool _waEnabled = false;
  String _waProvider = 'META';
  bool _waTokenConfigured = false;
  final _waToken = TextEditingController();
  final _waPhoneId = TextEditingController();
  final _waBaseUrl = TextEditingController();
  final _waCustomUrl = TextEditingController();

  // ---- templates ----
  List<Map<String, dynamic>> _templates = const [];
  String _tplType = 'WALK_IN';
  String _tplChannel = 'SMS';
  final _tplProviderRef = TextEditingController();
  final _tplBody = TextEditingController();
  bool _tplActive = true;

  // ---- send ----
  String _sendType = 'GENERIC';
  final _sendMobile = TextEditingController();
  final _sendParams = TextEditingController();

  // ---- logs ----
  List<Map<String, dynamic>> _logs = const [];

  @override
  void initState() {
    super.initState();
    _loadAll();
  }

  @override
  void dispose() {
    for (final c in [
      _smsApiKey, _smsSenderId, _smsCustomUrl, _waToken, _waPhoneId,
      _waBaseUrl, _waCustomUrl, _tplProviderRef, _tplBody, _sendMobile, _sendParams,
    ]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _loadAll() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      final settings = await api.get<Map<String, dynamic>>(
        '/api/v1/notifications/settings',
        fromJson: (j) => j as Map<String, dynamic>,
      );
      final templates = await api.get<List<dynamic>>(
        '/api/v1/notifications/templates',
        fromJson: (j) => j as List<dynamic>,
      );
      final logs = await api.get<List<dynamic>>(
        '/api/v1/notifications/logs',
        fromJson: (j) => j as List<dynamic>,
      );
      if (!mounted) return;
      setState(() {
        _templates = templates.cast<Map<String, dynamic>>();
        _logs = logs.cast<Map<String, dynamic>>();
        if (!_settingsInit) {
          _applySettings(settings);
          _settingsInit = true;
        }
      });
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Failed to load: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _applySettings(Map<String, dynamic> s) {
    _smsEnabled = s['smsEnabled'] == true;
    _smsProvider = '${s['smsProvider'] ?? 'MSG91'}';
    _smsKeyConfigured = s['smsApiKeyConfigured'] == true;
    _smsSenderId.text = '${s['smsSenderId'] ?? ''}';
    _smsCustomUrl.text = '${s['smsCustomUrl'] ?? ''}';
    _waEnabled = s['whatsappEnabled'] == true;
    _waProvider = '${s['whatsappProvider'] ?? 'META'}';
    _waTokenConfigured = s['whatsappTokenConfigured'] == true;
    _waPhoneId.text = '${s['whatsappPhoneNumberId'] ?? ''}';
    _waBaseUrl.text = '${s['whatsappBaseUrl'] ?? ''}';
    _waCustomUrl.text = '${s['whatsappCustomUrl'] ?? ''}';
  }

  Future<void> _saveSettings() async {
    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });
    try {
      final api = context.read<ApiClient>();
      final saved = await api.put<Map<String, dynamic>>(
        '/api/v1/notifications/settings',
        {
          'smsEnabled': _smsEnabled,
          'smsProvider': _smsProvider,
          // only overwrite the key if the admin typed a new one
          if (_smsApiKey.text.trim().isNotEmpty) 'smsApiKey': _smsApiKey.text.trim(),
          'smsSenderId': _smsSenderId.text.trim(),
          'smsCustomUrl': _smsCustomUrl.text.trim(),
          'whatsappEnabled': _waEnabled,
          'whatsappProvider': _waProvider,
          if (_waToken.text.trim().isNotEmpty) 'whatsappToken': _waToken.text.trim(),
          'whatsappPhoneNumberId': _waPhoneId.text.trim(),
          'whatsappBaseUrl': _waBaseUrl.text.trim(),
          'whatsappCustomUrl': _waCustomUrl.text.trim(),
        },
        fromJson: (j) => j as Map<String, dynamic>,
      );
      if (!mounted) return;
      setState(() {
        _applySettings(saved);
        _smsApiKey.clear();
        _waToken.clear();
        _info = 'Notification settings saved';
      });
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _saveTemplate() async {
    if (_tplBody.text.trim().isEmpty) {
      setState(() => _error = 'Template body is required');
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });
    try {
      final api = context.read<ApiClient>();
      await api.put<Map<String, dynamic>>(
        '/api/v1/notifications/templates',
        {
          'type': _tplType,
          'channel': _tplChannel,
          'providerRef': _tplProviderRef.text.trim(),
          'body': _tplBody.text.trim(),
          'active': _tplActive,
        },
        fromJson: (j) => j as Map<String, dynamic>,
      );
      _tplProviderRef.clear();
      _tplBody.clear();
      setState(() => _info = 'Template saved');
      await _loadAll();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _send() async {
    if (_sendMobile.text.trim().isEmpty) {
      setState(() => _error = 'Mobile number is required');
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });
    try {
      final api = context.read<ApiClient>();
      await api.post<List<dynamic>>(
        '/api/v1/notifications/send',
        {
          'type': _sendType,
          'mobile': _sendMobile.text.trim(),
          'params': _parseParams(_sendParams.text),
        },
        fromJson: (j) => j as List<dynamic>,
      );
      setState(() => _info = 'Notification dispatched (see Logs)');
      await _loadAll();
      if (mounted) setState(() => _tab = 'logs');
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  /// Parses "key=value" lines into a params map for template placeholders.
  Map<String, String> _parseParams(String raw) {
    final map = <String, String>{};
    for (final line in raw.split('\n')) {
      final i = line.indexOf('=');
      if (i > 0) {
        map[line.substring(0, i).trim()] = line.substring(i + 1).trim();
      }
    }
    return map;
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    const tabs = <(String, String)>[
      ('settings', 'Settings'),
      ('templates', 'Templates'),
      ('send', 'Send'),
      ('logs', 'Logs'),
    ];
    return PageContainer(
      scrollable: false,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text('Notifications', style: theme.textTheme.titleLarge),
              const Spacer(),
              IconButton(
                tooltip: 'Refresh',
                onPressed: _loading ? null : _loadAll,
                icon: const Icon(Icons.refresh),
              ),
            ],
          ),
          const SizedBox(height: Space.md),
          Wrap(
            spacing: Space.sm,
            children: [
              for (final t in tabs)
                ChoiceChip(
                  label: Text(t.$2),
                  selected: _tab == t.$1,
                  onSelected: (_) => setState(() => _tab = t.$1),
                ),
            ],
          ),
          const SizedBox(height: Space.md),
          if (_error != null) ...[
            MessageBanner.error(_error!),
            const SizedBox(height: Space.md),
          ],
          if (_info != null) ...[
            MessageBanner.success(_info!),
            const SizedBox(height: Space.md),
          ],
          Expanded(child: _body(theme)),
        ],
      ),
    );
  }

  Widget _body(ThemeData theme) {
    return switch (_tab) {
      'templates' => _templatesTab(theme),
      'send' => _sendTab(theme),
      'logs' => _logsTab(theme),
      _ => _settingsTab(theme),
    };
  }

  // ---------------- settings tab ----------------

  Widget _settingsTab(ThemeData theme) {
    return SingleChildScrollView(
      child: Column(
        children: [
          SectionCard(
            title: 'SMS',
            icon: Icons.sms_outlined,
            subtitle: 'Transactional SMS gateway (DLT-registered in India).',
            child: Column(
              children: [
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Enable SMS'),
                  value: _smsEnabled,
                  onChanged: (v) => setState(() => _smsEnabled = v),
                ),
                DropdownButtonFormField<String>(
                  initialValue: _smsProvider,
                  decoration: const InputDecoration(labelText: 'Provider'),
                  items: const [
                    DropdownMenuItem(value: 'MSG91', child: Text('MSG91 (DLT)')),
                    DropdownMenuItem(value: 'CUSTOM', child: Text('Custom / Fast2SMS webhook')),
                  ],
                  onChanged: (v) => setState(() => _smsProvider = v ?? 'MSG91'),
                ),
                const SizedBox(height: Space.sm),
                TextField(
                  controller: _smsApiKey,
                  decoration: InputDecoration(
                    labelText: 'API key',
                    hintText: _smsKeyConfigured ? 'configured — leave blank to keep' : 'not set',
                  ),
                ),
                const SizedBox(height: Space.sm),
                TextField(
                  controller: _smsSenderId,
                  decoration: const InputDecoration(labelText: 'Sender ID / DLT header'),
                ),
                const SizedBox(height: Space.sm),
                TextField(
                  controller: _smsCustomUrl,
                  decoration: const InputDecoration(labelText: 'Custom webhook URL (CUSTOM only)'),
                ),
              ],
            ),
          ),
          const SizedBox(height: Space.md),
          SectionCard(
            title: 'WhatsApp',
            icon: Icons.chat_outlined,
            subtitle: 'WhatsApp Business (Meta Cloud API) or a custom BSP.',
            child: Column(
              children: [
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Enable WhatsApp'),
                  value: _waEnabled,
                  onChanged: (v) => setState(() => _waEnabled = v),
                ),
                DropdownButtonFormField<String>(
                  initialValue: _waProvider,
                  decoration: const InputDecoration(labelText: 'Provider'),
                  items: const [
                    DropdownMenuItem(value: 'META', child: Text('Meta Cloud API')),
                    DropdownMenuItem(value: 'CUSTOM', child: Text('Custom BSP webhook')),
                  ],
                  onChanged: (v) => setState(() => _waProvider = v ?? 'META'),
                ),
                const SizedBox(height: Space.sm),
                TextField(
                  controller: _waToken,
                  decoration: InputDecoration(
                    labelText: 'Access token',
                    hintText: _waTokenConfigured ? 'configured — leave blank to keep' : 'not set',
                  ),
                ),
                const SizedBox(height: Space.sm),
                TextField(
                  controller: _waPhoneId,
                  decoration: const InputDecoration(labelText: 'Phone number ID (Meta)'),
                ),
                const SizedBox(height: Space.sm),
                TextField(
                  controller: _waBaseUrl,
                  decoration: const InputDecoration(labelText: 'Base URL (Meta Graph)'),
                ),
                const SizedBox(height: Space.sm),
                TextField(
                  controller: _waCustomUrl,
                  decoration: const InputDecoration(labelText: 'Custom webhook URL (CUSTOM only)'),
                ),
              ],
            ),
          ),
          const SizedBox(height: Space.md),
          Align(
            alignment: Alignment.centerRight,
            child: FilledButton.icon(
              onPressed: _loading ? null : _saveSettings,
              icon: const Icon(Icons.save_outlined, size: 18),
              label: const Text('Save settings'),
            ),
          ),
        ],
      ),
    );
  }

  // ---------------- templates tab ----------------

  Widget _templatesTab(ThemeData theme) {
    return SingleChildScrollView(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SectionCard(
            title: 'Add / update template',
            icon: Icons.edit_note_outlined,
            subtitle: 'One template per (type, channel). Use {name}, {amount}, … placeholders.',
            child: Column(
              children: [
                Row(
                  children: [
                    Expanded(
                      child: DropdownButtonFormField<String>(
                        initialValue: _tplType,
                        decoration: const InputDecoration(labelText: 'Type'),
                        items: [
                          for (final t in _types)
                            DropdownMenuItem(value: t, child: Text(_titleCase(t))),
                        ],
                        onChanged: (v) => setState(() => _tplType = v ?? 'WALK_IN'),
                      ),
                    ),
                    const SizedBox(width: Space.md),
                    Expanded(
                      child: DropdownButtonFormField<String>(
                        initialValue: _tplChannel,
                        decoration: const InputDecoration(labelText: 'Channel'),
                        items: [
                          for (final c in _channels)
                            DropdownMenuItem(value: c, child: Text(_titleCase(c))),
                        ],
                        onChanged: (v) => setState(() => _tplChannel = v ?? 'SMS'),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: Space.sm),
                TextField(
                  controller: _tplProviderRef,
                  decoration: const InputDecoration(
                    labelText: 'Provider ref (DLT template id / Meta template name)',
                  ),
                ),
                const SizedBox(height: Space.sm),
                TextField(
                  controller: _tplBody,
                  maxLines: 3,
                  decoration: const InputDecoration(labelText: 'Message body *'),
                ),
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Active'),
                  value: _tplActive,
                  onChanged: (v) => setState(() => _tplActive = v),
                ),
                Align(
                  alignment: Alignment.centerRight,
                  child: FilledButton(
                    onPressed: _loading ? null : _saveTemplate,
                    child: const Text('Save template'),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: Space.md),
          if (_templates.isEmpty)
            const EmptyState(
              icon: Icons.edit_note_outlined,
              title: 'No templates yet',
              message: 'Add a template above for each notification type and channel.',
            )
          else
            SectionCard(
              title: 'Configured templates',
              icon: Icons.list_alt_outlined,
              child: Column(
                children: [
                  for (final t in _templates)
                    ListTile(
                      contentPadding: EdgeInsets.zero,
                      title: Text('${_titleCase('${t['type']}')} · ${t['channel']}'),
                      subtitle: Text('${t['body']}'),
                      trailing: StatusChip.auto(t['active'] == true ? 'ACTIVE' : 'INACTIVE'),
                      onTap: () => setState(() {
                        _tplType = '${t['type']}';
                        _tplChannel = '${t['channel']}';
                        _tplProviderRef.text = '${t['providerRef'] ?? ''}';
                        _tplBody.text = '${t['body'] ?? ''}';
                        _tplActive = t['active'] == true;
                      }),
                    ),
                ],
              ),
            ),
        ],
      ),
    );
  }

  // ---------------- send tab ----------------

  Widget _sendTab(ThemeData theme) {
    return SingleChildScrollView(
      child: SectionCard(
        title: 'Send a notification',
        icon: Icons.send_outlined,
        subtitle: 'Manually dispatch using the configured template for the chosen type.',
        child: Column(
          children: [
            DropdownButtonFormField<String>(
              initialValue: _sendType,
              decoration: const InputDecoration(labelText: 'Type'),
              items: [
                for (final t in _types)
                  DropdownMenuItem(value: t, child: Text(_titleCase(t))),
              ],
              onChanged: (v) => setState(() => _sendType = v ?? 'GENERIC'),
            ),
            const SizedBox(height: Space.sm),
            TextField(
              controller: _sendMobile,
              keyboardType: TextInputType.phone,
              decoration: const InputDecoration(labelText: 'Mobile number *'),
            ),
            const SizedBox(height: Space.sm),
            TextField(
              controller: _sendParams,
              maxLines: 3,
              decoration: const InputDecoration(
                labelText: 'Placeholders (key=value per line)',
                hintText: 'name=Asha\namount=1500',
              ),
            ),
            const SizedBox(height: Space.md),
            Align(
              alignment: Alignment.centerRight,
              child: FilledButton.icon(
                onPressed: _loading ? null : _send,
                icon: const Icon(Icons.send, size: 18),
                label: const Text('Send'),
              ),
            ),
          ],
        ),
      ),
    );
  }

  // ---------------- logs tab ----------------

  Widget _logsTab(ThemeData theme) {
    if (_logs.isEmpty) {
      return const EmptyState(
        icon: Icons.history_outlined,
        title: 'No notifications sent yet',
        message: 'Delivery attempts (SENT / FAILED / SKIPPED) will appear here.',
      );
    }
    return ListView.separated(
      itemCount: _logs.length,
      separatorBuilder: (_, __) => const Divider(height: 1),
      itemBuilder: (context, i) {
        final l = _logs[i];
        return ListTile(
          leading: Icon(l['channel'] == 'WHATSAPP' ? Icons.chat_outlined : Icons.sms_outlined),
          title: Text('${_titleCase('${l['type']}')} → ${l['recipient']}'),
          subtitle: Text(
            '${l['channel']} · ${l['at'] ?? ''}'
            '${l['error'] != null ? '\n${l['error']}' : ''}',
            style: theme.textTheme.bodySmall,
          ),
          isThreeLine: l['error'] != null,
          trailing: StatusChip.auto('${l['status']}'),
        );
      },
    );
  }

  String _titleCase(String s) => s
      .toLowerCase()
      .split('_')
      .map((w) => w.isEmpty ? w : '${w[0].toUpperCase()}${w.substring(1)}')
      .join(' ');
}
