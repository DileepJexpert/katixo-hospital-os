import 'package:flutter/foundation.dart';

import '../api/http_client.dart';

/// Per-hospital feature flags (policy-driven), loaded after login. Drives which
/// modules/menus are visible — e.g. whether the hospital runs its own pharmacy.
class FeatureFlags extends ChangeNotifier {
  bool pharmacyEnabled = true; // safe default until loaded
  bool smsEnabled = false;
  bool whatsappEnabled = false;
  bool patientPortalEnabled = false;
  double expenseApprovalThreshold = 0; // 0 = approval disabled
  bool loaded = false;

  void _apply(Map<String, dynamic> m) {
    pharmacyEnabled = m['pharmacyEnabled'] != false; // default true
    smsEnabled = m['smsEnabled'] == true;
    whatsappEnabled = m['whatsappEnabled'] == true;
    patientPortalEnabled = m['patientPortalEnabled'] == true;
    expenseApprovalThreshold =
        num.tryParse('${m['expenseApprovalThreshold'] ?? 0}')?.toDouble() ?? 0;
    loaded = true;
    notifyListeners();
  }

  Future<void> load(ApiClient api) async {
    try {
      final m = await api.get<Map<String, dynamic>>(
        '/api/v1/settings/features',
        fromJson: (j) => j as Map<String, dynamic>,
      );
      _apply(m);
    } catch (_) {
      // keep safe defaults on failure
      loaded = true;
      notifyListeners();
    }
  }

  /// Admin update. Returns true on success.
  Future<bool> update(ApiClient api, {bool? pharmacyEnabled, bool? smsEnabled,
      bool? whatsappEnabled, bool? patientPortalEnabled,
      double? expenseApprovalThreshold}) async {
    try {
      final m = await api.put<Map<String, dynamic>>(
        '/api/v1/settings/features',
        {
          if (pharmacyEnabled != null) 'pharmacyEnabled': pharmacyEnabled,
          if (smsEnabled != null) 'smsEnabled': smsEnabled,
          if (whatsappEnabled != null) 'whatsappEnabled': whatsappEnabled,
          if (patientPortalEnabled != null) 'patientPortalEnabled': patientPortalEnabled,
          if (expenseApprovalThreshold != null)
            'expenseApprovalThreshold': expenseApprovalThreshold,
        },
        fromJson: (j) => j as Map<String, dynamic>,
      );
      _apply(m);
      return true;
    } catch (_) {
      return false;
    }
  }

  void reset() {
    pharmacyEnabled = true;
    smsEnabled = false;
    whatsappEnabled = false;
    patientPortalEnabled = false;
    expenseApprovalThreshold = 0;
    loaded = false;
    notifyListeners();
  }
}
