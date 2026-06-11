import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../api/http_client.dart';
import '../api/models.dart';

/// Holds JWT token and current user throughout the app.
/// Persists across sessions. Central auth state machine.
class AuthState extends ChangeNotifier {
  AuthState(this._prefs) : _token = _prefs.getString(_kToken);

  static const _kToken = 'auth.token';
  static const _kUser = 'auth.user';

  final SharedPreferences _prefs;

  String? _token;
  CurrentUser? _user;

  String? get token => _token;
  CurrentUser? get currentUser => _user;
  bool get isAuthenticated => _token != null && _user != null;

  Future<void> login(String username, String password, ApiClient apiClient) async {
    final response = await apiClient.post<LoginResponse>(
      '/api/v1/auth/login',
      LoginRequest(username: username, password: password),
      fromJson: (json) => LoginResponse.fromJson(json as Map<String, dynamic>),
    );

    _token = response.token;
    _user = response.user;

    await _prefs.setString(_kToken, _token!);
    await _prefs.setString(_kUser, _user.toString()); // Simplified; in prod, store user details.

    notifyListeners();
  }

  Future<void> logout() async {
    _token = null;
    _user = null;
    await _prefs.remove(_kToken);
    await _prefs.remove(_kUser);
    notifyListeners();
  }

  Future<void> restoreSession() async {
    // In production, validate token with backend (refresh if expired).
    // For now, just restore from prefs if exists.
    _token = _prefs.getString(_kToken);
    if (_token != null) {
      // Decode JWT payload to populate _user.
      // Simplified: in production, call /me or store full user details.
      notifyListeners();
    }
  }
}
