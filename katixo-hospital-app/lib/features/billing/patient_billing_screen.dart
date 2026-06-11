import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/status_chip.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// Patient billing view: consolidated bill, charges, payments, receipts.
class PatientBillingScreen extends StatefulWidget {
  final int? patientId;
  const PatientBillingScreen({this.patientId, super.key});

  @override
  State<PatientBillingScreen> createState() => _PatientBillingScreenState();
}

class _PatientBillingScreenState extends State<PatientBillingScreen> {
  Map<String, dynamic>? _billData;
  bool _loading = false;
  String? _error;
  String? _success;

  int? _selectedBillId;
  final _discountAmountCtrl = TextEditingController();
  final _discountReasonCtrl = TextEditingController();

  @override
  void dispose() {
    _discountAmountCtrl.dispose();
    _discountReasonCtrl.dispose();
    super.dispose();
  }

  Future<void> _loadBill(int billId) async {
    setState(() {
      _loading = true;
      _error = null;
      _billData = null;
    });

    try {
      final api = context.read<ApiClient>();
      final bill = await api.get<Map<String, dynamic>>(
        '/api/v1/billing/bills/$billId',
        fromJson: (json) => json as Map<String, dynamic>,
      );
      if (mounted) {
        setState(() {
          _billData = bill;
          _selectedBillId = billId;
        });
      }
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Failed to load bill: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _requestDiscount(int billId) async {
    if (_discountAmountCtrl.text.isEmpty || _discountReasonCtrl.text.isEmpty) {
      setState(() => _error = 'Enter discount amount and reason');
      return;
    }

    setState(() {
      _loading = true;
      _error = null;
    });

    try {
      final api = context.read<ApiClient>();
      final amount = double.tryParse(_discountAmountCtrl.text) ?? 0;
      await api.post<dynamic>(
        '/api/v1/billing/bills/$billId/discount',
        {
          'amount': amount,
          'reason': _discountReasonCtrl.text.trim(),
        },
        fromJson: (json) => json,
      );
      setState(() => _success = 'Discount request submitted for approval');
      _discountAmountCtrl.clear();
      _discountReasonCtrl.clear();
      if (_selectedBillId != null) await _loadBill(_selectedBillId!);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return PageContainer(
      child: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Patient Billing', style: theme.textTheme.titleLarge),
            const SizedBox(height: Space.md),

            if (_error != null) ...[
              MessageBanner.error(_error!),
              const SizedBox(height: Space.md),
            ],
            if (_success != null) ...[
              MessageBanner.success(_success!),
              const SizedBox(height: Space.md),
            ],

            // Bill ID input
            Card(
              child: Padding(
                padding: const EdgeInsets.all(Space.lg),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('View Bill', style: theme.textTheme.titleMedium),
                    const SizedBox(height: Space.md),
                    Row(
                      children: [
                        Expanded(
                          child: TextField(
                            initialValue: _selectedBillId?.toString() ?? '',
                            enabled: !_loading,
                            keyboardType: TextInputType.number,
                            decoration:
                                const InputDecoration(labelText: 'Bill ID'),
                            onChanged: (val) {
                              if (val.isNotEmpty) {
                                int? billId = int.tryParse(val);
                                if (billId != null) {
                                  _loadBill(billId);
                                }
                              }
                            },
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: Space.md),

            // Bill details
            if (_billData != null) ...[
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(Space.lg),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text('Bill Summary',
                                    style: theme.textTheme.titleMedium),
                                const SizedBox(height: Space.sm),
                                Text('Bill #${_billData?['bill']?['billNumber'] ?? 'N/A'}',
                                    style: theme.textTheme.bodySmall),
                              ],
                            ),
                          ),
                          StatusChip.auto(
                              _billData?['bill']?['billStatus'] ?? 'DRAFT'),
                        ],
                      ),
                      const SizedBox(height: Space.lg),
                      const Divider(),
                      const SizedBox(height: Space.lg),

                      // Hospital charges
                      Text('Hospital Services',
                          style: theme.textTheme.labelLarge),
                      const SizedBox(height: Space.md),
                      if ((_billData?['charges'] as List?)?.isEmpty ?? true)
                        Text('No charges',
                            style: theme.textTheme.bodySmall
                                ?.copyWith(fontStyle: FontStyle.italic))
                      else
                        for (final charge in (_billData?['charges'] ?? []))
                          Padding(
                            padding: const EdgeInsets.only(bottom: Space.sm),
                            child: Row(
                              children: [
                                Expanded(
                                  child: Text(
                                      '${charge['serviceCode']} • ${charge['serviceName']}',
                                      style: theme.textTheme.bodySmall),
                                ),
                                Text(
                                    '₹${charge['amount']}',
                                    style: theme.textTheme.bodySmall
                                        ?.copyWith(fontWeight: FontWeight.bold)),
                              ],
                            ),
                          ),
                      const SizedBox(height: Space.md),
                      Row(
                        children: [
                          Text('Hospital Total',
                              style: theme.textTheme.labelMedium),
                          const Spacer(),
                          Text(
                              '₹${_billData?['hospitalNetAmount'] ?? 0}',
                              style: theme.textTheme.labelMedium
                                  ?.copyWith(fontWeight: FontWeight.bold)),
                        ],
                      ),
                      const SizedBox(height: Space.lg),
                      const Divider(),
                      const SizedBox(height: Space.lg),

                      // ERP invoices
                      if ((_billData?['erpInvoices'] as List?)?.isNotEmpty ??
                          false) ...[
                        Text('Pharmacy & Consumables',
                            style: theme.textTheme.labelLarge),
                        const SizedBox(height: Space.md),
                        for (final inv in (_billData?['erpInvoices'] ?? []))
                          Padding(
                            padding: const EdgeInsets.only(bottom: Space.sm),
                            child: Row(
                              children: [
                                Expanded(
                                  child: Text(
                                      'Invoice ${inv['invoiceNumber']}',
                                      style: theme.textTheme.bodySmall),
                                ),
                                Text(
                                    '₹${inv['amount']}',
                                    style: theme.textTheme.bodySmall
                                        ?.copyWith(fontWeight: FontWeight.bold)),
                              ],
                            ),
                          ),
                        const SizedBox(height: Space.md),
                        Row(
                          children: [
                            Text('Pharmacy Total',
                                style: theme.textTheme.labelMedium),
                            const Spacer(),
                            Text(
                                '₹${_billData?['erpInvoicesTotal'] ?? 0}',
                                style: theme.textTheme.labelMedium
                                    ?.copyWith(fontWeight: FontWeight.bold)),
                          ],
                        ),
                        const SizedBox(height: Space.lg),
                        const Divider(),
                        const SizedBox(height: Space.lg),
                      ],

                      // Grand total
                      Row(
                        children: [
                          Text('Grand Total',
                              style: theme.textTheme.titleSmall
                                  ?.copyWith(fontWeight: FontWeight.bold)),
                          const Spacer(),
                          Text(
                              '₹${_billData?['grandTotal'] ?? 0}',
                              style: theme.textTheme.titleSmall
                                  ?.copyWith(fontWeight: FontWeight.bold)),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: Space.md),

              // Discount request
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(Space.lg),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Request Discount',
                          style: theme.textTheme.titleMedium),
                      const SizedBox(height: Space.md),
                      TextField(
                        controller: _discountAmountCtrl,
                        enabled: !_loading,
                        keyboardType:
                            TextInputType.numberWithOptions(decimal: true),
                        decoration:
                            const InputDecoration(labelText: 'Amount (₹) *'),
                      ),
                      const SizedBox(height: Space.md),
                      TextField(
                        controller: _discountReasonCtrl,
                        enabled: !_loading,
                        maxLines: 2,
                        decoration: const InputDecoration(
                          labelText: 'Reason for Discount *',
                        ),
                      ),
                      const SizedBox(height: Space.lg),
                      SizedBox(
                        width: double.infinity,
                        child: FilledButton(
                          onPressed: _selectedBillId == null || _loading
                              ? null
                              : () => _requestDiscount(_selectedBillId!),
                          child: _loading
                              ? const SizedBox(
                                  width: 20,
                                  height: 20,
                                  child: CircularProgressIndicator(
                                      strokeWidth: 2),
                                )
                              : const Text('Request Discount'),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
