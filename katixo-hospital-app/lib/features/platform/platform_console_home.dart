import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/auth/auth_state.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/app_shell.dart';
import 'platform_tenants_screen.dart';

/// Home for a PLATFORM_ADMIN (the SaaS control plane). Separate from every
/// hospital role home — a platform operator manages tenants, not patient data.
/// Single module today (Tenants); more control-plane tools can slot in here.
class PlatformConsoleHome extends StatefulWidget {
  const PlatformConsoleHome({super.key});

  @override
  State<PlatformConsoleHome> createState() => _PlatformConsoleHomeState();
}

class _PlatformConsoleHomeState extends State<PlatformConsoleHome> {
  int _index = 0;

  @override
  Widget build(BuildContext context) {
    final authState = context.watch<AuthState>();
    return AppShell(
      title: 'Platform Console',
      destinations: const [
        ShellDestination(
          label: 'Tenants',
          icon: Icons.apartment_outlined,
          selectedIcon: Icons.apartment,
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
      body: const PlatformTenantsScreen(),
    );
  }
}
