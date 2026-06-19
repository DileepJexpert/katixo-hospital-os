import 'package:flutter/material.dart';

import '../theme/design_tokens.dart';

/// A centered empty/placeholder state: icon + title + optional message + action.
class EmptyState extends StatelessWidget {
  const EmptyState({
    super.key,
    required this.icon,
    required this.title,
    this.message,
    this.action,
  });

  final IconData icon;
  final String title;
  final String? message;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    // Center when there's room; scroll instead of overflowing when the slot is
    // short (e.g. inside an Expanded on a small viewport). EmptyState is shared
    // across many screens, so this keeps every empty state overflow-proof.
    return LayoutBuilder(
      builder: (context, constraints) {
        final content = Padding(
          padding: const EdgeInsets.all(Space.xl),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(icon,
                  size: 48,
                  color:
                      theme.colorScheme.onSurfaceVariant.withValues(alpha: 0.5)),
              const SizedBox(height: Space.md),
              Text(title,
                  textAlign: TextAlign.center,
                  style: theme.textTheme.titleMedium),
              if (message != null) ...[
                const SizedBox(height: Space.xs),
                Text(message!,
                    textAlign: TextAlign.center,
                    style: theme.textTheme.bodySmall
                        ?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
              ],
              if (action != null) ...[
                const SizedBox(height: Space.lg),
                action!,
              ],
            ],
          ),
        );
        return SingleChildScrollView(
          child: ConstrainedBox(
            constraints: BoxConstraints(
              minHeight: constraints.hasBoundedHeight ? constraints.maxHeight : 0,
            ),
            child: Center(child: content),
          ),
        );
      },
    );
  }
}
