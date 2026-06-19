import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/api/models.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/message_banner.dart';
// MessageBanner moved to core/widgets; re-exported so existing
// `import '...registration_screen.dart' show MessageBanner;` callers keep working.
export '../../core/widgets/message_banner.dart' show MessageBanner;

/// Patient registration form (body only — lives inside FrontDeskHome shell).
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
    final theme = Theme.of(context);

    return PageContainer(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Register New Patient', style: theme.textTheme.titleLarge),
          const SizedBox(height: Space.md),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(Space.lg),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (_error != null) ...[
                    MessageBanner.error(_error!),
                    const SizedBox(height: Space.lg),
                  ],
                  if (_successMessage != null) ...[
                    MessageBanner.success(_successMessage!),
                    const SizedBox(height: Space.lg),
                  ],
                  Row(
                    children: [
                      Expanded(
                        child: TextField(
                          controller: _firstNameCtrl,
                          enabled: !_isLoading,
                          decoration: const InputDecoration(
                              labelText: 'First Name *'),
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
                          initialValue: _selectedGender,
                          decoration:
                              const InputDecoration(labelText: 'Gender *'),
                          items: const [
                            DropdownMenuItem(
                                value: 'MALE', child: Text('Male')),
                            DropdownMenuItem(
                                value: 'FEMALE', child: Text('Female')),
                            DropdownMenuItem(
                                value: 'OTHER', child: Text('Other')),
                            DropdownMenuItem(
                                value: 'PREFER_NOT_TO_SAY',
                                child: Text('Prefer not to say')),
                          ],
                          onChanged: _isLoading
                              ? null
                              : (value) =>
                                  setState(() => _selectedGender = value),
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
                    onChanged: (v) =>
                        setState(() => _privacyConsent = v ?? false),
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
                    title: Text(
                        'Patient consents to data sharing (optional)',
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
                              child:
                                  CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Text('Register Patient'),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

