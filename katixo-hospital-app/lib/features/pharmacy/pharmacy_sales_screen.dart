import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/auth/auth_state.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/empty_state.dart';
import '../../core/widgets/section_card.dart';
import '../../core/widgets/status_chip.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// Pharmacy sales: dispensed history (OPD cash, IPD credit, OTC), sale detail,
/// partial return of unused medicines, and full reverse. Body widget.
/// PHARMACIST/ADMIN (NURSE can return IPD indents — enforced server-side).
class PharmacySalesScreen extends StatefulWidget {
  const PharmacySalesScreen({super.key});

  @override
  State<PharmacySalesScreen> createState() => _PharmacySalesScreenState();
}

class _PharmacySalesScreenState extends State<PharmacySalesScreen> {
  List<Map<String, dynamic>> _sales = const [];
  Map<String, dynamic>? _selected; // sale detail (with lines)
  bool _loading = false;
  String? _error;
  String? _info;

  bool get _canAct {
    final role = context.read<AuthState>().currentUser?.role ?? '';
    return role == 'PHARMACIST' || role == 'NURSE' || role == 'ADMIN' ||
        role == 'SUPER_ADMIN';
  }

  bool get _canReverse {
    final role = context.read<AuthState>().currentUser?.role ?? '';
    return role == 'PHARMACIST' || role == 'ADMIN' || role == 'SUPER_ADMIN';
  }

  @override
  void initState() {
    super.initState();
    _loadList();
  }

  Future<void> _loadList() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      final list = await api.get<List<Map<String, dynamic>>>(
        '/api/v1/pharmacy-sales?limit=50',
        fromJson: (json) =>
            List<Map<String, dynamic>>.from(json as List? ?? const []),
      );
      if (mounted) setState(() => _sales = list);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Could not load sales: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _openSale(int id) async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      final sale = await api.get<Map<String, dynamic>>(
        '/api/v1/pharmacy-sales/$id',
        fromJson: (json) => json as Map<String, dynamic>,
      );
      if (mounted) setState(() => _selected = sale);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _returnDialog(Map<String, dynamic> sale) async {
    final lines = List<Map<String, dynamic>>.from(sale['lines'] as List? ?? const []);
    // Per-line returnable = quantity - returnedQuantity.
    final ctrls = <int, TextEditingController>{};
    final returnable = <int, num>{};
    for (var i = 0; i < lines.length; i++) {
      final qty = num.tryParse('${lines[i]['quantity']}') ?? 0;
      final ret = num.tryParse('${lines[i]['returnedQuantity'] ?? 0}') ?? 0;
      returnable[i] = qty - ret;
      ctrls[i] = TextEditingController();
    }
    final reasonCtrl = TextEditingController();

    final proceed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Return — ${sale['saleNumber']}'),
        content: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('Enter the quantity to return per item (max = remaining).'),
                const SizedBox(height: Space.sm),
                for (var i = 0; i < lines.length; i++)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: Space.xxs),
                    child: Row(
                      children: [
                        Expanded(
                          child: Text(
                            '${lines[i]['itemName'] ?? lines[i]['itemCode']}'
                            '  (rem ${returnable[i]})',
                          ),
                        ),
                        SizedBox(
                          width: 90,
                          child: TextField(
                            controller: ctrls[i],
                            enabled: (returnable[i] ?? 0) > 0,
                            keyboardType: TextInputType.number,
                            decoration: const InputDecoration(labelText: 'Qty'),
                          ),
                        ),
                      ],
                    ),
                  ),
                const SizedBox(height: Space.sm),
                TextField(
                  controller: reasonCtrl,
                  decoration: const InputDecoration(labelText: 'Reason'),
                ),
              ],
            ),
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Return')),
        ],
      ),
    );
    if (proceed != true) return;

    final returnLines = <Map<String, dynamic>>[];
    for (var i = 0; i < lines.length; i++) {
      final qty = num.tryParse(ctrls[i]!.text.trim());
      if (qty == null || qty <= 0) continue;
      if (qty > (returnable[i] ?? 0)) {
        setState(() => _error = '${lines[i]['itemName']}: cannot return more than ${returnable[i]}');
        return;
      }
      returnLines.add({'itemCode': lines[i]['itemCode'], 'quantity': qty});
    }
    if (returnLines.isEmpty) {
      setState(() => _error = 'Enter at least one quantity to return');
      return;
    }
    await _act('/api/v1/pharmacy-sales/${sale['id']}/return',
        {'reason': reasonCtrl.text.trim(), 'lines': returnLines}, 'Items returned',
        saleId: sale['id'] as int);
  }

  Future<void> _reverseDialog(Map<String, dynamic> sale) async {
    final reasonCtrl = TextEditingController();
    final proceed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Reverse — ${sale['saleNumber']}'),
        content: TextField(
          controller: reasonCtrl,
          decoration: const InputDecoration(labelText: 'Reason'),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Back')),
          FilledButton(
            style: FilledButton.styleFrom(
                backgroundColor: Theme.of(context).colorScheme.error),
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Reverse sale'),
          ),
        ],
      ),
    );
    if (proceed != true) return;
    await _act('/api/v1/pharmacy-sales/${sale['id']}/reverse',
        {'reason': reasonCtrl.text.trim()}, 'Sale reversed',
        saleId: sale['id'] as int);
  }

  Future<void> _act(String path, Object body, String okMsg, {required int saleId}) async {
    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });
    try {
      final api = context.read<ApiClient>();
      await api.post<dynamic>(path, body, fromJson: (j) => j);
      setState(() => _info = okMsg);
      await _openSale(saleId);
      await _loadList();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Action failed: $e');
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
              Text('Pharmacy Sales', style: theme.textTheme.titleLarge),
              const Spacer(),
              IconButton(
                tooltip: 'Refresh',
                onPressed: _loading ? null : () { _loadList(); if (_selected != null) _openSale(_selected!['id'] as int); },
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
          Expanded(child: _selected != null ? _detail(theme, _selected!) : _list(theme)),
        ],
      ),
    );
  }

  Widget _list(ThemeData theme) {
    if (_sales.isEmpty) {
      return EmptyState(
        icon: Icons.receipt_long_outlined,
        title: _loading ? 'Loading…' : 'No sales yet',
        message: 'OPD/OTC cash sales and IPD credit indents appear here.',
      );
    }
    return Card(
      child: ListView.separated(
        itemCount: _sales.length,
        separatorBuilder: (_, __) => const Divider(height: 1),
        itemBuilder: (context, i) {
          final s = _sales[i];
          return ListTile(
            leading: const Icon(Icons.medication_outlined),
            title: Row(
              children: [
                Text('${s['saleNumber']}', style: theme.textTheme.titleSmall),
                const SizedBox(width: Space.sm),
                StatusChip.auto('${s['saleType']}'),
                if (s['reversed'] == true) ...[
                  const SizedBox(width: Space.sm),
                  StatusChip.auto('REVERSED'),
                ],
              ],
            ),
            subtitle: Text(
              '${s['saleDate'] ?? ''} · ${s['paymentMode'] ?? ''}'
              '${s['referenceType'] != null ? ' · ${s['referenceType']} ${s['referenceId'] ?? ''}' : ''}',
              style: theme.textTheme.bodySmall,
            ),
            trailing: Text('₹${s['grandTotal']}', style: theme.textTheme.titleSmall),
            onTap: () => _openSale(s['id'] as int),
          );
        },
      ),
    );
  }

  Widget _detail(ThemeData theme, Map<String, dynamic> s) {
    final lines = List<Map<String, dynamic>>.from(s['lines'] as List? ?? const []);
    final reversed = s['reversed'] == true;
    final anyReturnable = lines.any((l) =>
        (num.tryParse('${l['quantity']}') ?? 0) -
            (num.tryParse('${l['returnedQuantity'] ?? 0}') ?? 0) >
        0);
    return SingleChildScrollView(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          TextButton.icon(
            onPressed: () => setState(() => _selected = null),
            icon: const Icon(Icons.arrow_back, size: 18),
            label: const Text('Back to sales'),
          ),
          SectionCard(
            title: '${s['saleNumber']}',
            icon: Icons.medication_outlined,
            subtitle: '${s['saleType']} · ${s['saleDate'] ?? ''} · ${s['paymentMode'] ?? ''}',
            action: reversed ? StatusChip.auto('REVERSED') : null,
            child: Column(
              children: [
                Row(
                  children: [
                    Expanded(flex: 4, child: _h(theme, 'Item')),
                    Expanded(flex: 2, child: _h(theme, 'Qty')),
                    Expanded(flex: 2, child: _h(theme, 'Returned')),
                    Expanded(flex: 2, child: _h(theme, 'MRP')),
                    Expanded(flex: 2, child: _h(theme, 'Total')),
                  ],
                ),
                const Divider(),
                for (final l in lines)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: Space.xs),
                    child: Row(
                      children: [
                        Expanded(flex: 4, child: Text('${l['itemName'] ?? l['itemCode']}')),
                        Expanded(flex: 2, child: Text('${l['quantity']}')),
                        Expanded(flex: 2, child: Text('${l['returnedQuantity'] ?? 0}')),
                        Expanded(flex: 2, child: Text('₹${l['mrp']}')),
                        Expanded(flex: 2, child: Text('₹${l['lineTotal']}')),
                      ],
                    ),
                  ),
                const Divider(),
                _totalRow(theme, 'Taxable', s['taxableTotal']),
                _totalRow(theme, 'CGST', s['cgstTotal']),
                _totalRow(theme, 'SGST', s['sgstTotal']),
                if ((num.tryParse('${s['igstTotal']}') ?? 0) > 0)
                  _totalRow(theme, 'IGST', s['igstTotal']),
                Row(
                  children: [
                    Text('Grand total', style: theme.textTheme.titleMedium),
                    const Spacer(),
                    Text('₹${s['grandTotal']}', style: theme.textTheme.titleLarge),
                  ],
                ),
                if (!reversed) ...[
                  const SizedBox(height: Space.md),
                  Wrap(
                    spacing: Space.sm,
                    alignment: WrapAlignment.end,
                    children: [
                      if (_canAct && anyReturnable)
                        OutlinedButton.icon(
                          onPressed: _loading ? null : () => _returnDialog(s),
                          icon: const Icon(Icons.assignment_return_outlined, size: 18),
                          label: const Text('Return items'),
                        ),
                      if (_canReverse)
                        OutlinedButton.icon(
                          onPressed: _loading ? null : () => _reverseDialog(s),
                          icon: const Icon(Icons.undo, size: 18),
                          label: const Text('Reverse sale'),
                        ),
                    ],
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _h(ThemeData theme, String s) => Text(s,
      style: theme.textTheme.labelMedium
          ?.copyWith(color: theme.colorScheme.onSurfaceVariant));

  Widget _totalRow(ThemeData theme, String label, Object? value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: Space.xxs),
      child: Row(
        children: [
          Text(label, style: theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
          const Spacer(),
          Text('₹$value', style: theme.textTheme.bodyMedium),
        ],
      ),
    );
  }
}
