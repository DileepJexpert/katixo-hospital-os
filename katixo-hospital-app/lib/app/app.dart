import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../core/auth/auth_state.dart';
import '../core/routing/app_router.dart';
import '../core/theme/app_theme.dart';
import '../core/theme/theme_controller.dart';

class KatixoHospitalApp extends StatefulWidget {
  const KatixoHospitalApp({super.key});

  @override
  State<KatixoHospitalApp> createState() => _KatixoHospitalAppState();
}

class _KatixoHospitalAppState extends State<KatixoHospitalApp> {
  late final GoRouter _router;

  @override
  void initState() {
    super.initState();
    // Router is created once; auth changes flow through refreshListenable.
    _router = createRouter(context.read<AuthState>());
  }

  @override
  Widget build(BuildContext context) {
    final themeController = context.watch<ThemeController>();

    return MaterialApp.router(
      title: 'Katixo Hospital OS',
      debugShowCheckedModeBanner: false,
      themeMode: themeController.mode,
      theme: AppTheme.light(themeController.palette),
      darkTheme: AppTheme.dark(themeController.palette),
      routerConfig: _router,
    );
  }
}
