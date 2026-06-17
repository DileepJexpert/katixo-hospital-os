import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/auth/auth_state.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/app_shell.dart';
import '../inventory/item_master_screen.dart';
import '../inventory/otc_sale_screen.dart';
import '../nursing/nursing_screen.dart';
import '../prescription/prescriptions_screen.dart';
import 'pharmacy_sales_screen.dart';
import 'pharmacy_queue_screen.dart';

/// Pharmacist role home: dispense queue, item master, OTC sale and ward indents.
class PharmacistHome extends StatefulWidget {
  const PharmacistHome({super.key});

  @override
  State<PharmacistHome> createState() => _PharmacistHomeState();
}

class _PharmacistHomeState extends State<PharmacistHome> {
  int _index = 0;

  @override
  Widget build(BuildContext context) {
    final authState = context.watch<AuthState>();
    final theme = Theme.of(context);

    return AppShell(
      title: 'Pharmacy',
      destinations: const [
        ShellDestination(
          label: 'Dispense Queue',
          icon: Icons.local_pharmacy_outlined,
          selectedIcon: Icons.local_pharmacy,
        ),
        ShellDestination(
          label: 'Item Master',
          icon: Icons.inventory_2_outlined,
          selectedIcon: Icons.inventory_2,
        ),
        ShellDestination(
          label: 'OTC Sale',
          icon: Icons.point_of_sale_outlined,
          selectedIcon: Icons.point_of_sale,
        ),
        ShellDestination(
          label: 'Ward Indents',
          icon: Icons.assignment_outlined,
          selectedIcon: Icons.assignment,
        ),
        ShellDestination(
          label: 'Sales',
          icon: Icons.receipt_long_outlined,
          selectedIcon: Icons.receipt_long,
        ),
        ShellDestination(
          label: 'Prescriptions',
          icon: Icons.medical_information_outlined,
          selectedIcon: Icons.medical_information,
        ),
      ],
      selectedIndex: _index,
      onDestinationSelected: (i) => setState(() => _index = i),
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
      body: switch (_index) {
        1 => const ItemMasterScreen(),
        2 => const OtcSaleScreen(),
        3 => const NursingScreen(),
        4 => const PharmacySalesScreen(),
        5 => const PrescriptionsScreen(),
        _ => const PharmacyQueueScreen(),
      },
    );
  }
}
