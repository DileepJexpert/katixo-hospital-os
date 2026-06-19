import 'package:flutter/material.dart';

import '../theme/design_tokens.dart';

/// Inline error/success banner used across the app (shown above a screen or
/// form body). Tinted from [StatusColors] so it reads the same everywhere.
///
/// Lives in core/widgets so any screen can import it directly; historically it
/// was defined in front_desk/registration_screen.dart and imported via
/// `show MessageBanner`, which still works through a re-export there.
class MessageBanner extends StatelessWidget {
  const MessageBanner._(this.message, this.color, this.icon, {super.key});

  factory MessageBanner.error(String message, {Key? key}) => MessageBanner._(
      message, StatusColors.danger, Icons.error_outline,
      key: key);

  factory MessageBanner.success(String message, {Key? key}) =>
      MessageBanner._(
          message, StatusColors.success, Icons.check_circle_outline,
          key: key);

  final String message;
  final Color color;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(Space.md),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: Corners.smRadius,
        border: Border.all(color: color.withValues(alpha: 0.3)),
      ),
      child: Row(
        children: [
          Icon(icon, size: 20, color: color),
          const SizedBox(width: Space.sm),
          Expanded(
            child: Text(message,
                style: Theme.of(context)
                    .textTheme
                    .bodySmall
                    ?.copyWith(color: color)),
          ),
        ],
      ),
    );
  }
}
