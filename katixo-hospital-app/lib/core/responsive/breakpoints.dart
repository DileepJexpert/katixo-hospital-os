import 'package:flutter/widgets.dart';

import '../theme/design_tokens.dart';

/// Form factor breakpoints — phone, tablet, desktop, large display.
enum FormFactor {
  mobile, //  < 600
  tablet, //  600 – 1023
  desktop, // 1024 – 1439
  large; //   >= 1440 (TV / queue boards / wall dashboards)

  static FormFactor fromWidth(double width) {
    if (width < 600) return FormFactor.mobile;
    if (width < 1024) return FormFactor.tablet;
    if (width < 1440) return FormFactor.desktop;
    return FormFactor.large;
  }
}

/// Context extension so any widget can ask `context.formFactor`,
/// `context.isMobile`, `context.gutter`, etc.
extension ResponsiveContext on BuildContext {
  FormFactor get formFactor =>
      FormFactor.fromWidth(MediaQuery.sizeOf(this).width);

  bool get isMobile => formFactor == FormFactor.mobile;
  bool get isTablet => formFactor == FormFactor.tablet;
  bool get isDesktop =>
      formFactor == FormFactor.desktop || formFactor == FormFactor.large;

  /// Page gutter resolved per breakpoint.
  double get gutter => switch (formFactor) {
        FormFactor.mobile => Space.gutterMobile,
        FormFactor.tablet => Space.gutterTablet,
        _ => Space.gutterDesktop,
      };

  /// Grid columns for KPI/card grids per breakpoint.
  int get gridColumns => switch (formFactor) {
        FormFactor.mobile => 1,
        FormFactor.tablet => 2,
        FormFactor.desktop => 3,
        FormFactor.large => 4,
      };

  /// Generic per-breakpoint value selector:
  /// `context.responsive(mobile: 1, tablet: 2, desktop: 4)`
  T responsive<T>({required T mobile, T? tablet, T? desktop, T? large}) =>
      switch (formFactor) {
        FormFactor.mobile => mobile,
        FormFactor.tablet => tablet ?? mobile,
        FormFactor.desktop => desktop ?? tablet ?? mobile,
        FormFactor.large => large ?? desktop ?? tablet ?? mobile,
      };
}
