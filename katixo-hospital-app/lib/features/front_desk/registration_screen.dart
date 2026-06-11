import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/api/models.dart';
import '../../core/auth/auth_state.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/app_shell.dart';

class RegistrationScreen extends StatefulWidget {
  const RegistrationScreen({super.key});

  @override
  State<RegistrationScreen> createState() => _RegistrationScreenState();
}

class _RegistrationScreenState extends State<RegistrationScreen> {
  final _firstNameCtrl = TextEditingController();
  final _lastNameCtrl = TextEditingController();
  final _mobileCtrl = TextEditingController();
  final _dateOfBirthCtrl = TextEditingController();
  final _addressCtrl = TextEditingController();

  String? _selectedGender;
  bool _privacyConsent = false;
  bool _dataSharingConsent = false;
  bool _isLoading = false;
  String? _error;
  String? _successMessage;

  @override
  void dispose() {
    _firstNameCtrl.dispose();
    _lastNameCtrl.dispose();
    _mobileCtrl.dispose();
    _dateOfBirthCtrl.dispose();
    _addressCtrl.dispose();
    super.dispose();
  }

  Future<void> _selectDate() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: DateTime(2000),
      firstDate: DateTime(1920),
      lastDate: DateTime.now(),
    );
    if (picked != null) {
      _dateOfBirthCtrl.text =
          '${picked.year}-${picked.month.toString().padLeft(2, '0')}-${picked.day.toString().padLeft(2, '0')}';
    }
  }

  Future<void> _handleRegister() async {
    if (_firstNameCtrl.text.isEmpty ||
        _lastNameCtrl.text.isEmpty ||
        _mobileCtrl.text.isEmpty ||
        _dateOfBirthCtrl.text.isEmpty ||
        _selectedGender == null) {
      setState(() => _error = 'Please fill all required fields');
      return;
    }
    if (!_privacyConsent) {
      setState(() => _error = 'Privacy consent is required to register');
      return;
    }

    setState(() {
      _isLoading = true;
      _error = null;
      _successMessage = null;
    });

    try {
      final apiClient = context.read<ApiClient>();

      final request = PatientRegistrationRequest(
        firstName: _firstNameCtrl.text.trim(),
        lastName: _lastNameCtrl.text.trim(),
        mobile: _mobileCtrl.text.trim(),
        dateOfBirth: _dateOfBirthCtrl.text.trim(),
        gender: _selectedGender!,
        addressLine1:
            _addressCtrl.text.isEmpty ? null : _addressCtrl.text.trim(),
        privacyConsentGiven: _privacyConsent,
        dataSharingConsent: _dataSharingConsent,
      );

      final patient = await apiClient.post<PatientResponse>(
        '/api/v1/patients',
        request,
        fromJson: (json) =>
            PatientResponse.fromJson(json as Map<String, dynamic>),
      );

      setState(() {
        _successMessage =
            'Registered ${patient.fullName} — UHID: ${patient.uhid}';
        _firstNameCtrl.clear();
        _lastNameCtrl.clear();
        _mobileCtrl.clear();
        _dateOfBirthCtrl.clear();
        _addressCtrl.clear();
        _selectedGender = null;
        _privacyConsent = false;
        _dataSharingConsent = false;
      });
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Registration failed: $e');
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final authState = context.watch<AuthState>();

    return AppShell(
      title: 'Front Desk',
      destinations: const [
        ShellDestination(
          label: 'Registration',
          icon: Icons.person_add_outlined,
          selectedIcon: Icons.person_add,
        ),
        ShellDestination(
          label: 'OPD',
          icon: Icons.event_outlined,
          selectedIcon: Icons.event,
        ),
      ],
      selectedIndex: 0,
      onDestinationSelected: (_) {},
      actions: [
        if (authState.currentUser != null)
          Padding(
            padding: const EdgeInsets.only(right: Space.sm),
            child: Center(
              child: Text(
                authState.currentUser!.name,
                style: Theme.of(context).textTheme.labelLarge,
              ),
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
            Text('Register New Patient',
                style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: Space.md),
            _buildForm(context),
          ],
        ),
      ),
    );
  }

  Widget _buildForm(BuildContext context) {
    final theme = Theme.of(context);

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(Space.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (_error != null) ...[
              _MessageBanner(message: _error!, kind: _BannerKind.error),
              const SizedBox(height: Space.lg),
            ],
            if (_successMessage != null) ...[
              _MessageBanner(
                  message: _successMessage!, kind: _BannerKind.success),
              const SizedBox(height: Space.lg),
            ],

            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _firstNameCtrl,
                    enabled: !_isLoading,
                    decoration:
                        const InputDecoration(labelText: 'First Name *'),
                  ),
                ),
                const SizedBox(width: Space.md),
                Expanded(
                  child: TextField(
                    controller: _lastNameCtrl,
                    enabled: !_isLoading,
                    decoration:
                        const InputDecoration(labelText: 'Last Name *'),
                  ),
                ),
              ],
            ),
            const SizedBox(height: Space.md),

            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _mobileCtrl,
                    enabled: !_isLoading,
                    keyboardType: TextInputType.phone,
                    decoration: const InputDecoration(
                      labelText: 'Mobile Number *',
                      prefixText: '+91 ',
                    ),
                  ),
                ),
                const SizedBox(width: Space.md),
                Expanded(
                  child: DropdownButtonFormField<String>(
                    value: _selectedGender,
                    decoration: const InputDecoration(labelText: 'Gender *'),
                    items: const [
                      DropdownMenuItem(value: 'MALE', child: Text('Male')),
                      DropdownMenuItem(value: 'FEMALE', child: Text('Female')),
                      DropdownMenuItem(value: 'OTHER', child: Text('Other')),
                      DropdownMenuItem(
                          value: 'PREFER_NOT_TO_SAY',
                          child: Text('Prefer not to say')),
                    ],
                    onChanged: _isLoading
                        ? null
                        : (value) => setState(() => _selectedGender = value),
                  ),
                ),
              ],
            ),
            const SizedBox(height: Space.md),

            TextField(
              controller: _dateOfBirthCtrl,
              enabled: !_isLoading,
              readOnly: true,
              onTap: _isLoading ? null : _selectDate,
              decoration: const InputDecoration(
                labelText: 'Date of Birth *',
                prefixIcon: Icon(Icons.calendar_today_outlined),
                hintText: 'YYYY-MM-DD',
              ),
            ),
            const SizedBox(height: Space.md),

            TextField(
              controller: _addressCtrl,
              enabled: !_isLoading,
              maxLines: 2,
              decoration: const InputDecoration(labelText: 'Address'),
            ),
            const SizedBox(height: Space.lg),

            CheckboxListTile(
              enabled: !_isLoading,
              value: _privacyConsent,
              onChanged: (v) => setState(() => _privacyConsent = v ?? false),
              controlAffinity: ListTileControlAffinity.leading,
              title: Text('Patient consents to the privacy policy *',
                  style: theme.textTheme.bodyMedium),
            ),
            CheckboxListTile(
              enabled: !_isLoading,
              value: _dataSharingConsent,
              onChanged: (v) =>
                  setState(() => _dataSharingConsent = v ?? false),
              controlAffinity: ListTileControlAffinity.leading,
              title: Text('Patient consents to data sharing (optional)',
                  style: theme.textTheme.bodyMedium),
            ),
            const SizedBox(height: Space.lg),

            SizedBox(
              width: double.infinity,
              height: Metrics.buttonHeight,
              child: FilledButton(
                onPressed: _isLoading ? null : _handleRegister,
                child: _isLoading
                    ? const SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Text('Register Patient'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

enum _BannerKind { error, success }

class _MessageBanner extends StatelessWidget {
  const _MessageBanner({required this.message, required this.kind});

  final String message;
  final _BannerKind kind;

  @override
  Widget build(BuildContext context) {
    final color = kind == _BannerKind.error
        ? StatusColors.danger
        : StatusColors.success;
    final icon = kind == _BannerKind.error
        ? Icons.error_outline
        : Icons.check_circle_outline;

    return Container(
      padding: const EdgeInsets.all(Space.md),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: Corners.smRadius,
        border: Border.all(color: color.withValues(alpha: 0.3)),
      ),
      child: Row(
        children: [
          Icon(icon, size: 20, color: color),
          const SizedBox(width: Space.sm),
          Expanded(
            child: Text(
              message,
              style:
                  Theme.of(context).textTheme.bodySmall?.copyWith(color: color),
            ),
          ),
        ],
      ),
    );
  }
}
