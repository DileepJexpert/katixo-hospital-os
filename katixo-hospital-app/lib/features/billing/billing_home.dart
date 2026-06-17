import 'package:flutter/material.dart';
import '../../core/widgets/security_button.dart';
import 'package:provider/provider.dart';

import '../../core/auth/auth_state.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/app_shell.dart';
import '../expense/expense_screen.dart';
import '../report/financial_reports_screen.dart';
import '../tpa/tpa_screen.dart';
import '../vendor/vendors_screen.dart';
import 'bills_screen.dart';
import 'packages_screen.dart';
import 'tariffs_screen.dart';

/// Billing role home: bills, expenses and TPA/insurance.
class BillingHome extends StatefulWidget {
  const BillingHome({super.key});

  @override
  State<BillingHome> createState() => _BillingHomeState();
}

class _BillingHomeState extends State<BillingHome> {
  int _index = 0;

  @override
  Widget build(BuildContext context) {
    final authState = context.watch<AuthState>();
    final role = authState.currentUser?.role ?? '';
    final isAdmin = role == 'ADMIN' || role == 'SUPER_ADMIN';

    final tabs = <({ShellDestination dest, Widget body})>[
      (dest: const ShellDestination(label: 'Bills', icon: Icons.receipt_long_outlined, selectedIcon: Icons.receipt_long), body: const BillsScreen()),
      (dest: const ShellDestination(label: 'Expenses', icon: Icons.receipt_outlined, selectedIcon: Icons.receipt), body: const ExpenseScreen()),
      (dest: const ShellDestination(label: 'Vendors', icon: Icons.store_outlined, selectedIcon: Icons.store), body: const VendorsScreen()),
      (dest: const ShellDestination(label: 'TPA / Insurance', icon: Icons.health_and_safety_outlined, selectedIcon: Icons.health_and_safety), body: const TpaScreen()),
      (dest: const ShellDestination(label: 'Reports', icon: Icons.assessment_outlined, selectedIcon: Icons.assessment), body: const FinancialReportsScreen()),
      (dest: const ShellDestination(label: 'Packages', icon: Icons.inventory_2_outlined, selectedIcon: Icons.inventory_2), body: const PackagesScreen()),
      if (isAdmin)
        (dest: const ShellDestination(label: 'Tariffs', icon: Icons.price_change_outlined, selectedIcon: Icons.price_change), body: const TariffsScreen()),
    ];
    final selected = _index.clamp(0, tabs.length - 1);
    return AppShell(
      title: 'Billing',
      destinations: [for (final t in tabs) t.dest],
      selectedIndex: selected,
      onDestinationSelected: (i) => setState(() => _index = i),
      actions: [
        if (authState.currentUser != null)
          Center(
            child: Padding(
              padding: const EdgeInsets.only(right: Space.sm),
              child: Text(authState.currentUser!.name,
                  style: Theme.of(context).textTheme.labelLarge),
            ),
          ),
        const SecurityButton(),
        IconButton(
          tooltip: 'Sign out',
          icon: const Icon(Icons.logout_outlined),
          onPressed: () => authState.logout(),
        ),
      ],
      body: tabs[selected].body,
    );
  }
}
