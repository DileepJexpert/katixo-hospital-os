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

  final _blockingCtrl = TextEditingController();
  final _warningCtrl = TextEditingController();
  bool _checklistLoaded = false;

  @override
  void initState() {
    super.initState();
    _loadChecklist();
  }

  @override
  void dispose() {
    _thresholdCtrl.dispose();
    _blockingCtrl.dispose();
    _warningCtrl.dispose();
    super.dispose();
  }

  Future<void> _loadChecklist() async {
    try {
      final api = context.read<ApiClient>();
      final f = await api.get<Map<String, dynamic>>('/api/v1/settings/features',
          fromJson: (j) => j as Map<String, dynamic>);
      if (mounted) {
        setState(() {
          _blockingCtrl.text = '${f['dischargeChecklistBlockingItems'] ?? ''}';
          _warningCtrl.text = '${f['dischargeChecklistWarningItems'] ?? ''}';
          _checklistLoaded = true;
        });
      }
    } catch (_) {
      // Non-fatal — the rest of settings still works.
    }
  }

  Future<void> _saveChecklist() async {
    setState(() {
      _saving = true;
      _info = null;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      await api.put<dynamic>(
        '/api/v1/settings/features',
        {
          'dischargeChecklistBlockingItems': _blockingCtrl.text.trim(),
          'dischargeChecklistWarningItems': _warningCtrl.text.trim(),
        },
        fromJson: (j) => j,
      );
      if (mounted) setState(() => _info = 'Discharge checklist saved');
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
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
          const SizedBox(height: Space.md),
          SectionCard(
            title: 'Discharge checklist',
            icon: Icons.checklist_outlined,
            subtitle: 'Comma-separated item codes shown at IPD discharge. Blocking items '
                'must be ticked before a NORMAL discharge; advisory items only warn. '
                'Codes are shown title-cased (e.g. FINAL_BILL_CLEARED → "Final bill cleared").',
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                TextField(
                  controller: _blockingCtrl,
                  decoration: const InputDecoration(
                    labelText: 'Blocking items (CSV)',
                    hintText: 'FINAL_BILL_CLEARED,MEDICINES_RETURNED,REPORTS_HANDED_OVER',
                  ),
                ),
                const SizedBox(height: Space.sm),
                TextField(
                  controller: _warningCtrl,
                  decoration: const InputDecoration(
                    labelText: 'Advisory items (CSV)',
                    hintText: 'FOLLOW_UP_SCHEDULED,DISCHARGE_SUMMARY_GIVEN',
                  ),
                ),
                const SizedBox(height: Space.sm),
                Align(
                  alignment: Alignment.centerRight,
                  child: FilledButton(
                    onPressed: (_saving || !_checklistLoaded) ? null : _saveChecklist,
                    child: const Text('Save checklist'),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
