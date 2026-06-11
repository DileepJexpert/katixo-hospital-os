import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/api/models.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/app_shell.dart';

class RegistrationScreen extends StatefulWidget {
  const RegistrationScreen({super.key});

  @override
  State<RegistrationScreen> createState() => _RegistrationScreenState();
}

class _RegistrationScreenState extends State<RegistrationScreen> {
  late TextEditingController _firstNameCtrl;
  late TextEditingController _lastNameCtrl;
  late TextEditingController _mobileCtrl;
  late TextEditingController _dateOfBirthCtrl;
  late TextEditingController _addressCtrl;

  String? _selectedGender;
  bool _privacyConsent = false;
  bool _isLoading = false;
  String? _error;
  String? _successMessage;

  final List<PatientIdentifierRequest> _identifiers = [];

  @override
  void initState() {
    super.initState();
    _firstNameCtrl = TextEditingController();
    _lastNameCtrl = TextEditingController();
    _mobileCtrl = TextEditingController();
    _dateOfBirthCtrl = TextEditingController();
    _addressCtrl = TextEditingController();
  }

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
      _dateOfBirthCtrl.text = '${picked.year}-${picked.month.toString().padLeft(2, '0')}-${picked.day.toString().padLeft(2, '0')}';
    }
  }

  Future<void> _handleRegister() async {
    if (_firstNameCtrl.text.isEmpty ||
        _lastNameCtrl.text.isEmpty ||
        _mobileCtrl.text.isEmpty ||
        _dateOfBirthCtrl.text.isEmpty ||
        _selectedGender == null ||
        !_privacyConsent) {
      setState(() => _error = 'Please fill all required fields and accept privacy consent');
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
        address: _addressCtrl.text.isEmpty ? null : _addressCtrl.text.trim(),
        privacyConsentGiven: _privacyConsent,
        identifiers: _identifiers,
      );

      final response = await apiClient.post<PatientResponse>(
        '/api/v1/patients',
        request,
        fromJson: (json) => PatientResponse.fromJson(json as Map<String, dynamic>),
      );

      if (mounted) {
        setState(() {
          _successMessage = 'Patient registered successfully. UHID: ${response.uhid}';
          _firstNameCtrl.clear();
          _lastNameCtrl.clear();
          _mobileCtrl.clear();
          _dateOfBirthCtrl.clear();
          _addressCtrl.clear();
          _selectedGender = null;
          _privacyConsent = false;
          _identifiers.clear();
        });
      }
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Registration failed: ${e.toString()}');
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return AppShell(
      title: 'Patient Registration',
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
    final scheme = theme.colorScheme;

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(Space.lg),
        child: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Error / Success messages
              if (_error != null)
                Container(
                  padding: const EdgeInsets.all(Space.md),
                  margin: const EdgeInsets.only(bottom: Space.lg),
                  decoration: BoxDecoration(
                    color: const Color(0xFFC62828).withValues(alpha: 0.12),
                    borderRadius: Corners.smRadius,
                    border: Border.all(
                      color: const Color(0xFFC62828).withValues(alpha: 0.3),
                    ),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.error_outline,
                          size: 20, color: Color(0xFFC62828)),
                      const SizedBox(width: Space.sm),
                      Expanded(
                        child: Text(
                          _error!,
                          style: theme.textTheme.bodySmall
                              ?.copyWith(color: const Color(0xFFC62828)),
                        ),
                      ),
                    ],
                  ),
                ),

              if (_successMessage != null)
                Container(
                  padding: const EdgeInsets.all(Space.md),
                  margin: const EdgeInsets.only(bottom: Space.lg),
                  decoration: BoxDecoration(
                    color: const Color(0xFF2E7D32).withValues(alpha: 0.12),
                    borderRadius: Corners.smRadius,
                    border: Border.all(
                      color: const Color(0xFF2E7D32).withValues(alpha: 0.3),
                    ),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.check_circle_outline,
                          size: 20, color: Color(0xFF2E7D32)),
                      const SizedBox(width: Space.sm),
                      Expanded(
                        child: Text(
                          _successMessage!,
                          style: theme.textTheme.bodySmall
                              ?.copyWith(color: const Color(0xFF2E7D32)),
                        ),
                      ),
                    ],
                  ),
                ),

              // Name fields
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
                      decoration: const InputDecoration(labelText: 'Last Name *'),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: Space.md),

              // Mobile & Gender
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
                    child: DropdownMenu<String>(
                      enabled: !_isLoading,
                      label: const Text('Gender *'),
                      dropdownMenuEntries: const [
                        DropdownMenuEntry(value: 'M', label: 'Male'),
                        DropdownMenuEntry(value: 'F', label: 'Female'),
                        DropdownMenuEntry(value: 'O', label: 'Other'),
                      ],
                      onSelected: (value) =>
                          setState(() => _selectedGender = value),
                      initialSelection: _selectedGender,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: Space.md),

              // Date of Birth
              TextField(
                controller: _dateOfBirthCtrl,
                enabled: !_isLoading,
                readOnly: true,
                onTap: _isLoading ? null : _selectDate,
                decoration: InputDecoration(
                  labelText: 'Date of Birth *',
                  prefixIcon: const Icon(Icons.calendar_today_outlined),
                  hintText: 'YYYY-MM-DD',
                ),
              ),
              const SizedBox(height: Space.md),

              // Address
              TextField(
                controller: _addressCtrl,
                enabled: !_isLoading,
                maxLines: 2,
                decoration: const InputDecoration(labelText: 'Address'),
              ),
              const SizedBox(height: Space.lg),

              // Privacy Consent checkbox
              CheckboxListTile(
                enabled: !_isLoading,
                value: _privacyConsent,
                onChanged: (value) =>
                    setState(() => _privacyConsent = value ?? false),
                title: Text(
                  'I consent to privacy policy and data sharing',
                  style: theme.textTheme.bodyMedium,
                ),
              ),
              const SizedBox(height: Space.lg),

              // Register button
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
      ),
    );
  }
}
