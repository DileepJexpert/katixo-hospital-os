import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/auth/auth_state.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/app_shell.dart';
import '../radiology/radiology_screen.dart';
import 'lab_screen.dart';

/// Lab technician role home: the lab console plus radiology (technologists mark
/// studies performed).
class LabTechHome extends StatefulWidget {
  const LabTechHome({super.key});

  @override
  State<LabTechHome> createState() => _LabTechHomeState();
}

class _LabTechHomeState extends State<LabTechHome> {
  int _index = 0;

  @override
  Widget build(BuildContext context) {
    final authState = context.watch<AuthState>();
    return AppShell(
      title: 'Laboratory',
      destinations: const [
        ShellDestination(
          label: 'Lab',
          icon: Icons.science_outlined,
          selectedIcon: Icons.science,
        ),
        ShellDestination(
          label: 'Radiology',
          icon: Icons.scanner_outlined,
          selectedIcon: Icons.scanner,
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
                  style: Theme.of(context).textTheme.labelLarge),
            ),
          ),
        IconButton(
          tooltip: 'Sign out',
          icon: const Icon(Icons.logout_outlined),
          onPressed: () => authState.logout(),
        ),
      ],
      body: switch (_index) {
        1 => const RadiologyScreen(),
        _ => const LabScreen(),
      },
    );
  }
}
