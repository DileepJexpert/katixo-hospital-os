import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../features/admin/owner_dashboard.dart';
import '../../features/auth/login_screen.dart';
import '../../features/billing/billing_home.dart';
import '../../features/doctor/doctor_home.dart';
import '../../features/front_desk/front_desk_home.dart';
import '../../features/lab/lab_tech_home.dart';
import '../../features/nurse/nurse_home.dart';
import '../../features/pharmacy/pharmacist_home.dart';
import '../auth/auth_state.dart';

/// Central routing. `refreshListenable` re-evaluates redirects whenever
/// auth state changes (login/logout) — no manual navigation needed.
/// '/' resolves to the signed-in user's role home.
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
        builder: (context, state) => _roleHome(authState),
      ),
    ],
  );
}

/// Maps the signed-in role to its home screen. Roles without a
/// dedicated module yet land on the front-desk home.
Widget _roleHome(AuthState authState) {
  return switch (authState.currentUser?.role) {
    'ADMIN' => const OwnerDashboard(),
    'DOCTOR' => const DoctorHome(),
    'NURSE' => const NurseHome(),
    'LAB_TECH' => const LabTechHome(),
    // RADIOLOGIST → RadiologistHome is wired once the radiology backend
    // (/api/v1/radiology/*) lands; the UI exists in features/radiology/.
    'PHARMACIST' => const PharmacistHome(),
    'BILLING' => const BillingHome(),
    _ => const FrontDeskHome(),
  };
}
