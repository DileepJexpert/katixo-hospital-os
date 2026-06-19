import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../theme/design_tokens.dart';

/// Keyboard-friendly gender picker. Tab into it from the previous field and
/// press **M / F / O / N** to set Male / Female / Other / Prefer-not-to-say;
/// the chips are also clickable. Reusable across registration and patient edit.
class GenderField extends StatefulWidget {
  const GenderField({
    super.key,
    required this.value,
    required this.onChanged,
    this.enabled = true,
    this.label = 'Gender *',
  });

  final String? value;
  final ValueChanged<String?> onChanged;
  final bool enabled;
  final String label;

  @override
  State<GenderField> createState() => _GenderFieldState();
}

class _GenderFieldState extends State<GenderField> {
  late final FocusNode _focusNode =
      FocusNode(debugLabel: 'GenderField')..addListener(_onFocusChange);

  // (value, label, shortcut key)
  static const List<(String, String, String)> _options = [
    ('MALE', 'Male', 'M'),
    ('FEMALE', 'Female', 'F'),
    ('OTHER', 'Other', 'O'),
    ('PREFER_NOT_TO_SAY', 'N/A', 'N'),
  ];

  void _onFocusChange() => setState(() {});

  @override
  void dispose() {
    _focusNode
      ..removeListener(_onFocusChange)
      ..dispose();
    super.dispose();
  }

  KeyEventResult _onKey(FocusNode node, KeyEvent event) {
    if (!widget.enabled || event is! KeyDownEvent) return KeyEventResult.ignored;
    final ch = event.character?.toUpperCase();
    if (ch == null || ch.isEmpty) return KeyEventResult.ignored;
    for (final o in _options) {
      if (o.$3 == ch) {
        widget.onChanged(o.$1);
        return KeyEventResult.handled;
      }
    }
    return KeyEventResult.ignored;
  }

  @override
  Widget build(BuildContext context) {
    return Focus(
      focusNode: _focusNode,
      onKeyEvent: _onKey,
      child: InputDecorator(
        isFocused: _focusNode.hasFocus,
        decoration: InputDecoration(
          labelText: widget.label,
          helperText: 'Type M / F / O',
          // Keep the label permanently floated so the chips sit below it.
          floatingLabelBehavior: FloatingLabelBehavior.always,
        ),
        child: Wrap(
          spacing: Space.xs,
          runSpacing: Space.xs,
          children: [
            for (final o in _options)
              ChoiceChip(
                label: Text(o.$2),
                selected: widget.value == o.$1,
                visualDensity: VisualDensity.compact,
                onSelected: widget.enabled
                    ? (_) {
                        widget.onChanged(o.$1);
                        _focusNode.requestFocus();
                      }
                    : null,
              ),
          ],
        ),
      ),
    );
  }
}
