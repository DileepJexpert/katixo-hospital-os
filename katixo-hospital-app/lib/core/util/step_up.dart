import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../api/http_client.dart';
import '../theme/design_tokens.dart';

/// Step-up (re-authentication) for sensitive actions. The backend gates high
/// discount approval, payment void, bill cancel and discharge sign-off behind a
/// fresh TOTP code (header `X-Step-Up-Code`). This runs [action] — which builds
/// its request with the headers it is given — and, if the server responds
/// `STEP_UP_REQUIRED` / `INVALID_STEP_UP_CODE`, prompts for the authenticator
/// code and retries with it. Loops until it succeeds, the user cancels (returns
/// null), or a non-step-up error is thrown (rethrown to the caller's handler).
///
/// `STEP_UP_ENROLLMENT_REQUIRED` is surfaced as an [ApiException] for the caller
/// to show (the user must enable MFA first — nothing to prompt for here).
Future<T?> withStepUp<T>(
  BuildContext context,
  Future<T> Function(Map<String, String>? headers) action,
) async {
  Map<String, String>? headers;
  String? lastError;
  while (true) {
    try {
      return await action(headers);
    } on ApiException catch (e) {
      final code = e.error.error;
      if (code != 'STEP_UP_REQUIRED' && code != 'INVALID_STEP_UP_CODE') {
        rethrow;
      }
      if (!context.mounted) return null;
      final entered = await _promptCode(context, error: lastError);
      if (entered == null) return null; // user cancelled
      headers = {'X-Step-Up-Code': entered};
      lastError = 'That code was incorrect or expired — try again.';
    }
  }
}

Future<String?> _promptCode(BuildContext context, {String? error}) {
  final controller = TextEditingController();
  return showDialog<String>(
    context: context,
    barrierDismissible: false,
    builder: (context) => AlertDialog(
      title: const Text('Confirm with authenticator'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('This action needs a second factor. Enter the 6-digit code '
              'from your authenticator app.'),
          const SizedBox(height: Space.md),
          TextField(
            controller: controller,
            autofocus: true,
            keyboardType: TextInputType.number,
            maxLength: 6,
            inputFormatters: [FilteringTextInputFormatter.digitsOnly],
            decoration: const InputDecoration(
              labelText: 'Authenticator code',
              counterText: '',
            ),
            onSubmitted: (v) => Navigator.pop(context, v.trim()),
          ),
          if (error != null) ...[
            const SizedBox(height: Space.sm),
            Text(error, style: const TextStyle(color: StatusColors.danger)),
          ],
        ],
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
        FilledButton(
          onPressed: () => Navigator.pop(context, controller.text.trim()),
          child: const Text('Confirm'),
        ),
      ],
    ),
  );
}
