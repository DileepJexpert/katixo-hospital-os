import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/api/patient_portal_models.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/app_shell.dart';

class PatientBillingPortal extends StatefulWidget {
  const PatientBillingPortal({super.key});

  @override
  State<PatientBillingPortal> createState() => _PatientBillingPortalState();
}

class _PatientBillingPortalState extends State<PatientBillingPortal> {
  PatientDashboardResponse? _dashboard;
  bool _loading = false;
  String? _error;
  int _navIndex = 0;

  @override
  void initState() {
    super.initState();
    _loadDashboard();
  }

  Future<void> _loadDashboard() async {
    setState(() => _loading = true);
    try {
      final api = context.read<ApiClient>();
      final data = await api.get<PatientDashboardResponse>(
        '/api/v1/patient-portal/billing/dashboard',
        fromJson: (json) =>
            PatientDashboardResponse.fromJson(json as Map<String, dynamic>),
      );
      setState(() {
        _dashboard = data;
        _error = null;
      });
    } catch (e) {
      setState(() => _error = 'Failed to load dashboard: $e');
    } finally {
      setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return AppShell(
      title: 'My Billing',
      destinations: const [
        ShellDestination(
          label: 'Dashboard',
          icon: Icons.dashboard_outlined,
          selectedIcon: Icons.dashboard,
        ),
        ShellDestination(
          label: 'Bills',
          icon: Icons.receipt_outlined,
          selectedIcon: Icons.receipt,
        ),
        ShellDestination(
          label: 'Payments',
          icon: Icons.payment_outlined,
          selectedIcon: Icons.payment,
        ),
      ],
      selectedIndex: _navIndex,
      onDestinationSelected: (i) => setState(() => _navIndex = i),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? _ErrorWidget(error: _error!, onRetry: _loadDashboard)
              : _dashboard == null
                  ? const SizedBox()
                  : _navIndex == 0
                      ? _DashboardTab(dashboard: _dashboard!)
                      : _navIndex == 1
                          ? _BillsTab(patientId: _dashboard!.patientId)
                          : _PaymentsTab(patientId: _dashboard!.patientId),
    );
  }
}

class _DashboardTab extends StatelessWidget {
  final PatientDashboardResponse dashboard;

  const _DashboardTab({required this.dashboard});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return SingleChildScrollView(
      child: Padding(
        padding: const EdgeInsets.all(Space.md),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Welcome back, ${dashboard.patientName}',
                style: theme.textTheme.titleLarge),
            const SizedBox(height: Space.md),
            _buildKPIRow(
              [
                _KPICard(
                  label: 'Outstanding',
                  value:
                      '₹${dashboard.totalOutstanding.toStringAsFixed(0)}',
                  icon: Icons.warning_amber_outlined,
                  color: Colors.orange,
                ),
                _KPICard(
                  label: 'Active Bills',
                  value: dashboard.activeBills.toString(),
                  icon: Icons.receipt_outlined,
                  color: Colors.blue,
                ),
                _KPICard(
                  label: 'Paid Bills',
                  value: dashboard.paidBills.toString(),
                  icon: Icons.check_circle_outline,
                  color: Colors.green,
                ),
              ],
            ),
            const SizedBox(height: Space.xl),
            Text('Recent Bills', style: theme.textTheme.titleMedium),
            const SizedBox(height: Space.md),
            if (dashboard.recentBills.isEmpty)
              const Center(
                child: Padding(
                  padding: EdgeInsets.all(Space.lg),
                  child: Text('No bills yet'),
                ),
              )
            else
              ...dashboard.recentBills
                  .map((bill) => _BillListTile(bill: bill))
                  .toList(),
          ],
        ),
      ),
    );
  }

  Widget _buildKPIRow(List<Widget> cards) {
    return Wrap(
      spacing: Space.md,
      runSpacing: Space.md,
      children: cards,
    );
  }
}

class _KPICard extends StatelessWidget {
  final String label;
  final String value;
  final IconData icon;
  final Color color;

  const _KPICard({
    required this.label,
    required this.value,
    required this.icon,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Container(
        width: 150,
        padding: const EdgeInsets.all(Space.md),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(icon, color: color, size: 32),
            const SizedBox(height: Space.md),
            Text(value,
                style: Theme.of(context).textTheme.headlineSmall,
                maxLines: 1,
                overflow: TextOverflow.ellipsis),
            const SizedBox(height: Space.xs),
            Text(label,
                style: Theme.of(context).textTheme.bodySmall,
                maxLines: 1,
                overflow: TextOverflow.ellipsis),
          ],
        ),
      ),
    );
  }
}

class _BillsTab extends StatefulWidget {
  final int patientId;

  const _BillsTab({required this.patientId});

  @override
  State<_BillsTab> createState() => _BillsTabState();
}

class _BillsTabState extends State<_BillsTab> {
  List<PatientBillResponse> _bills = [];
  bool _loading = false;
  String _selectedStatus = 'ACTIVE';
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadBills();
  }

  Future<void> _loadBills() async {
    setState(() => _loading = true);
    try {
      final api = context.read<ApiClient>();
      final bills = await api.get<List<PatientBillResponse>>(
        '/api/v1/patient-portal/billing/bills?status=$_selectedStatus',
        fromJson: (json) {
          if (json is List) {
            return json
                .map((b) =>
                    PatientBillResponse.fromJson(b as Map<String, dynamic>))
                .toList();
          }
          return [];
        },
      );
      setState(() {
        _bills = bills;
        _error = null;
      });
    } catch (e) {
      setState(() => _error = 'Failed to load bills: $e');
    } finally {
      setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(Space.md),
          child: DropdownButton<String>(
            value: _selectedStatus,
            isExpanded: true,
            items: const ['ACTIVE', 'PAID', 'PARTIALLY_PAID', 'CANCELLED']
                .map((s) => DropdownMenuItem(value: s, child: Text(s)))
                .toList(),
            onChanged: (value) {
              if (value != null) {
                setState(() => _selectedStatus = value);
                _loadBills();
              }
            },
          ),
        ),
        if (_error != null)
          Padding(
            padding: const EdgeInsets.all(Space.md),
            child: Text(_error!, style: const TextStyle(color: Colors.red)),
          ),
        Expanded(
          child: _loading
              ? const Center(child: CircularProgressIndicator())
              : _bills.isEmpty
                  ? const Center(child: Text('No bills found'))
                  : ListView.builder(
                      itemCount: _bills.length,
                      itemBuilder: (context, index) =>
                          _BillListTile(bill: _bills[index]),
                    ),
        ),
      ],
    );
  }
}

class _BillListTile extends StatelessWidget {
  final PatientBillResponse bill;

  const _BillListTile({required this.bill});

  Color _getStatusColor(String status) {
    switch (status) {
      case 'ACTIVE':
        return Colors.orange;
      case 'PAID':
        return Colors.green;
      case 'PARTIALLY_PAID':
        return Colors.blue;
      case 'CANCELLED':
        return Colors.red;
      default:
        return Colors.grey;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.all(Space.sm),
      child: ListTile(
        title: Text(bill.billNumber),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const SizedBox(height: Space.xs),
            Text('₹${bill.grandTotal.toStringAsFixed(2)}'),
            const SizedBox(height: Space.xs),
            Chip(
              label: Text(bill.statusDisplay),
              backgroundColor: _getStatusColor(bill.billStatus),
              labelStyle: const TextStyle(color: Colors.white),
            ),
          ],
        ),
        isThreeLine: true,
        trailing: const Icon(Icons.chevron_right),
        onTap: () {
          Navigator.push(
            context,
            MaterialPageRoute(
              builder: (context) => _BillDetailScreen(billId: bill.id),
            ),
          );
        },
      ),
    );
  }
}

class _BillDetailScreen extends StatefulWidget {
  final int billId;

  const _BillDetailScreen({required this.billId});

  @override
  State<_BillDetailScreen> createState() => _BillDetailScreenState();
}

class _BillDetailScreenState extends State<_BillDetailScreen> {
  PatientBillResponse? _bill;
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadBill();
  }

  Future<void> _loadBill() async {
    try {
      final api = context.read<ApiClient>();
      final bill = await api.get<PatientBillResponse>(
        '/api/v1/patient-portal/billing/bills/${widget.billId}',
        fromJson: (json) =>
            PatientBillResponse.fromJson(json as Map<String, dynamic>),
      );
      setState(() {
        _bill = bill;
        _error = null;
      });
    } catch (e) {
      setState(() => _error = 'Failed to load bill: $e');
    } finally {
      setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(_bill?.billNumber ?? 'Bill Details')),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? _ErrorWidget(error: _error!, onRetry: _loadBill)
              : _bill == null
                  ? const SizedBox()
                  : SingleChildScrollView(
                      child: Padding(
                        padding: const EdgeInsets.all(Space.md),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Card(
                              child: Padding(
                                padding: const EdgeInsets.all(Space.md),
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Row(
                                      mainAxisAlignment:
                                          MainAxisAlignment.spaceBetween,
                                      children: [
                                        Text(_bill!.billNumber,
                                            style: Theme.of(context)
                                                .textTheme
                                                .titleMedium),
                                        Chip(
                                          label: Text(_bill!.statusDisplay),
                                          backgroundColor: _bill!.billStatus ==
                                                  'PAID'
                                              ? Colors.green
                                              : Colors.orange,
                                          labelStyle: const TextStyle(
                                              color: Colors.white),
                                        ),
                                      ],
                                    ),
                                    const Divider(),
                                    _buildRow('Hospital Charges',
                                        '₹${_bill!.hospitalChargesTotal.toStringAsFixed(2)}'),
                                    _buildRow('Pharmacy Charges',
                                        '₹${_bill!.erpInvoicesTotal.toStringAsFixed(2)}'),
                                    if (_bill!.discountAmount > 0)
                                      _buildRow('Discount',
                                          '-₹${_bill!.discountAmount.toStringAsFixed(2)}'),
                                    const Divider(),
                                    _buildRow(
                                      'Total Due',
                                      '₹${_bill!.grandTotal.toStringAsFixed(2)}',
                                      isBold: true,
                                    ),
                                  ],
                                ),
                              ),
                            ),
                            const SizedBox(height: Space.md),
                            Text('Itemized Charges',
                                style:
                                    Theme.of(context).textTheme.titleMedium),
                            const SizedBox(height: Space.md),
                            ..._bill!.charges
                                .map((charge) => _buildChargeItem(charge))
                                .toList(),
                          ],
                        ),
                      ),
                    ),
    );
  }

  Widget _buildRow(String label, String value, {bool isBold = false}) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: Space.xs),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label,
              style: TextStyle(
                fontWeight: isBold ? FontWeight.bold : FontWeight.normal,
              )),
          Text(value,
              style: TextStyle(
                fontWeight: isBold ? FontWeight.bold : FontWeight.normal,
              )),
        ],
      ),
    );
  }

  Widget _buildChargeItem(ChargeLineItem charge) {
    return Card(
      margin: const EdgeInsets.only(bottom: Space.sm),
      child: Padding(
        padding: const EdgeInsets.all(Space.sm),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(charge.serviceName,
                style: Theme.of(context).textTheme.titleSmall),
            const SizedBox(height: Space.xs),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text('${charge.quantity} × ₹${charge.unitRate.toStringAsFixed(2)}'),
                Text('₹${charge.totalAmount.toStringAsFixed(2)}',
                    style: const TextStyle(fontWeight: FontWeight.bold)),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _PaymentsTab extends StatefulWidget {
  final int patientId;

  const _PaymentsTab({required this.patientId});

  @override
  State<_PaymentsTab> createState() => _PaymentsTabState();
}

class _PaymentsTabState extends State<_PaymentsTab> {
  List<PaymentHistoryResponse> _payments = [];
  bool _loading = false;

  @override
  void initState() {
    super.initState();
    _loadPayments();
  }

  Future<void> _loadPayments() async {
    setState(() => _loading = true);
    try {
      final api = context.read<ApiClient>();
      final payments = await api.get<List<PaymentHistoryResponse>>(
        '/api/v1/patient-portal/billing/payments',
        fromJson: (json) {
          if (json is List) {
            return json
                .map((p) =>
                    PaymentHistoryResponse.fromJson(p as Map<String, dynamic>))
                .toList();
          }
          return [];
        },
      );
      setState(() => _payments = payments);
    } catch (e) {
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('Error: $e')));
    } finally {
      setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return _loading
        ? const Center(child: CircularProgressIndicator())
        : _payments.isEmpty
            ? const Center(child: Text('No payments yet'))
            : ListView.builder(
                itemCount: _payments.length,
                itemBuilder: (context, index) {
                  final payment = _payments[index];
                  return Card(
                    margin: const EdgeInsets.all(Space.sm),
                    child: ListTile(
                      title: Text(payment.billNumber),
                      subtitle: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const SizedBox(height: Space.xs),
                          Text(
                              'Amount: ₹${payment.amount.toStringAsFixed(2)}'),
                          Text('Method: ${payment.paymentMethod}'),
                        ],
                      ),
                      trailing: Chip(
                        label: Text(payment.status),
                        backgroundColor: payment.status == 'SUCCESS'
                            ? Colors.green
                            : Colors.orange,
                        labelStyle: const TextStyle(color: Colors.white),
                      ),
                      isThreeLine: true,
                    ),
                  );
                },
              );
  }
}

class _ErrorWidget extends StatelessWidget {
  final String error;
  final VoidCallback onRetry;

  const _ErrorWidget({required this.error, required this.onRetry});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(Icons.error_outline, size: 48, color: Colors.red),
          const SizedBox(height: Space.md),
          Text(error, textAlign: TextAlign.center),
          const SizedBox(height: Space.md),
          ElevatedButton(
            onPressed: onRetry,
            child: const Text('Retry'),
          ),
        ],
      ),
    );
  }
}
