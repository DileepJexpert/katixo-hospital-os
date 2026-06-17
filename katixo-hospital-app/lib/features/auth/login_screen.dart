import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/auth/auth_state.dart';
import '../../core/responsive/breakpoints.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/theme_switcher.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  late TextEditingController _usernameCtrl;
  late TextEditingController _passwordCtrl;
  late TextEditingController _mfaCtrl;
  bool _isLoading = false;
  bool _mfaRequired = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _usernameCtrl = TextEditingController();
    _passwordCtrl = TextEditingController();
    _mfaCtrl = TextEditingController();
  }

  @override
  void dispose() {
    _usernameCtrl.dispose();
    _passwordCtrl.dispose();
    _mfaCtrl.dispose();
    super.dispose();
  }

  Future<void> _handleLogin() async {
    if (_usernameCtrl.text.isEmpty || _passwordCtrl.text.isEmpty) {
      setState(() => _error = 'Username and password required');
      return;
    }
    if (_mfaRequired && _mfaCtrl.text.isEmpty) {
      setState(() => _error = 'Enter the 6-digit code from your authenticator app');
      return;
    }

    setState(() {
      _isLoading = true;
      _error = null;
    });

    try {
      final authState = context.read<AuthState>();
      final apiClient = context.read<ApiClient>();

      await authState.login(
        _usernameCtrl.text,
        _passwordCtrl.text,
        apiClient,
        mfaCode: _mfaRequired ? _mfaCtrl.text : null,
      );

      // Navigation handled by router redirect watching authState.
    } on UnauthorizedException {
      setState(() => _error = 'Invalid credentials');
    } on ApiException catch (e) {
      // The account has two-factor on: reveal the code field and prompt.
      if (e.error.error == 'MFA_REQUIRED') {
        setState(() {
          _mfaRequired = true;
          _error = 'Enter the 6-digit code from your authenticator app';
        });
      } else if (e.error.error == 'INVALID_MFA_CODE') {
        setState(() {
          _mfaRequired = true;
          _error = e.error.message;
        });
      } else {
        setState(() => _error = e.error.message);
      }
    } catch (e) {
      setState(() => _error = 'Login failed: ${e.toString()}');
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Katixo Hospital OS'),
        actions: const [ThemeSwitcher(), SizedBox(width: Space.sm)],
      ),
      body: ResponsiveBuilder(
        mobile: (context) => _buildContent(context, isCompact: true),
        tablet: (context) => _buildContent(context, isCompact: false),
        desktop: (context) => Center(child: _buildContent(context, isCompact: false)),
      ),
    );
  }

  Widget _buildContent(BuildContext context, {bool isCompact = false}) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;

    return SingleChildScrollView(
      child: ConstrainedBox(
        constraints: BoxConstraints(minHeight: MediaQuery.of(context).size.height),
        child: Center(
          child: Padding(
            padding: EdgeInsets.all(context.gutter),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 400),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  // Logo / Title
                  Container(
                    padding: const EdgeInsets.all(Space.lg),
                    decoration: BoxDecoration(
                      color: scheme.primaryContainer,
                      borderRadius: Corners.mdRadius,
                    ),
                    child: Icon(
                      Icons.local_hospital_outlined,
                      size: 56,
                      color: scheme.onPrimaryContainer,
                    ),
                  ),
                  const SizedBox(height: Space.xl),

                  Text(
                    'Hospital Management',
                    style: theme.textTheme.headlineMedium,
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: Space.xs),
                  Text(
                    'Sign in to your account',
                    style: theme.textTheme.bodyMedium
                        ?.copyWith(color: scheme.onSurfaceVariant),
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: Space.xl),

                  // Error message
                  if (_error != null)
                    Container(
                      padding: const EdgeInsets.all(Space.md),
                      decoration: BoxDecoration(
                        color: StatusColors.danger.withValues(alpha: 0.12),
                        borderRadius: Corners.smRadius,
                        border: Border.all(
                          color: StatusColors.danger.withValues(alpha: 0.3),
                        ),
                      ),
                      child: Row(
                        children: [
                          const Icon(Icons.error_outline,
                              size: 20, color: StatusColors.danger),
                          const SizedBox(width: Space.sm),
                          Expanded(
                            child: Text(
                              _error!,
                              style: theme.textTheme.bodySmall
                                  ?.copyWith(color: StatusColors.danger),
                            ),
                          ),
                        ],
                      ),
                    ),
                  if (_error != null) const SizedBox(height: Space.lg),

                  // Username
                  TextField(
                    controller: _usernameCtrl,
                    enabled: !_isLoading,
                    decoration: const InputDecoration(
                      labelText: 'Username or Email',
                      prefixIcon: Icon(Icons.person_outline),
                    ),
                  ),
                  const SizedBox(height: Space.md),

                  // Password
                  TextField(
                    controller: _passwordCtrl,
                    enabled: !_isLoading,
                    obscureText: true,
                    onSubmitted: (_) {
                      if (!_mfaRequired) _handleLogin();
                    },
                    decoration: const InputDecoration(
                      labelText: 'Password',
                      prefixIcon: Icon(Icons.lock_outline),
                    ),
                  ),

                  // Two-factor code (shown once the account asks for it)
                  if (_mfaRequired) ...[
                    const SizedBox(height: Space.md),
                    TextField(
                      controller: _mfaCtrl,
                      enabled: !_isLoading,
                      autofocus: true,
                      keyboardType: TextInputType.number,
                      maxLength: 6,
                      onSubmitted: (_) => _handleLogin(),
                      decoration: const InputDecoration(
                        labelText: 'Authenticator code',
                        helperText: '6-digit code from your authenticator app',
                        prefixIcon: Icon(Icons.shield_outlined),
                        counterText: '',
                      ),
                    ),
                  ],
                  const SizedBox(height: Space.lg),

                  // Login button
                  SizedBox(
                    width: double.infinity,
                    height: Metrics.buttonHeight,
                    child: FilledButton(
                      onPressed: _isLoading ? null : _handleLogin,
                      child: _isLoading
                          ? const SizedBox(
                              width: 20,
                              height: 20,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Text('Sign In'),
                    ),
                  ),
                  const SizedBox(height: Space.lg),

                  // Demo credentials hint
                  Container(
                    padding: const EdgeInsets.all(Space.md),
                    decoration: BoxDecoration(
                      color: scheme.surfaceContainerHigh,
                      borderRadius: Corners.smRadius,
                      border: Border.all(color: scheme.outlineVariant),
                    ),
                    child: Text(
                      'Demo: admin / admin123\nFront Desk: reception / desk123\nDoctor: doctor1 / pass123',
                      style: theme.textTheme.labelSmall
                          ?.copyWith(color: scheme.onSurfaceVariant),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
