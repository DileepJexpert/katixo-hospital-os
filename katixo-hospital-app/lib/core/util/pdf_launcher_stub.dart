// Non-web stub: this app ships web-only, so opening a PDF outside the browser
// is unsupported. Present only so the conditional export compiles everywhere.
import 'dart:typed_data';

void openPdfBytes(Uint8List bytes, String filename) {
  throw UnsupportedError('Opening PDFs is only supported on Flutter Web.');
}

void openBytesInBrowser(Uint8List bytes, String filename, String mimeType) {
  throw UnsupportedError('Opening files is only supported on Flutter Web.');
}
