import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/section_card.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// Per-user account security: enrol / activate / disable TOTP two-factor.
/// Available to every signed-in user (each manages their own login).
class SecurityScreen extends StatefulWidget {
  const SecurityScreen({super.key});

  @override
  State<SecurityScreen> createState() => _SecurityScreenState();
}

class _SecurityScreenState extends State<SecurityScreen> {
  bool _loading = true;
  bool _busy = false;
  String? _error;
  String? _info;

  bool _enabled = false;
  bool _pending = false;

  // Set during an in-progress enrollment.
  String? _enrollSecret;
  String? _enrollUri;

  final _codeCtrl = TextEditingController();

  @override
  void initState() {
    super.initState();
    _loadStatus();
  }

  @override
  void dispose() {
    _codeCtrl.dispose();
    super.dispose();
  }

  ApiClient get _api => context.read<ApiClient>();

  Future<void> _loadStatus() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final data = await _api.get<Map<String, dynamic>>(
        '/api/v1/mfa/status',
        fromJson: (d) => d as Map<String, dynamic>,
      );
      if (!mounted) return;
      setState(() {
        _enabled = data['enabled'] == true;
        _pending = data['pending'] == true;
        _loading = false;
      });
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
          _error = 'Could not load security status';
          _loading = false;
        });
      }
    }
  }

  Future<void> _enroll() async {
    setState(() {
      _busy = true;
      _error = null;
      _info = null;
    });
    try {
      final data = await _api.post<Map<String, dynamic>>(
        '/api/v1/mfa/enroll',
        const {},
        fromJson: (d) => d as Map<String, dynamic>,
      );
      if (!mounted) return;
      setState(() {
        _enrollSecret = data['secret'] as String?;
        _enrollUri = data['otpAuthUri'] as String?;
        _codeCtrl.clear();
        _busy = false;
      });
    } on ApiException catch (e) {
      if (mounted) {
        setState(() {
          _error = e.error.message;
          _busy = false;
        });
      }
    }
  }

  Future<void> _activate() async {
    if (_codeCtrl.text.trim().isEmpty) {
      setState(() => _error = 'Enter the 6-digit code to activate');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
      _info = null;
    });
    try {
      await _api.post<Map<String, dynamic>>(
        '/api/v1/mfa/activate',
        {'code': _codeCtrl.text.trim()},
        fromJson: (d) => d as Map<String, dynamic>,
      );
      if (!mounted) return;
      setState(() {
        _enrollSecret = null;
        _enrollUri = null;
        _codeCtrl.clear();
        _busy = false;
        _info = 'Two-factor authentication is now on';
      });
      _loadStatus();
    } on ApiException catch (e) {
      if (mounted) {
        setState(() {
          _error = e.error.message;
          _busy = false;
        });
      }
    }
  }

  Future<void> _disable() async {
    if (_codeCtrl.text.trim().isEmpty) {
      setState(() => _error = 'Enter a current code to turn off two-factor');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
      _info = null;
    });
    try {
      await _api.post<Map<String, dynamic>>(
        '/api/v1/mfa/disable',
        {'code': _codeCtrl.text.trim()},
        fromJson: (d) => d as Map<String, dynamic>,
      );
      if (!mounted) return;
      setState(() {
        _codeCtrl.clear();
        _busy = false;
        _info = 'Two-factor authentication is off';
      });
      _loadStatus();
    } on ApiException catch (e) {
      if (mounted) {
        setState(() {
          _error = e.error.message;
          _busy = false;
        });
      }
    }
  }

  void _cancelEnroll() {
    setState(() {
      _enrollSecret = null;
      _enrollUri = null;
      _codeCtrl.clear();
      _error = null;
    });
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return PageContainer(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Account Security', style: theme.textTheme.titleLarge),
          const SizedBox(height: Space.md),
          if (_error != null) ...[
            MessageBanner.error(_error!),
            const SizedBox(height: Space.md),
          ],
          if (_info != null) ...[
            MessageBanner.success(_info!),
            const SizedBox(height: Space.md),
          ],
          if (_loading)
            const Padding(
              padding: EdgeInsets.all(Space.xl),
              child: Center(child: CircularProgressIndicator()),
            )
          else
            SectionCard(
              title: 'Two-factor authentication',
              icon: Icons.shield_outlined,
              subtitle:
                  'Add a one-time code from an authenticator app (Google Authenticator, '
                  'Authy, etc.) on top of your password.',
              child: _buildBody(theme),
            ),
        ],
      ),
    );
  }

  Widget _buildBody(ThemeData theme) {
    // Mid-enrollment: show the secret + a field to confirm the first code.
    if (_enrollSecret != null) {
      return _buildEnrollFlow(theme);
    }
    if (_enabled) {
      return _buildEnabled(theme);
    }
    return _buildDisabled(theme);
  }

  Widget _buildDisabled(ThemeData theme) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            const Icon(Icons.lock_open_outlined, color: StatusColors.warning),
            const SizedBox(width: Space.sm),
            Text('Two-factor is off',
                style: theme.textTheme.bodyMedium
                    ?.copyWith(color: StatusColors.warning)),
          ],
        ),
        if (_pending) ...[
          const SizedBox(height: Space.sm),
          Text(
            'A previous enrollment was started but never confirmed. '
            'Starting again issues a fresh secret.',
            style: theme.textTheme.bodySmall,
          ),
        ],
        const SizedBox(height: Space.md),
        FilledButton.icon(
          onPressed: _busy ? null : _enroll,
          icon: const Icon(Icons.add_moderator_outlined),
          label: const Text('Enable two-factor'),
        ),
      ],
    );
  }

  Widget _buildEnabled(ThemeData theme) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            const Icon(Icons.verified_user_outlined,
                color: StatusColors.success),
            const SizedBox(width: Space.sm),
            Text('Two-factor is on',
                style: theme.textTheme.bodyMedium
                    ?.copyWith(color: StatusColors.success)),
          ],
        ),
        const SizedBox(height: Space.md),
        Text('Enter a current code to turn it off:',
            style: theme.textTheme.bodySmall),
        const SizedBox(height: Space.sm),
        _codeField(),
        const SizedBox(height: Space.md),
        OutlinedButton.icon(
          onPressed: _busy ? null : _disable,
          icon: const Icon(Icons.remove_moderator_outlined),
          label: const Text('Disable two-factor'),
        ),
      ],
    );
  }

  Widget _buildEnrollFlow(ThemeData theme) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('1. Add this account to your authenticator app',
            style: theme.textTheme.titleSmall),
        const SizedBox(height: Space.sm),
        Text('Enter this secret key manually (or use the setup URI below):',
            style: theme.textTheme.bodySmall),
        const SizedBox(height: Space.sm),
        _copyableField('Secret key', _enrollSecret!),
        if (_enrollUri != null) ...[
          const SizedBox(height: Space.sm),
          _copyableField('Setup URI (otpauth://)', _enrollUri!),
        ],
        const SizedBox(height: Space.lg),
        Text('2. Enter the 6-digit code it shows to confirm',
            style: theme.textTheme.titleSmall),
        const SizedBox(height: Space.sm),
        _codeField(),
        const SizedBox(height: Space.md),
        Row(
          children: [
            FilledButton(
              onPressed: _busy ? null : _activate,
              child: const Text('Activate'),
            ),
            const SizedBox(width: Space.md),
            TextButton(
              onPressed: _busy ? null : _cancelEnroll,
              child: const Text('Cancel'),
            ),
          ],
        ),
      ],
    );
  }

  Widget _codeField() {
    return SizedBox(
      width: 220,
      child: TextField(
        controller: _codeCtrl,
        enabled: !_busy,
        keyboardType: TextInputType.number,
        maxLength: 6,
        decoration: const InputDecoration(
          labelText: 'Authenticator code',
          prefixIcon: Icon(Icons.pin_outlined),
          counterText: '',
        ),
      ),
    );
  }

  Widget _copyableField(String label, String value) {
    final theme = Theme.of(context);
    return Container(
      padding: const EdgeInsets.symmetric(
          horizontal: Space.md, vertical: Space.sm),
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerHigh,
        borderRadius: Corners.smRadius,
        border: Border.all(color: theme.colorScheme.outlineVariant),
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(label, style: theme.textTheme.labelSmall),
                SelectableText(
                  value,
                  style: theme.textTheme.bodySmall
                      ?.copyWith(fontFamily: 'monospace'),
                ),
              ],
            ),
          ),
          IconButton(
            tooltip: 'Copy',
            icon: const Icon(Icons.copy_outlined, size: 18),
            onPressed: () {
              Clipboard.setData(ClipboardData(text: value));
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(content: Text('$label copied')),
              );
            },
          ),
        ],
      ),
    );
  }
}
