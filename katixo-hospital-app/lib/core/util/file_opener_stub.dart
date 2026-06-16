import 'dart:typed_data';

/// Non-web fallback. The product ships as Flutter Web, so this path is only
/// reached if the app is ever built for another target — it fails loudly
/// rather than silently doing nothing.
void openBytesInNewTab(Uint8List bytes, String filename,
    {String mimeType = 'application/pdf'}) {
  throw UnsupportedError(
    'Opening files is only supported on Flutter Web in this build.',
  );
}
