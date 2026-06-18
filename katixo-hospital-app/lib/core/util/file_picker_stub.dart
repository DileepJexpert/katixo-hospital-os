// Non-web stub: this app ships web-only, so the native file dialog is
// unsupported off the browser. Present only so the conditional export compiles
// everywhere.
import 'dart:typed_data';

/// A file chosen by the user via [pickFile].
class PickedFile {
  PickedFile({required this.name, this.contentType, required this.bytes});

  final String name;
  final String? contentType;
  final Uint8List bytes;
}

Future<PickedFile?> pickFile() async => null;
