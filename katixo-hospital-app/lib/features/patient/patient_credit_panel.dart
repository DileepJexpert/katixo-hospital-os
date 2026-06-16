import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/section_card.dart';
import '../../core/widgets/status_chip.dart';

/// Patient credit (prepaid-balance) account panel: balance / limit / status,
/// admin actions (adjust, set limit, change status) and recent transactions.
/// Self-loads from `/api/v1/patients/{id}/credit`. Role-gated by the caller —
/// reads need BILLING/DOCTOR/ADMIN; mutations need BILLING/ADMIN (ADMIN-only
/// for limit + status), enforced again server-side.
class PatientCreditPanel extends StatefulWidget {
  const PatientCreditPanel({super.key, required this.patientId, required this.role});

  final int patientId;
  final String role;

  @override
  State<PatientCreditPanel> createState() => _PatientCreditPanelState();
}

class _PatientCreditPanelState extends State<PatientCreditPanel> {
  Map<String, dynamic>? _account;
  List<Map<String, dynamic>> _txns = const [];
  bool _loading = false;
  bool _noAccount = false;
  String? _error;
  String? _info;

  bool get _isAdmin => widget.role == 'ADMIN' || widget.role == 'SUPER_ADMIN';
  bool get _isBilling =>
      widget.role == 'BILLING' || _isAdmin;

  @override
  void initState() {
    super.initState();
    _load();
  }

  String get _base => '/api/v1/patients/${widget.patientId}/credit';

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      final acct = await api.get<Map<String, dynamic>>(
        _base,
        fromJson: (j) => j as Map<String, dynamic>,
      );
      List<Map<String, dynamic>> txns = const [];
      if (_isBilling) {
        final page = await api.get<Map<String, dynamic>>(
          '$_base/transactions?page=0&size=20',
          fromJson: (j) => j as Map<String, dynamic>,
        );
        txns = List<Map<String, dynamic>>.from(page['content'] as List? ?? const []);
      }
      if (mounted) {
        setState(() {
          _account = acct;
          _txns = txns;
          _noAccount = false;
        });
      }
    } on ApiException catch (e) {
      // A patient created outside registration may not have an account yet.
      if (e.error.error == 'CREDIT_ACCOUNT_NOT_FOUND') {
        setState(() {
          _account = null;
          _noAccount = true;
        });
      } else {
        setState(() => _error = e.error.message);
      }
    } catch (e) {
      setState(() => _error = 'Could not load credit account: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _open() async {
    await _mutate(() async {
      final api = context.read<ApiClient>();
      await api.post<dynamic>(_base, const <String, dynamic>{}, fromJson: (j) => j);
    }, 'Credit account opened');
  }

  Future<void> _mutate(Future<void> Function() action, String okMsg) async {
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
      setState(() => _error = 'Action failed: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _adjustDialog() async {
    final amountCtrl = TextEditingController();
    final reasonCtrl = TextEditingController();
    final proceed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Adjust credit balance'),
        content: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text('Positive adds credit, negative deducts.'),
              const SizedBox(height: Space.sm),
              TextField(
                controller: amountCtrl,
                keyboardType: const TextInputType.numberWithOptions(signed: true, decimal: true),
                decoration: const InputDecoration(labelText: 'Amount (₹) *'),
              ),
              const SizedBox(height: Space.sm),
              TextField(
                controller: reasonCtrl,
                decoration: const InputDecoration(labelText: 'Reason *'),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Apply')),
        ],
      ),
    );
    if (proceed != true) return;
    final amount = double.tryParse(amountCtrl.text.trim());
    if (amount == null || reasonCtrl.text.trim().isEmpty) {
      setState(() => _error = 'A valid amount and reason are required');
      return;
    }
    await _mutate(() async {
      final api = context.read<ApiClient>();
      await api.post<dynamic>('$_base/adjust',
          {'amount': amount, 'reason': reasonCtrl.text.trim()}, fromJson: (j) => j);
    }, 'Balance adjusted');
  }

  Future<void> _limitDialog() async {
    final limitCtrl = TextEditingController(
        text: '${_account?['creditLimit'] ?? ''}'.replaceAll('null', ''));
    final proceed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Set credit limit'),
        content: TextField(
          controller: limitCtrl,
          keyboardType: TextInputType.number,
          decoration: const InputDecoration(labelText: 'Credit limit (₹)'),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Save')),
        ],
      ),
    );
    if (proceed != true) return;
    final limit = double.tryParse(limitCtrl.text.trim());
    if (limit == null || limit < 0) {
      setState(() => _error = 'Enter a non-negative limit');
      return;
    }
    await _mutate(() async {
      final api = context.read<ApiClient>();
      await api.put<dynamic>('$_base/limit', {'creditLimit': limit}, fromJson: (j) => j);
    }, 'Credit limit updated');
  }

  Future<void> _statusDialog() async {
    String status = '${_account?['status'] ?? 'ACTIVE'}';
    final proceed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Change account status'),
        content: StatefulBuilder(
          builder: (context, setLocal) => DropdownButtonFormField<String>(
            value: status,
            decoration: const InputDecoration(labelText: 'Status'),
            items: const [
              DropdownMenuItem(value: 'ACTIVE', child: Text('Active')),
              DropdownMenuItem(value: 'SUSPENDED', child: Text('Suspended')),
              DropdownMenuItem(value: 'BLOCKED', child: Text('Blocked')),
            ],
            onChanged: (v) => setLocal(() => status = v ?? 'ACTIVE'),
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Save')),
        ],
      ),
    );
    if (proceed != true) return;
    await _mutate(() async {
      final api = context.read<ApiClient>();
      await api.put<dynamic>('$_base/status', {'status': status}, fromJson: (j) => j);
    }, 'Status updated to $status');
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return SectionCard(
      title: 'Credit account',
      icon: Icons.account_balance_wallet_outlined,
      action: IconButton(
        tooltip: 'Refresh',
        onPressed: _loading ? null : _load,
        icon: const Icon(Icons.refresh, size: 20),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (_error != null) ...[
            Text(_error!, style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.error)),
            const SizedBox(height: Space.sm),
          ],
          if (_info != null) ...[
            Text(_info!, style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.primary)),
            const SizedBox(height: Space.sm),
          ],
          if (_noAccount)
            Row(
              children: [
                Expanded(
                  child: Text('No credit account for this patient yet.',
                      style: theme.textTheme.bodyMedium
                          ?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
                ),
                if (_isBilling)
                  FilledButton(
                    onPressed: _loading ? null : _open,
                    child: const Text('Open account'),
                  ),
              ],
            )
          else if (_account != null) ...[
            Wrap(
              spacing: Space.xl,
              runSpacing: Space.md,
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                _metric(theme, 'Available balance', '₹${_account!['availableBalance'] ?? 0}'),
                _metric(theme, 'Credit limit', '₹${_account!['creditLimit'] ?? 0}'),
                _metric(theme, 'Total credited', '₹${_account!['totalCredited'] ?? 0}'),
                _metric(theme, 'Total debited', '₹${_account!['totalDebited'] ?? 0}'),
                StatusChip.auto('${_account!['status'] ?? 'ACTIVE'}'),
              ],
            ),
            const SizedBox(height: Space.md),
            Wrap(
              spacing: Space.sm,
              runSpacing: Space.sm,
              children: [
                if (_isBilling)
                  OutlinedButton.icon(
                    onPressed: _loading ? null : _adjustDialog,
                    icon: const Icon(Icons.add_card_outlined, size: 18),
                    label: const Text('Adjust'),
                  ),
                if (_isAdmin)
                  OutlinedButton.icon(
                    onPressed: _loading ? null : _limitDialog,
                    icon: const Icon(Icons.speed_outlined, size: 18),
                    label: const Text('Set limit'),
                  ),
                if (_isAdmin)
                  OutlinedButton.icon(
                    onPressed: _loading ? null : _statusDialog,
                    icon: const Icon(Icons.toggle_on_outlined, size: 18),
                    label: const Text('Status'),
                  ),
              ],
            ),
            if (_isBilling) ...[
              const SizedBox(height: Space.md),
              const Divider(),
              Text('Recent transactions', style: theme.textTheme.titleSmall),
              const SizedBox(height: Space.xs),
              if (_txns.isEmpty)
                Text('No transactions yet',
                    style: theme.textTheme.bodySmall
                        ?.copyWith(color: theme.colorScheme.onSurfaceVariant))
              else
                for (final t in _txns)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: Space.xxs),
                    child: Row(
                      children: [
                        Expanded(
                          child: Text(
                            '${t['transactionType']} · ${t['description'] ?? t['sourceRef'] ?? ''}',
                            style: theme.textTheme.bodySmall,
                          ),
                        ),
                        Text('₹${t['amount']}', style: theme.textTheme.bodySmall),
                        const SizedBox(width: Space.sm),
                        Text('bal ₹${t['balanceAfter']}',
                            style: theme.textTheme.bodySmall
                                ?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
                      ],
                    ),
                  ),
            ],
          ] else if (_loading)
            const Center(child: Padding(padding: EdgeInsets.all(Space.md), child: CircularProgressIndicator())),
        ],
      ),
    );
  }

  Widget _metric(ThemeData theme, String label, String value) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label,
            style: theme.textTheme.bodySmall
                ?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
        Text(value, style: theme.textTheme.titleMedium),
      ],
    );
  }
}
