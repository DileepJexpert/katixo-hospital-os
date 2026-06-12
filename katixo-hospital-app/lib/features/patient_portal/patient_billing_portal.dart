import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/api/patient_portal_models.dart';
import '../../core/auth/auth_state.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/app_shell.dart';
import '../../core/widgets/kpi_tile.dart';
import '../../core/widgets/status_chip.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// Patient role home: self-service view of own bills and payments.
class PatientBillingPortal extends StatefulWidget {
  const PatientBillingPortal({super.key});

  @override
  State<PatientBillingPortal> createState() => _PatientBillingPortalState();
}

class _PatientBillingPortalState extends State<PatientBillingPortal> {
  PatientDashboardResponse? _dashboard;
  bool _loading = false;
  bool _attempted = false;
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
      if (mounted) {
        setState(() {
          _dashboard = data;
          _error = null;
        });
      }
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.error.message);
    } catch (e) {
      if (mounted) setState(() => _error = 'Failed to load dashboard: $e');
    } finally {
      if (mounted) {
        setState(() {
          _loading = false;
          _attempted = true;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final authState = context.watch<AuthState>();
    final theme = Theme.of(context);

    return AppShell(
      title: 'My Hospital',
      destinations: const [
        ShellDestination(
          label: 'Overview',
          icon: Icons.dashboard_outlined,
          selectedIcon: Icons.dashboard,
        ),
        ShellDestination(
          label: 'My Bills',
          icon: Icons.receipt_long_outlined,
          selectedIcon: Icons.receipt_long,
        ),
      ],
      selectedIndex: _navIndex,
      onDestinationSelected: (i) => setState(() => _navIndex = i),
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
      body: _navIndex == 1
          ? const _BillsTab()
          : PageContainer(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Text('Billing Overview',
                          style: theme.textTheme.titleLarge),
                      const Spacer(),
                      IconButton(
                        tooltip: 'Refresh',
                        onPressed: _loading ? null : _loadDashboard,
                        icon: const Icon(Icons.refresh),
                      ),
                    ],
                  ),
                  const SizedBox(height: Space.md),
                  if (_error != null) ...[
                    MessageBanner.error(_error!),
                    const SizedBox(height: Space.md),
                  ],
                  if (_dashboard == null && !_attempted)
                    const Center(
                      child: Padding(
                        padding: EdgeInsets.all(Space.xl),
                        child: CircularProgressIndicator(),
                      ),
                    )
                  else if (_dashboard != null) ...[
                    Wrap(
                      spacing: Space.md,
                      runSpacing: Space.md,
                      children: [
                        SizedBox(
                          width: 220,
                          child: KpiTile(
                            label: 'Outstanding',
                            value: _dashboard!.totalOutstanding
                                .toStringAsFixed(0),
                            icon: Icons.account_balance_wallet_outlined,
                            unit: '₹',
                          ),
                        ),
                        SizedBox(
                          width: 220,
                          child: KpiTile(
                            label: 'Bills',
                            value: _dashboard!.activeBills.toString(),
                            icon: Icons.receipt_outlined,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: Space.xl),
                    Text('Recent Bills', style: theme.textTheme.titleMedium),
                    const SizedBox(height: Space.sm),
                    if (_dashboard!.recentBills.isEmpty)
                      Card(
                        child: Padding(
                          padding: const EdgeInsets.all(Space.xl),
                          child: Center(
                            child: Text('No bills yet',
                                style: theme.textTheme.bodyMedium?.copyWith(
                                    color: theme
                                        .colorScheme.onSurfaceVariant)),
                          ),
                        ),
                      )
                    else
                      Card(
                        child: Column(
                          children: [
                            for (var i = 0;
                                i < _dashboard!.recentBills.length;
                                i++) ...[
                              if (i > 0) const Divider(height: 1),
                              _BillRow(bill: _dashboard!.recentBills[i]),
                            ],
                          ],
                        ),
                      ),
                  ],
                ],
              ),
            ),
    );
  }
}

/// Bills tab: status filter + dense list with inline expandable charges.
class _BillsTab extends StatefulWidget {
  const _BillsTab();

  @override
  State<_BillsTab> createState() => _BillsTabState();
}

class _BillsTabState extends State<_BillsTab> {
  static const _statuses = ['FINAL', 'DRAFT', 'CANCELLED'];

  List<PatientBillResponse> _bills = [];
  bool _loading = false;
  String _status = 'FINAL';
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
        '/api/v1/patient-portal/billing/bills?status=$_status',
        fromJson: (json) => (json as List? ?? [])
            .map((e) =>
                PatientBillResponse.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
      if (mounted) {
        setState(() {
          _bills = bills;
          _error = null;
        });
      }
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.error.message);
    } catch (e) {
      if (mounted) setState(() => _error = 'Failed to load bills: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return PageContainer(
      scrollable: false,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text('My Bills', style: theme.textTheme.titleLarge),
              const Spacer(),
              IconButton(
                tooltip: 'Refresh',
                onPressed: _loading ? null : _loadBills,
                icon: const Icon(Icons.refresh),
              ),
            ],
          ),
          const SizedBox(height: Space.md),
          Wrap(
            spacing: Space.sm,
            children: [
              for (final s in _statuses)
                ChoiceChip(
                  label: Text(s.replaceAll('_', ' '),
                      style: theme.textTheme.labelSmall),
                  selected: _status == s,
                  visualDensity: VisualDensity.compact,
                  onSelected: (_) {
                    setState(() => _status = s);
                    _loadBills();
                  },
                ),
            ],
          ),
          const SizedBox(height: Space.md),
          if (_error != null) ...[
            MessageBanner.error(_error!),
            const SizedBox(height: Space.md),
          ],
          if (_bills.isEmpty && !_loading)
            Card(
              child: Padding(
                padding: const EdgeInsets.all(Space.xl),
                child: Center(
                  child: Text(
                      'No ${_status.replaceAll('_', ' ').toLowerCase()} bills',
                      style: theme.textTheme.bodyMedium?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant)),
                ),
              ),
            )
          else
            Expanded(
              child: Card(
                child: ListView.separated(
                  itemCount: _bills.length,
                  separatorBuilder: (_, __) => const Divider(height: 1),
                  itemBuilder: (context, i) =>
                      _BillRow(bill: _bills[i], expandable: true),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

/// One dense bill row; optionally expands to show the charge breakdown.
class _BillRow extends StatelessWidget {
  const _BillRow({required this.bill, this.expandable = false});

  final PatientBillResponse bill;
  final bool expandable;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    final title = Row(
      children: [
        SizedBox(
          width: 150,
          child: Text(bill.billNumber,
              style: theme.textTheme.bodyMedium
                  ?.copyWith(fontWeight: FontWeight.w600)),
        ),
        Expanded(
          child: Text(
            '${bill.generatedAt.year}-${bill.generatedAt.month.toString().padLeft(2, '0')}-${bill.generatedAt.day.toString().padLeft(2, '0')}',
            style: theme.textTheme.bodySmall
                ?.copyWith(color: theme.colorScheme.onSurfaceVariant),
          ),
        ),
        Text('₹${bill.grandTotal.toStringAsFixed(2)}',
            style: theme.textTheme.bodyMedium
                ?.copyWith(fontWeight: FontWeight.w600)),
        const SizedBox(width: Space.md),
        StatusChip.auto(bill.billStatus),
      ],
    );

    if (!expandable) {
      return Padding(
        padding: const EdgeInsets.symmetric(
            horizontal: Space.md, vertical: Space.sm),
        child: title,
      );
    }

    return ExpansionTile(
      dense: true,
      tilePadding: const EdgeInsets.symmetric(horizontal: Space.md),
      childrenPadding:
          const EdgeInsets.fromLTRB(Space.lg, 0, Space.lg, Space.md),
      title: title,
      children: [
        _amountRow(theme, 'Hospital charges',
            '₹${bill.hospitalChargesTotal.toStringAsFixed(2)}'),
        _amountRow(theme, 'Pharmacy (GST invoices)',
            '₹${bill.erpInvoicesTotal.toStringAsFixed(2)}'),
        if (bill.discountAmount > 0)
          _amountRow(theme, 'Discount',
              '-₹${bill.discountAmount.toStringAsFixed(2)}'),
        const Divider(height: Space.md),
        _amountRow(theme, 'Total', '₹${bill.grandTotal.toStringAsFixed(2)}',
            bold: true),
        if (bill.charges.isNotEmpty) ...[
          const SizedBox(height: Space.sm),
          for (final c in bill.charges)
            Padding(
              padding: const EdgeInsets.only(bottom: Space.xxs),
              child: Row(
                children: [
                  Expanded(
                    child: Text('${c.serviceName} × ${c.quantity}',
                        style: theme.textTheme.labelSmall?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant)),
                  ),
                  Text('₹${c.totalAmount.toStringAsFixed(2)}',
                      style: theme.textTheme.labelSmall),
                ],
              ),
            ),
        ],
      ],
    );
  }

  Widget _amountRow(ThemeData theme, String label, String amount,
      {bool bold = false}) {
    final style = bold
        ? theme.textTheme.bodySmall?.copyWith(fontWeight: FontWeight.w700)
        : theme.textTheme.bodySmall;
    return Padding(
      padding: const EdgeInsets.only(bottom: Space.xxs),
      child: Row(
        children: [
          Expanded(child: Text(label, style: style)),
          Text(amount, style: style),
        ],
      ),
    );
  }
}
