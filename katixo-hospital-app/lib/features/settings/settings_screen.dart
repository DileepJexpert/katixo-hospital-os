import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/config/feature_flags.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/section_card.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// Hospital settings (admin): feature toggles that show/hide whole modules.
class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  bool _saving = false;
  String? _info;
  String? _error;

  final _thresholdCtrl = TextEditingController();
  bool _thresholdInit = false;

  @override
  void dispose() {
    _thresholdCtrl.dispose();
    super.dispose();
  }

  Future<void> _set({bool? pharmacy, bool? sms, bool? whatsapp, bool? portal,
      double? expenseApprovalThreshold}) async {
    setState(() {
      _saving = true;
      _info = null;
      _error = null;
    });
    final flags = context.read<FeatureFlags>();
    final api = context.read<ApiClient>();
    final ok = await flags.update(api,
        pharmacyEnabled: pharmacy, smsEnabled: sms,
        whatsappEnabled: whatsapp, patientPortalEnabled: portal,
        expenseApprovalThreshold: expenseApprovalThreshold);
    if (mounted) {
      setState(() {
        _saving = false;
        if (ok) {
          _info = 'Settings saved';
        } else {
          _error = 'Could not save settings';
        }
      });
    }
  }

  void _saveThreshold() {
    final v = double.tryParse(_thresholdCtrl.text.trim());
    if (v == null || v < 0) {
      setState(() => _error = 'Enter a valid amount (0 disables approval)');
      return;
    }
    _set(expenseApprovalThreshold: v);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final flags = context.watch<FeatureFlags>();
    if (!_thresholdInit && flags.loaded) {
      _thresholdCtrl.text = flags.expenseApprovalThreshold == 0
          ? ''
          : flags.expenseApprovalThreshold.toStringAsFixed(0);
      _thresholdInit = true;
    }
    return PageContainer(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Hospital Settings', style: theme.textTheme.titleLarge),
          const SizedBox(height: Space.md),
          if (_error != null) ...[
            MessageBanner.error(_error!),
            const SizedBox(height: Space.md),
          ],
          if (_info != null) ...[
            MessageBanner.success(_info!),
            const SizedBox(height: Space.md),
          ],
          SectionCard(
            title: 'Modules',
            icon: Icons.toggle_on_outlined,
            subtitle: 'Turn modules on or off for this hospital',
            child: Column(
              children: [
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  secondary: const Icon(Icons.local_pharmacy_outlined),
                  title: const Text('In-house pharmacy'),
                  subtitle: const Text(
                      'This hospital runs its own pharmacy/medical store (stock, OPD/OTC/IPD dispensing). '
                      'Turn off if pharmacy is outsourced or not present — pharmacy menus are then hidden.'),
                  value: flags.pharmacyEnabled,
                  onChanged: _saving ? null : (v) => _set(pharmacy: v),
                ),
                const Divider(),
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  secondary: const Icon(Icons.sms_outlined),
                  title: const Text('SMS notifications'),
                  value: flags.smsEnabled,
                  onChanged: _saving ? null : (v) => _set(sms: v),
                ),
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  secondary: const Icon(Icons.chat_outlined),
                  title: const Text('WhatsApp notifications'),
                  value: flags.whatsappEnabled,
                  onChanged: _saving ? null : (v) => _set(whatsapp: v),
                ),
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  secondary: const Icon(Icons.web_outlined),
                  title: const Text('Patient portal'),
                  value: flags.patientPortalEnabled,
                  onChanged: _saving ? null : (v) => _set(portal: v),
                ),
              ],
            ),
          ),
          const SizedBox(height: Space.md),
          SectionCard(
            title: 'Expense approval',
            icon: Icons.rule_outlined,
            subtitle: 'Expenses above this amount need admin approval before they '
                'post to the books. Leave blank or 0 to disable approval.',
            child: Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _thresholdCtrl,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(
                      labelText: 'Approval threshold (₹)',
                      hintText: '0 = no approval needed',
                    ),
                    onSubmitted: (_) => _saveThreshold(),
                  ),
                ),
                const SizedBox(width: Space.md),
                FilledButton(
                  onPressed: _saving ? null : _saveThreshold,
                  child: const Text('Save'),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
