import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/auth/auth_state.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/app_shell.dart';
import '../lab/lab_screen.dart';
import '../nursing/nursing_screen.dart';
import 'doctor_worklist_screen.dart';

/// Doctor role home: consult worklist, lab and ward indents.
class DoctorHome extends StatefulWidget {
  const DoctorHome({super.key});

  @override
  State<DoctorHome> createState() => _DoctorHomeState();
}

class _DoctorHomeState extends State<DoctorHome> {
  int _index = 0;

  @override
  Widget build(BuildContext context) {
    final authState = context.watch<AuthState>();
    final theme = Theme.of(context);

    return AppShell(
      title: 'Doctor',
      destinations: const [
        ShellDestination(
          label: 'Worklist',
          icon: Icons.list_alt_outlined,
          selectedIcon: Icons.list_alt,
        ),
        ShellDestination(
          label: 'Lab',
          icon: Icons.science_outlined,
          selectedIcon: Icons.science,
        ),
        ShellDestination(
          label: 'Ward Indents',
          icon: Icons.assignment_outlined,
          selectedIcon: Icons.assignment,
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
        2 => const NursingScreen(),
        1 => const LabScreen(),
        _ => const DoctorWorklistScreen(),
      },
    );
  }
}
