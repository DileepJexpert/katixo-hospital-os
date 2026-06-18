// Web implementation: open the native file dialog and read the chosen file.
// ignore: avoid_web_libraries_in_flutter
import 'dart:html' as html;
import 'dart:typed_data';

/// A file chosen by the user via [pickFile].
class PickedFile {
  PickedFile({required this.name, this.contentType, required this.bytes});

  final String name;
  final String? contentType;
  final Uint8List bytes;
}

/// Shows the browser's file picker and returns the selected file's bytes, name
/// and MIME type — or null if the user cancelled (closed the dialog).
Future<PickedFile?> pickFile() async {
  final input = html.FileUploadInputElement();
  input.multiple = false;
  input.click();

  await input.onChange.first;
  final files = input.files;
  if (files == null || files.isEmpty) return null;

  final file = files.first;
  final reader = html.FileReader();
  reader.readAsArrayBuffer(file);
  await reader.onLoadEnd.first;

  final result = reader.result;
  if (result is! ByteBuffer && result is! Uint8List) return null;
  final bytes = result is Uint8List ? result : (result as ByteBuffer).asUint8List();

  return PickedFile(
    name: file.name,
    contentType: file.type.isEmpty ? null : file.type,
    bytes: bytes,
  );
}
