import 'package:flutter/material.dart';

import '../core/responsive/breakpoints.dart';
import '../core/responsive/responsive_builder.dart';
import '../core/theme/design_tokens.dart';
import '../core/widgets/app_shell.dart';
import '../core/widgets/kpi_tile.dart';
import '../core/widgets/status_chip.dart';

/// Demo shell: proves adaptive navigation, design tokens, and
/// theme switching end-to-end. Role modules will replace the
/// placeholder bodies as they are built.
class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  int _index = 0;

  static const _destinations = [
    ShellDestination(
        label: 'Dashboard',
        icon: Icons.dashboard_outlined,
        selectedIcon: Icons.dashboard),
    ShellDestination(
        label: 'Front Desk',
        icon: Icons.badge_outlined,
        selectedIcon: Icons.badge),
    ShellDestination(
        label: 'Doctor',
        icon: Icons.medical_services_outlined,
        selectedIcon: Icons.medical_services),
    ShellDestination(
        label: 'Billing',
        icon: Icons.receipt_long_outlined,
        selectedIcon: Icons.receipt_long),
  ];

  @override
  Widget build(BuildContext context) {
    return AppShell(
      title: 'Katixo Hospital OS',
      destinations: _destinations,
      selectedIndex: _index,
      onDestinationSelected: (i) => setState(() => _index = i),
      body: switch (_index) {
        0 => const _DashboardDemo(),
        _ => _ModulePlaceholder(name: _destinations[_index].label),
      },
    );
  }
}

class _DashboardDemo extends StatelessWidget {
  const _DashboardDemo();

  @override
  Widget build(BuildContext context) {
    return PageContainer(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Today', style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: Space.md),
          GridView.count(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            crossAxisCount: context.gridColumns,
            mainAxisSpacing: Space.md,
            crossAxisSpacing: Space.md,
            childAspectRatio: context.responsive(
                mobile: 3.4, tablet: 3.0, desktop: 2.8, large: 3.2),
            children: const [
              KpiTile(
                  label: 'OPD VISITS',
                  value: '128',
                  icon: Icons.people_outline,
                  trend: '12%',
                  trendUp: true),
              KpiTile(
                  label: 'BED OCCUPANCY',
                  value: '82%',
                  icon: Icons.bed_outlined,
                  trend: '4%',
                  trendUp: true),
              KpiTile(
                  label: 'LAB PENDING REVIEW',
                  value: '7',
                  icon: Icons.science_outlined,
                  trend: '2',
                  trendUp: false),
              KpiTile(
                  label: 'PHARMACY QUEUE',
                  value: '15',
                  icon: Icons.local_pharmacy_outlined),
            ],
          ),
          const SizedBox(height: Space.xl),
          Text('OPD Queue', style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: Space.md),
          Card(
            child: Column(
              children: [
                for (final (token, name, doctor, status) in const [
                  ('T-014', 'Ramesh Kumar', 'Dr. Sharma', 'IN_CONSULTATION'),
                  ('T-015', 'Sunita Devi', 'Dr. Sharma', 'CALLED'),
                  ('T-016', 'Arjun Patel', 'Dr. Mehta', 'IN_QUEUE'),
                  ('T-017', 'Fatima Begum', 'Dr. Mehta', 'IN_QUEUE'),
                ]) ...[
                  ListTile(
                    leading: CircleAvatar(
                      radius: 16,
                      child: Text(token.substring(2),
                          style: Theme.of(context).textTheme.labelSmall),
                    ),
                    title: Text(name),
                    subtitle: Text(doctor),
                    trailing: StatusChip.auto(status),
                  ),
                  if (token != 'T-017') const Divider(),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _ModulePlaceholder extends StatelessWidget {
  const _ModulePlaceholder({required this.name});

  final String name;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.construction_outlined,
              size: 48, color: Theme.of(context).colorScheme.outline),
          const SizedBox(height: Space.md),
          Text('$name module — coming soon',
              style: Theme.of(context).textTheme.titleMedium),
        ],
      ),
    );
  }
}
