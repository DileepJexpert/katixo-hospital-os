import 'package:flutter/material.dart';
import '../../core/widgets/security_button.dart';
import 'package:provider/provider.dart';

import '../../core/auth/auth_state.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/app_shell.dart';
import '../consent/consent_screen.dart';
import '../ipd/ipd_screen.dart';
import '../nabh/nabh_screen.dart';
import 'nursing_screen.dart';
import 'vitals_screen.dart';

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
          label: 'Vitals',
          icon: Icons.monitor_heart_outlined,
          selectedIcon: Icons.monitor_heart,
        ),
        ShellDestination(
          label: 'IPD',
          icon: Icons.local_hotel_outlined,
          selectedIcon: Icons.local_hotel,
        ),
        ShellDestination(
          label: 'NABH',
          icon: Icons.verified_outlined,
          selectedIcon: Icons.verified,
        ),
        ShellDestination(
          label: 'Consent',
          icon: Icons.assignment_turned_in_outlined,
          selectedIcon: Icons.assignment_turned_in,
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
        const SecurityButton(),
        IconButton(
          tooltip: 'Sign out',
          icon: const Icon(Icons.logout_outlined),
          onPressed: () => authState.logout(),
        ),
      ],
      body: switch (_index) {
        1 => const VitalsScreen(),
        2 => const IpdScreen(),
        3 => const NabhScreen(),
        4 => const ConsentScreen(),
        _ => const NursingScreen(),
      },
    );
  }
}
