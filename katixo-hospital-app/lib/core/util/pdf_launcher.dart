// Opens server-rendered PDF bytes in the browser.
//
// Conditional export: the real implementation (`pdf_launcher_web.dart`) uses
// browser APIs and is selected when `dart:html` is available (Flutter Web).
// On any other target the stub throws — this app ships web-only, but the
// conditional keeps `flutter analyze`/build clean across platforms.
export 'pdf_launcher_stub.dart'
    if (dart.library.html) 'pdf_launcher_web.dart';
