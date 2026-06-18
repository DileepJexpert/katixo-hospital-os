import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/auth/auth_state.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/empty_state.dart';
import '../../core/widgets/status_chip.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// Staff / user administration: an ADMIN onboards and maintains the hospital's
/// own staff logins — create with a role, edit, activate/deactivate, reset
/// password. The production replacement for the dev seeder. Body widget — host
/// supplies the AppShell. ADMIN/SUPER_ADMIN only.
class StaffScreen extends StatefulWidget {
  const StaffScreen({super.key});

  @override
  State<StaffScreen> createState() => _StaffScreenState();
}

class _StaffScreenState extends State<StaffScreen> {
  /// Roles an admin may assign (SUPER_ADMIN is intentionally excluded).
  static const _roles = <String>[
    'ADMIN', 'DOCTOR', 'NURSE', 'PHARMACIST', 'LAB_TECH', 'BILLING', 'FRONT_DESK',
  ];

  List<Map<String, dynamic>> _staff = const [];
  bool _includeInactive = true;
  bool _loading = false;
  String? _error;
  String? _info;

  bool get _canEdit {
    final role = context.read<AuthState>().currentUser?.role ?? '';
    return role == 'ADMIN' || role == 'SUPER_ADMIN';
  }

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      final list = await api.get<List<Map<String, dynamic>>>(
        '/api/v1/staff/manage?includeInactive=$_includeInactive',
        fromJson: (json) =>
            List<Map<String, dynamic>>.from(json as List? ?? const []),
      );
      if (mounted) setState(() => _staff = list);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Could not load staff: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _action(String path, String okMsg) async {
    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });
    try {
      final api = context.read<ApiClient>();
      await api.post<dynamic>(path, const <String, dynamic>{}, fromJson: (j) => j);
      setState(() => _info = okMsg);
      await _load();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _toggleActive(Map<String, dynamic> u) async {
    final id = u['id'];
    final active = u['status'] == 'ACTIVE';
    await _action(
      '/api/v1/staff/$id/${active ? 'deactivate' : 'activate'}',
      active ? 'Login deactivated' : 'Login activated',
    );
  }

  Future<void> _editDialog([Map<String, dynamic>? existing]) async {
    final isEdit = existing != null;
    final name = TextEditingController(text: '${existing?['name'] ?? ''}');
    final username = TextEditingController(text: '${existing?['username'] ?? ''}');
    final password = TextEditingController();
    final staffCode = TextEditingController(text: '${existing?['staffCode'] ?? ''}');
    final specialisation =
        TextEditingController(text: '${existing?['specialisation'] ?? ''}');
    String role = '${existing?['role'] ?? 'DOCTOR'}';
    if (!_roles.contains(role)) role = 'DOCTOR';

    final save = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(isEdit ? 'Edit staff' : 'Add staff login'),
        content: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: name,
                  decoration: const InputDecoration(labelText: 'Full name *'),
                ),
                const SizedBox(height: Space.sm),
                TextField(
                  controller: username,
                  enabled: !isEdit, // username is the login key; not editable
                  decoration: InputDecoration(
                    labelText: 'Username *',
                    helperText: isEdit ? 'Username cannot be changed' : null,
                  ),
                ),
                if (!isEdit) ...[
                  const SizedBox(height: Space.sm),
                  TextField(
                    controller: password,
                    obscureText: true,
                    decoration: const InputDecoration(
                      labelText: 'Password *',
                      helperText: 'At least 6 characters',
                    ),
                  ),
                ],
                const SizedBox(height: Space.sm),
                StatefulBuilder(
                  builder: (context, setLocal) => DropdownButtonFormField<String>(
                    value: role,
                    decoration: const InputDecoration(labelText: 'Role *'),
                    items: [
                      for (final r in _roles)
                        DropdownMenuItem(value: r, child: Text(_titleCase(r))),
                    ],
                    onChanged: (v) => setLocal(() => role = v ?? 'DOCTOR'),
                  ),
                ),
                const SizedBox(height: Space.sm),
                TextField(
                  controller: staffCode,
                  decoration: const InputDecoration(labelText: 'Staff code'),
                ),
                const SizedBox(height: Space.sm),
                TextField(
                  controller: specialisation,
                  decoration: const InputDecoration(
                      labelText: 'Specialisation (e.g. Cardiology)'),
                ),
              ],
            ),
          ),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Cancel')),
          FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('Save')),
        ],
      ),
    );
    if (save != true) return;

    if (name.text.trim().isEmpty || username.text.trim().isEmpty) {
      setState(() => _error = 'Name and username are required');
      return;
    }
    if (!isEdit && password.text.length < 6) {
      setState(() => _error = 'Password must be at least 6 characters');
      return;
    }

    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });
    try {
      final api = context.read<ApiClient>();
      if (isEdit) {
        await api.put<dynamic>(
          '/api/v1/staff/${existing['id']}',
          {
            'name': name.text.trim(),
            'role': role,
            'staffCode': staffCode.text.trim(),
            'specialisation': specialisation.text.trim(),
          },
          fromJson: (j) => j,
        );
        setState(() => _info = 'Staff updated');
      } else {
        await api.post<dynamic>(
          '/api/v1/staff',
          {
            'name': name.text.trim(),
            'username': username.text.trim(),
            'password': password.text,
            'role': role,
            'staffCode': staffCode.text.trim(),
            'specialisation': specialisation.text.trim(),
          },
          fromJson: (j) => j,
        );
        setState(() => _info = 'Staff login created');
      }
      await _load();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _resetPasswordDialog(Map<String, dynamic> u) async {
    final password = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Reset password — ${u['username']}'),
        content: TextField(
          controller: password,
          obscureText: true,
          decoration: const InputDecoration(
            labelText: 'New password *',
            helperText: 'At least 6 characters',
          ),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Cancel')),
          FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('Reset')),
        ],
      ),
    );
    if (ok != true) return;
    if (password.text.length < 6) {
      setState(() => _error = 'Password must be at least 6 characters');
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });
    try {
      final api = context.read<ApiClient>();
      await api.post<dynamic>(
        '/api/v1/staff/${u['id']}/reset-password',
        {'newPassword': password.text},
        fromJson: (j) => j,
      );
      setState(() => _info = 'Password reset for ${u['username']}');
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
      scrollable: false,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text('Staff & Logins', style: theme.textTheme.titleLarge),
              const Spacer(),
              Row(
                children: [
                  const Text('Show inactive'),
                  Switch(
                    value: _includeInactive,
                    onChanged: (v) {
                      setState(() => _includeInactive = v);
                      _load();
                    },
                  ),
                ],
              ),
              const SizedBox(width: Space.sm),
              IconButton(
                tooltip: 'Refresh',
                onPressed: _loading ? null : _load,
                icon: const Icon(Icons.refresh),
              ),
              if (_canEdit) ...[
                const SizedBox(width: Space.sm),
                FilledButton.icon(
                  onPressed: _loading ? null : () => _editDialog(),
                  icon: const Icon(Icons.person_add_outlined, size: 18),
                  label: const Text('Add staff'),
                ),
              ],
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
          Expanded(
            child: _staff.isEmpty
                ? EmptyState(
                    icon: Icons.badge_outlined,
                    title: 'No staff yet',
                    message: _canEdit
                        ? 'Add logins for your doctors, nurses, pharmacists and front-desk staff.'
                        : 'No staff configured.',
                  )
                : Card(
                    child: ListView.separated(
                      itemCount: _staff.length,
                      separatorBuilder: (_, __) => const Divider(height: 1),
                      itemBuilder: (context, i) {
                        final u = _staff[i];
                        final active = u['status'] == 'ACTIVE';
                        return ListTile(
                          leading: const Icon(Icons.badge_outlined),
                          title: Row(
                            children: [
                              Flexible(
                                  child: Text('${u['name']}',
                                      style: theme.textTheme.titleSmall)),
                              const SizedBox(width: Space.sm),
                              StatusChip.auto('${u['role']}'),
                              if (!active) ...[
                                const SizedBox(width: Space.xs),
                                StatusChip.auto('INACTIVE'),
                              ],
                              if (u['mfaEnabled'] == true) ...[
                                const SizedBox(width: Space.xs),
                                const Icon(Icons.verified_user_outlined, size: 16),
                              ],
                            ],
                          ),
                          subtitle: Text(
                            '@${u['username']}'
                            '${u['staffCode'] != null ? ' · ${u['staffCode']}' : ''}'
                            '${u['specialisation'] != null ? ' · ${u['specialisation']}' : ''}',
                            style: theme.textTheme.bodySmall,
                          ),
                          trailing: _canEdit
                              ? Row(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    IconButton(
                                      tooltip: 'Edit',
                                      icon: const Icon(Icons.edit_outlined, size: 20),
                                      onPressed:
                                          _loading ? null : () => _editDialog(u),
                                    ),
                                    IconButton(
                                      tooltip: 'Reset password',
                                      icon: const Icon(Icons.key_outlined, size: 20),
                                      onPressed: _loading
                                          ? null
                                          : () => _resetPasswordDialog(u),
                                    ),
                                    IconButton(
                                      tooltip: active ? 'Deactivate' : 'Activate',
                                      icon: Icon(
                                          active
                                              ? Icons.block
                                              : Icons.check_circle_outline,
                                          size: 20),
                                      onPressed:
                                          _loading ? null : () => _toggleActive(u),
                                    ),
                                  ],
                                )
                              : null,
                        );
                      },
                    ),
                  ),
          ),
        ],
      ),
    );
  }

  String _titleCase(String s) => s
      .toLowerCase()
      .split('_')
      .map((w) => w.isEmpty ? w : '${w[0].toUpperCase()}${w.substring(1)}')
      .join(' ');
}
