import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'app/app.dart';
import 'core/api/http_client.dart';
import 'core/auth/auth_state.dart';
import 'core/config/feature_flags.dart';
import 'core/theme/theme_controller.dart';

// Backend dev port (see katixo-hospital-service application.yml).
// Override per environment with --dart-define=API_BASE_URL=...
const String _baseUrl = String.fromEnvironment(
  'API_BASE_URL',
  defaultValue: 'http://localhost:8081',
);

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final prefs = await SharedPreferences.getInstance();

  // Auth state restores any persisted session in its constructor.
  final authState = AuthState(prefs);

  // Create API client with auth state.
  final apiClient = ApiClient(
    _baseUrl,
    authState: authState,
    onUnauthorized: () => authState.logout(),
  );

  // Per-hospital feature flags — (re)loaded whenever the user signs in.
  final featureFlags = FeatureFlags();
  void syncFeatures() {
    if (authState.isAuthenticated) {
      featureFlags.load(apiClient);
    } else {
      featureFlags.reset();
    }
  }

  authState.addListener(syncFeatures);
  syncFeatures(); // restored session on cold start

  runApp(
    MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => ThemeController(prefs)),
        ChangeNotifierProvider(create: (_) => authState),
        ChangeNotifierProvider<FeatureFlags>.value(value: featureFlags),
        Provider(create: (_) => apiClient),
      ],
      child: const KatixoHospitalApp(),
    ),
  );
}
