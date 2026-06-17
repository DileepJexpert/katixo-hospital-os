import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/auth/auth_state.dart';
import '../../core/config/feature_flags.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/app_shell.dart';
import '../billing/bills_screen.dart';
import '../billing/tariffs_screen.dart';
import '../dashboard/dashboard_screen.dart';
import '../report/financial_reports_screen.dart';
import '../vendor/vendors_screen.dart';
import '../doctor/doctor_worklist_screen.dart';
import '../expense/expense_screen.dart';
import '../front_desk/registration_screen.dart';
import '../front_desk/walk_in_screen.dart';
import '../inventory/item_master_screen.dart';
import '../inventory/otc_sale_screen.dart';
import '../ipd/ipd_screen.dart';
import '../lab/lab_screen.dart';
import '../notification/notifications_screen.dart';
import '../nursing/nursing_screen.dart';
import '../patient/patients_screen.dart';
import '../payroll/payroll_screen.dart';
import '../pharmacy/pharmacy_queue_screen.dart';
import '../pharmacy/pharmacy_sales_screen.dart';
import '../prescription/prescriptions_screen.dart';
import '../settings/settings_screen.dart';
import '../tpa/tpa_screen.dart';

/// Super-admin: a single login that can see every module (testing / owner-operator).
/// Backend grants SUPER_ADMIN all role authorities, so every screen's API works.
class SuperAdminHome extends StatefulWidget {
  const SuperAdminHome({super.key});

  @override
  State<SuperAdminHome> createState() => _SuperAdminHomeState();
}

class _SuperAdminHomeState extends State<SuperAdminHome> {
  int _index = 0;

  @override
  Widget build(BuildContext context) {
    final authState = context.watch<AuthState>();
    final flags = context.watch<FeatureFlags>();

    final tabs = <({ShellDestination dest, Widget body})>[
      (dest: const ShellDestination(label: 'Dashboard', icon: Icons.dashboard_outlined, selectedIcon: Icons.dashboard), body: const DashboardScreen()),
      (dest: const ShellDestination(label: 'Patients', icon: Icons.people_outline, selectedIcon: Icons.people), body: const PatientsScreen()),
      (dest: const ShellDestination(label: 'Register', icon: Icons.person_add_outlined, selectedIcon: Icons.person_add), body: const RegistrationScreen()),
      (dest: const ShellDestination(label: 'Walk-in', icon: Icons.directions_walk_outlined, selectedIcon: Icons.directions_walk), body: const WalkInScreen()),
      (dest: const ShellDestination(label: 'IPD', icon: Icons.local_hotel_outlined, selectedIcon: Icons.local_hotel), body: const IpdScreen()),
      (dest: const ShellDestination(label: 'Nursing', icon: Icons.assignment_outlined, selectedIcon: Icons.assignment), body: const NursingScreen()),
      (dest: const ShellDestination(label: 'Consult Queue', icon: Icons.list_alt_outlined, selectedIcon: Icons.list_alt), body: const DoctorWorklistScreen()),
      (dest: const ShellDestination(label: 'Prescriptions', icon: Icons.medical_information_outlined, selectedIcon: Icons.medical_information), body: const PrescriptionsScreen()),
      (dest: const ShellDestination(label: 'Lab', icon: Icons.science_outlined, selectedIcon: Icons.science), body: const LabScreen()),
      (dest: const ShellDestination(label: 'Bills', icon: Icons.receipt_long_outlined, selectedIcon: Icons.receipt_long), body: const BillsScreen()),
      if (flags.pharmacyEnabled)
        (dest: const ShellDestination(label: 'Dispense Queue', icon: Icons.local_pharmacy_outlined, selectedIcon: Icons.local_pharmacy), body: const PharmacyQueueScreen()),
      if (flags.pharmacyEnabled)
        (dest: const ShellDestination(label: 'Pharmacy', icon: Icons.inventory_2_outlined, selectedIcon: Icons.inventory_2), body: const ItemMasterScreen()),
      if (flags.pharmacyEnabled)
        (dest: const ShellDestination(label: 'Pharmacy Sales', icon: Icons.receipt_long_outlined, selectedIcon: Icons.receipt_long), body: const PharmacySalesScreen()),
      if (flags.pharmacyEnabled)
        (dest: const ShellDestination(label: 'OTC Sale', icon: Icons.point_of_sale_outlined, selectedIcon: Icons.point_of_sale), body: const OtcSaleScreen()),
      (dest: const ShellDestination(label: 'Expenses', icon: Icons.receipt_outlined, selectedIcon: Icons.receipt), body: const ExpenseScreen()),
      (dest: const ShellDestination(label: 'Payroll', icon: Icons.payments_outlined, selectedIcon: Icons.payments), body: const PayrollScreen()),
      (dest: const ShellDestination(label: 'TPA', icon: Icons.health_and_safety_outlined, selectedIcon: Icons.health_and_safety), body: const TpaScreen()),
      (dest: const ShellDestination(label: 'Vendors', icon: Icons.store_outlined, selectedIcon: Icons.store), body: const VendorsScreen()),
      (dest: const ShellDestination(label: 'Tariffs', icon: Icons.price_change_outlined, selectedIcon: Icons.price_change), body: const TariffsScreen()),
      (dest: const ShellDestination(label: 'Reports', icon: Icons.assessment_outlined, selectedIcon: Icons.assessment), body: const FinancialReportsScreen()),
      (dest: const ShellDestination(label: 'Notifications', icon: Icons.notifications_outlined, selectedIcon: Icons.notifications), body: const NotificationsScreen()),
      (dest: const ShellDestination(label: 'Settings', icon: Icons.settings_outlined, selectedIcon: Icons.settings), body: const SettingsScreen()),
    ];
    final index = _index.clamp(0, tabs.length - 1);

    return AppShell(
      title: 'Super Admin',
      destinations: [for (final t in tabs) t.dest],
      selectedIndex: index,
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
      body: tabs[index].body,
    );
  }
}
