import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../core/theme/app_theme.dart';
import '../core/theme/theme_controller.dart';
import 'home_screen.dart';

class KatixoHospitalApp extends StatelessWidget {
  const KatixoHospitalApp({super.key});

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<ThemeController>();

    return MaterialApp(
      title: 'Katixo Hospital OS',
      debugShowCheckedModeBanner: false,
      themeMode: controller.mode,
      theme: AppTheme.light(controller.palette),
      darkTheme: AppTheme.dark(controller.palette),
      home: const HomeScreen(),
    );
  }
}
