import 'package:flutter/material.dart';

import '../theme/design_tokens.dart';

/// Compact KPI tile for dashboards (OPD count, occupancy, revenue…).
///
/// Supports two trend styles:
///  - `trendUp` (bool) for a simple up/down arrow, or
///  - `trendDirection` (enum) for up/down/neutral.
/// `trendDirection` takes precedence when both are supplied.
class KpiTile extends StatelessWidget {
  const KpiTile({
    super.key,
    required this.label,
    required this.value,
    this.icon,
    this.unit,
    this.trend,
    this.trendUp,
    this.trendDirection,
  });

  final String label;
  final String value;
  final IconData? icon;
  final String? unit;
  final String? trend;
  final bool? trendUp;
  final TrendDirection? trendDirection;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;

    final direction = trendDirection ??
        (trendUp == null
            ? null
            : (trendUp! ? TrendDirection.up : TrendDirection.down));
    final trendColor = switch (direction) {
      TrendDirection.up => StatusColors.success,
      TrendDirection.down => StatusColors.danger,
      TrendDirection.neutral => StatusColors.neutral,
      null => null,
    };

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
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.baseline,
                    textBaseline: TextBaseline.alphabetic,
                    children: [
                      Flexible(
                        child: Text(value,
                            style: theme.textTheme.headlineMedium,
                            overflow: TextOverflow.ellipsis),
                      ),
                      if (unit != null) ...[
                        const SizedBox(width: Space.xs),
                        Text(unit!,
                            style: theme.textTheme.labelSmall
                                ?.copyWith(color: scheme.onSurfaceVariant)),
                      ],
                    ],
                  ),
                ],
              ),
            ),
            if (trend != null && trendColor != null)
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(
                    direction == TrendDirection.down
                        ? Icons.trending_down
                        : Icons.trending_up,
                    size: 16,
                    color: trendColor,
                  ),
                  const SizedBox(width: Space.xxs),
                  Text(
                    trend!,
                    style: theme.textTheme.labelSmall?.copyWith(color: trendColor),
                  ),
                ],
              ),
          ],
        ),
      ),
    );
  }
}

enum TrendDirection { up, down, neutral }
