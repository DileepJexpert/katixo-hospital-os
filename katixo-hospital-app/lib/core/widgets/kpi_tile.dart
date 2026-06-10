import 'package:flutter/material.dart';

import '../theme/design_tokens.dart';

/// Compact KPI tile for dashboards (OPD count, occupancy, revenue…).
class KpiTile extends StatelessWidget {
  const KpiTile({
    super.key,
    required this.label,
    required this.value,
    this.icon,
    this.trend,
    this.trendUp,
  });

  final String label;
  final String value;
  final IconData? icon;
  final String? trend;
  final bool? trendUp;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(Space.lg),
        child: Row(
          children: [
            if (icon != null) ...[
              Container(
                padding: const EdgeInsets.all(Space.sm),
                decoration: BoxDecoration(
                  color: scheme.primaryContainer,
                  borderRadius: Corners.smRadius,
                ),
                child: Icon(icon, size: 22, color: scheme.onPrimaryContainer),
              ),
              const SizedBox(width: Space.md),
            ],
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(label,
                      style: theme.textTheme.labelSmall
                          ?.copyWith(color: scheme.onSurfaceVariant)),
                  const SizedBox(height: Space.xxs),
                  Text(value, style: theme.textTheme.headlineMedium),
                ],
              ),
            ),
            if (trend != null)
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(
                    (trendUp ?? true)
                        ? Icons.trending_up
                        : Icons.trending_down,
                    size: 16,
                    color: (trendUp ?? true)
                        ? StatusColors.success
                        : StatusColors.danger,
                  ),
                  const SizedBox(width: Space.xxs),
                  Text(
                    trend!,
                    style: theme.textTheme.labelSmall?.copyWith(
                      color: (trendUp ?? true)
                          ? StatusColors.success
                          : StatusColors.danger,
                    ),
                  ),
                ],
              ),
          ],
        ),
      ),
    );
  }
}
