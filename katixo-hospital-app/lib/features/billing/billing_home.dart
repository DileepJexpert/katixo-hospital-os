import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/auth/auth_state.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/util/pdf_action.dart';
import '../../core/widgets/app_shell.dart';
import '../../core/widgets/status_chip.dart';
import '../expense/expense_screen.dart';
import '../tpa/tpa_screen.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// Billing role home: generate a bill for an OPD visit or IPD admission,
/// view the consolidated bill, request discount, finalize, show receipt.
class BillingHome extends StatefulWidget {
  const BillingHome({super.key});

  @override
  State<BillingHome> createState() => _BillingHomeState();
}

class _BillingHomeState extends State<BillingHome> {
  final _sourceIdCtrl = TextEditingController();
  String _sourceType = 'OPD_VISIT';

  Map<String, dynamic>? _consolidated;
  List<Map<String, dynamic>> _payments = const [];
  bool _loading = false;
  String? _error;
  String? _info;
  int _index = 0;

  int? get _billId =>
      (_consolidated?['bill'] as Map<String, dynamic>?)?['id'] as int?;

  @override
  void dispose() {
    _sourceIdCtrl.dispose();
    super.dispose();
  }

  Future<void> _generateBill() async {
    final sourceId = int.tryParse(_sourceIdCtrl.text.trim());
    if (sourceId == null) {
      setState(() => _error = 'Enter a valid visit/admission ID');
      return;
    }

    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });

    try {
      final api = context.read<ApiClient>();
      final bill = await api.post<Map<String, dynamic>>(
        '/api/v1/billing/bills',
        {'sourceType': _sourceType, 'sourceId': sourceId},
        fromJson: (json) => json as Map<String, dynamic>,
      );
      setState(() => _info = 'Bill ${bill['billNumber']} generated');
      await _loadConsolidated(bill['id'] as int);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Bill generation failed: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _loadConsolidated(int billId) async {
    try {
      final api = context.read<ApiClient>();
      final view = await api.get<Map<String, dynamic>>(
        '/api/v1/billing/bills/$billId',
        fromJson: (json) => json as Map<String, dynamic>,
      );
      final payments = await api.get<List<Map<String, dynamic>>>(
        '/api/v1/billing/bills/$billId/payments',
        fromJson: (json) =>
            List<Map<String, dynamic>>.from(json as List? ?? const []),
      );
      if (mounted) {
        setState(() {
          _consolidated = view;
          _payments = payments;
        });
      }
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    }
  }

  /// Collect a payment against the finalized bill. The backend books it
  /// in-process (DR Cash|Bank / CR Patient AR).
  Future<void> _paymentDialog() async {
    final bill = _consolidated?['bill'] as Map<String, dynamic>?;
    final billId = _billId;
    if (bill == null || billId == null) return;

    final balanceDue = '${bill['balanceDue'] ?? bill['netAmount']}';
    final amountCtrl = TextEditingController(text: balanceDue);
    final referenceCtrl = TextEditingController();
    var mode = 'CASH';

    final collect = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: const Text('Record Payment'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: amountCtrl,
                keyboardType: TextInputType.number,
                decoration: InputDecoration(
                  labelText: 'Amount (₹) *',
                  prefixText: '₹ ',
                  helperText: 'Balance due: ₹$balanceDue',
                ),
              ),
              const SizedBox(height: Space.md),
              DropdownButtonFormField<String>(
                value: mode,
                decoration: const InputDecoration(labelText: 'Payment mode *'),
                items: const [
                  DropdownMenuItem(value: 'CASH', child: Text('Cash')),
                  DropdownMenuItem(value: 'CARD', child: Text('Card')),
                  DropdownMenuItem(value: 'UPI', child: Text('UPI')),
                  DropdownMenuItem(value: 'CHEQUE', child: Text('Cheque')),
                  DropdownMenuItem(
                      value: 'BANK_TRANSFER', child: Text('Bank transfer')),
                ],
                onChanged: (v) => setDialogState(() => mode = v ?? 'CASH'),
              ),
              const SizedBox(height: Space.md),
              TextField(
                controller: referenceCtrl,
                decoration: const InputDecoration(
                    labelText: 'Reference (UPI/cheque no.)'),
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Cancel'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('Collect'),
            ),
          ],
        ),
      ),
    );

    if (collect != true) return;

    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      final payment = await api.post<Map<String, dynamic>>(
        '/api/v1/billing/bills/$billId/payments',
        {
          'amount': double.tryParse(amountCtrl.text) ?? 0,
          'paymentMode': mode,
          if (referenceCtrl.text.trim().isNotEmpty)
            'reference': referenceCtrl.text.trim(),
        },
        fromJson: (json) => json as Map<String, dynamic>,
      );
      setState(() =>
          _info = 'Payment of ₹${payment['amount']} recorded ($mode)');
      await _loadConsolidated(billId);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }


  Future<void> _discountDialog() async {
    final amountCtrl = TextEditingController();
    final reasonCtrl = TextEditingController();

    final apply = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Request Discount'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: amountCtrl,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(
                  labelText: 'Amount (₹) *', prefixText: '₹ '),
            ),
            const SizedBox(height: Space.md),
            TextField(
              controller: reasonCtrl,
              decoration: const InputDecoration(labelText: 'Reason *'),
            ),
            const SizedBox(height: Space.sm),
            const Text(
                'Above the policy threshold the discount needs admin approval.',
                style: TextStyle(fontSize: 12)),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Request'),
          ),
        ],
      ),
    );

    if (apply == true) {
      final billId = _billId;
      if (billId == null) return;
      setState(() {
        _loading = true;
        _error = null;
      });
      try {
        final api = context.read<ApiClient>();
        final bill = await api.post<Map<String, dynamic>>(
          '/api/v1/billing/bills/$billId/discount',
          {
            'amount': double.tryParse(amountCtrl.text) ?? 0,
            'reason': reasonCtrl.text.trim(),
          },
          fromJson: (json) => json as Map<String, dynamic>,
        );
        setState(() => _info = 'Discount ${bill['discountStatus']}');
        await _loadConsolidated(billId);
      } on ApiException catch (e) {
        setState(() => _error = e.error.message);
      } finally {
        if (mounted) setState(() => _loading = false);
      }
    }
  }

  Future<void> _finalize() async {
    final billId = _billId;
    if (billId == null) return;

    setState(() {
      _loading = true;
      _error = null;
    });

    try {
      final api = context.read<ApiClient>();
      await api.post<Map<String, dynamic>>(
        '/api/v1/billing/bills/$billId/finalize',
        const <String, dynamic>{},
        fromJson: (json) => json as Map<String, dynamic>,
      );
      setState(() => _info = 'Bill finalized');
      await _loadConsolidated(billId);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _showReceipt() async {
    final billId = _billId;
    if (billId == null) return;

    try {
      final api = context.read<ApiClient>();
      final receipt = await api.get<Map<String, dynamic>>(
        '/api/v1/billing/bills/$billId/receipt',
        fromJson: (json) => json as Map<String, dynamic>,
      );
      if (!mounted) return;
      await showDialog<void>(
        context: context,
        builder: (context) => _ReceiptDialog(receipt: receipt, billId: billId),
      );
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    }
  }

  @override
  Widget build(BuildContext context) {
    final authState = context.watch<AuthState>();
    final theme = Theme.of(context);

    return AppShell(
      title: 'Billing',
      destinations: const [
        ShellDestination(
          label: 'Bills',
          icon: Icons.receipt_long_outlined,
          selectedIcon: Icons.receipt_long,
        ),
        ShellDestination(
          label: 'Expenses',
          icon: Icons.receipt_outlined,
          selectedIcon: Icons.receipt,
        ),
        ShellDestination(
          label: 'TPA / Insurance',
          icon: Icons.health_and_safety_outlined,
          selectedIcon: Icons.health_and_safety,
        ),
      ],
      selectedIndex: _index,
      onDestinationSelected: (i) => setState(() => _index = i),
      actions: [
        if (authState.currentUser != null)
          Center(
            child: Padding(
              padding: const EdgeInsets.only(right: Space.sm),
              child: Text(authState.currentUser!.name,
                  style: theme.textTheme.labelLarge),
            ),
          ),
        IconButton(
          tooltip: 'Sign out',
          icon: const Icon(Icons.logout_outlined),
          onPressed: () => authState.logout(),
        ),
      ],
      body: _index == 1
          ? const ExpenseScreen()
          : _index == 2
          ? const TpaScreen()
          : PageContainer(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Generate Bill', style: theme.textTheme.titleLarge),
            const SizedBox(height: Space.md),

            if (_error != null) ...[
              MessageBanner.error(_error!),
              const SizedBox(height: Space.md),
            ],
            if (_info != null) ...[
              MessageBanner.success(_info!),
              const SizedBox(height: Space.md),
            ],

            Card(
              child: Padding(
                padding: const EdgeInsets.all(Space.lg),
                child: Row(
                  children: [
                    SizedBox(
                      width: 180,
                      child: DropdownButtonFormField<String>(
                        value: _sourceType,
                        decoration:
                            const InputDecoration(labelText: 'Source'),
                        items: const [
                          DropdownMenuItem(
                              value: 'OPD_VISIT', child: Text('OPD Visit')),
                          DropdownMenuItem(
                              value: 'IPD_ADMISSION',
                              child: Text('IPD Admission')),
                        ],
                        onChanged: (v) =>
                            setState(() => _sourceType = v ?? 'OPD_VISIT'),
                      ),
                    ),
                    const SizedBox(width: Space.md),
                    Expanded(
                      child: TextField(
                        controller: _sourceIdCtrl,
                        enabled: !_loading,
                        keyboardType: TextInputType.number,
                        decoration: const InputDecoration(
                            labelText: 'Visit / Admission ID *'),
                        onSubmitted: (_) => _generateBill(),
                      ),
                    ),
                    const SizedBox(width: Space.md),
                    FilledButton.icon(
                      onPressed: _loading ? null : _generateBill,
                      icon: const Icon(Icons.receipt_outlined, size: 18),
                      label: const Text('Generate'),
                    ),
                  ],
                ),
              ),
            ),

            if (_consolidated != null) ...[
              const SizedBox(height: Space.lg),
              _ConsolidatedBillCard(
                consolidated: _consolidated!,
                payments: _payments,
                loading: _loading,
                onDiscount: _discountDialog,
                onFinalize: _finalize,
                onReceipt: _showReceipt,
                onPayment: _paymentDialog,
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _ConsolidatedBillCard extends StatelessWidget {
  const _ConsolidatedBillCard({
    required this.consolidated,
    required this.payments,
    required this.loading,
    required this.onDiscount,
    required this.onFinalize,
    required this.onReceipt,
    required this.onPayment,
  });

  final Map<String, dynamic> consolidated;
  final List<Map<String, dynamic>> payments;
  final bool loading;
  final VoidCallback onDiscount;
  final VoidCallback onFinalize;
  final VoidCallback onReceipt;
  final VoidCallback onPayment;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final bill = consolidated['bill'] as Map<String, dynamic>;
    final charges =
        List<Map<String, dynamic>>.from(consolidated['charges'] as List? ?? []);
    final billStatus = bill['billStatus'] as String;
    final isDraft = billStatus == 'DRAFT';
    final isFinal = billStatus == 'FINAL';
    final balanceDue =
        num.tryParse('${bill['balanceDue'] ?? bill['netAmount']}') ?? 0;
    final journalNumber = bill['journalNumber'] as String?;

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(Space.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Text('${bill['billNumber']}',
                    style: theme.textTheme.titleMedium),
                const SizedBox(width: Space.sm),
                StatusChip.auto(billStatus),
                const SizedBox(width: Space.sm),
                StatusChip.auto('${bill['discountStatus']}'),
                const Spacer(),
                if (isDraft) ...[
                  OutlinedButton(
                    onPressed: loading ? null : onDiscount,
                    child: const Text('Discount'),
                  ),
                  const SizedBox(width: Space.sm),
                  FilledButton(
                    onPressed: loading ? null : onFinalize,
                    child: const Text('Finalize'),
                  ),
                ] else ...[
                  if (isFinal && balanceDue > 0) ...[
                    FilledButton.icon(
                      onPressed: loading ? null : onPayment,
                      icon: const Icon(Icons.payments_outlined, size: 18),
                      label: const Text('Record Payment'),
                    ),
                    const SizedBox(width: Space.sm),
                  ],
                  OutlinedButton.icon(
                    onPressed: onReceipt,
                    icon: const Icon(Icons.print_outlined, size: 18),
                    label: const Text('Receipt'),
                  ),
                ],
              ],
            ),

            // Ledger reference for the finalized bill (posted in-process).
            if (isFinal && journalNumber != null) ...[
              const SizedBox(height: Space.sm),
              Text('Journal: $journalNumber',
                  style: theme.textTheme.bodySmall
                      ?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
            ],
            const SizedBox(height: Space.md),
            const Divider(),

            // Charge lines
            for (final c in charges)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: Space.xs),
                child: Row(
                  children: [
                    Expanded(
                      child: Text(
                          '${c['serviceName'] ?? c['serviceCode']} ×${c['quantity']}',
                          style: theme.textTheme.bodyMedium),
                    ),
                    Text('₹${c['amount']}', style: theme.textTheme.bodyMedium),
                  ],
                ),
              ),

            const Divider(),
            _totalRow(theme, 'Hospital Charges', bill['chargesTotal']),
            if ((num.tryParse('${bill['discountAmount']}') ?? 0) > 0)
              _totalRow(theme, 'Discount', '- ${bill['discountAmount']}'),
            _totalRow(theme, 'Hospital Net', consolidated['hospitalNetAmount']),
            _totalRow(
                theme, 'Pharmacy', consolidated['pharmacyTotal']),
            const SizedBox(height: Space.xs),
            Row(
              children: [
                Text('Grand Total', style: theme.textTheme.titleMedium),
                const Spacer(),
                Text('₹${consolidated['grandTotal']}',
                    style: theme.textTheme.titleLarge),
              ],
            ),

            if (isFinal) ...[
              _totalRow(theme, 'Paid', bill['amountPaid']),
              Row(
                children: [
                  Text('Balance Due', style: theme.textTheme.titleSmall),
                  const Spacer(),
                  Text(
                    '₹$balanceDue',
                    style: theme.textTheme.titleMedium?.copyWith(
                      color: balanceDue > 0
                          ? theme.colorScheme.error
                          : theme.colorScheme.primary,
                    ),
                  ),
                ],
              ),
            ],

            if (payments.isNotEmpty) ...[
              const SizedBox(height: Space.md),
              Text('Payments', style: theme.textTheme.titleSmall),
              const SizedBox(height: Space.xs),
              for (final p in payments)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: Space.xxs),
                  child: Row(
                    children: [
                      Text('₹${p['amount']} · ${p['paymentMode']}'
                          '${p['reference'] != null ? ' · ${p['reference']}' : ''}'),
                      if (p['journalNumber'] != null) ...[
                        const SizedBox(width: Space.sm),
                        Text('(${p['journalNumber']})',
                            style: theme.textTheme.bodySmall?.copyWith(
                                color: theme.colorScheme.onSurfaceVariant)),
                      ],
                      const Spacer(),
                      Text('${p['createdAt'] ?? ''}'.split('T').first,
                          style: theme.textTheme.bodySmall),
                    ],
                  ),
                ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _totalRow(ThemeData theme, String label, Object? value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: Space.xxs),
      child: Row(
        children: [
          Text(label,
              style: theme.textTheme.bodyMedium
                  ?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
          const Spacer(),
          Text('₹$value', style: theme.textTheme.bodyMedium),
        ],
      ),
    );
  }
}

class _ReceiptDialog extends StatelessWidget {
  const _ReceiptDialog({required this.receipt, this.billId});

  final Map<String, dynamic> receipt;
  final dynamic billId;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return AlertDialog(
      title: Text('Receipt — ${receipt['billNumber'] ?? ''}'),
      content: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
        child: SingleChildScrollView(
          child: Text(
            const JsonViewFormatter().format(receipt),
            style:
                theme.textTheme.bodySmall?.copyWith(fontFamily: 'monospace'),
          ),
        ),
      ),
      actions: [
        if (billId != null)
          TextButton.icon(
            onPressed: () => openPdfFromApi(
              context,
              context.read<ApiClient>(),
              '/api/v1/billing/bills/$billId/receipt.pdf',
              'bill-${receipt['billNumber'] ?? billId}.pdf',
            ),
            icon: const Icon(Icons.picture_as_pdf_outlined, size: 18),
            label: const Text('Open PDF'),
          ),
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Close'),
        ),
      ],
    );
  }
}

/// Plain-text receipt rendering until the PDF template lands.
class JsonViewFormatter {
  const JsonViewFormatter();

  String format(Map<String, dynamic> map, [int indent = 0]) {
    final buffer = StringBuffer();
    final pad = '  ' * indent;
    map.forEach((key, value) {
      if (value is Map<String, dynamic>) {
        buffer.writeln('$pad$key:');
        buffer.write(format(value, indent + 1));
      } else if (value is List) {
        buffer.writeln('$pad$key:');
        for (final e in value) {
          if (e is Map<String, dynamic>) {
            buffer.write(format(e, indent + 1));
            buffer.writeln('$pad  —');
          } else {
            buffer.writeln('$pad  $e');
          }
        }
      } else {
        buffer.writeln('$pad$key: $value');
      }
    });
    return buffer.toString();
  }
}
