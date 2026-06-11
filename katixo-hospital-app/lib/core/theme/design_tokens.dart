import 'package:flutter/material.dart';

/// ============================================================
/// DESIGN TOKENS — single source of truth for the entire app.
///
/// Change a value here and it propagates to every component:
/// every button, card, table, chip, dialog, nav bar, etc.
/// Nothing elsewhere in the app hardcodes a color, size,
/// radius, or duration.
/// ============================================================

/// ---------- Brand palettes ----------
/// Each brand defines one seed color; Material 3 derives the
/// full light/dark ColorScheme from it. Add a palette here and
/// it automatically appears in the theme switcher.
enum BrandPalette {
  katixoTeal('Katixo Teal', Color(0xFF00796B)),
  clinicalBlue('Clinical Blue', Color(0xFF1565C0)),
  warmAmber('Warm Amber', Color(0xFFB26A00)),
  royalIndigo('Royal Indigo', Color(0xFF3F51B5));

  const BrandPalette(this.label, this.seed);
  final String label;
  final Color seed;
}

/// ---------- Semantic status colors ----------
/// Hospital domain states (used by StatusChip, bed board, queue
/// board, lab flags). Kept independent of brand palette so a
/// "critical" red stays red whatever the brand is.
class StatusColors {
  StatusColors._();
  static const Color success = Color(0xFF2E7D32); // released, paid, vacant
  static const Color warning = Color(0xFFF57F17); // pending, partial
  static const Color danger = Color(0xFFC62828); // abnormal, blocked, isolation
  static const Color info = Color(0xFF0277BD); // in-progress, queued
  static const Color neutral = Color(0xFF616161); // cancelled, inactive
}

/// ---------- Spacing (8px grid, compact) ----------
class Space {
  Space._();
  static const double xxs = 2;
  static const double xs = 4;
  static const double sm = 8;
  static const double md = 12;
  static const double lg = 16;
  static const double xl = 24;
  static const double xxl = 32;

  /// Page-level gutter, resolved per breakpoint by Responsive.
  static const double gutterMobile = 12;
  static const double gutterTablet = 16;
  static const double gutterDesktop = 24;
}

/// ---------- Corner radii ----------
class Corners {
  Corners._();
  static const double sm = 6;
  static const double md = 10;
  static const double lg = 14;

  static BorderRadius get smRadius => BorderRadius.circular(sm);
  static BorderRadius get mdRadius => BorderRadius.circular(md);
  static BorderRadius get lgRadius => BorderRadius.circular(lg);
}

/// ---------- Typography scale (compact, professional) ----------
/// Sizes only — family/weight resolved in AppTheme so the whole
/// app can switch font by changing one constant.
class TypeScale {
  TypeScale._();
  static const String fontFamily = 'Roboto'; // bundled with Flutter

  static const double displayLarge = 30;
  static const double headlineMedium = 22;
  static const double titleLarge = 18;
  static const double titleMedium = 15;
  static const double bodyLarge = 14;
  static const double bodyMedium = 13;
  static const double bodySmall = 12;
  static const double labelLarge = 13;
  static const double labelSmall = 11;
}

/// ---------- Elevations ----------
class Elevations {
  Elevations._();
  static const double none = 0;
  static const double card = 0; // flat cards w/ border = compact pro look
  static const double raised = 2;
  static const double dialog = 6;
}

/// ---------- Motion ----------
class Motion {
  Motion._();
  static const Duration fast = Duration(milliseconds: 150);
  static const Duration normal = Duration(milliseconds: 250);
  static const Curve curve = Curves.easeOutCubic;
}

/// ---------- Component metrics ----------
/// Control sizes for dense, data-heavy hospital screens.
class Metrics {
  Metrics._();
  static const double inputHeight = 40;
  static const double buttonHeight = 38;
  static const double tableRowHeight = 40;
  static const double tableHeaderHeight = 42;
  static const double appBarHeight = 52;
  static const double navRailWidth = 72;
  static const double navRailExtendedWidth = 220;
  static const double maxContentWidth = 1440; // clamp on huge monitors
  static const double dialogMaxWidth = 560;
}
