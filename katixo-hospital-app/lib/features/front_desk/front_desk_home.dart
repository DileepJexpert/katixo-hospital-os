import 'package:flutter/material.dart';
import '../../core/widgets/security_button.dart';
import 'package:provider/provider.dart';

import '../../core/auth/auth_state.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/app_shell.dart';
import '../ipd/ipd_screen.dart';
import '../opd/appointments_screen.dart';
import '../patient/patients_screen.dart';
import 'registration_screen.dart';
import 'walk_in_screen.dart';

/// Front-desk role home: registration + walk-in OPD visit.
class FrontDeskHome extends StatefulWidget {
  const FrontDeskHome({super.key});

  @override
  State<FrontDeskHome> createState() => _FrontDeskHomeState();
}

class _FrontDeskHomeState extends State<FrontDeskHome> {
  int _index = 0;

  @override
  Widget build(BuildContext context) {
    final authState = context.watch<AuthState>();

    return AppShell(
      title: 'Front Desk',
      destinations: const [
        ShellDestination(
          label: 'Registration',
          icon: Icons.person_add_outlined,
          selectedIcon: Icons.person_add,
        ),
        ShellDestination(
          label: 'Walk-in Visit',
          icon: Icons.directions_walk_outlined,
          selectedIcon: Icons.directions_walk,
        ),
        ShellDestination(
          label: 'Appointments',
          icon: Icons.event_note_outlined,
          selectedIcon: Icons.event_note,
        ),
        ShellDestination(
          label: 'IPD',
          icon: Icons.local_hotel_outlined,
          selectedIcon: Icons.local_hotel,
        ),
        ShellDestination(
          label: 'Patients',
          icon: Icons.people_outline,
          selectedIcon: Icons.people,
        ),
      ],
      selectedIndex: _index,
      onDestinationSelected: (i) => setState(() => _index = i),
      actions: [
        if (authState.currentUser != null)
          Center(
            child: Padding(
              padding: const EdgeInsets.only(right: Space.sm),
              child: Text(
                authState.currentUser!.name,
                style: Theme.of(context).textTheme.labelLarge,
              ),
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
        1 => const WalkInScreen(),
        2 => const AppointmentsScreen(),
        3 => const IpdScreen(),
        4 => const PatientsScreen(),
        _ => const RegistrationScreen(),
      },
    );
  }
}
