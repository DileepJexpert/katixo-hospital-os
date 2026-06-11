import 'package:go_router/go_router.dart';

import '../../features/auth/login_screen.dart';
import '../../features/front_desk/registration_screen.dart';
import '../auth/auth_state.dart';

/// Central routing. `refreshListenable` re-evaluates redirects whenever
/// auth state changes (login/logout) — no manual navigation needed.
GoRouter createRouter(AuthState authState) {
  return GoRouter(
    refreshListenable: authState,
    redirect: (context, state) {
      final isAuth = authState.isAuthenticated;
      final isLoggingIn = state.matchedLocation == '/login';

      if (!isAuth) return isLoggingIn ? null : '/login';
      if (isLoggingIn) return '/';
      return null;
    },
    routes: [
      GoRoute(
        path: '/login',
        builder: (context, state) => const LoginScreen(),
      ),
      GoRoute(
        path: '/',
        builder: (context, state) => const RegistrationScreen(),
      ),
      // More routes as modules are built:
      // GoRoute(path: '/opd/queue', ...),
      // GoRoute(path: '/doctor/worklist', ...),
    ],
  );
}
