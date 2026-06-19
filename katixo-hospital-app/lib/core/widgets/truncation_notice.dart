import 'package:flutter/material.dart';

import '../theme/design_tokens.dart';

/// Shown beneath a capped list when it likely holds more rows than the server
/// returned — i.e. the row count equals the fetch limit. An honest signal that
/// data may be hidden until true pagination lands, instead of silently
/// truncating. Render it only when `count >= limit`.
class TruncationNotice extends StatelessWidget {
  const TruncationNotice({super.key, required this.limit});

  final int limit;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: Space.sm),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.info_outline,
              size: 16, color: theme.colorScheme.onSurfaceVariant),
          const SizedBox(width: Space.xs),
          Flexible(
            child: Text(
              'Showing the first $limit. Narrow the filters to see more.',
              style: theme.textTheme.bodySmall
                  ?.copyWith(color: theme.colorScheme.onSurfaceVariant),
            ),
          ),
        ],
      ),
    );
  }
}
