import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/empty_state.dart';
import '../../core/widgets/kpi_tile.dart';
import '../../core/widgets/section_card.dart';
import '../../core/widgets/status_chip.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// Platform operator view of all hospital tenants: provision a new tenant
/// (registry row + schema + migrations) and suspend / activate existing ones.
/// Backed by /api/v1/platform/tenants (PLATFORM_ADMIN only). Body widget — the
/// PlatformConsoleHome supplies the AppShell.
class PlatformTenantsScreen extends StatefulWidget {
  const PlatformTenantsScreen({super.key});

  @override
  State<PlatformTenantsScreen> createState() => _PlatformTenantsScreenState();
}

class _PlatformTenantsScreenState extends State<PlatformTenantsScreen> {
  List<Map<String, dynamic>> _tenants = const [];
  bool _loading = false;
  String? _error;
  String? _info;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _load());
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      final list = await api.get<List<Map<String, dynamic>>>(
        '/api/v1/platform/tenants',
        fromJson: (json) =>
            List<Map<String, dynamic>>.from(json as List? ?? const []),
      );
      if (mounted) setState(() => _tenants = list);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (_) {
      setState(() => _error = 'Could not load tenants — check your connection.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  int _countStatus(String s) =>
      _tenants.where((t) => '${t['status']}' == s).length;

  Future<void> _act(
      String path, Map<String, dynamic> body, String ok) async {
    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });
    try {
      final api = context.read<ApiClient>();
      await api.post<Map<String, dynamic>>(path, body,
          fromJson: (j) => j as Map<String, dynamic>);
      if (!mounted) return; // operator may have signed out mid-request
      setState(() => _info = ok);
      await _load();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (_) {
      setState(() => _error = 'That action could not be completed.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _provisionDialog() async {
    final idCtrl = TextEditingController();
    final nameCtrl = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Provision tenant'),
        content: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: idCtrl,
                autofocus: true,
                decoration: const InputDecoration(
                  labelText: 'Tenant ID *',
                  helperText: 'Short slug, e.g. apollo-jaipur',
                ),
              ),
              const SizedBox(height: Space.md),
              TextField(
                controller: nameCtrl,
                decoration: const InputDecoration(
                  labelText: 'Display name *',
                  helperText: 'e.g. Apollo Hospital, Jaipur',
                ),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Provision'),
          ),
        ],
      ),
    );
    if (ok != true) return;
    final tenantId = idCtrl.text.trim();
    final displayName = nameCtrl.text.trim();
    if (tenantId.isEmpty || displayName.isEmpty) {
      setState(() => _error = 'Tenant ID and display name are both required');
      return;
    }
    await _act(
      '/api/v1/platform/tenants',
      {'tenantId': tenantId, 'displayName': displayName},
      "Tenant '$tenantId' provisioned",
    );
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
              Text('Tenants', style: theme.textTheme.titleLarge),
              const Spacer(),
              IconButton(
                tooltip: 'Refresh',
                onPressed: _loading ? null : _load,
                icon: const Icon(Icons.refresh),
              ),
              const SizedBox(width: Space.sm),
              FilledButton.icon(
                onPressed: _loading ? null : _provisionDialog,
                icon: const Icon(Icons.add, size: 18),
                label: const Text('Provision tenant'),
              ),
            ],
          ),
          const SizedBox(height: Space.md),
          _kpiStrip(),
          const SizedBox(height: Space.md),
          if (_error != null) ...[
            MessageBanner.error(_error!),
            const SizedBox(height: Space.md),
          ],
          if (_info != null) ...[
            MessageBanner.success(_info!),
            const SizedBox(height: Space.md),
          ],
          if (_loading) const LinearProgressIndicator(),
          Expanded(child: _list(theme)),
        ],
      ),
    );
  }

  Widget _kpiStrip() {
    return Wrap(
      spacing: Space.md,
      runSpacing: Space.sm,
      children: [
        KpiTile(
            label: 'Total',
            value: '${_tenants.length}',
            icon: Icons.apartment_outlined),
        KpiTile(
            label: 'Active',
            value: '${_countStatus('ACTIVE')}',
            icon: Icons.check_circle_outline),
        KpiTile(
            label: 'Suspended',
            value: '${_countStatus('SUSPENDED')}',
            icon: Icons.pause_circle_outline),
        KpiTile(
            label: 'Provisioning',
            value: '${_countStatus('PROVISIONING')}',
            icon: Icons.hourglass_empty),
      ],
    );
  }

  Widget _list(ThemeData theme) {
    if (_tenants.isEmpty) {
      return EmptyState(
        icon: Icons.apartment_outlined,
        title: 'No tenants yet',
        message: 'Provision the first hospital tenant to get started.',
        action: FilledButton.icon(
          onPressed: _loading ? null : _provisionDialog,
          icon: const Icon(Icons.add, size: 18),
          label: const Text('Provision tenant'),
        ),
      );
    }
    return SingleChildScrollView(
      child: SectionCard(
        title: 'All tenants',
        child: Column(
          children: [
            for (final t in _tenants) _row(theme, t),
          ],
        ),
      ),
    );
  }

  Widget _row(ThemeData theme, Map<String, dynamic> t) {
    final tenantId = '${t['tenantId']}';
    final status = '${t['status']}';
    final isActive = status == 'ACTIVE';
    final isSuspended = status == 'SUSPENDED';
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: Space.xs),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('${t['displayName'] ?? tenantId}',
                    style: theme.textTheme.bodyLarge),
                Text('$tenantId · ${t['schemaName'] ?? ''}',
                    style: theme.textTheme.labelSmall
                        ?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
              ],
            ),
          ),
          const SizedBox(width: Space.sm),
          StatusChip.auto(status),
          const SizedBox(width: Space.sm),
          if (isActive)
            TextButton.icon(
              onPressed: _loading
                  ? null
                  : () => _act('/api/v1/platform/tenants/$tenantId/suspend',
                      const {}, "Tenant '$tenantId' suspended"),
              icon: const Icon(Icons.pause_circle_outline, size: 18),
              label: const Text('Suspend'),
            )
          else if (isSuspended)
            TextButton.icon(
              onPressed: _loading
                  ? null
                  : () => _act('/api/v1/platform/tenants/$tenantId/activate',
                      const {}, "Tenant '$tenantId' activated"),
              icon: const Icon(Icons.play_circle_outline, size: 18),
              label: const Text('Activate'),
            ),
        ],
      ),
    );
  }
}
