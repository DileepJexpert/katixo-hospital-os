import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/auth/auth_state.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/empty_state.dart';
import '../../core/widgets/status_chip.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;
import '../patient/patient_picker.dart';

/// Billing packages: define bundled fixed-price packages (+ component services)
/// and apply a package to a patient's encounter (adds its price as a charge that
/// flows into the bill). Body widget. ADMIN defines; BILLING/ADMIN apply.
class PackagesScreen extends StatefulWidget {
  const PackagesScreen({super.key});

  @override
  State<PackagesScreen> createState() => _PackagesScreenState();
}

class _PackagesScreenState extends State<PackagesScreen> {
  static const _types = <String>['FIXED', 'ITEMIZED_INTERNAL', 'EXCESS_BILLING'];

  List<Map<String, dynamic>> _packages = const [];
  bool _loading = false;
  String? _error;
  String? _info;

  String get _role => context.read<AuthState>().currentUser?.role ?? '';
  bool get _isAdmin => _role == 'ADMIN' || _role == 'SUPER_ADMIN';
  bool get _canApply => _role == 'BILLING' || _isAdmin;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      final list = await api.get<List<Map<String, dynamic>>>(
        '/api/v1/billing/packages',
        fromJson: (j) => List<Map<String, dynamic>>.from(j as List? ?? const []),
      );
      if (mounted) setState(() => _packages = list);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Could not load packages: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _act(Future<void> Function() action, String okMsg) async {
    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });
    try {
      await action();
      setState(() => _info = okMsg);
      await _load();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Action failed: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _createDialog() async {
    final codeCtrl = TextEditingController();
    final nameCtrl = TextEditingController();
    final priceCtrl = TextEditingController();
    String type = 'FIXED';
    final components = <Map<String, dynamic>>[];

    final created = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setLocal) => AlertDialog(
          title: const Text('New package'),
          content: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
            child: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  TextField(controller: codeCtrl, decoration: const InputDecoration(labelText: 'Code *')),
                  TextField(controller: nameCtrl, decoration: const InputDecoration(labelText: 'Name *')),
                  Row(
                    children: [
                      Expanded(
                        child: DropdownButtonFormField<String>(
                          value: type,
                          isExpanded: true,
                          decoration: const InputDecoration(labelText: 'Type'),
                          items: [for (final t in _types) DropdownMenuItem(value: t, child: Text(_title(t)))],
                          onChanged: (v) => setLocal(() => type = v ?? 'FIXED'),
                        ),
                      ),
                      const SizedBox(width: Space.md),
                      Expanded(
                        child: TextField(
                          controller: priceCtrl,
                          keyboardType: TextInputType.number,
                          decoration: const InputDecoration(labelText: 'Price (₹) *'),
                        ),
                      ),
                    ],
                  ),
                  const Divider(height: Space.xl),
                  Text('Included components', style: Theme.of(context).textTheme.titleSmall),
                  for (var i = 0; i < components.length; i++)
                    ListTile(
                      contentPadding: EdgeInsets.zero,
                      dense: true,
                      title: Text('${components[i]['serviceName']} (${components[i]['serviceCode']})'),
                      subtitle: Text('qty ${components[i]['includedQuantity']}'),
                      trailing: IconButton(
                        icon: const Icon(Icons.delete_outline, size: 20),
                        onPressed: () => setLocal(() => components.removeAt(i)),
                      ),
                    ),
                  TextButton.icon(
                    onPressed: () async {
                      final c = await _addComponentDialog();
                      if (c != null) setLocal(() => components.add(c));
                    },
                    icon: const Icon(Icons.add, size: 18),
                    label: const Text('Add component'),
                  ),
                ],
              ),
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
            FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Create')),
          ],
        ),
      ),
    );
    if (created != true) return;
    final price = double.tryParse(priceCtrl.text.trim());
    if (codeCtrl.text.trim().isEmpty || nameCtrl.text.trim().isEmpty || price == null) {
      setState(() => _error = 'Code, name and a valid price are required');
      return;
    }
    await _act(() async {
      final api = context.read<ApiClient>();
      await api.post<dynamic>('/api/v1/billing/packages', {
        'code': codeCtrl.text.trim(),
        'name': nameCtrl.text.trim(),
        'packageType': type,
        'packagePrice': price,
        'components': components,
      }, fromJson: (j) => j);
    }, 'Package created');
  }

  Future<Map<String, dynamic>?> _addComponentDialog() async {
    final codeCtrl = TextEditingController();
    final nameCtrl = TextEditingController();
    final qtyCtrl = TextEditingController(text: '1');
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Add component'),
        content: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(controller: codeCtrl, decoration: const InputDecoration(labelText: 'Service code *')),
              TextField(controller: nameCtrl, decoration: const InputDecoration(labelText: 'Service name')),
              TextField(controller: qtyCtrl, keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: 'Included qty')),
            ],
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Add')),
        ],
      ),
    );
    if (ok != true || codeCtrl.text.trim().isEmpty) return null;
    return {
      'serviceCode': codeCtrl.text.trim(),
      'serviceName': nameCtrl.text.trim().isEmpty ? codeCtrl.text.trim() : nameCtrl.text.trim(),
      'includedQuantity': double.tryParse(qtyCtrl.text.trim()) ?? 1,
    };
  }

  Future<void> _applyDialog(Map<String, dynamic> pkg) async {
    final patient = await showPatientPicker(context);
    if (patient == null) return;
    String sourceType = 'IPD_ADMISSION';
    final sourceIdCtrl = TextEditingController();
    final proceed = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setLocal) => AlertDialog(
          title: Text('Apply ${pkg['name']}'),
          content: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text('${patient['fullName']} · ₹${pkg['packagePrice']}',
                    style: Theme.of(context).textTheme.bodySmall),
                const SizedBox(height: Space.sm),
                DropdownButtonFormField<String>(
                  value: sourceType,
                  decoration: const InputDecoration(labelText: 'Encounter'),
                  items: const [
                    DropdownMenuItem(value: 'OPD_VISIT', child: Text('OPD Visit')),
                    DropdownMenuItem(value: 'IPD_ADMISSION', child: Text('IPD Admission')),
                  ],
                  onChanged: (v) => setLocal(() => sourceType = v ?? 'IPD_ADMISSION'),
                ),
                TextField(
                  controller: sourceIdCtrl,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: 'Visit / Admission ID *'),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
            FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Apply')),
          ],
        ),
      ),
    );
    if (proceed != true) return;
    final sourceId = int.tryParse(sourceIdCtrl.text.trim());
    if (sourceId == null) {
      setState(() => _error = 'A valid encounter ID is required');
      return;
    }
    await _act(() async {
      final api = context.read<ApiClient>();
      await api.post<dynamic>('/api/v1/billing/packages/${pkg['id']}/apply', {
        'patientId': patient['id'],
        'sourceType': sourceType,
        'sourceId': sourceId,
      }, fromJson: (j) => j);
    }, 'Package applied — it will appear on the generated bill');
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return PageContainer(
      scrollable: false,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text('Packages', style: theme.textTheme.titleLarge),
              const Spacer(),
              IconButton(
                tooltip: 'Refresh',
                onPressed: _loading ? null : _load,
                icon: const Icon(Icons.refresh),
              ),
              if (_isAdmin) ...[
                const SizedBox(width: Space.sm),
                FilledButton.icon(
                  onPressed: _loading ? null : _createDialog,
                  icon: const Icon(Icons.add, size: 18),
                  label: const Text('New package'),
                ),
              ],
            ],
          ),
          const SizedBox(height: Space.md),
          if (_error != null) ...[
            MessageBanner.error(_error!),
            const SizedBox(height: Space.md),
          ],
          if (_info != null) ...[
            MessageBanner.success(_info!),
            const SizedBox(height: Space.md),
          ],
          Expanded(
            child: _packages.isEmpty
                ? EmptyState(
                    icon: Icons.inventory_2_outlined,
                    title: _loading ? 'Loading…' : 'No packages',
                    message: _isAdmin ? 'Define bundled packages with "New package".' : 'No packages defined.')
                : Card(
                    child: ListView.separated(
                      itemCount: _packages.length,
                      separatorBuilder: (_, __) => const Divider(height: 1),
                      itemBuilder: (context, i) {
                        final p = _packages[i];
                        return ListTile(
                          leading: const Icon(Icons.inventory_2_outlined),
                          title: Row(
                            children: [
                              Flexible(child: Text('${p['code']} · ${p['name']}',
                                  style: theme.textTheme.titleSmall)),
                              const SizedBox(width: Space.sm),
                              StatusChip.auto('${p['packageType']}'),
                            ],
                          ),
                          subtitle: Text('₹${p['packagePrice']}', style: theme.textTheme.bodySmall),
                          trailing: _canApply
                              ? FilledButton(
                                  onPressed: _loading ? null : () => _applyDialog(p),
                                  child: const Text('Apply'),
                                )
                              : null,
                        );
                      },
                    ),
                  ),
          ),
        ],
      ),
    );
  }

  String _title(String s) => s
      .toLowerCase()
      .split('_')
      .map((w) => w.isEmpty ? w : '${w[0].toUpperCase()}${w.substring(1)}')
      .join(' ');
}
