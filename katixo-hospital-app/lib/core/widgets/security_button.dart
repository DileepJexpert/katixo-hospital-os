import 'package:flutter/material.dart';

import '../../features/settings/security_screen.dart';

/// App-bar action that opens the per-user Account Security screen
/// (two-factor enrollment / disable). Available to every signed-in user,
/// so each role home drops it next to the sign-out button.
class SecurityButton extends StatelessWidget {
  const SecurityButton({super.key});

  @override
  Widget build(BuildContext context) {
    return IconButton(
      tooltip: 'Account security',
      icon: const Icon(Icons.shield_outlined),
      onPressed: () => Navigator.of(context).push(
        MaterialPageRoute<void>(
          builder: (_) => Scaffold(
            appBar: AppBar(title: const Text('Account Security')),
            body: const SecurityScreen(),
          ),
        ),
      ),
    );
  }
}
