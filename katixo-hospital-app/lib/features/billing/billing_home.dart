import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/auth/auth_state.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/app_shell.dart';
import '../expense/expense_screen.dart';
import '../tpa/tpa_screen.dart';
import 'bills_screen.dart';
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

    final destinations = <ShellDestination>[
      const ShellDestination(
        label: 'Bills',
        icon: Icons.receipt_long_outlined,
        selectedIcon: Icons.receipt_long,
      ),
      const ShellDestination(
        label: 'Expenses',
        icon: Icons.receipt_outlined,
        selectedIcon: Icons.receipt,
      ),
      const ShellDestination(
        label: 'TPA / Insurance',
        icon: Icons.health_and_safety_outlined,
        selectedIcon: Icons.health_and_safety,
      ),
      if (isAdmin)
        const ShellDestination(
          label: 'Tariffs',
          icon: Icons.price_change_outlined,
          selectedIcon: Icons.price_change,
        ),
    ];
    final selected = _index.clamp(0, destinations.length - 1);
    return AppShell(
      title: 'Billing',
      destinations: destinations,
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
        IconButton(
          tooltip: 'Sign out',
          icon: const Icon(Icons.logout_outlined),
          onPressed: () => authState.logout(),
        ),
      ],
      body: switch (selected) {
        1 => const ExpenseScreen(),
        2 => const TpaScreen(),
        3 => const TariffsScreen(),
        _ => const BillsScreen(),
      },
    );
  }
}
