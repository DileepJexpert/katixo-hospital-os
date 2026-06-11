import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'app/app.dart';
import 'core/api/http_client.dart';
import 'core/auth/auth_state.dart';
import 'core/theme/theme_controller.dart';

const String _baseUrl = 'http://localhost:8080'; // Update for production

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final prefs = await SharedPreferences.getInstance();

  // Create auth state and restore session.
  final authState = AuthState(prefs);
  await authState.restoreSession();

  // Create API client with auth state.
  final apiClient = ApiClient(
    _baseUrl,
    authState: authState,
    onUnauthorized: () => authState.logout(),
  );

  runApp(
    MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => ThemeController(prefs)),
        ChangeNotifierProvider(create: (_) => authState),
        Provider(create: (_) => apiClient),
      ],
      child: const KatixoHospitalApp(),
    ),
  );
}
