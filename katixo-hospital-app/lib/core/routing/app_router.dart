import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../features/auth/login_screen.dart';
import '../../features/front_desk/registration_screen.dart';
import '../auth/auth_state.dart';
import '../theme/design_tokens.dart';

/// Central routing. Routes adapt based on authState.isAuthenticated.
GoRouter createRouter(AuthState authState) {
  return GoRouter(
    redirect: (context, state) {
      final isAuth = authState.isAuthenticated;
      final isLoggingIn = state.matchedLocation == '/login';

      // If not authenticated, redirect to login (except if already there).
      if (!isAuth) return isLoggingIn ? null : '/login';

      // If authenticated and trying to go to login, redirect to home.
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
      // etc.
    ],
  );
}

/// Global nav helpers (can be called from anywhere with context).
extension GoRouterExt on GoRouter {
  void goHome(BuildContext context) => go('/');
  void goLogin(BuildContext context) => go('/login');
}
