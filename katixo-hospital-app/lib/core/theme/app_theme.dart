import 'package:flutter/material.dart';

import 'design_tokens.dart';

/// Builds the full light/dark ThemeData from design tokens.
/// Every Material component is styled HERE and only here —
/// screens never override colors/sizes locally.
class AppTheme {
  AppTheme._();

  static ThemeData light(BrandPalette palette) =>
      _build(palette, Brightness.light);

  static ThemeData dark(BrandPalette palette) =>
      _build(palette, Brightness.dark);

  static ThemeData _build(BrandPalette palette, Brightness brightness) {
    final scheme = ColorScheme.fromSeed(
      seedColor: palette.seed,
      brightness: brightness,
    );
    final isDark = brightness == Brightness.dark;

    final textTheme = _textTheme(scheme);

    return ThemeData(
      useMaterial3: true,
      colorScheme: scheme,
      brightness: brightness,
      fontFamily: TypeScale.fontFamily,
      textTheme: textTheme,
      visualDensity: VisualDensity.compact,
      scaffoldBackgroundColor:
          isDark ? scheme.surface : scheme.surfaceContainerLowest,
      splashFactory: InkSparkle.splashFactory,

      // ---------- App bar ----------
      appBarTheme: AppBarTheme(
        toolbarHeight: Metrics.appBarHeight,
        centerTitle: false,
        elevation: Elevations.none,
        scrolledUnderElevation: 1,
        backgroundColor: scheme.surface,
        foregroundColor: scheme.onSurface,
        titleTextStyle: textTheme.titleLarge,
      ),

      // ---------- Cards: flat + hairline border = compact pro ----------
      cardTheme: CardTheme(
        elevation: Elevations.card,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(
          borderRadius: Corners.mdRadius,
          side: BorderSide(color: scheme.outlineVariant),
        ),
        color: scheme.surface,
      ),

      // ---------- Inputs ----------
      inputDecorationTheme: InputDecorationTheme(
        isDense: true,
        filled: true,
        fillColor: isDark
            ? scheme.surfaceContainerHigh
            : scheme.surfaceContainerLowest,
        contentPadding: const EdgeInsets.symmetric(
            horizontal: Space.md, vertical: Space.sm),
        border: OutlineInputBorder(
          borderRadius: Corners.smRadius,
          borderSide: BorderSide(color: scheme.outline),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: Corners.smRadius,
          borderSide: BorderSide(color: scheme.outlineVariant),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: Corners.smRadius,
          borderSide: BorderSide(color: scheme.primary, width: 1.6),
        ),
        labelStyle: textTheme.bodyMedium,
      ),

      // ---------- Buttons ----------
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          minimumSize: const Size(64, Metrics.buttonHeight),
          padding: const EdgeInsets.symmetric(horizontal: Space.lg),
          shape: RoundedRectangleBorder(borderRadius: Corners.smRadius),
          textStyle: textTheme.labelLarge,
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          minimumSize: const Size(64, Metrics.buttonHeight),
          padding: const EdgeInsets.symmetric(horizontal: Space.lg),
          shape: RoundedRectangleBorder(borderRadius: Corners.smRadius),
          textStyle: textTheme.labelLarge,
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          minimumSize: const Size(48, Metrics.buttonHeight),
          padding: const EdgeInsets.symmetric(horizontal: Space.md),
          shape: RoundedRectangleBorder(borderRadius: Corners.smRadius),
          textStyle: textTheme.labelLarge,
        ),
      ),

      // ---------- Chips (status badges everywhere) ----------
      chipTheme: ChipThemeData(
        labelStyle: textTheme.labelSmall,
        padding: const EdgeInsets.symmetric(
            horizontal: Space.sm, vertical: Space.xxs),
        shape: RoundedRectangleBorder(borderRadius: Corners.smRadius),
        side: BorderSide(color: scheme.outlineVariant),
      ),

      // ---------- Data tables (worklists, queues, bills) ----------
      dataTableTheme: DataTableThemeData(
        headingRowHeight: Metrics.tableHeaderHeight,
        dataRowMinHeight: Metrics.tableRowHeight,
        dataRowMaxHeight: Metrics.tableRowHeight,
        headingTextStyle:
            textTheme.labelLarge?.copyWith(fontWeight: FontWeight.w600),
        dataTextStyle: textTheme.bodyMedium,
        dividerThickness: 0.5,
      ),

      // ---------- Lists ----------
      listTileTheme: ListTileThemeData(
        dense: true,
        visualDensity: VisualDensity.compact,
        contentPadding: const EdgeInsets.symmetric(horizontal: Space.md),
        shape: RoundedRectangleBorder(borderRadius: Corners.smRadius),
      ),

      // ---------- Dialogs ----------
      dialogTheme: DialogTheme(
        elevation: Elevations.dialog,
        shape: RoundedRectangleBorder(borderRadius: Corners.lgRadius),
        titleTextStyle: textTheme.titleLarge,
        contentTextStyle: textTheme.bodyMedium,
      ),

      // ---------- Navigation (adaptive shell) ----------
      navigationRailTheme: NavigationRailThemeData(
        backgroundColor: scheme.surface,
        indicatorShape:
            RoundedRectangleBorder(borderRadius: Corners.smRadius),
        labelType: NavigationRailLabelType.none,
        selectedLabelTextStyle:
            textTheme.labelLarge?.copyWith(color: scheme.primary),
        unselectedLabelTextStyle: textTheme.labelLarge,
      ),
      navigationBarTheme: NavigationBarThemeData(
        height: 60,
        backgroundColor: scheme.surface,
        labelTextStyle: WidgetStatePropertyAll(textTheme.labelSmall),
      ),

      dividerTheme: DividerThemeData(
        color: scheme.outlineVariant,
        thickness: 0.5,
        space: 0,
      ),

      snackBarTheme: SnackBarThemeData(
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: Corners.smRadius),
      ),

      tooltipTheme: TooltipThemeData(
        textStyle: textTheme.labelSmall?.copyWith(color: scheme.onInverseSurface),
        decoration: BoxDecoration(
          color: scheme.inverseSurface,
          borderRadius: Corners.smRadius,
        ),
      ),
    );
  }

  static TextTheme _textTheme(ColorScheme scheme) {
    final base = Typography.material2021(platform: TargetPlatform.android)
        .black
        .apply(
          bodyColor: scheme.onSurface,
          displayColor: scheme.onSurface,
        );
    return base.copyWith(
      displayLarge: base.displayLarge?.copyWith(
          fontSize: TypeScale.displayLarge, fontWeight: FontWeight.w600),
      headlineMedium: base.headlineMedium?.copyWith(
          fontSize: TypeScale.headlineMedium, fontWeight: FontWeight.w600),
      titleLarge: base.titleLarge?.copyWith(
          fontSize: TypeScale.titleLarge, fontWeight: FontWeight.w600),
      titleMedium: base.titleMedium?.copyWith(
          fontSize: TypeScale.titleMedium, fontWeight: FontWeight.w500),
      bodyLarge: base.bodyLarge?.copyWith(fontSize: TypeScale.bodyLarge),
      bodyMedium: base.bodyMedium?.copyWith(fontSize: TypeScale.bodyMedium),
      bodySmall: base.bodySmall?.copyWith(fontSize: TypeScale.bodySmall),
      labelLarge: base.labelLarge?.copyWith(
          fontSize: TypeScale.labelLarge, fontWeight: FontWeight.w500),
      labelSmall: base.labelSmall?.copyWith(fontSize: TypeScale.labelSmall),
    );
  }
}
