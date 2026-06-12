import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/abdm_models.dart';
import '../../core/api/http_client.dart';
import '../../core/api/models.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/status_chip.dart';
import 'registration_screen.dart' show MessageBanner;

/// ABDM / ABHA management (body only — lives inside FrontDeskHome shell):
/// look up a patient by UHID, link/unlink their ABHA, record and revoke
/// data-sharing consents, and view their care contexts.
class AbhaScreen extends StatefulWidget {
  const AbhaScreen({super.key});

  @override
  State<AbhaScreen> createState() => _AbhaScreenState();
}

class _AbhaScreenState extends State<AbhaScreen> {
  final _uhidCtrl = TextEditingController();
  final _abhaNumberCtrl = TextEditingController();
  final _abhaAddressCtrl = TextEditingController();

  String _verificationMethod = 'DEMOGRAPHICS';

  PatientResponse? _patient;
  AbhaLinkResponse? _abhaLink;
  List<ConsentResponse> _consents = const [];
  List<CareContextResponse> _careContexts = const [];

  bool _isLoading = false;
  String? _error;
  String? _successMessage;

  @override
  void dispose() {
    _uhidCtrl.dispose();
    _abhaNumberCtrl.dispose();
    _abhaAddressCtrl.dispose();
    super.dispose();
  }

  // ------------------------------------------------------------
  // data loading
  // ------------------------------------------------------------

  Future<void> _lookupPatient() async {
    final uhid = _uhidCtrl.text.trim();
    if (uhid.isEmpty) return;

    setState(() {
      _isLoading = true;
      _error = null;
      _successMessage = null;
      _patient = null;
      _abhaLink = null;
      _consents = const [];
      _careContexts = const [];
    });

    try {
      final apiClient = context.read<ApiClient>();
      final patient = await apiClient.get<PatientResponse>(
        '/api/v1/patients/uhid/$uhid',
        fromJson: (json) =>
            PatientResponse.fromJson(json as Map<String, dynamic>),
      );
      setState(() => _patient = patient);
      await _refreshAbdmData();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Lookup failed: $e');
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  /// Loads ABHA link + consents + care contexts for the selected patient.
  Future<void> _refreshAbdmData() async {
    final patient = _patient;
    if (patient == null) return;
    final apiClient = context.read<ApiClient>();

    try {
      final link = await apiClient.get<AbhaLinkResponse>(
        '/api/v1/abdm/abha/patient/${patient.id}',
        fromJson: (json) =>
            AbhaLinkResponse.fromJson(json as Map<String, dynamic>),
      );
      setState(() => _abhaLink = link);
    } on ApiException catch (e) {
      if (e.error.error == 'ABHA_NOT_LINKED') {
        setState(() => _abhaLink = null); // expected state, not an error
      } else if (e.error.error == 'ABDM_DISABLED') {
        setState(() => _error = e.error.message);
        return;
      } else {
        setState(() => _error = e.error.message);
      }
    }

    // Consents and care contexts only matter when an ABHA is linked.
    if (_abhaLink == null) return;
    try {
      final consents = await apiClient.get<List<ConsentResponse>>(
        '/api/v1/abdm/consents/patient/${patient.id}',
        fromJson: (json) => (json as List)
            .map((e) => ConsentResponse.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
      final careContexts = await apiClient.get<List<CareContextResponse>>(
        '/api/v1/abdm/care-contexts/patient/${patient.id}',
        fromJson: (json) => (json as List)
            .map((e) =>
                CareContextResponse.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
      setState(() {
        _consents = consents;
        _careContexts = careContexts;
      });
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    }
  }

  // ------------------------------------------------------------
  // actions
  // ------------------------------------------------------------

  Future<void> _linkAbha() async {
    final patient = _patient;
    if (patient == null) return;
    if (_abhaNumberCtrl.text.trim().isEmpty) {
      setState(() => _error = 'ABHA number is required');
      return;
    }

    setState(() {
      _isLoading = true;
      _error = null;
      _successMessage = null;
    });

    try {
      final apiClient = context.read<ApiClient>();
      final link = await apiClient.post<AbhaLinkResponse>(
        '/api/v1/abdm/abha/link',
        LinkAbhaRequest(
          patientId: patient.id,
          abhaNumber: _abhaNumberCtrl.text.trim(),
          abhaAddress: _abhaAddressCtrl.text.trim(),
          verificationMethod: _verificationMethod,
        ),
        fromJson: (json) =>
            AbhaLinkResponse.fromJson(json as Map<String, dynamic>),
      );
      setState(() {
        _abhaLink = link;
        _successMessage = 'ABHA ${link.abhaNumber} linked to ${patient.fullName}';
        _abhaNumberCtrl.clear();
        _abhaAddressCtrl.clear();
      });
      await _refreshAbdmData();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Linking failed: $e');
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  Future<void> _unlinkAbha() async {
    final patient = _patient;
    if (patient == null) return;

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Unlink ABHA?'),
        content: Text(
            'Remove the ABHA link for ${patient.fullName}? New care episodes '
            'will no longer be shareable on the ABDM network.'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('Cancel')),
          FilledButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('Unlink')),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;

    setState(() {
      _isLoading = true;
      _error = null;
      _successMessage = null;
    });

    try {
      final apiClient = context.read<ApiClient>();
      await apiClient.delete<AbhaLinkResponse>(
        '/api/v1/abdm/abha/patient/${patient.id}',
        fromJson: (json) =>
            AbhaLinkResponse.fromJson(json as Map<String, dynamic>),
      );
      setState(() {
        _abhaLink = null;
        _consents = const [];
        _careContexts = const [];
        _successMessage = 'ABHA unlinked from ${patient.fullName}';
      });
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  Future<void> _recordConsent() async {
    final patient = _patient;
    if (patient == null) return;

    final request = await showDialog<RecordConsentRequest>(
      context: context,
      builder: (ctx) => _ConsentDialog(patientId: patient.id),
    );
    if (request == null || !mounted) return;

    setState(() {
      _isLoading = true;
      _error = null;
      _successMessage = null;
    });

    try {
      final apiClient = context.read<ApiClient>();
      await apiClient.post<ConsentResponse>(
        '/api/v1/abdm/consents',
        request,
        fromJson: (json) =>
            ConsentResponse.fromJson(json as Map<String, dynamic>),
      );
      setState(() => _successMessage = 'Consent recorded');
      await _refreshAbdmData();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  Future<void> _revokeConsent(ConsentResponse consent) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Revoke consent?'),
        content: Text('Revoke consent ${consent.artifactId.substring(0, 8)}… '
            'covering ${consent.hiTypes}?'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('Cancel')),
          FilledButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('Revoke')),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;

    setState(() {
      _isLoading = true;
      _error = null;
    });

    try {
      final apiClient = context.read<ApiClient>();
      await apiClient.post<ConsentResponse>(
        '/api/v1/abdm/consents/${consent.id}/revoke',
        const <String, dynamic>{},
        fromJson: (json) =>
            ConsentResponse.fromJson(json as Map<String, dynamic>),
      );
      setState(() => _successMessage = 'Consent revoked');
      await _refreshAbdmData();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  // ------------------------------------------------------------
  // build
  // ------------------------------------------------------------

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return PageContainer(
      child: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('ABHA / ABDM', style: theme.textTheme.titleLarge),
            const SizedBox(height: Space.md),
            if (_error != null) ...[
              MessageBanner.error(_error!),
              const SizedBox(height: Space.md),
            ],
            if (_successMessage != null) ...[
              MessageBanner.success(_successMessage!),
              const SizedBox(height: Space.md),
            ],
            _buildLookupCard(theme),
            if (_patient != null) ...[
              const SizedBox(height: Space.lg),
              _buildAbhaCard(theme),
              if (_abhaLink != null) ...[
                const SizedBox(height: Space.lg),
                _buildConsentsCard(theme),
                const SizedBox(height: Space.lg),
                _buildCareContextsCard(theme),
              ],
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildLookupCard(ThemeData theme) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(Space.lg),
        child: Row(
          children: [
            Expanded(
              child: TextField(
                controller: _uhidCtrl,
                enabled: !_isLoading,
                decoration: const InputDecoration(
                  labelText: 'Patient UHID',
                  prefixIcon: Icon(Icons.badge_outlined),
                ),
                onSubmitted: (_) => _lookupPatient(),
              ),
            ),
            const SizedBox(width: Space.md),
            SizedBox(
              height: Metrics.buttonHeight,
              child: FilledButton.icon(
                onPressed: _isLoading ? null : _lookupPatient,
                icon: const Icon(Icons.search),
                label: const Text('Find'),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildAbhaCard(ThemeData theme) {
    final patient = _patient!;
    final link = _abhaLink;

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(Space.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(patient.fullName,
                      style: theme.textTheme.titleMedium),
                ),
                StatusChip(
                  link == null ? 'NOT LINKED' : link.linkStatus,
                  kind:
                      link == null ? StatusKind.warning : StatusKind.success,
                ),
              ],
            ),
            Text(
                '${patient.uhid} · ${patient.gender ?? ''} · ${patient.mobile}',
                style: theme.textTheme.bodySmall),
            const SizedBox(height: Space.lg),
            if (link != null) ...[
              _InfoRow(label: 'ABHA Number', value: link.abhaNumber),
              if (link.abhaAddress != null)
                _InfoRow(label: 'ABHA Address', value: link.abhaAddress!),
              _InfoRow(label: 'Verified Via', value: link.verificationMethod),
              const SizedBox(height: Space.md),
              OutlinedButton.icon(
                onPressed: _isLoading ? null : _unlinkAbha,
                icon: const Icon(Icons.link_off),
                label: const Text('Unlink ABHA'),
              ),
            ] else ...[
              Row(
                children: [
                  Expanded(
                    flex: 2,
                    child: TextField(
                      controller: _abhaNumberCtrl,
                      enabled: !_isLoading,
                      decoration: const InputDecoration(
                        labelText: 'ABHA Number *',
                        hintText: '91-1112-2223-3330',
                      ),
                    ),
                  ),
                  const SizedBox(width: Space.md),
                  Expanded(
                    flex: 2,
                    child: TextField(
                      controller: _abhaAddressCtrl,
                      enabled: !_isLoading,
                      decoration: const InputDecoration(
                        labelText: 'ABHA Address',
                        hintText: 'name@abdm',
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: Space.md),
              Row(
                children: [
                  Expanded(
                    child: DropdownButtonFormField<String>(
                      value: _verificationMethod,
                      decoration: const InputDecoration(
                          labelText: 'Verification Method'),
                      items: const [
                        DropdownMenuItem(
                            value: 'AADHAAR_OTP',
                            child: Text('Aadhaar OTP')),
                        DropdownMenuItem(
                            value: 'MOBILE_OTP', child: Text('Mobile OTP')),
                        DropdownMenuItem(
                            value: 'DEMOGRAPHICS',
                            child: Text('Demographics')),
                      ],
                      onChanged: _isLoading
                          ? null
                          : (v) => setState(
                              () => _verificationMethod = v ?? 'DEMOGRAPHICS'),
                    ),
                  ),
                  const SizedBox(width: Space.md),
                  SizedBox(
                    height: Metrics.buttonHeight,
                    child: FilledButton.icon(
                      onPressed: _isLoading ? null : _linkAbha,
                      icon: const Icon(Icons.link),
                      label: const Text('Link ABHA'),
                    ),
                  ),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildConsentsCard(ThemeData theme) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(Space.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text('Data-Sharing Consents',
                      style: theme.textTheme.titleMedium),
                ),
                FilledButton.tonalIcon(
                  onPressed: _isLoading ? null : _recordConsent,
                  icon: const Icon(Icons.add),
                  label: const Text('Record Consent'),
                ),
              ],
            ),
            const SizedBox(height: Space.md),
            if (_consents.isEmpty)
              Text('No consents recorded.', style: theme.textTheme.bodySmall)
            else
              ..._consents.map((c) => ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: Icon(
                      c.consentStatus == 'GRANTED'
                          ? Icons.verified_user_outlined
                          : Icons.gpp_bad_outlined,
                      color: c.consentStatus == 'GRANTED'
                          ? StatusColors.success
                          : StatusColors.danger,
                    ),
                    title: Text(c.hiTypes,
                        style: theme.textTheme.bodyMedium),
                    subtitle: Text(
                        '${c.purposeCode} · expires ${_date(c.expiresAt)}',
                        style: theme.textTheme.bodySmall),
                    trailing: c.consentStatus == 'GRANTED'
                        ? TextButton(
                            onPressed: _isLoading
                                ? null
                                : () => _revokeConsent(c),
                            child: const Text('Revoke'),
                          )
                        : StatusChip(c.consentStatus,
                            kind: StatusKind.danger),
                  )),
          ],
        ),
      ),
    );
  }

  Widget _buildCareContextsCard(ThemeData theme) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(Space.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Care Contexts (shared episodes)',
                style: theme.textTheme.titleMedium),
            const SizedBox(height: Space.md),
            if (_careContexts.isEmpty)
              Text('No care contexts yet — they are created from completed '
                  'visits and admissions.',
                  style: theme.textTheme.bodySmall)
            else
              ..._careContexts.map((c) => ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: Icon(
                        c.sourceType == 'OPD_VISIT'
                            ? Icons.medical_services_outlined
                            : Icons.local_hospital_outlined),
                    title: Text(c.displayName,
                        style: theme.textTheme.bodyMedium),
                    subtitle: Text(c.careContextReference,
                        style: theme.textTheme.bodySmall),
                    trailing: StatusChip(
                      c.linkStatus.replaceAll('_', ' '),
                      kind: switch (c.linkStatus) {
                        'LINKED' => StatusKind.success,
                        'FAILED' => StatusKind.danger,
                        _ => StatusKind.warning,
                      },
                    ),
                  )),
          ],
        ),
      ),
    );
  }

  static String _date(String? isoDateTime) =>
      isoDateTime == null ? '-' : isoDateTime.split('T').first;
}

class _InfoRow extends StatelessWidget {
  const _InfoRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.only(bottom: Space.xs),
      child: Row(
        children: [
          SizedBox(
            width: 140,
            child: Text(label, style: theme.textTheme.bodySmall),
          ),
          Text(value, style: theme.textTheme.bodyMedium),
        ],
      ),
    );
  }
}

/// Dialog collecting a new consent: HI types, data period, expiry.
class _ConsentDialog extends StatefulWidget {
  const _ConsentDialog({required this.patientId});

  final int patientId;

  @override
  State<_ConsentDialog> createState() => _ConsentDialogState();
}

class _ConsentDialogState extends State<_ConsentDialog> {
  static const _allHiTypes = [
    'Prescription',
    'DiagnosticReport',
    'OPConsultation',
    'DischargeSummary',
  ];

  final Set<String> _selected = {'Prescription'};
  DateTime _dataFrom = DateTime.now().subtract(const Duration(days: 365));
  DateTime _dataTo = DateTime.now();
  DateTime _expiresAt = DateTime.now().add(const Duration(days: 180));
  String? _validation;

  Future<void> _pick(DateTime initial, ValueChanged<DateTime> assign) async {
    final picked = await showDatePicker(
      context: context,
      initialDate: initial,
      firstDate: DateTime(2000),
      lastDate: DateTime.now().add(const Duration(days: 365 * 5)),
    );
    if (picked != null) setState(() => assign(picked));
  }

  String _fmt(DateTime d) =>
      '${d.year}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

  void _submit() {
    if (_selected.isEmpty) {
      setState(() => _validation = 'Select at least one record type');
      return;
    }
    if (!_dataTo.isAfter(_dataFrom)) {
      setState(() => _validation = 'Data period end must be after start');
      return;
    }
    if (!_expiresAt.isAfter(DateTime.now())) {
      setState(() => _validation = 'Expiry must be in the future');
      return;
    }
    Navigator.pop(
      context,
      RecordConsentRequest(
        patientId: widget.patientId,
        hiTypes: _selected.toList(),
        dataFrom: '${_fmt(_dataFrom)}T00:00:00',
        dataTo: '${_fmt(_dataTo)}T23:59:59',
        expiresAt: '${_fmt(_expiresAt)}T23:59:59',
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return AlertDialog(
      title: const Text('Record Data-Sharing Consent'),
      content: SizedBox(
        width: 420,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (_validation != null) ...[
              MessageBanner.error(_validation!),
              const SizedBox(height: Space.md),
            ],
            Text('Record types', style: theme.textTheme.labelLarge),
            Wrap(
              spacing: Space.sm,
              children: _allHiTypes
                  .map((t) => FilterChip(
                        label: Text(t),
                        selected: _selected.contains(t),
                        onSelected: (sel) => setState(() =>
                            sel ? _selected.add(t) : _selected.remove(t)),
                      ))
                  .toList(),
            ),
            const SizedBox(height: Space.md),
            ListTile(
              contentPadding: EdgeInsets.zero,
              title: Text('Data period from: ${_fmt(_dataFrom)}'),
              trailing: const Icon(Icons.calendar_today_outlined),
              onTap: () => _pick(_dataFrom, (d) => _dataFrom = d),
            ),
            ListTile(
              contentPadding: EdgeInsets.zero,
              title: Text('Data period to: ${_fmt(_dataTo)}'),
              trailing: const Icon(Icons.calendar_today_outlined),
              onTap: () => _pick(_dataTo, (d) => _dataTo = d),
            ),
            ListTile(
              contentPadding: EdgeInsets.zero,
              title: Text('Consent expires: ${_fmt(_expiresAt)}'),
              trailing: const Icon(Icons.calendar_today_outlined),
              onTap: () => _pick(_expiresAt, (d) => _expiresAt = d),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel')),
        FilledButton(onPressed: _submit, child: const Text('Record')),
      ],
    );
  }
}
