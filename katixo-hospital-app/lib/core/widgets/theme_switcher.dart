import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../theme/design_tokens.dart';
import '../theme/theme_controller.dart';

/// App-bar action: quick light/dark toggle + palette menu.
/// Drop this in any AppBar; it talks to the central controller.
class ThemeSwitcher extends StatelessWidget {
  const ThemeSwitcher({super.key});

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<ThemeController>();
    final isDark = controller.isDark(context);

    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        IconButton(
          tooltip: isDark ? 'Switch to light' : 'Switch to dark',
          icon: Icon(isDark ? Icons.light_mode_outlined : Icons.dark_mode_outlined),
          onPressed: () => controller.toggleDark(context),
        ),
        PopupMenuButton<BrandPalette>(
          tooltip: 'Brand palette',
          icon: const Icon(Icons.palette_outlined),
          initialValue: controller.palette,
          onSelected: controller.setPalette,
          itemBuilder: (context) => [
            for (final palette in BrandPalette.values)
              PopupMenuItem(
                value: palette,
                child: Row(
                  children: [
                    Container(
                      width: 16,
                      height: 16,
                      decoration: BoxDecoration(
                        color: palette.seed,
                        shape: BoxShape.circle,
                      ),
                    ),
                    const SizedBox(width: Space.sm),
                    Text(palette.label),
                    if (palette == controller.palette) ...[
                      const Spacer(),
                      const Icon(Icons.check, size: 16),
                    ],
                  ],
                ),
              ),
          ],
        ),
      ],
    );
  }
}
