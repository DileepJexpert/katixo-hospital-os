import 'package:flutter/material.dart';

import '../responsive/breakpoints.dart';
import '../theme/design_tokens.dart';
import 'theme_switcher.dart';

/// One navigation destination (a role module).
class ShellDestination {
  const ShellDestination({
    required this.label,
    required this.icon,
    required this.selectedIcon,
  });

  final String label;
  final IconData icon;
  final IconData selectedIcon;
}

/// Adaptive scaffold:
///  - mobile  → bottom NavigationBar
///  - tablet  → compact NavigationRail
///  - desktop → extended NavigationRail (labels visible)
///  - large   → same as desktop, content width clamped by PageContainer
///
/// All chrome (app bar, nav) lives here so every screen gets the
/// same frame automatically.
class AppShell extends StatelessWidget {
  const AppShell({
    super.key,
    required this.title,
    required this.destinations,
    required this.selectedIndex,
    required this.onDestinationSelected,
    required this.body,
    this.actions = const [],
  });

  final String title;
  final List<ShellDestination> destinations;
  final int selectedIndex;
  final ValueChanged<int> onDestinationSelected;
  final Widget body;
  final List<Widget> actions;

  @override
  Widget build(BuildContext context) {
    final isMobile = context.isMobile;
    final extended = context.formFactor == FormFactor.desktop ||
        context.formFactor == FormFactor.large;

    final appBar = AppBar(
      title: Text(title),
      actions: [...actions, const ThemeSwitcher(), const SizedBox(width: Space.sm)],
    );

    if (isMobile) {
      return Scaffold(
        appBar: appBar,
        body: body,
        bottomNavigationBar: NavigationBar(
          selectedIndex: selectedIndex,
          onDestinationSelected: onDestinationSelected,
          destinations: [
            for (final d in destinations)
              NavigationDestination(
                icon: Icon(d.icon),
                selectedIcon: Icon(d.selectedIcon),
                label: d.label,
              ),
          ],
        ),
      );
    }

    return Scaffold(
      appBar: appBar,
      body: Row(
        children: [
          // Scrollable rail so homes with many modules never overflow vertically.
          LayoutBuilder(
            builder: (context, constraints) => SingleChildScrollView(
              child: ConstrainedBox(
                constraints: BoxConstraints(minHeight: constraints.maxHeight),
                child: IntrinsicHeight(
                  child: NavigationRail(
                    extended: extended,
                    minExtendedWidth: Metrics.navRailExtendedWidth,
                    minWidth: Metrics.navRailWidth,
                    selectedIndex: selectedIndex,
                    onDestinationSelected: onDestinationSelected,
                    labelType: extended ? null : NavigationRailLabelType.none,
                    destinations: [
                      for (final d in destinations)
                        NavigationRailDestination(
                          icon: Icon(d.icon),
                          selectedIcon: Icon(d.selectedIcon),
                          label: Text(d.label),
                        ),
                    ],
                  ),
                ),
              ),
            ),
          ),
          const VerticalDivider(),
          Expanded(child: body),
        ],
      ),
    );
  }
}
