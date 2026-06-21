import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/section_card.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// ABDM operator console — HIP (share records), HIU (fetch records), and NHCX
/// (electronic claims). These are manual/testing tools; once the live gateway is
/// wired the real flows are event-driven (care-context link on visit close,
/// data push on consent callback). All endpoints are gated by `abdm.enabled` and
/// return `*_NOT_CONFIGURED` until the gateway transport beans are added.
class AbdmConsoleScreen extends StatefulWidget {
  const AbdmConsoleScreen({super.key});

  @override
  State<AbdmConsoleScreen> createState() => _AbdmConsoleScreenState();
}

class _AbdmConsoleScreenState extends State<AbdmConsoleScreen> {
  int _tab = 0;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return PageContainer(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text('ABDM Exchange', style: theme.textTheme.titleLarge),
              const Spacer(),
              SegmentedButton<int>(
                segments: const [
                  ButtonSegment(value: 0, label: Text('HIP · Share')),
                  ButtonSegment(value: 1, label: Text('HIU · Fetch')),
                  ButtonSegment(value: 2, label: Text('NHCX · Claims')),
                ],
                selected: {_tab},
                onSelectionChanged: (s) => setState(() => _tab = s.first),
              ),
            ],
          ),
          const SizedBox(height: Space.md),
          const _GatewayNote(),
          const SizedBox(height: Space.md),
          IndexedStack(
            index: _tab,
            children: const [_HipPanel(), _HiuPanel(), _NhcxPanel()],
          ),
        ],
      ),
    );
  }
}

class _GatewayNote extends StatelessWidget {
  const _GatewayNote();
  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(Space.sm),
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(Corners.md),
      ),
      child: Text(
        'These tools build real FHIR + encryption locally. The transmit step '
        'returns "gateway not configured" until your ABDM sandbox credentials '
        'and the gateway transport are wired.',
        style: theme.textTheme.bodySmall
            ?.copyWith(color: theme.colorScheme.onSurfaceVariant),
      ),
    );
  }
}

// ---------- shared helpers ----------

Widget _field(TextEditingController c, String label,
    {TextInputType? keyboard, String? hint}) {
  return Padding(
    padding: const EdgeInsets.only(bottom: Space.sm),
    child: TextField(
      controller: c,
      keyboardType: keyboard,
      decoration: InputDecoration(labelText: label, hintText: hint),
    ),
  );
}

/// Posts [body] to [path]; returns (error, dataText). On success error is null.
Future<({String? error, String data})> _submit(
    BuildContext context, String path, Map<String, dynamic> body) async {
  try {
    final api = context.read<ApiClient>();
    final res = await api.post<Map<String, dynamic>>(
      path,
      body,
      fromJson: (j) => Map<String, dynamic>.from(j as Map? ?? const {}),
    );
    return (error: null, data: res.toString());
  } on ApiException catch (e) {
    return (error: e.error.message, data: '');
  } catch (e) {
    return (error: 'Failed: $e', data: '');
  }
}

// ---------- HIP ----------

class _HipPanel extends StatefulWidget {
  const _HipPanel();
  @override
  State<_HipPanel> createState() => _HipPanelState();
}

class _HipPanelState extends State<_HipPanel> {
  final _linkPatientId = TextEditingController();
  final _linkDisplay = TextEditingController();
  final _ccRef = TextEditingController();
  final _ccDisplay = TextEditingController();

  final _pPatientId = TextEditingController();
  final _pPatientName = TextEditingController();
  final _pGender = TextEditingController();
  final _pDoctor = TextEditingController();
  final _pTxn = TextEditingController();
  final _pCcRef = TextEditingController();
  final _pHiuKey = TextEditingController();
  final _pHiuNonce = TextEditingController();
  final _pPushUrl = TextEditingController();
  final _pMedName = TextEditingController();
  final _pMedDose = TextEditingController();
  String _hiType = 'Prescription';
  bool _busy = false;
  String? _error;
  String? _info;

  Future<void> _run(String path, Map<String, dynamic> body) async {
    setState(() { _busy = true; _error = null; _info = null; });
    final r = await _submit(context, path, body);
    if (!mounted) return;
    setState(() { _busy = false; _error = r.error; _info = r.error == null ? 'OK · ${r.data}' : null; });
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (_error != null) ...[MessageBanner.error(_error!), const SizedBox(height: Space.sm)],
        if (_info != null) ...[MessageBanner.success(_info!), const SizedBox(height: Space.sm)],
        SectionCard(
          title: 'Link care contexts',
          icon: Icons.link_outlined,
          child: Column(
            children: [
              _field(_linkPatientId, 'Patient ID', keyboard: TextInputType.number),
              _field(_linkDisplay, 'Patient display name'),
              _field(_ccRef, 'Care-context reference', hint: 'e.g. visit/admission id'),
              _field(_ccDisplay, 'Care-context display', hint: 'e.g. OPD visit 12 Jun'),
              Align(
                alignment: Alignment.centerRight,
                child: FilledButton(
                  onPressed: _busy ? null : () => _run('/api/v1/abdm/hip/care-contexts/link', {
                    'patientId': int.tryParse(_linkPatientId.text.trim()),
                    'patientDisplay': _linkDisplay.text.trim(),
                    'contexts': [
                      {'referenceNumber': _ccRef.text.trim(), 'display': _ccDisplay.text.trim()}
                    ],
                  }),
                  child: const Text('Link'),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: Space.md),
        SectionCard(
          title: 'Push health data (consent-backed)',
          icon: Icons.upload_outlined,
          child: Column(
            children: [
              _field(_pPatientId, 'Patient ID', keyboard: TextInputType.number),
              _field(_pPatientName, 'Patient name'),
              _field(_pGender, 'Gender', hint: 'male / female / other'),
              _field(_pDoctor, 'Practitioner name'),
              DropdownButtonFormField<String>(
                initialValue: _hiType,
                decoration: const InputDecoration(labelText: 'HI type'),
                items: const [
                  DropdownMenuItem(value: 'Prescription', child: Text('Prescription')),
                  DropdownMenuItem(value: 'DiagnosticReport', child: Text('Diagnostic Report')),
                ],
                onChanged: (v) => setState(() => _hiType = v ?? 'Prescription'),
              ),
              const SizedBox(height: Space.sm),
              _field(_pTxn, 'Transaction ID', hint: 'from the data request'),
              _field(_pCcRef, 'Care-context reference'),
              _field(_pPushUrl, 'HIU data-push URL'),
              _field(_pHiuKey, 'HIU public key (base64)'),
              _field(_pHiuNonce, 'HIU nonce (base64)'),
              if (_hiType == 'Prescription') ...[
                _field(_pMedName, 'Medicine name'),
                _field(_pMedDose, 'Dosage / frequency'),
              ],
              Align(
                alignment: Alignment.centerRight,
                child: FilledButton(
                  onPressed: _busy ? null : () => _run('/api/v1/abdm/hip/data/push', {
                    'patientId': int.tryParse(_pPatientId.text.trim()),
                    'patientName': _pPatientName.text.trim(),
                    'gender': _pGender.text.trim(),
                    'practitionerName': _pDoctor.text.trim(),
                    'transactionId': _pTxn.text.trim(),
                    'careContextReference': _pCcRef.text.trim(),
                    'hiType': _hiType,
                    'dataPushUrl': _pPushUrl.text.trim(),
                    'hiuPublicKeyBase64': _pHiuKey.text.trim(),
                    'hiuNonceBase64': _pHiuNonce.text.trim(),
                    'medications': _hiType == 'Prescription'
                        ? [
                            {'name': _pMedName.text.trim(), 'dosage': _pMedDose.text.trim()}
                          ]
                        : null,
                  }),
                  child: const Text('Push'),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

// ---------- HIU ----------

class _HiuPanel extends StatefulWidget {
  const _HiuPanel();
  @override
  State<_HiuPanel> createState() => _HiuPanelState();
}

class _HiuPanelState extends State<_HiuPanel> {
  final _patientId = TextEditingController();
  final _abha = TextEditingController();
  final _hiTypes = TextEditingController(text: 'Prescription,DiagnosticReport');
  final _from = TextEditingController();
  final _to = TextEditingController();
  final _purpose = TextEditingController(text: 'Care management');

  final _consentId = TextEditingController();
  final _dFrom = TextEditingController();
  final _dTo = TextEditingController();
  final _pushUrl = TextEditingController();
  bool _busy = false;
  String? _error;
  String? _info;

  Future<void> _run(String path, Map<String, dynamic> body) async {
    setState(() { _busy = true; _error = null; _info = null; });
    final r = await _submit(context, path, body);
    if (!mounted) return;
    setState(() { _busy = false; _error = r.error; _info = r.error == null ? 'OK · ${r.data}' : null; });
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (_error != null) ...[MessageBanner.error(_error!), const SizedBox(height: Space.sm)],
        if (_info != null) ...[MessageBanner.success(_info!), const SizedBox(height: Space.sm)],
        SectionCard(
          title: 'Request consent',
          icon: Icons.privacy_tip_outlined,
          child: Column(
            children: [
              _field(_patientId, 'Patient ID', keyboard: TextInputType.number),
              _field(_abha, 'Patient ABHA address', hint: 'name@abdm'),
              _field(_hiTypes, 'HI types (comma-separated)'),
              _field(_from, 'Date from', hint: 'YYYY-MM-DD'),
              _field(_to, 'Date to', hint: 'YYYY-MM-DD'),
              _field(_purpose, 'Purpose'),
              Align(
                alignment: Alignment.centerRight,
                child: FilledButton(
                  onPressed: _busy ? null : () => _run('/api/v1/abdm/hiu/consent/request', {
                    'patientId': int.tryParse(_patientId.text.trim()),
                    'abhaAddress': _abha.text.trim(),
                    'hiTypes': _hiTypes.text.split(',').map((e) => e.trim()).where((e) => e.isNotEmpty).toList(),
                    'dateFrom': _from.text.trim(),
                    'dateTo': _to.text.trim(),
                    'purposeText': _purpose.text.trim(),
                  }),
                  child: const Text('Request consent'),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: Space.md),
        SectionCard(
          title: 'Request data',
          icon: Icons.download_outlined,
          child: Column(
            children: [
              _field(_consentId, 'Consent artefact ID', hint: 'granted artefact'),
              _field(_dFrom, 'Date from', hint: 'YYYY-MM-DD'),
              _field(_dTo, 'Date to', hint: 'YYYY-MM-DD'),
              _field(_pushUrl, 'Our data-push URL (callback)'),
              Align(
                alignment: Alignment.centerRight,
                child: FilledButton(
                  onPressed: _busy ? null : () => _run('/api/v1/abdm/hiu/data/request', {
                    'consentArtefactId': _consentId.text.trim(),
                    'dateFrom': _dFrom.text.trim(),
                    'dateTo': _dTo.text.trim(),
                    'dataPushUrl': _pushUrl.text.trim(),
                  }),
                  child: const Text('Request data'),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

// ---------- NHCX ----------

class _NhcxPanel extends StatefulWidget {
  const _NhcxPanel();
  @override
  State<_NhcxPanel> createState() => _NhcxPanelState();
}

class _NhcxPanelState extends State<_NhcxPanel> {
  final _patientId = TextEditingController();
  final _patientName = TextEditingController();
  final _payer = TextEditingController();
  final _coverage = TextEditingController();
  final _total = TextEditingController();
  final _itemName = TextEditingController();
  final _itemAmount = TextEditingController();
  String _useCase = 'claim';
  bool _busy = false;
  String? _error;
  String? _info;

  Future<void> _run() async {
    setState(() { _busy = true; _error = null; _info = null; });
    final r = await _submit(context, '/api/v1/abdm/nhcx/claims', {
      'patientId': int.tryParse(_patientId.text.trim()),
      'patientName': _patientName.text.trim(),
      'payerCode': _payer.text.trim(),
      'coverageReference': _coverage.text.trim(),
      'totalAmount': double.tryParse(_total.text.trim()),
      'useCase': _useCase,
      'items': [
        {'name': _itemName.text.trim(), 'amount': double.tryParse(_itemAmount.text.trim())}
      ],
    });
    if (!mounted) return;
    setState(() { _busy = false; _error = r.error; _info = r.error == null ? 'OK · ${r.data}' : null; });
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (_error != null) ...[MessageBanner.error(_error!), const SizedBox(height: Space.sm)],
        if (_info != null) ...[MessageBanner.success(_info!), const SizedBox(height: Space.sm)],
        SectionCard(
          title: 'Submit claim / pre-authorization',
          icon: Icons.request_quote_outlined,
          child: Column(
            children: [
              DropdownButtonFormField<String>(
                initialValue: _useCase,
                decoration: const InputDecoration(labelText: 'Use case'),
                items: const [
                  DropdownMenuItem(value: 'claim', child: Text('Claim')),
                  DropdownMenuItem(value: 'preauth', child: Text('Pre-authorization')),
                ],
                onChanged: (v) => setState(() => _useCase = v ?? 'claim'),
              ),
              const SizedBox(height: Space.sm),
              _field(_patientId, 'Patient ID', keyboard: TextInputType.number),
              _field(_patientName, 'Patient name'),
              _field(_payer, 'Payer code', hint: 'insurer / TPA'),
              _field(_coverage, 'Coverage reference', hint: 'policy / scheme'),
              _field(_total, 'Total amount (₹)', keyboard: TextInputType.number),
              _field(_itemName, 'Line item'),
              _field(_itemAmount, 'Line amount (₹)', keyboard: TextInputType.number),
              Align(
                alignment: Alignment.centerRight,
                child: FilledButton(
                  onPressed: _busy ? null : _run,
                  child: const Text('Submit'),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}
