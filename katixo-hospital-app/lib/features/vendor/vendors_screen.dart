import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/auth/auth_state.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/empty_state.dart';
import '../../core/widgets/status_chip.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// Vendor / supplier master: the reusable payees expenses (and future purchase
/// bills) link to. Create / edit / activate-deactivate, with GSTIN, contact and
/// bank details. Body widget — host supplies the AppShell. BILLING/ADMIN.
class VendorsScreen extends StatefulWidget {
  const VendorsScreen({super.key});

  @override
  State<VendorsScreen> createState() => _VendorsScreenState();
}

class _VendorsScreenState extends State<VendorsScreen> {
  static const _types = <String>[
    'SUPPLIER', 'SERVICE_PROVIDER', 'LANDLORD', 'UTILITY',
    'CONTRACTOR', 'GOVERNMENT', 'OTHER',
  ];

  List<Map<String, dynamic>> _vendors = const [];
  bool _includeInactive = false;
  bool _loading = false;
  String? _error;
  String? _info;

  bool get _canEdit {
    final role = context.read<AuthState>().currentUser?.role ?? '';
    return role == 'BILLING' || role == 'ADMIN' || role == 'SUPER_ADMIN';
  }

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
        '/api/v1/vendors?includeInactive=$_includeInactive',
        fromJson: (json) =>
            List<Map<String, dynamic>>.from(json as List? ?? const []),
      );
      if (mounted) setState(() => _vendors = list);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Could not load vendors: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _toggleActive(Map<String, dynamic> v) async {
    final id = v['id'];
    final active = v['active'] == true;
    await _action(
      '/api/v1/vendors/$id/${active ? 'deactivate' : 'activate'}',
      active ? 'Vendor deactivated' : 'Vendor activated',
    );
  }

  Future<void> _action(String path, String okMsg) async {
    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });
    try {
      final api = context.read<ApiClient>();
      await api.post<dynamic>(path, const <String, dynamic>{}, fromJson: (j) => j);
      setState(() => _info = okMsg);
      await _load();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _editDialog([Map<String, dynamic>? existing]) async {
    final isEdit = existing != null;
    final ctrls = <String, TextEditingController>{
      for (final f in [
        'name', 'gstin', 'pan', 'contactPerson', 'contactPhone', 'contactEmail',
        'addressLine', 'city', 'state', 'pincode', 'bankAccountName',
        'bankAccountNumber', 'bankIfsc', 'notes',
      ])
        f: TextEditingController(text: '${existing?[f] ?? ''}'),
    };
    String type = '${existing?['vendorType'] ?? 'SUPPLIER'}';

    Widget field(String key, String label, {int maxLines = 1}) => Padding(
          padding: const EdgeInsets.only(bottom: Space.sm),
          child: TextField(
            controller: ctrls[key],
            maxLines: maxLines,
            decoration: InputDecoration(labelText: label),
          ),
        );

    final save = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(isEdit ? 'Edit vendor' : 'Add vendor'),
        content: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                field('name', 'Name *'),
                StatefulBuilder(
                  builder: (context, setLocal) => DropdownButtonFormField<String>(
                    value: type,
                    decoration: const InputDecoration(labelText: 'Type'),
                    items: [
                      for (final t in _types)
                        DropdownMenuItem(value: t, child: Text(_titleCase(t))),
                    ],
                    onChanged: (v) => setLocal(() => type = v ?? 'SUPPLIER'),
                  ),
                ),
                const SizedBox(height: Space.sm),
                field('gstin', 'GSTIN'),
                field('pan', 'PAN'),
                field('contactPerson', 'Contact person'),
                field('contactPhone', 'Contact phone'),
                field('contactEmail', 'Contact email'),
                field('addressLine', 'Address'),
                field('city', 'City'),
                field('state', 'State'),
                field('pincode', 'Pincode'),
                field('bankAccountName', 'Bank account name'),
                field('bankAccountNumber', 'Bank account number'),
                field('bankIfsc', 'IFSC'),
                field('notes', 'Notes', maxLines: 2),
              ],
            ),
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Save')),
        ],
      ),
    );
    if (save != true) return;
    if (ctrls['name']!.text.trim().isEmpty) {
      setState(() => _error = 'Vendor name is required');
      return;
    }

    final body = <String, dynamic>{'vendorType': type};
    ctrls.forEach((k, c) {
      final v = c.text.trim();
      if (v.isNotEmpty) body[k] = v;
    });

    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });
    try {
      final api = context.read<ApiClient>();
      if (isEdit) {
        await api.put<dynamic>('/api/v1/vendors/${existing['id']}', body, fromJson: (j) => j);
        setState(() => _info = 'Vendor updated');
      } else {
        await api.post<dynamic>('/api/v1/vendors', body, fromJson: (j) => j);
        setState(() => _info = 'Vendor created');
      }
      await _load();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
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
              Text('Vendors', style: theme.textTheme.titleLarge),
              const Spacer(),
              Row(
                children: [
                  const Text('Show inactive'),
                  Switch(
                    value: _includeInactive,
                    onChanged: (v) {
                      setState(() => _includeInactive = v);
                      _load();
                    },
                  ),
                ],
              ),
              const SizedBox(width: Space.sm),
              IconButton(
                tooltip: 'Refresh',
                onPressed: _loading ? null : _load,
                icon: const Icon(Icons.refresh),
              ),
              if (_canEdit) ...[
                const SizedBox(width: Space.sm),
                FilledButton.icon(
                  onPressed: _loading ? null : () => _editDialog(),
                  icon: const Icon(Icons.add, size: 18),
                  label: const Text('Add vendor'),
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
            child: _vendors.isEmpty
                ? EmptyState(
                    icon: Icons.store_outlined,
                    title: 'No vendors yet',
                    message: _canEdit
                        ? 'Add suppliers, landlords, utilities and contractors to reuse on expenses.'
                        : 'No vendors configured.',
                  )
                : Card(
                    child: ListView.separated(
                      itemCount: _vendors.length,
                      separatorBuilder: (_, __) => const Divider(height: 1),
                      itemBuilder: (context, i) {
                        final v = _vendors[i];
                        final active = v['active'] == true;
                        return ListTile(
                          leading: const Icon(Icons.store_outlined),
                          title: Row(
                            children: [
                              Flexible(child: Text('${v['name']}', style: theme.textTheme.titleSmall)),
                              const SizedBox(width: Space.sm),
                              if (!active) StatusChip.auto('INACTIVE'),
                            ],
                          ),
                          subtitle: Text(
                            '${v['vendorCode']} · ${_titleCase('${v['vendorType']}')}'
                            '${v['gstin'] != null ? ' · GSTIN ${v['gstin']}' : ''}'
                            '${v['contactPhone'] != null ? ' · ${v['contactPhone']}' : ''}',
                            style: theme.textTheme.bodySmall,
                          ),
                          trailing: _canEdit
                              ? Row(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    IconButton(
                                      tooltip: 'Edit',
                                      icon: const Icon(Icons.edit_outlined, size: 20),
                                      onPressed: _loading ? null : () => _editDialog(v),
                                    ),
                                    IconButton(
                                      tooltip: active ? 'Deactivate' : 'Activate',
                                      icon: Icon(active ? Icons.block : Icons.check_circle_outline, size: 20),
                                      onPressed: _loading ? null : () => _toggleActive(v),
                                    ),
                                  ],
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

  String _titleCase(String s) => s
      .toLowerCase()
      .split('_')
      .map((w) => w.isEmpty ? w : '${w[0].toUpperCase()}${w.substring(1)}')
      .join(' ');
}
