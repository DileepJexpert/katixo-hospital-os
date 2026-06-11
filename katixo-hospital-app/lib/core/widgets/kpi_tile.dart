import 'package:flutter/material.dart';

import '../theme/design_tokens.dart';

/// Compact KPI card showing a metric with label, value, and optional trend.
class KpiTile extends StatelessWidget {
  const KpiTile({
    super.key,
    required this.label,
    required this.value,
    required this.icon,
    this.unit,
    this.trend,
    this.trendDirection,
  });

  final String label;
  final String value;
  final IconData icon;
  final String? unit;
  final String? trend;
  final TrendDirection? trendDirection;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final trendColor = switch (trendDirection) {
      TrendDirection.up => StatusColors.success,
      TrendDirection.down => StatusColors.danger,
      TrendDirection.neutral => StatusColors.neutral,
      _ => null,
    };

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(Space.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Icon(icon, color: theme.colorScheme.primary, size: 28),
                if (trend != null && trendColor != null)
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: Space.sm,
                      vertical: Space.xs,
                    ),
                    decoration: BoxDecoration(
                      color: trendColor.withOpacity(0.1),
                      borderRadius: Corners.smRadius,
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(
                          trendDirection == TrendDirection.up
                              ? Icons.trending_up
                              : Icons.trending_down,
                          size: 16,
                          color: trendColor,
                        ),
                        const SizedBox(width: Space.xs),
                        Text(
                          trend!,
                          style: theme.textTheme.labelSmall?.copyWith(
                            color: trendColor,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ],
                    ),
                  ),
              ],
            ),
            const SizedBox(height: Space.md),
            Text(
              label,
              style: theme.textTheme.labelSmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: Space.xs),
            Row(
              crossAxisAlignment: CrossAxisAlignment.baseline,
              textBaseline: TextBaseline.alphabetic,
              children: [
                Text(
                  value,
                  style: theme.textTheme.headlineMedium?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
                ),
                if (unit != null) ...[
                  const SizedBox(width: Space.xs),
                  Text(
                    unit!,
                    style: theme.textTheme.labelSmall?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                ],
              ],
            ),
          ],
        ),
      ),
    );
  }
}

enum TrendDirection { up, down, neutral }
