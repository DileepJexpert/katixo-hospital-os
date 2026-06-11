import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../core/api/http_client.dart';
import '../core/auth/auth_state.dart';
import '../core/routing/app_router.dart';
import '../core/theme/app_theme.dart';
import '../core/theme/theme_controller.dart';

class KatixoHospitalApp extends StatelessWidget {
  const KatixoHospitalApp({super.key});

  @override
  Widget build(BuildContext context) {
    final themeController = context.watch<ThemeController>();
    final authState = context.watch<AuthState>();

    final router = createRouter(authState);

    return MaterialApp.router(
      title: 'Katixo Hospital OS',
      debugShowCheckedModeBanner: false,
      themeMode: themeController.mode,
      theme: AppTheme.light(themeController.palette),
      darkTheme: AppTheme.dark(themeController.palette),
      routerConfig: router,
    );
  }
}
