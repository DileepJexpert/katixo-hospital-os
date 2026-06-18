// Web implementation: open PDF bytes in a new browser tab via a Blob URL.
// ignore: avoid_web_libraries_in_flutter
import 'dart:html' as html;
import 'dart:typed_data';

/// Opens [bytes] (a PDF) in a new browser tab. The browser's built-in viewer
/// renders it with print/download controls. The object URL is revoked after a
/// delay so the tab has time to load it.
void openPdfBytes(Uint8List bytes, String filename) {
  openBytesInBrowser(bytes, filename, 'application/pdf');
}

/// Opens arbitrary [bytes] of [mimeType] in a new browser tab via a Blob URL.
/// PDFs/images render inline; other types download. Used for file attachments.
void openBytesInBrowser(Uint8List bytes, String filename, String mimeType) {
  final blob = html.Blob(<Object>[bytes], mimeType);
  final url = html.Url.createObjectUrlFromBlob(blob);
  html.window.open(url, '_blank');
  Future<void>.delayed(const Duration(minutes: 1),
      () => html.Url.revokeObjectUrl(url));
}
