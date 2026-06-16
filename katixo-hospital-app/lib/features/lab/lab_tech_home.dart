import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/auth/auth_state.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/app_shell.dart';
import 'lab_screen.dart';

/// Lab technician role home: the lab console (worklist → sample → result).
class LabTechHome extends StatelessWidget {
  const LabTechHome({super.key});

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
      ],
      selectedIndex: 0,
      onDestinationSelected: (_) {},
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
      body: const LabScreen(),
    );
  }
}
