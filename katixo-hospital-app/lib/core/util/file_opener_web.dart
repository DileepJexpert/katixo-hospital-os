import 'dart:html' as html;
import 'dart:typed_data';

/// Opens file bytes in a new browser tab via a Blob object-URL. A new tab
/// lets the user view, print or save the PDF with the browser's native viewer.
/// Called from a button press (a user gesture), so popup blockers allow it.
void openBytesInNewTab(Uint8List bytes, String filename,
    {String mimeType = 'application/pdf'}) {
  final blob = html.Blob(<dynamic>[bytes], mimeType);
  final url = html.Url.createObjectUrlFromBlob(blob);
  // Typed as dynamic: dart:html declares a non-null return, but a blocked
  // popup yields null at runtime — keep the guard without a lint warning.
  final dynamic opened = html.window.open(url, '_blank');
  // If the popup was blocked, fall back to a download anchor so the user still
  // gets the file.
  if (opened == null) {
    final anchor = html.AnchorElement(href: url)
      ..download = filename
      ..style.display = 'none';
    html.document.body?.append(anchor);
    anchor.click();
    anchor.remove();
  }
  // Revoke after a delay so the new tab / download has time to read the blob.
  Future<void>.delayed(const Duration(seconds: 60),
      () => html.Url.revokeObjectUrl(url));
}
