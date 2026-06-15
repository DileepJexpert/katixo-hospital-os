import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/auth/auth_state.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/app_shell.dart';
import '../dashboard/dashboard_screen.dart';
import '../expense/expense_screen.dart';
import '../lab/lab_report_screen.dart';
import '../payroll/payroll_screen.dart';

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
          label: 'Lab Report',
          icon: Icons.science_outlined,
          selectedIcon: Icons.science,
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
        _ => const LabReportScreen(),
      },
    );
  }
}
