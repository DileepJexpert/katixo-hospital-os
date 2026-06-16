import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/auth/auth_state.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/app_shell.dart';
import '../ipd/ipd_screen.dart';
import 'nursing_screen.dart';

/// Nurse role home: ward indents + the inpatient (IPD) board.
class NurseHome extends StatefulWidget {
  const NurseHome({super.key});

  @override
  State<NurseHome> createState() => _NurseHomeState();
}

class _NurseHomeState extends State<NurseHome> {
  int _index = 0;

  @override
  Widget build(BuildContext context) {
    final authState = context.watch<AuthState>();
    return AppShell(
      title: 'Nursing',
      destinations: const [
        ShellDestination(
          label: 'Indents',
          icon: Icons.assignment_outlined,
          selectedIcon: Icons.assignment,
        ),
        ShellDestination(
          label: 'IPD',
          icon: Icons.local_hotel_outlined,
          selectedIcon: Icons.local_hotel,
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
        0 => const NursingScreen(),
        _ => const IpdScreen(),
      },
    );
  }
}
