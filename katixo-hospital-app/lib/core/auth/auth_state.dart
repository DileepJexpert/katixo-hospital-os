import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../api/http_client.dart';
import '../api/models.dart';

/// Holds JWT token and current user throughout the app.
/// Persists across sessions; router redirects watch this.
class AuthState extends ChangeNotifier {
  AuthState(this._prefs) {
    _token = _prefs.getString(_kToken);
    final rawUser = _prefs.getString(_kUser);
    if (rawUser != null) {
      _user = CurrentUser.fromJson(jsonDecode(rawUser) as Map<String, dynamic>);
    }
  }

  static const _kToken = 'auth.token';
  static const _kUser = 'auth.user';

  final SharedPreferences _prefs;

  String? _token;
  CurrentUser? _user;

  String? get token => _token;
  CurrentUser? get currentUser => _user;
  bool get isAuthenticated => _token != null && _user != null;

  Future<void> login(
      String username, String password, ApiClient apiClient, {String? mfaCode}) async {
    final response = await apiClient.post<LoginResponse>(
      '/api/v1/auth/login',
      LoginRequest(username: username, password: password, mfaCode: mfaCode),
      fromJson: (json) => LoginResponse.fromJson(json as Map<String, dynamic>),
    );

    _token = response.token;
    _user = response.user;

    await _prefs.setString(_kToken, _token!);
    await _prefs.setString(_kUser, jsonEncode(_user!.toJson()));

    notifyListeners();
  }

  /// Platform-operator login (control plane). Hits the tenant-less endpoint and
  /// builds a CurrentUser carrying only the PLATFORM_ADMIN role — no tenant, so
  /// the tenant headers go out empty (platform endpoints authorise off the JWT).
  Future<void> loginPlatform(
      String username, String password, ApiClient apiClient) async {
    final response = await apiClient.post<Map<String, dynamic>>(
      '/api/v1/auth/platform/login',
      {'username': username, 'password': password},
      fromJson: (json) => json as Map<String, dynamic>,
    );

    _token = response['token'] as String;
    final u = response['user'] as Map<String, dynamic>;
    final name = (u['displayName'] as String?) ?? (u['username'] as String);
    _user = CurrentUser(
      userId: 'platform:${u['username']}',
      username: u['username'] as String,
      name: name,
      role: (u['role'] as String?) ?? 'PLATFORM_ADMIN',
      tenantId: '',
      hospitalGroupId: '',
      branchId: '',
    );

    await _prefs.setString(_kToken, _token!);
    await _prefs.setString(_kUser, jsonEncode(_user!.toJson()));

    notifyListeners();
  }

  Future<void> logout() async {
    _token = null;
    _user = null;
    await _prefs.remove(_kToken);
    await _prefs.remove(_kUser);
    notifyListeners();
  }
}
