// Dependency-free file picker for Flutter Web.
//
// Conditional export: the real implementation (`file_picker_web.dart`) uses
// browser APIs and is selected when `dart:html` is available (Flutter Web).
// On any other target the stub returns null — this app ships web-only, but the
// conditional keeps `flutter analyze`/build clean across platforms.
//
// Both implementations expose the same `PickedFile` class and `pickFile()`
// function, so callers just `import 'file_picker.dart'`.
export 'file_picker_stub.dart'
    if (dart.library.html) 'file_picker_web.dart';
