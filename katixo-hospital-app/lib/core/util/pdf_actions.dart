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

/// Fetches an arbitrary stored document at [path] (authenticated, any content
/// type) and opens it in a new browser tab. Mirrors [openPdf] but accepts the
/// server's content type instead of assuming PDF — used for file attachments
/// (scans, images, PDFs). Returns true on success.
Future<bool> openDocument(
  BuildContext context,
  ApiClient api,
  String path, {
  required String filename,
  String mimeType = 'application/octet-stream',
}) async {
  final messenger = ScaffoldMessenger.of(context);
  messenger.showSnackBar(
    const SnackBar(content: Text('Opening file…'), duration: Duration(seconds: 1)),
  );
  try {
    // getBytes sends an Accept header; pass the file's type (server ignores it
    // but it keeps the request honest) and read the raw bytes back.
    final bytes = await api.getBytes(path, accept: mimeType);
    openBytesInBrowser(bytes, filename, mimeType);
    return true;
  } on ApiException catch (e) {
    messenger.showSnackBar(SnackBar(content: Text('Could not open file: ${e.error.message}')));
    return false;
  } catch (e) {
    messenger.showSnackBar(SnackBar(content: Text('Could not open file: $e')));
    return false;
  }
}
