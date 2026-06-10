import 'package:flutter/widgets.dart';

import '../theme/design_tokens.dart';
import 'breakpoints.dart';

/// Renders a different widget per form factor.
/// Missing slots fall back to the next smaller one.
class ResponsiveBuilder extends StatelessWidget {
  const ResponsiveBuilder({
    super.key,
    required this.mobile,
    this.tablet,
    this.desktop,
    this.large,
  });

  final WidgetBuilder mobile;
  final WidgetBuilder? tablet;
  final WidgetBuilder? desktop;
  final WidgetBuilder? large;

  @override
  Widget build(BuildContext context) {
    final builder = switch (context.formFactor) {
      FormFactor.mobile => mobile,
      FormFactor.tablet => tablet ?? mobile,
      FormFactor.desktop => desktop ?? tablet ?? mobile,
      FormFactor.large => large ?? desktop ?? tablet ?? mobile,
    };
    return builder(context);
  }
}

/// Clamps content width on very large screens and applies the
/// breakpoint gutter — wrap every page body in this.
class PageContainer extends StatelessWidget {
  const PageContainer({super.key, required this.child, this.scrollable = true});

  final Widget child;
  final bool scrollable;

  @override
  Widget build(BuildContext context) {
    final content = Align(
      alignment: Alignment.topCenter,
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: Metrics.maxContentWidth),
        child: Padding(
          padding: EdgeInsets.all(context.gutter),
          child: child,
        ),
      ),
    );
    return scrollable ? SingleChildScrollView(child: content) : content;
  }
}
