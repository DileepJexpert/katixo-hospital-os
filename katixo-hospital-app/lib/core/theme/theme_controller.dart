import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'design_tokens.dart';

/// Central runtime theme control.
///
/// Switch light/dark/system mode or brand palette from anywhere
/// (e.g. the app bar toggle or Settings screen) and the entire
/// app rebuilds instantly. Choices persist across sessions.
class ThemeController extends ChangeNotifier {
  ThemeController(this._prefs)
      : _mode = _readMode(_prefs),
        _palette = _readPalette(_prefs);

  static const _kMode = 'theme.mode';
  static const _kPalette = 'theme.palette';

  final SharedPreferences _prefs;

  ThemeMode _mode;
  BrandPalette _palette;

  ThemeMode get mode => _mode;
  BrandPalette get palette => _palette;

  bool isDark(BuildContext context) =>
      _mode == ThemeMode.dark ||
      (_mode == ThemeMode.system &&
          MediaQuery.platformBrightnessOf(context) == Brightness.dark);

  void setMode(ThemeMode mode) {
    if (mode == _mode) return;
    _mode = mode;
    _prefs.setString(_kMode, mode.name);
    notifyListeners();
  }

  void toggleDark(BuildContext context) =>
      setMode(isDark(context) ? ThemeMode.light : ThemeMode.dark);

  void setPalette(BrandPalette palette) {
    if (palette == _palette) return;
    _palette = palette;
    _prefs.setString(_kPalette, palette.name);
    notifyListeners();
  }

  static ThemeMode _readMode(SharedPreferences prefs) {
    final raw = prefs.getString(_kMode);
    return ThemeMode.values.asNameMap()[raw] ?? ThemeMode.system;
  }

  static BrandPalette _readPalette(SharedPreferences prefs) {
    final raw = prefs.getString(_kPalette);
    return BrandPalette.values.asNameMap()[raw] ?? BrandPalette.katixoTeal;
  }
}
