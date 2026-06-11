import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/dashboard_models.dart';
import '../../core/api/http_client.dart';
import '../../core/auth/auth_state.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/app_shell.dart';
import '../../core/widgets/kpi_tile.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// Owner/Admin dashboard: operational KPIs and metrics.
class OwnerDashboard extends StatefulWidget {
  const OwnerDashboard({super.key});

  @override
  State<OwnerDashboard> createState() => _OwnerDashboardState();
}

class _OwnerDashboardState extends State<OwnerDashboard> {
  DashboardMetrics? _metrics;
  bool _loading = false;
  String? _error;
  Timer? _refreshTimer;

  @override
  void initState() {
    super.initState();
    _loadMetrics();
    _refreshTimer =
        Timer.periodic(const Duration(seconds: 30), (_) => _loadMetrics());
  }

  @override
  void dispose() {
    _refreshTimer?.cancel();
    super.dispose();
  }

  Future<void> _loadMetrics() async {
    try {
      final api = context.read<ApiClient>();
      final metrics = await api.get<DashboardMetrics>(
        '/api/v1/dashboard/metrics',
        fromJson: (json) =>
            DashboardMetrics.fromJson(json as Map<String, dynamic>),
      );
      if (mounted) {
        setState(() {
          _metrics = metrics;
          _error = null;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() => _error = 'Failed to load metrics: $e');
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final authState = context.watch<AuthState>();
    final theme = Theme.of(context);
    final gridCols = context.gridColumns;

    return AppShell(
      title: 'Admin',
      destinations: const [
        ShellDestination(
          label: 'Dashboard',
          icon: Icons.dashboard_outlined,
          selectedIcon: Icons.dashboard,
        ),
      ],
      selectedIndex: 0,
      onDestinationSelected: (_) {},
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
      body: PageContainer(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Text('Dashboard', style: theme.textTheme.titleLarge),
                const Spacer(),
                IconButton(
                  tooltip: 'Refresh',
                  onPressed: _loading ? null : _loadMetrics,
                  icon: const Icon(Icons.refresh),
                ),
              ],
            ),
            const SizedBox(height: Space.md),

            if (_error != null) ...[
              MessageBanner.error(_error!),
              const SizedBox(height: Space.md),
            ],

            if (_metrics == null)
              Center(
                child: Padding(
                  padding: const EdgeInsets.all(Space.xl),
                  child: CircularProgressIndicator(),
                ),
              )
            else ...[
              // OPD Metrics
              Text('Outpatient (OPD)', style: theme.textTheme.titleMedium),
              const SizedBox(height: Space.md),
              Wrap(
                spacing: Space.md,
                runSpacing: Space.md,
                children: [
                  SizedBox(
                    width: _kpiWidth(context, gridCols),
                    child: KpiTile(
                      label: 'Visits Today',
                      value: _metrics!.opdMetrics.visitsToday.toString(),
                      icon: Icons.people_outline,
                    ),
                  ),
                  SizedBox(
                    width: _kpiWidth(context, gridCols),
                    child: KpiTile(
                      label: 'Visits This Month',
                      value: _metrics!.opdMetrics.visitsThisMonth.toString(),
                      icon: Icons.calendar_today_outlined,
                    ),
                  ),
                  SizedBox(
                    width: _kpiWidth(context, gridCols),
                    child: KpiTile(
                      label: 'Avg Consultation Fee',
                      value: _metrics!.opdMetrics.averageConsultationFee
                          .toStringAsFixed(0),
                      icon: Icons.currency_rupee_outlined,
                      unit: '₹',
                    ),
                  ),
                  SizedBox(
                    width: _kpiWidth(context, gridCols),
                    child: KpiTile(
                      label: 'OPD Revenue',
                      value: _metrics!.opdMetrics.totalRevenue
                          .toStringAsFixed(0),
                      icon: Icons.trending_up_outlined,
                      unit: '₹',
                      trendDirection: TrendDirection.up,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: Space.xl),

              // IPD Metrics
              Text('Inpatient (IPD)', style: theme.textTheme.titleMedium),
              const SizedBox(height: Space.md),
              Wrap(
                spacing: Space.md,
                runSpacing: Space.md,
                children: [
                  SizedBox(
                    width: _kpiWidth(context, gridCols),
                    child: KpiTile(
                      label: 'Occupancy',
                      value: '${_metrics!.ipdMetrics.occupancyPercentage}%',
                      icon: Icons.bed_outlined,
                      unit:
                          '${_metrics!.ipdMetrics.occupiedBeds}/${_metrics!.ipdMetrics.totalBeds}',
                    ),
                  ),
                  SizedBox(
                    width: _kpiWidth(context, gridCols),
                    child: KpiTile(
                      label: 'Avg Length of Stay',
                      value:
                          _metrics!.ipdMetrics.averageLengthOfStay.toStringAsFixed(1),
                      icon: Icons.calendar_month_outlined,
                      unit: 'days',
                    ),
                  ),
                  SizedBox(
                    width: _kpiWidth(context, gridCols),
                    child: KpiTile(
                      label: 'IPD Revenue',
                      value: _metrics!.ipdMetrics.totalRevenue
                          .toStringAsFixed(0),
                      icon: Icons.trending_up_outlined,
                      unit: '₹',
                    ),
                  ),
                ],
              ),
              const SizedBox(height: Space.xl),

              // Pharmacy Metrics
              Text('Pharmacy', style: theme.textTheme.titleMedium),
              const SizedBox(height: Space.md),
              Wrap(
                spacing: Space.md,
                runSpacing: Space.md,
                children: [
                  SizedBox(
                    width: _kpiWidth(context, gridCols),
                    child: KpiTile(
                      label: 'Dispensed',
                      value: _metrics!.pharmacyMetrics.dispensedCount
                          .toString(),
                      icon: Icons.local_pharmacy_outlined,
                    ),
                  ),
                  SizedBox(
                    width: _kpiWidth(context, gridCols),
                    child: KpiTile(
                      label: 'Pending',
                      value:
                          _metrics!.pharmacyMetrics.pendingCount.toString(),
                      icon: Icons.hourglass_bottom_outlined,
                    ),
                  ),
                  SizedBox(
                    width: _kpiWidth(context, gridCols),
                    child: KpiTile(
                      label: 'Pharmacy Revenue',
                      value: _metrics!.pharmacyMetrics.totalRevenue
                          .toStringAsFixed(0),
                      icon: Icons.trending_up_outlined,
                      unit: '₹',
                    ),
                  ),
                ],
              ),
              const SizedBox(height: Space.xl),

              // Billing Metrics
              Text('Billing', style: theme.textTheme.titleMedium),
              const SizedBox(height: Space.md),
              Wrap(
                spacing: Space.md,
                runSpacing: Space.md,
                children: [
                  SizedBox(
                    width: _kpiWidth(context, gridCols),
                    child: KpiTile(
                      label: 'Bills Generated',
                      value: _metrics!.billingMetrics.billsGenerated
                          .toString(),
                      icon: Icons.receipt_outlined,
                    ),
                  ),
                  SizedBox(
                    width: _kpiWidth(context, gridCols),
                    child: KpiTile(
                      label: 'Bills Finalized',
                      value: _metrics!.billingMetrics.billsFinalized
                          .toString(),
                      icon: Icons.check_circle_outline,
                    ),
                  ),
                  SizedBox(
                    width: _kpiWidth(context, gridCols),
                    child: KpiTile(
                      label: 'Total Revenue',
                      value: _metrics!.billingMetrics.totalRevenue
                          .toStringAsFixed(0),
                      icon: Icons.trending_up_outlined,
                      unit: '₹',
                    ),
                  ),
                  SizedBox(
                    width: _kpiWidth(context, gridCols),
                    child: KpiTile(
                      label: 'Outstanding',
                      value: _metrics!.billingMetrics.outstandingAmount
                          .toStringAsFixed(0),
                      icon: Icons.warning_amber_outlined,
                      unit: '₹',
                    ),
                  ),
                ],
              ),
              const SizedBox(height: Space.xl),

              // Lab Metrics
              Text('Laboratory', style: theme.textTheme.titleMedium),
              const SizedBox(height: Space.md),
              Wrap(
                spacing: Space.md,
                runSpacing: Space.md,
                children: [
                  SizedBox(
                    width: _kpiWidth(context, gridCols),
                    child: KpiTile(
                      label: 'Orders Created',
                      value: _metrics!.labMetrics.ordersCreated.toString(),
                      icon: Icons.science_outlined,
                    ),
                  ),
                  SizedBox(
                    width: _kpiWidth(context, gridCols),
                    child: KpiTile(
                      label: 'Results Completed',
                      value:
                          _metrics!.labMetrics.resultsCompleted.toString(),
                      icon: Icons.done_all_outlined,
                    ),
                  ),
                  SizedBox(
                    width: _kpiWidth(context, gridCols),
                    child: KpiTile(
                      label: 'Pending Approval',
                      value: _metrics!.labMetrics.pendingApproval
                          .toString(),
                      icon: Icons.pending_actions_outlined,
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

  double _kpiWidth(BuildContext context, int cols) {
    final maxWidth = Metrics.maxContentWidth;
    final gutter = context.gutter;
    final availableWidth = maxWidth - (gutter * 2);
    return (availableWidth / cols) - Space.md;
  }
}
