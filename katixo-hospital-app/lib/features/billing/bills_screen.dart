import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/auth/auth_state.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/util/formatters.dart';
import '../../core/util/pdf_actions.dart';
import '../../core/util/validators.dart';
import '../../core/widgets/message_banner.dart';
import '../../core/widgets/status_chip.dart';

/// Generate a consolidated bill for an OPD visit / IPD admission, request a
/// discount, finalize, collect payment, and view the receipt. Body widget.
class BillsScreen extends StatefulWidget {
  const BillsScreen({super.key});

  @override
  State<BillsScreen> createState() => _BillsScreenState();
}

class _BillsScreenState extends State<BillsScreen> {
  final _sourceIdCtrl = TextEditingController();
  String _sourceType = 'OPD_VISIT';

  Map<String, dynamic>? _consolidated;
  List<Map<String, dynamic>> _payments = const [];
  bool _loading = false;
  String? _error;
  String? _info;

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
                  DropdownMenuItem(value: 'BANK_TRANSFER', child: Text('Bank transfer')),
                ],
                onChanged: (v) => setDialogState(() => mode = v ?? 'CASH'),
              ),
              const SizedBox(height: Space.md),
              TextField(
                controller: referenceCtrl,
                decoration: const InputDecoration(labelText: 'Reference (UPI/cheque no.)'),
              ),
            ],
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
            FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Collect')),
          ],
        ),
      ),
    );

    if (collect != true) return;
    final amountError = positiveAmount(amountCtrl.text, field: 'Payment amount');
    if (amountError != null) {
      setState(() => _error = amountError);
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      final payment = await api.post<Map<String, dynamic>>(
        '/api/v1/billing/bills/$billId/payments',
        {
          'amount': double.parse(amountCtrl.text.trim()),
          'paymentMode': mode,
          if (referenceCtrl.text.trim().isNotEmpty) 'reference': referenceCtrl.text.trim(),
        },
        fromJson: (json) => json as Map<String, dynamic>,
      );
      setState(() => _info = 'Payment of ${formatMoney(payment['amount'])} recorded ($mode)');
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
              decoration: const InputDecoration(labelText: 'Amount (₹) *', prefixText: '₹ '),
            ),
            const SizedBox(height: Space.md),
            TextField(controller: reasonCtrl, decoration: const InputDecoration(labelText: 'Reason *')),
            const SizedBox(height: Space.sm),
            const Text('Above the policy threshold the discount needs admin approval.',
                style: TextStyle(fontSize: 12)),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Request')),
        ],
      ),
    );
    if (apply != true) return;
    final billId = _billId;
    if (billId == null) return;
    final discountError = firstError([
      positiveAmount(amountCtrl.text, field: 'Discount amount'),
      requiredText(reasonCtrl.text, field: 'Reason'),
    ]);
    if (discountError != null) {
      setState(() => _error = discountError);
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      final bill = await api.post<Map<String, dynamic>>(
        '/api/v1/billing/bills/$billId/discount',
        {'amount': double.parse(amountCtrl.text.trim()), 'reason': reasonCtrl.text.trim()},
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
        builder: (context) => _ReceiptDialog(receipt: receipt),
      );
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    }
  }

  Future<void> _openReceiptPdf() async {
    final billId = _billId;
    if (billId == null) return;
    await openPdf(
      context,
      context.read<ApiClient>(),
      '/api/v1/billing/bills/$billId/receipt.pdf',
      filename: 'bill-$billId.pdf',
    );
  }

  /// Admin approves the cashier's discount request — the threshold gate fires
  /// when `discountStatus == PENDING_APPROVAL`; the cashier sees that chip and
  /// the admin sees the Approve button.
  Future<void> _approveDiscount() async {
    final billId = _billId;
    if (billId == null) return;
    await _postAction('/api/v1/billing/bills/$billId/discount/approve',
        const <String, dynamic>{}, 'Discount approved');
  }

  /// Manually link a pharmacy sale to this bill (used when auto-attach missed
  /// one — e.g. a sale recorded after the bill was generated). Pharmacist /
  /// billing / admin can do this.
  Future<void> _pharmacyRefDialog() async {
    final billId = _billId;
    if (billId == null) return;
    final saleNumberCtrl = TextEditingController();
    final amountCtrl = TextEditingController();
    String docType = 'INVOICE';

    final proceed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Link pharmacy sale'),
        content: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: saleNumberCtrl,
                decoration: const InputDecoration(labelText: 'Sale number *'),
              ),
              const SizedBox(height: Space.sm),
              TextField(
                controller: amountCtrl,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(labelText: 'Amount (₹) *'),
              ),
              const SizedBox(height: Space.sm),
              DropdownButtonFormField<String>(
                value: docType,
                decoration: const InputDecoration(labelText: 'Doc type'),
                items: const [
                  DropdownMenuItem(value: 'INVOICE', child: Text('Invoice')),
                  DropdownMenuItem(value: 'OTC', child: Text('OTC')),
                  DropdownMenuItem(value: 'IPD_INDENT', child: Text('IPD indent')),
                ],
                onChanged: (v) => docType = v ?? 'INVOICE',
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Cancel')),
          FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('Link')),
        ],
      ),
    );
    if (proceed != true) return;
    final amount = double.tryParse(amountCtrl.text.trim());
    if (saleNumberCtrl.text.trim().isEmpty || amount == null || amount <= 0) {
      setState(() => _error = 'Sale number and a positive amount are required');
      return;
    }
    await _postAction('/api/v1/billing/bills/$billId/pharmacy-refs', {
      'saleNumber': saleNumberCtrl.text.trim(),
      'amount': amount,
      'docType': docType,
    }, 'Pharmacy sale linked');
  }

  /// Add a manual hospital charge to a DRAFT bill (the auto-charge path covers
  /// the typical OPD/IPD lines; manual is for one-offs or corrections).
  Future<void> _addChargeDialog() async {
    final billId = _billId;
    final bill = _consolidated?['bill'] as Map<String, dynamic>?;
    if (billId == null || bill == null) return;
    final sourceType = '${bill['sourceType']}';
    final sourceId = bill['sourceId'] as int?;
    final patientId = bill['patientId'] as int?;
    if (sourceId == null || patientId == null) return;

    final codeCtrl = TextEditingController();
    final qtyCtrl = TextEditingController(text: '1');

    final proceed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Add charge'),
        content: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('On $sourceType #$sourceId',
                  style: Theme.of(context).textTheme.bodySmall),
              const SizedBox(height: Space.sm),
              TextField(
                controller: codeCtrl,
                decoration: const InputDecoration(
                  labelText: 'Service code *',
                  hintText: 'e.g. CONSULT_DOC, NURSE_VISIT',
                ),
              ),
              const SizedBox(height: Space.sm),
              TextField(
                controller: qtyCtrl,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(labelText: 'Quantity'),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Cancel')),
          FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('Add')),
        ],
      ),
    );
    if (proceed != true) return;
    final code = codeCtrl.text.trim();
    final qty = int.tryParse(qtyCtrl.text.trim()) ?? 1;
    if (code.isEmpty) {
      setState(() => _error = 'Service code is required');
      return;
    }
    // The charge endpoint adds an UNBILLED charge to the patient. To see it on
    // this bill we regenerate the consolidated view after adding.
    await _postAction('/api/v1/billing/charges', {
      'patientId': patientId,
      'sourceType': sourceType,
      'sourceId': sourceId,
      'serviceCode': code,
      'quantity': qty,
    }, 'Charge added');
  }

  /// Cancel a bill — reverses its AR/income journal. Backend rejects this if
  /// payments are still posted, so the UI surfaces that error.
  Future<void> _cancelBillDialog() async {
    final billId = _billId;
    final bill = _consolidated?['bill'] as Map<String, dynamic>?;
    if (billId == null || bill == null) return;
    final reasonCtrl = TextEditingController();
    final amount = formatMoney(bill['netAmount'] ?? bill['grandTotal']);
    final proceed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Cancel bill'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Reverse bill ${bill['billNumber']} for $amount? '
                'This posts a reversing journal and cannot be undone.',
                style: Theme.of(context).textTheme.bodyMedium),
            const SizedBox(height: Space.md),
            TextField(
              controller: reasonCtrl,
              decoration: const InputDecoration(labelText: 'Reason'),
            ),
          ],
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Back')),
          FilledButton(
            style: FilledButton.styleFrom(
                backgroundColor: Theme.of(context).colorScheme.error),
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Cancel bill'),
          ),
        ],
      ),
    );
    if (proceed != true) return;
    await _postAction('/api/v1/billing/bills/$billId/cancel',
        {'reason': reasonCtrl.text.trim()}, 'Bill cancelled');
  }

  /// Void a posted payment — reverses its ledger journal and restores the
  /// bill's balance. Required before a bill can be cancelled.
  Future<void> _voidPaymentDialog(Map<String, dynamic> payment) async {
    final paymentId = payment['id'];
    if (paymentId == null) return;
    final reasonCtrl = TextEditingController();
    final proceed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Void payment of ${formatMoney(payment['amount'])}'),
        content: TextField(
          controller: reasonCtrl,
          decoration: const InputDecoration(labelText: 'Reason'),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Back')),
          FilledButton(
            style: FilledButton.styleFrom(
                backgroundColor: Theme.of(context).colorScheme.error),
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Void'),
          ),
        ],
      ),
    );
    if (proceed != true) return;
    await _postAction('/api/v1/billing/payments/$paymentId/void',
        {'reason': reasonCtrl.text.trim()}, 'Payment voided');
  }

  /// Small fan-out for the admin actions above — sets loading, posts, refreshes
  /// the consolidated view, surfaces the server message.
  Future<void> _postAction(
      String path, Object body, String successMessage) async {
    final billId = _billId;
    if (billId == null) return;
    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });
    try {
      final api = context.read<ApiClient>();
      await api.post<dynamic>(path, body, fromJson: (json) => json);
      setState(() => _info = successMessage);
      await _loadConsolidated(billId);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Action failed: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return PageContainer(
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
                      decoration: const InputDecoration(labelText: 'Source'),
                      items: const [
                        DropdownMenuItem(value: 'OPD_VISIT', child: Text('OPD Visit')),
                        DropdownMenuItem(value: 'IPD_ADMISSION', child: Text('IPD Admission')),
                      ],
                      onChanged: (v) => setState(() => _sourceType = v ?? 'OPD_VISIT'),
                    ),
                  ),
                  const SizedBox(width: Space.md),
                  Expanded(
                    child: TextField(
                      controller: _sourceIdCtrl,
                      enabled: !_loading,
                      keyboardType: TextInputType.number,
                      decoration: const InputDecoration(labelText: 'Visit / Admission ID *'),
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
              role: context.watch<AuthState>().currentUser?.role ?? '',
              onDiscount: _discountDialog,
              onApproveDiscount: _approveDiscount,
              onFinalize: _finalize,
              onReceipt: _showReceipt,
              onReceiptPdf: _openReceiptPdf,
              onPayment: _paymentDialog,
              onAddCharge: _addChargeDialog,
              onLinkPharmacy: _pharmacyRefDialog,
              onCancelBill: _cancelBillDialog,
              onVoidPayment: _voidPaymentDialog,
            ),
          ],
        ],
      ),
    );
  }
}

class _ConsolidatedBillCard extends StatelessWidget {
  const _ConsolidatedBillCard({
    required this.consolidated,
    required this.payments,
    required this.loading,
    required this.role,
    required this.onDiscount,
    required this.onApproveDiscount,
    required this.onFinalize,
    required this.onReceipt,
    required this.onReceiptPdf,
    required this.onPayment,
    required this.onAddCharge,
    required this.onLinkPharmacy,
    required this.onCancelBill,
    required this.onVoidPayment,
  });

  final Map<String, dynamic> consolidated;
  final List<Map<String, dynamic>> payments;
  final bool loading;
  final String role;
  final VoidCallback onDiscount;
  final VoidCallback onApproveDiscount;
  final VoidCallback onFinalize;
  final VoidCallback onReceipt;
  final VoidCallback onReceiptPdf;
  final VoidCallback onPayment;
  final VoidCallback onAddCharge;
  final VoidCallback onLinkPharmacy;
  final VoidCallback onCancelBill;
  final void Function(Map<String, dynamic> payment) onVoidPayment;

  bool get _isAdmin => role == 'ADMIN' || role == 'SUPER_ADMIN';
  bool get _isBilling =>
      role == 'BILLING' || role == 'ADMIN' || role == 'SUPER_ADMIN';
  bool get _canLinkPharmacy =>
      role == 'PHARMACIST' || _isBilling;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final bill = consolidated['bill'] as Map<String, dynamic>;
    final charges = List<Map<String, dynamic>>.from(consolidated['charges'] as List? ?? []);
    final billStatus = bill['billStatus'] as String;
    final isDraft = billStatus == 'DRAFT';
    final isFinal = billStatus == 'FINAL';
    final isCancelled = billStatus == 'CANCELLED';
    final discountPending = '${bill['discountStatus']}' == 'PENDING_APPROVAL';
    final balanceDue = num.tryParse('${bill['balanceDue'] ?? bill['netAmount']}') ?? 0;
    final journalNumber = bill['journalNumber'] as String?;

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(Space.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Wrap(
              crossAxisAlignment: WrapCrossAlignment.center,
              spacing: Space.sm,
              runSpacing: Space.sm,
              children: [
                Text('${bill['billNumber']}', style: theme.textTheme.titleMedium),
                StatusChip.auto(billStatus),
                StatusChip.auto('${bill['discountStatus']}'),
                if (isDraft) ...[
                  if (_isBilling)
                    OutlinedButton(
                      onPressed: loading ? null : onAddCharge,
                      child: const Text('Add charge'),
                    ),
                  if (_canLinkPharmacy)
                    OutlinedButton(
                      onPressed: loading ? null : onLinkPharmacy,
                      child: const Text('Link pharmacy'),
                    ),
                  OutlinedButton(
                      onPressed: loading ? null : onDiscount,
                      child: const Text('Discount')),
                  if (discountPending && _isAdmin)
                    FilledButton.icon(
                      onPressed: loading ? null : onApproveDiscount,
                      icon: const Icon(Icons.verified_outlined, size: 18),
                      label: const Text('Approve discount'),
                    ),
                  FilledButton(
                      onPressed: loading ? null : onFinalize,
                      child: const Text('Finalize')),
                ] else ...[
                  if (isFinal && balanceDue > 0)
                    FilledButton.icon(
                      onPressed: loading ? null : onPayment,
                      icon: const Icon(Icons.payments_outlined, size: 18),
                      label: const Text('Record Payment'),
                    ),
                  OutlinedButton.icon(
                    onPressed: onReceipt,
                    icon: const Icon(Icons.visibility_outlined, size: 18),
                    label: const Text('Receipt'),
                  ),
                  FilledButton.icon(
                    onPressed: onReceiptPdf,
                    icon: const Icon(Icons.picture_as_pdf_outlined, size: 18),
                    label: const Text('PDF'),
                  ),
                ],
                if (!isCancelled && _isBilling)
                  PopupMenuButton<String>(
                    icon: const Icon(Icons.more_vert),
                    tooltip: 'More',
                    onSelected: (v) {
                      if (v == 'cancel') onCancelBill();
                    },
                    itemBuilder: (context) => const [
                      PopupMenuItem(
                          value: 'cancel', child: Text('Cancel bill…')),
                    ],
                  ),
              ],
            ),
            if (isFinal && journalNumber != null) ...[
              const SizedBox(height: Space.sm),
              Text('Journal: $journalNumber',
                  style: theme.textTheme.bodySmall
                      ?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
            ],
            const SizedBox(height: Space.md),
            const Divider(),
            for (final c in charges)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: Space.xs),
                child: Row(
                  children: [
                    Expanded(
                      child: Text('${c['serviceName'] ?? c['serviceCode']} ×${c['quantity']}',
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
            _totalRow(theme, 'Pharmacy', consolidated['pharmacyTotal']),
            const SizedBox(height: Space.xs),
            Row(
              children: [
                Text('Grand Total', style: theme.textTheme.titleMedium),
                const Spacer(),
                Text('₹${consolidated['grandTotal']}', style: theme.textTheme.titleLarge),
              ],
            ),
            if (isFinal) ...[
              _totalRow(theme, 'Paid', bill['amountPaid']),
              Row(
                children: [
                  Text('Balance Due', style: theme.textTheme.titleSmall),
                  const Spacer(),
                  Text('₹$balanceDue',
                      style: theme.textTheme.titleMedium?.copyWith(
                        color: balanceDue > 0 ? theme.colorScheme.error : theme.colorScheme.primary,
                      )),
                ],
              ),
            ],
            if (payments.isNotEmpty) ...[
              const SizedBox(height: Space.md),
              Text('Payments', style: theme.textTheme.titleSmall),
              const SizedBox(height: Space.xs),
              for (final p in payments) ...[
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: Space.xxs),
                  child: Row(
                    children: [
                      if (p['reversed'] == true) ...[
                        StatusChip.auto('VOIDED'),
                        const SizedBox(width: Space.sm),
                      ],
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
                      if (_isBilling && p['reversed'] != true) ...[
                        const SizedBox(width: Space.sm),
                        IconButton(
                          tooltip: 'Void payment',
                          icon: const Icon(Icons.undo, size: 18),
                          onPressed: loading ? null : () => onVoidPayment(p),
                        ),
                      ],
                    ],
                  ),
                ),
              ],
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
          Text(label, style: theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
          const Spacer(),
          Text('₹$value', style: theme.textTheme.bodyMedium),
        ],
      ),
    );
  }
}

class _ReceiptDialog extends StatelessWidget {
  const _ReceiptDialog({required this.receipt});

  final Map<String, dynamic> receipt;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return AlertDialog(
      title: Text('Receipt — ${receipt['billNumber'] ?? ''}'),
      content: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
        child: SingleChildScrollView(
          child: Text(
            const _JsonViewFormatter().format(receipt),
            style: theme.textTheme.bodySmall?.copyWith(fontFamily: 'monospace'),
          ),
        ),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('Close')),
      ],
    );
  }
}

/// Plain-text receipt rendering until the PDF template lands.
class _JsonViewFormatter {
  const _JsonViewFormatter();

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
