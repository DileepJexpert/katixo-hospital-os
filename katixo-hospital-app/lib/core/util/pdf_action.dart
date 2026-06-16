import 'package:flutter/material.dart';

import '../api/http_client.dart';
import 'file_opener.dart';

/// Fetches a PDF from a secured backend endpoint (auth headers come from
/// [api]) and opens it in a new browser tab. Shows a SnackBar on failure.
/// Returns true when the bytes were fetched and handed to the browser.
///
/// This is the bridge for the backend PDF endpoints (bill receipt, expense
/// voucher, payslip, lab report): `ApiClient` is otherwise JSON-only, so a
/// plain link can't carry the JWT — we pull the bytes through the client and
/// hand them to a Blob URL.
Future<bool> openPdfFromApi(
  BuildContext context,
  ApiClient api,
  String endpoint,
  String filename,
) async {
  final messenger = ScaffoldMessenger.of(context);
  try {
    final bytes = await api.getBytes(endpoint);
    openBytesInNewTab(bytes, filename);
    return true;
  } catch (e) {
    messenger.showSnackBar(
      SnackBar(content: Text('Could not open PDF: $e')),
    );
    return false;
  }
}
