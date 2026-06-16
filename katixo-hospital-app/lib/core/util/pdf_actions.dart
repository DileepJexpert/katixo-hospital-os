import 'package:flutter/material.dart';

import '../api/http_client.dart';
import 'pdf_launcher.dart';

/// Fetches a server-rendered PDF at [path] (authenticated) and opens it in the
/// browser. Shows a transient SnackBar while fetching and on error, so callers
/// just wire a button to this. [filename] is used for the pop-up-blocked
/// download fallback.
///
/// Returns true on success. Safe to call from any screen that has an
/// [ApiClient] in its provider scope.
Future<bool> openPdf(
  BuildContext context,
  ApiClient api,
  String path, {
  required String filename,
}) async {
  final messenger = ScaffoldMessenger.of(context);
  messenger.showSnackBar(
    const SnackBar(
      content: Text('Preparing PDF…'),
      duration: Duration(seconds: 1),
    ),
  );
  try {
    final bytes = await api.getBytes(path);
    openPdfBytes(bytes, filename);
    return true;
  } on ApiException catch (e) {
    messenger.showSnackBar(SnackBar(content: Text('Could not open PDF: ${e.error.message}')));
    return false;
  } catch (e) {
    messenger.showSnackBar(SnackBar(content: Text('Could not open PDF: $e')));
    return false;
  }
}
