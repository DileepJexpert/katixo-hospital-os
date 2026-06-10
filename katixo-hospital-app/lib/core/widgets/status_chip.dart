import 'package:flutter/material.dart';

import '../theme/design_tokens.dart';

/// Domain status semantics, mapped to token colors once —
/// used across queue boards, lab results, bed boards, bills.
enum StatusKind { success, warning, danger, info, neutral }

class StatusChip extends StatelessWidget {
  const StatusChip(this.label, {super.key, required this.kind});

  /// Maps common backend status strings to a visual kind so
  /// screens can do `StatusChip.auto('IN_QUEUE')`.
  factory StatusChip.auto(String status, {Key? key}) {
    final kind = switch (status.toUpperCase()) {
      'RELEASED' || 'COMPLETED' || 'PAID' || 'VACANT' || 'APPROVED' ||
      'DISPENSED' || 'ACTIVE' || 'FINAL' =>
        StatusKind.success,
      'PENDING' || 'PENDING_APPROVAL' || 'PARTIALLY_DISPENSED' ||
      'DRAFT' || 'SAMPLE_COLLECTED' =>
        StatusKind.warning,
      'ABNORMAL' || 'BLOCKED' || 'ISOLATION' || 'REJECTED' || 'LAMA' ||
      'DEATH' || 'FAILED' =>
        StatusKind.danger,
      'IN_QUEUE' || 'IN_PROGRESS' || 'QUEUED' || 'CALLED' ||
      'IN_CONSULTATION' || 'OCCUPIED' || 'ORDERED' =>
        StatusKind.info,
      _ => StatusKind.neutral,
    };
    return StatusChip(status.replaceAll('_', ' '), key: key, kind: kind);
  }

  final String label;
  final StatusKind kind;

  Color get _color => switch (kind) {
        StatusKind.success => StatusColors.success,
        StatusKind.warning => StatusColors.warning,
        StatusKind.danger => StatusColors.danger,
        StatusKind.info => StatusColors.info,
        StatusKind.neutral => StatusColors.neutral,
      };

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(
          horizontal: Space.sm, vertical: Space.xxs),
      decoration: BoxDecoration(
        color: _color.withValues(alpha: 0.12),
        borderRadius: Corners.smRadius,
        border: Border.all(color: _color.withValues(alpha: 0.45), width: 0.8),
      ),
      child: Text(
        label,
        style: Theme.of(context)
            .textTheme
            .labelSmall
            ?.copyWith(color: _color, fontWeight: FontWeight.w600),
      ),
    );
  }
}
