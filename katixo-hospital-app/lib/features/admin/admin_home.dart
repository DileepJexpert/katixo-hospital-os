import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/auth/auth_state.dart';
import '../../core/config/feature_flags.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/app_shell.dart';
import '../billing/tariffs_screen.dart';
import '../dashboard/dashboard_screen.dart';
import '../expense/expense_screen.dart';
import '../report/financial_reports_screen.dart';
import '../vendor/vendors_screen.dart';
import '../inventory/item_master_screen.dart';
import '../inventory/otc_sale_screen.dart';
import '../ipd/ipd_screen.dart';
import '../lab/lab_screen.dart';
import '../nursing/nursing_screen.dart';
import '../notification/notifications_screen.dart';
import '../payroll/payroll_screen.dart';
import '../settings/settings_screen.dart';
import '../tpa/tpa_screen.dart';

/// Admin / owner cockpit: finance, clinical, pharmacy (if enabled), insurance,
/// and hospital settings. Pharmacy tabs respect the in-house-pharmacy flag.
class AdminHome extends StatefulWidget {
  const AdminHome({super.key});

  @override
  State<AdminHome> createState() => _AdminHomeState();
}

class _AdminHomeState extends State<AdminHome> {
  int _index = 0;

  @override
  Widget build(BuildContext context) {
    final authState = context.watch<AuthState>();
    final flags = context.watch<FeatureFlags>();

    final tabs = <({ShellDestination dest, Widget body})>[
      (dest: const ShellDestination(label: 'Dashboard', icon: Icons.dashboard_outlined, selectedIcon: Icons.dashboard), body: const DashboardScreen()),
      (dest: const ShellDestination(label: 'Expenses', icon: Icons.receipt_outlined, selectedIcon: Icons.receipt), body: const ExpenseScreen()),
      (dest: const ShellDestination(label: 'Payroll', icon: Icons.payments_outlined, selectedIcon: Icons.payments), body: const PayrollScreen()),
      (dest: const ShellDestination(label: 'Lab', icon: Icons.science_outlined, selectedIcon: Icons.science), body: const LabScreen()),
      (dest: const ShellDestination(label: 'IPD', icon: Icons.local_hotel_outlined, selectedIcon: Icons.local_hotel), body: const IpdScreen()),
      (dest: const ShellDestination(label: 'Nursing', icon: Icons.assignment_outlined, selectedIcon: Icons.assignment), body: const NursingScreen()),
      if (flags.pharmacyEnabled)
        (dest: const ShellDestination(label: 'Pharmacy', icon: Icons.inventory_2_outlined, selectedIcon: Icons.inventory_2), body: const ItemMasterScreen()),
      if (flags.pharmacyEnabled)
        (dest: const ShellDestination(label: 'OTC Sale', icon: Icons.point_of_sale_outlined, selectedIcon: Icons.point_of_sale), body: const OtcSaleScreen()),
      (dest: const ShellDestination(label: 'TPA', icon: Icons.health_and_safety_outlined, selectedIcon: Icons.health_and_safety), body: const TpaScreen()),
      (dest: const ShellDestination(label: 'Vendors', icon: Icons.store_outlined, selectedIcon: Icons.store), body: const VendorsScreen()),
      (dest: const ShellDestination(label: 'Tariffs', icon: Icons.price_change_outlined, selectedIcon: Icons.price_change), body: const TariffsScreen()),
      (dest: const ShellDestination(label: 'Reports', icon: Icons.assessment_outlined, selectedIcon: Icons.assessment), body: const FinancialReportsScreen()),
      (dest: const ShellDestination(label: 'Notifications', icon: Icons.notifications_outlined, selectedIcon: Icons.notifications), body: const NotificationsScreen()),
      (dest: const ShellDestination(label: 'Settings', icon: Icons.settings_outlined, selectedIcon: Icons.settings), body: const SettingsScreen()),
    ];
    final index = _index.clamp(0, tabs.length - 1);

    return AppShell(
      title: 'Admin',
      destinations: [for (final t in tabs) t.dest],
      selectedIndex: index,
      onDestinationSelected: (i) => setState(() => _index = i),
      actions: [
        if (authState.currentUser != null)
          Center(
            child: Padding(
              padding: const EdgeInsets.only(right: Space.sm),
              child: Text(
                authState.currentUser!.name,
                style: Theme.of(context).textTheme.labelLarge,
              ),
            ),
          ),
        IconButton(
          tooltip: 'Sign out',
          icon: const Icon(Icons.logout_outlined),
          onPressed: () => authState.logout(),
        ),
      ],
      body: tabs[index].body,
    );
  }
}
