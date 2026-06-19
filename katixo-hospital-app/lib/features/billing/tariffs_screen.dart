import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/auth/auth_state.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/empty_state.dart';
import '../../core/widgets/section_card.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// Tariff master: admins create + browse the hospital's service-rate catalogue
/// (room rent, doctor consultation, procedures, OT, lab, etc.). The same codes
/// are picked up by manual hospital charges on a bill. Body widget — host
/// supplies the AppShell.
class TariffsScreen extends StatefulWidget {
  const TariffsScreen({super.key});

  @override
  State<TariffsScreen> createState() => _TariffsScreenState();
}

class _TariffsScreenState extends State<TariffsScreen> {
  static const _categories = <String>[
    'CONSULTATION', 'ROOM_RENT', 'PROCEDURE', 'LAB',
    'RADIOLOGY', 'NURSING', 'OT', 'OTHER',
  ];

  List<Map<String, dynamic>> _tariffs = const [];
  bool _loading = false;
  String? _error;
  String? _info;

  final _codeCtrl = TextEditingController();
  final _nameCtrl = TextEditingController();
  final _rateCtrl = TextEditingController();
  String _category = 'CONSULTATION';

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _codeCtrl.dispose();
    _nameCtrl.dispose();
    _rateCtrl.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      final list = await api.get<List<Map<String, dynamic>>>(
        '/api/v1/billing/tariffs',
        fromJson: (json) =>
            List<Map<String, dynamic>>.from(json as List? ?? const []),
      );
      if (mounted) setState(() => _tariffs = list);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Could not load tariffs: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _create() async {
    final code = _codeCtrl.text.trim();
    final name = _nameCtrl.text.trim();
    final rate = double.tryParse(_rateCtrl.text.trim());
    if (code.isEmpty || name.isEmpty || rate == null || rate < 0) {
      setState(() => _error = 'Code, name and a non-negative rate are required');
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });
    try {
      final api = context.read<ApiClient>();
      await api.post<Map<String, dynamic>>(
        '/api/v1/billing/tariffs',
        {
          'serviceCode': code,
          'serviceName': name,
          'category': _category,
          'rate': rate,
        },
        fromJson: (json) => json as Map<String, dynamic>,
      );
      _codeCtrl.clear();
      _nameCtrl.clear();
      _rateCtrl.clear();
      setState(() => _info = 'Tariff $code added');
      await _load();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Failed to add tariff: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final role = context.read<AuthState>().currentUser?.role ?? '';
    final canCreate = role == 'ADMIN' || role == 'SUPER_ADMIN';

    return PageContainer(
      scrollable: false,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text('Tariff Master', style: theme.textTheme.titleLarge),
              const Spacer(),
              IconButton(
                tooltip: 'Refresh',
                onPressed: _loading ? null : _load,
                icon: const Icon(Icons.refresh),
              ),
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
          if (canCreate) ...[
            SectionCard(
              title: 'Add tariff',
              icon: Icons.add_circle_outline,
              subtitle:
                  'Service code is the stable id used by manual charges and auto-charges.',
              child: Column(
                children: [
                  Row(
                    children: [
                      Expanded(
                        flex: 2,
                        child: TextField(
                          controller: _codeCtrl,
                          decoration: const InputDecoration(
                              labelText: 'Service code *'),
                        ),
                      ),
                      const SizedBox(width: Space.md),
                      Expanded(
                        flex: 3,
                        child: TextField(
                          controller: _nameCtrl,
                          decoration: const InputDecoration(
                              labelText: 'Service name *'),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: Space.sm),
                  Row(
                    children: [
                      Expanded(
                        child: DropdownButtonFormField<String>(
                          initialValue: _category,
                          decoration:
                              const InputDecoration(labelText: 'Category'),
                          items: [
                            for (final c in _categories)
                              DropdownMenuItem(value: c, child: Text(c)),
                          ],
                          onChanged: (v) =>
                              setState(() => _category = v ?? 'OTHER'),
                        ),
                      ),
                      const SizedBox(width: Space.md),
                      Expanded(
                        child: TextField(
                          controller: _rateCtrl,
                          keyboardType: TextInputType.number,
                          decoration:
                              const InputDecoration(labelText: 'Rate (₹) *'),
                        ),
                      ),
                      const SizedBox(width: Space.md),
                      FilledButton(
                        onPressed: _loading ? null : _create,
                        child: const Text('Add'),
                      ),
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(height: Space.md),
          ],
          Expanded(
            child: _tariffs.isEmpty
                ? EmptyState(
                    icon: Icons.list_alt_outlined,
                    title: 'No tariffs yet',
                    message: canCreate
                        ? 'Add tariffs above; they appear here once created.'
                        : 'No service rates configured for this hospital yet.',
                  )
                : Card(
                    child: ListView.separated(
                      itemCount: _tariffs.length,
                      separatorBuilder: (_, __) => const Divider(height: 1),
                      itemBuilder: (context, i) {
                        final t = _tariffs[i];
                        return ListTile(
                          title:
                              Text('${t['serviceCode']} · ${t['serviceName']}'),
                          subtitle: Text('${t['category']}'),
                          trailing: Text('₹${t['rate']}',
                              style: theme.textTheme.titleSmall),
                        );
                      },
                    ),
                  ),
          ),
        ],
      ),
    );
  }
}
