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

  Future<void> _set({bool? pharmacy, bool? sms, bool? whatsapp, bool? portal}) async {
    setState(() {
      _saving = true;
      _info = null;
      _error = null;
    });
    final flags = context.read<FeatureFlags>();
    final api = context.read<ApiClient>();
    final ok = await flags.update(api,
        pharmacyEnabled: pharmacy, smsEnabled: sms,
        whatsappEnabled: whatsapp, patientPortalEnabled: portal);
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

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final flags = context.watch<FeatureFlags>();
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
        ],
      ),
    );
  }
}
