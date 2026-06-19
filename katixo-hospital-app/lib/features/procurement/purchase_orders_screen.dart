import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/auth/auth_state.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/empty_state.dart';
import '../../core/widgets/section_card.dart';
import '../../core/widgets/status_chip.dart';
import '../../core/widgets/truncation_notice.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// Purchase orders + goods receipt. Raise a PO to a vendor, then receive goods
/// against it (which feeds pharmacy stock + AP via the inventory receive path).
/// Body widget. PHARMACIST/ADMIN raise + receive; BILLING can view.
class PurchaseOrdersScreen extends StatefulWidget {
  const PurchaseOrdersScreen({super.key});

  @override
  State<PurchaseOrdersScreen> createState() => _PurchaseOrdersScreenState();
}

class _PurchaseOrdersScreenState extends State<PurchaseOrdersScreen> {
  static const _limit = 50;

  List<Map<String, dynamic>> _orders = const [];
  Map<String, dynamic>? _selected; // PO detail with lines
  bool _loading = false;
  String? _error;
  String? _info;

  bool get _canEdit {
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
        '/api/v1/purchase-orders?limit=$_limit',
        fromJson: (json) =>
            List<Map<String, dynamic>>.from(json as List? ?? const []),
      );
      if (mounted) setState(() => _orders = list);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (_) {
      setState(() => _error = 'Could not load purchase orders — check your connection.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _openPo(int id) async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      final po = await api.get<Map<String, dynamic>>(
        '/api/v1/purchase-orders/$id',
        fromJson: (json) => json as Map<String, dynamic>,
      );
      if (mounted) setState(() => _selected = po);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<List<Map<String, dynamic>>> _fetch(String path) async {
    final api = context.read<ApiClient>();
    return api.get<List<Map<String, dynamic>>>(
      path,
      fromJson: (json) => List<Map<String, dynamic>>.from(json as List? ?? const []),
    );
  }

  // ---------------- create ----------------

  Future<void> _createDialog() async {
    List<Map<String, dynamic>> vendors;
    try {
      vendors = await _fetch('/api/v1/vendors');
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
      return;
    }
    if (vendors.isEmpty) {
      setState(() => _error = 'Add a vendor first (Vendors screen)');
      return;
    }
    int? vendorId = vendors.first['id'] as int?;
    DateTime? expected;
    final notesCtrl = TextEditingController();
    final lines = <Map<String, dynamic>>[]; // {itemId,itemName,quantity,unitCost}
    String iso(DateTime d) => d.toIso8601String().split('T').first;

    if (!mounted) return;
    final created = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setLocal) => AlertDialog(
          title: const Text('New purchase order'),
          content: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
            child: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  DropdownButtonFormField<int?>(
                    initialValue: vendorId,
                    isExpanded: true,
                    decoration: const InputDecoration(labelText: 'Vendor'),
                    items: [
                      for (final v in vendors)
                        DropdownMenuItem<int?>(
                          value: v['id'] as int?,
                          child: Text('${v['name']} (${v['vendorCode']})'),
                        ),
                    ],
                    onChanged: (v) => setLocal(() => vendorId = v),
                  ),
                  const SizedBox(height: Space.sm),
                  OutlinedButton.icon(
                    onPressed: () async {
                      final p = await showDatePicker(
                        context: context,
                        initialDate: expected ?? DateTime.now(),
                        firstDate: DateTime(2020),
                        lastDate: DateTime(2100));
                      if (p != null) setLocal(() => expected = p);
                    },
                    icon: const Icon(Icons.calendar_today, size: 16),
                    label: Text(expected == null ? 'Expected date (optional)' : 'Expected ${iso(expected!)}'),
                  ),
                  const Divider(height: Space.xl),
                  Text('Lines', style: Theme.of(context).textTheme.titleSmall),
                  for (var i = 0; i < lines.length; i++)
                    ListTile(
                      contentPadding: EdgeInsets.zero,
                      dense: true,
                      title: Text('${lines[i]['itemName']}'),
                      subtitle: Text('${lines[i]['quantity']} × ₹${lines[i]['unitCost']}'),
                      trailing: IconButton(
                        icon: const Icon(Icons.delete_outline, size: 20),
                        onPressed: () => setLocal(() => lines.removeAt(i)),
                      ),
                    ),
                  TextButton.icon(
                    onPressed: () async {
                      final line = await _addLineDialog();
                      if (line != null) setLocal(() => lines.add(line));
                    },
                    icon: const Icon(Icons.add, size: 18),
                    label: const Text('Add line'),
                  ),
                  const SizedBox(height: Space.sm),
                  TextField(
                    controller: notesCtrl,
                    decoration: const InputDecoration(labelText: 'Notes'),
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
    if (vendorId == null || lines.isEmpty) {
      setState(() => _error = 'Pick a vendor and add at least one line');
      return;
    }
    await _act(() async {
      final api = context.read<ApiClient>();
      await api.post<dynamic>('/api/v1/purchase-orders', {
        'vendorId': vendorId,
        if (expected != null) 'expectedDate': iso(expected!),
        'notes': notesCtrl.text.trim(),
        'lines': [
          for (final l in lines)
            {'itemId': l['itemId'], 'quantity': l['quantity'], 'unitCost': l['unitCost']}
        ],
      }, fromJson: (j) => j);
    }, 'Purchase order created');
  }

  Future<Map<String, dynamic>?> _addLineDialog() async {
    List<Map<String, dynamic>> items;
    try {
      items = await _fetch('/api/v1/inventory/items');
    } catch (_) {
      items = const [];
    }
    if (items.isEmpty) {
      setState(() => _error = 'No items in the catalogue to order');
      return null;
    }
    int? itemId = items.first['id'] as int?;
    String itemName = '${items.first['name'] ?? ''}';
    final qtyCtrl = TextEditingController();
    final costCtrl = TextEditingController();
    if (!mounted) return null;
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setLocal) => AlertDialog(
          title: const Text('Add line'),
          content: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                DropdownButtonFormField<int?>(
                  initialValue: itemId,
                  isExpanded: true,
                  decoration: const InputDecoration(labelText: 'Item'),
                  items: [
                    for (final it in items)
                      DropdownMenuItem<int?>(
                        value: it['id'] as int?,
                        child: Text('${it['name']} (${it['code']})'),
                      ),
                  ],
                  onChanged: (v) => setLocal(() {
                    itemId = v;
                    itemName = '${items.firstWhere((e) => e['id'] == v, orElse: () => const {})['name'] ?? ''}';
                  }),
                ),
                const SizedBox(height: Space.sm),
                TextField(
                  controller: qtyCtrl,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: 'Quantity *'),
                ),
                TextField(
                  controller: costCtrl,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: 'Unit cost (₹)'),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
            FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Add')),
          ],
        ),
      ),
    );
    final qty = double.tryParse(qtyCtrl.text.trim());
    if (ok != true || itemId == null || qty == null || qty <= 0) return null;
    return {
      'itemId': itemId,
      'itemName': itemName,
      'quantity': qty,
      'unitCost': double.tryParse(costCtrl.text.trim()) ?? 0,
    };
  }

  // ---------------- receive ----------------

  Future<void> _receiveDialog(Map<String, dynamic> po) async {
    final lines = List<Map<String, dynamic>>.from(po['lines'] as List? ?? const []);
    final rows = <int, Map<String, TextEditingController>>{};
    final remaining = <int, num>{};
    final expiry = <int, DateTime?>{};
    for (final l in lines) {
      final id = l['id'] as int;
      final rem = (num.tryParse('${l['orderedQuantity']}') ?? 0) -
          (num.tryParse('${l['receivedQuantity'] ?? 0}') ?? 0);
      remaining[id] = rem;
      rows[id] = {
        'qty': TextEditingController(),
        'batch': TextEditingController(),
        'cost': TextEditingController(text: '${l['unitCost'] ?? ''}'),
        'mrp': TextEditingController(),
      };
    }
    String iso(DateTime d) => d.toIso8601String().split('T').first;

    final proceed = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setLocal) => AlertDialog(
          title: Text('Receive — ${po['poNumber']}'),
          content: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
            child: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  for (final l in lines)
                    if ((remaining[l['id']] ?? 0) > 0)
                      Card(
                        margin: const EdgeInsets.symmetric(vertical: Space.xxs),
                        child: Padding(
                          padding: const EdgeInsets.all(Space.sm),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text('${l['itemName']} — remaining ${remaining[l['id']]}',
                                  style: Theme.of(context).textTheme.titleSmall),
                              Row(
                                children: [
                                  Expanded(
                                    child: TextField(
                                      controller: rows[l['id']]!['qty'],
                                      keyboardType: TextInputType.number,
                                      decoration: const InputDecoration(labelText: 'Receive qty'),
                                    ),
                                  ),
                                  const SizedBox(width: Space.sm),
                                  Expanded(
                                    child: TextField(
                                      controller: rows[l['id']]!['batch'],
                                      decoration: const InputDecoration(labelText: 'Batch # *'),
                                    ),
                                  ),
                                ],
                              ),
                              Row(
                                children: [
                                  Expanded(
                                    child: TextField(
                                      controller: rows[l['id']]!['cost'],
                                      keyboardType: TextInputType.number,
                                      decoration: const InputDecoration(labelText: 'Cost'),
                                    ),
                                  ),
                                  const SizedBox(width: Space.sm),
                                  Expanded(
                                    child: TextField(
                                      controller: rows[l['id']]!['mrp'],
                                      keyboardType: TextInputType.number,
                                      decoration: const InputDecoration(labelText: 'MRP'),
                                    ),
                                  ),
                                  const SizedBox(width: Space.sm),
                                  TextButton(
                                    onPressed: () async {
                                      final p = await showDatePicker(
                                        context: context,
                                        initialDate: DateTime.now(),
                                        firstDate: DateTime(2020),
                                        lastDate: DateTime(2100));
                                      if (p != null) setLocal(() => expiry[l['id'] as int] = p);
                                    },
                                    child: Text(expiry[l['id']] == null
                                        ? 'Expiry'
                                        : iso(expiry[l['id']]!)),
                                  ),
                                ],
                              ),
                            ],
                          ),
                        ),
                      ),
                ],
              ),
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
            FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Receive')),
          ],
        ),
      ),
    );
    if (proceed != true) return;

    final receiveLines = <Map<String, dynamic>>[];
    for (final l in lines) {
      final id = l['id'] as int;
      final r = rows[id]!;
      final qty = double.tryParse(r['qty']!.text.trim());
      if (qty == null || qty <= 0) continue;
      if (qty > (remaining[id] ?? 0)) {
        setState(() => _error = '${l['itemName']}: cannot receive more than ${remaining[id]}');
        return;
      }
      if (r['batch']!.text.trim().isEmpty) {
        setState(() => _error = '${l['itemName']}: a batch number is required to receive');
        return;
      }
      receiveLines.add({
        'lineId': id,
        'quantity': qty,
        'batchNumber': r['batch']!.text.trim(),
        if (expiry[id] != null) 'expiryDate': iso(expiry[id]!),
        if (r['cost']!.text.trim().isNotEmpty) 'costPrice': double.tryParse(r['cost']!.text.trim()),
        if (r['mrp']!.text.trim().isNotEmpty) 'mrp': double.tryParse(r['mrp']!.text.trim()),
      });
    }
    if (receiveLines.isEmpty) {
      setState(() => _error = 'Enter at least one quantity to receive');
      return;
    }
    await _act(() async {
      final api = context.read<ApiClient>();
      await api.post<dynamic>('/api/v1/purchase-orders/${po['id']}/receive',
          {'lines': receiveLines}, fromJson: (j) => j);
    }, 'Goods received', poId: po['id'] as int);
  }

  Future<void> _cancel(Map<String, dynamic> po) async {
    await _act(() async {
      final api = context.read<ApiClient>();
      await api.post<dynamic>('/api/v1/purchase-orders/${po['id']}/cancel',
          const <String, dynamic>{}, fromJson: (j) => j);
    }, 'Purchase order cancelled', poId: po['id'] as int);
  }

  Future<void> _act(Future<void> Function() action, String okMsg, {int? poId}) async {
    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });
    try {
      await action();
      setState(() => _info = okMsg);
      if (poId != null) await _openPo(poId);
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
              Text('Purchase Orders', style: theme.textTheme.titleLarge),
              const Spacer(),
              IconButton(
                tooltip: 'Refresh',
                onPressed: _loading ? null : _loadList,
                icon: const Icon(Icons.refresh),
              ),
              if (_canEdit && _selected == null) ...[
                const SizedBox(width: Space.sm),
                FilledButton.icon(
                  onPressed: _loading ? null : _createDialog,
                  icon: const Icon(Icons.add, size: 18),
                  label: const Text('New PO'),
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
          Expanded(child: _selected != null ? _detail(theme, _selected!) : _list(theme)),
        ],
      ),
    );
  }

  Widget _list(ThemeData theme) {
    if (_orders.isEmpty) {
      return EmptyState(
        icon: Icons.shopping_cart_outlined,
        title: _loading ? 'Loading…' : 'No purchase orders',
        message: _canEdit ? 'Raise a PO to a vendor with "New PO".' : 'No purchase orders yet.',
      );
    }
    return Column(
      children: [
        Expanded(
          child: Card(
            child: ListView.separated(
        itemCount: _orders.length,
        separatorBuilder: (_, __) => const Divider(height: 1),
        itemBuilder: (context, i) {
          final po = _orders[i];
          return ListTile(
            leading: const Icon(Icons.shopping_cart_outlined),
            title: Row(
              children: [
                Text('${po['poNumber']}', style: theme.textTheme.titleSmall),
                const SizedBox(width: Space.sm),
                StatusChip.auto('${po['poStatus']}'),
              ],
            ),
            subtitle: Text(
              '${po['vendorName']} · ${po['orderDate'] ?? ''}'
              '${po['expectedDate'] != null ? ' · exp ${po['expectedDate']}' : ''}',
              style: theme.textTheme.bodySmall,
            ),
            trailing: Text('₹${po['totalAmount']}', style: theme.textTheme.titleSmall),
            onTap: () => _openPo(po['id'] as int),
          );
        },
            ),
          ),
        ),
        if (_orders.length >= _limit) const TruncationNotice(limit: _limit),
      ],
    );
  }

  Widget _detail(ThemeData theme, Map<String, dynamic> po) {
    final lines = List<Map<String, dynamic>>.from(po['lines'] as List? ?? const []);
    final status = '${po['poStatus']}';
    final canReceive = status == 'ORDERED' || status == 'PARTIALLY_RECEIVED';
    final canCancel = status == 'ORDERED';
    return SingleChildScrollView(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          TextButton.icon(
            onPressed: () => setState(() => _selected = null),
            icon: const Icon(Icons.arrow_back, size: 18),
            label: const Text('Back to list'),
          ),
          SectionCard(
            title: '${po['poNumber']}',
            icon: Icons.shopping_cart_outlined,
            subtitle: '${po['vendorName']} · ${po['orderDate'] ?? ''}',
            action: StatusChip.auto(status),
            child: Column(
              children: [
                Row(
                  children: [
                    Expanded(flex: 4, child: _h(theme, 'Item')),
                    Expanded(flex: 2, child: _h(theme, 'Ordered')),
                    Expanded(flex: 2, child: _h(theme, 'Received')),
                    Expanded(flex: 2, child: _h(theme, 'Cost')),
                  ],
                ),
                const Divider(),
                for (final l in lines)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: Space.xs),
                    child: Row(
                      children: [
                        Expanded(flex: 4, child: Text('${l['itemName']}')),
                        Expanded(flex: 2, child: Text('${l['orderedQuantity']}')),
                        Expanded(flex: 2, child: Text('${l['receivedQuantity'] ?? 0}')),
                        Expanded(flex: 2, child: Text('₹${l['unitCost']}')),
                      ],
                    ),
                  ),
                const Divider(),
                Row(
                  children: [
                    Text('Total', style: theme.textTheme.titleMedium),
                    const Spacer(),
                    Text('₹${po['totalAmount']}', style: theme.textTheme.titleLarge),
                  ],
                ),
                if (_canEdit && (canReceive || canCancel)) ...[
                  const SizedBox(height: Space.md),
                  Wrap(
                    spacing: Space.sm,
                    alignment: WrapAlignment.end,
                    children: [
                      if (canReceive)
                        FilledButton.icon(
                          onPressed: _loading ? null : () => _receiveDialog(po),
                          icon: const Icon(Icons.inventory_outlined, size: 18),
                          label: const Text('Receive goods'),
                        ),
                      if (canCancel)
                        OutlinedButton.icon(
                          onPressed: _loading ? null : () => _cancel(po),
                          icon: const Icon(Icons.block, size: 18),
                          label: const Text('Cancel PO'),
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
}
