import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/auth/auth_state.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/app_shell.dart';
import '../dashboard/dashboard_screen.dart';
import '../expense/expense_screen.dart';
import '../inventory/item_master_screen.dart';
import '../inventory/otc_sale_screen.dart';
import '../ipd/ipd_screen.dart';
import '../lab/lab_screen.dart';
import '../nursing/nursing_screen.dart';
import '../payroll/payroll_screen.dart';
import '../tpa/tpa_screen.dart';

/// Admin role home: back-office accounting — operating expenses and HR/payroll.
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

    return AppShell(
      title: 'Admin',
      destinations: const [
        ShellDestination(
          label: 'Dashboard',
          icon: Icons.dashboard_outlined,
          selectedIcon: Icons.dashboard,
        ),
        ShellDestination(
          label: 'Expenses',
          icon: Icons.receipt_outlined,
          selectedIcon: Icons.receipt,
        ),
        ShellDestination(
          label: 'Payroll',
          icon: Icons.payments_outlined,
          selectedIcon: Icons.payments,
        ),
        ShellDestination(
          label: 'Lab',
          icon: Icons.science_outlined,
          selectedIcon: Icons.science,
        ),
        ShellDestination(
          label: 'IPD',
          icon: Icons.local_hotel_outlined,
          selectedIcon: Icons.local_hotel,
        ),
        ShellDestination(
          label: 'Nursing',
          icon: Icons.assignment_outlined,
          selectedIcon: Icons.assignment,
        ),
        ShellDestination(
          label: 'Pharmacy',
          icon: Icons.inventory_2_outlined,
          selectedIcon: Icons.inventory_2,
        ),
        ShellDestination(
          label: 'OTC Sale',
          icon: Icons.point_of_sale_outlined,
          selectedIcon: Icons.point_of_sale,
        ),
        ShellDestination(
          label: 'TPA',
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
      body: switch (_index) {
        0 => const DashboardScreen(),
        1 => const ExpenseScreen(),
        2 => const PayrollScreen(),
        3 => const LabScreen(),
        4 => const IpdScreen(),
        5 => const NursingScreen(),
        6 => const ItemMasterScreen(),
        7 => const OtcSaleScreen(),
        _ => const TpaScreen(),
      },
    );
  }
}
