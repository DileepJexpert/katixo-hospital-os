// Opens in-memory file bytes (e.g. a PDF) for the user. The implementation is
// platform-specific: on web we use a Blob object-URL; other platforms get a
// stub that reports the feature is unsupported. The conditional export keeps
// the app compiling on every target while only pulling in `dart:html` on web.
export 'file_opener_stub.dart' if (dart.library.html) 'file_opener_web.dart';
