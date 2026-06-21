import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/section_card.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// ABHA (ABDM Health ID) panel for a patient. The "Record / Link" path works
/// today (stores a QR-captured or known ABHA without a gateway round-trip); the
/// Aadhaar-OTP "Create" path lights up once the real ABDM gateway client is
/// wired. All endpoints are gated server-side by the `abdm.enabled` policy.
class AbhaPanel extends StatefulWidget {
  const AbhaPanel({super.key, required this.patientId});

  final int patientId;

  @override
  State<AbhaPanel> createState() => _AbhaPanelState();
}

class _AbhaPanelState extends State<AbhaPanel> {
  Map<String, dynamic> _abha = const {};
  bool _loading = false;
  String? _error;
  String? _info;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _load());
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      final v = await api.get<Map<String, dynamic>>(
        '/api/v1/abdm/abha/patient/${widget.patientId}',
        fromJson: (json) => Map<String, dynamic>.from(json as Map? ?? const {}),
      );
      if (mounted) setState(() => _abha = v);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _act(Future<void> Function() action, String okMsg) async {
    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });
    try {
      await action();
      setState(() => _info = okMsg);
      await _load();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Failed: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  // ---- Record / link by typing the ABHA (works without the gateway) ----
  Future<void> _recordDialog() async {
    final number = TextEditingController();
    final address = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Record / link ABHA'),
        content: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: number,
                decoration: const InputDecoration(
                    labelText: 'ABHA number', hintText: '14-digit'),
              ),
              const SizedBox(height: Space.sm),
              TextField(
                controller: address,
                decoration: const InputDecoration(
                    labelText: 'ABHA address', hintText: 'name@abdm'),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Save')),
        ],
      ),
    );
    if (ok != true) return;
    await _act(() async {
      final api = context.read<ApiClient>();
      await api.post<dynamic>('/api/v1/abdm/abha/record', {
        'patientId': widget.patientId,
        'abhaNumber': number.text.trim(),
        'abhaAddress': address.text.trim(),
      }, fromJson: (j) => j);
    }, 'ABHA recorded');
  }

  // ---- Create via Aadhaar OTP (needs the real gateway client) ----
  Future<void> _createDialog() async {
    final aadhaar = TextEditingController();
    final otp = TextEditingController();
    final mobile = TextEditingController();
    String? txnId;
    String? localError;
    bool busy = false;

    await showDialog<void>(
      context: context,
      builder: (dialogCtx) => StatefulBuilder(
        builder: (dialogCtx, setLocal) {
          Future<void> sendOtp() async {
            setLocal(() { busy = true; localError = null; });
            try {
              final api = context.read<ApiClient>();
              final res = await api.post<Map<String, dynamic>>(
                '/api/v1/abdm/abha/enroll/aadhaar/otp',
                {'patientId': widget.patientId, 'aadhaar': aadhaar.text.trim()},
                fromJson: (j) => Map<String, dynamic>.from(j as Map),
              );
              setLocal(() => txnId = '${res['txnId'] ?? ''}');
            } on ApiException catch (e) {
              setLocal(() => localError = e.error.message);
            } catch (e) {
              setLocal(() => localError = 'Failed: $e');
            } finally {
              setLocal(() => busy = false);
            }
          }

          Future<void> verify() async {
            setLocal(() { busy = true; localError = null; });
            try {
              final api = context.read<ApiClient>();
              await api.post<dynamic>(
                '/api/v1/abdm/abha/enroll/aadhaar/verify',
                {
                  'patientId': widget.patientId,
                  'txnId': txnId,
                  'otp': otp.text.trim(),
                  'mobile': mobile.text.trim(),
                },
                fromJson: (j) => j,
              );
              if (dialogCtx.mounted) Navigator.pop(dialogCtx);
              setState(() => _info = 'ABHA created');
              await _load();
            } on ApiException catch (e) {
              setLocal(() => localError = e.error.message);
            } catch (e) {
              setLocal(() => localError = 'Failed: $e');
            } finally {
              setLocal(() => busy = false);
            }
          }

          return AlertDialog(
            title: const Text('Create ABHA (Aadhaar OTP)'),
            content: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (localError != null) ...[
                    Text(localError!,
                        style: TextStyle(color: Theme.of(dialogCtx).colorScheme.error)),
                    const SizedBox(height: Space.sm),
                  ],
                  TextField(
                    controller: aadhaar,
                    keyboardType: TextInputType.number,
                    enabled: txnId == null,
                    decoration: const InputDecoration(labelText: 'Aadhaar number'),
                  ),
                  if (txnId != null) ...[
                    const SizedBox(height: Space.sm),
                    TextField(
                      controller: otp,
                      keyboardType: TextInputType.number,
                      decoration: const InputDecoration(labelText: 'OTP'),
                    ),
                    const SizedBox(height: Space.sm),
                    TextField(
                      controller: mobile,
                      keyboardType: TextInputType.phone,
                      decoration: const InputDecoration(
                          labelText: 'Mobile (for ABHA)', prefixText: '+91 '),
                    ),
                  ],
                ],
              ),
            ),
            actions: [
              TextButton(
                  onPressed: busy ? null : () => Navigator.pop(dialogCtx),
                  child: const Text('Cancel')),
              FilledButton(
                onPressed: busy ? null : (txnId == null ? sendOtp : verify),
                child: Text(busy ? '…' : (txnId == null ? 'Send OTP' : 'Verify & create')),
              ),
            ],
          );
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final number = _abha['abhaNumber'];
    final address = _abha['abhaAddress'];
    final has = (number != null && '$number'.isNotEmpty) ||
        (address != null && '$address'.isNotEmpty);
    return SectionCard(
      title: 'ABHA (ABDM Health ID)',
      icon: Icons.badge_outlined,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (_error != null) ...[
            MessageBanner.error(_error!),
            const SizedBox(height: Space.sm),
          ],
          if (_info != null) ...[
            MessageBanner.success(_info!),
            const SizedBox(height: Space.sm),
          ],
          if (has)
            Text(
              'Number: ${number ?? '—'}   ·   Address: ${address ?? '—'}',
              style: theme.textTheme.bodyMedium,
            )
          else
            Text(_loading ? 'Loading…' : 'No ABHA linked for this patient.',
                style: theme.textTheme.bodySmall
                    ?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
          const SizedBox(height: Space.sm),
          Wrap(
            spacing: Space.sm,
            children: [
              OutlinedButton.icon(
                onPressed: _loading ? null : _recordDialog,
                icon: const Icon(Icons.qr_code_2_outlined, size: 18),
                label: const Text('Record / link ABHA'),
              ),
              OutlinedButton.icon(
                onPressed: _loading ? null : _createDialog,
                icon: const Icon(Icons.add_card_outlined, size: 18),
                label: const Text('Create (Aadhaar OTP)'),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
