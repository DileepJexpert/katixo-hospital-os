import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// Pharmacy item master: create items, search, receive stock (batch/expiry/
/// cost), and check on-hand quantity. Body widget — host supplies the AppShell.
class ItemMasterScreen extends StatefulWidget {
  const ItemMasterScreen({super.key});

  @override
  State<ItemMasterScreen> createState() => _ItemMasterScreenState();
}

class _ItemMasterScreenState extends State<ItemMasterScreen> {
  final _searchCtrl = TextEditingController();

  List<Map<String, dynamic>> _items = const [];
  bool _loading = false;
  String? _error;
  String? _info;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _load());
  }

  @override
  void dispose() {
    _searchCtrl.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      final q = _searchCtrl.text.trim();
      final list = await api.get<List<Map<String, dynamic>>>(
        '/api/v1/inventory/items${q.isEmpty ? '' : '?search=$q'}',
        fromJson: (json) =>
            List<Map<String, dynamic>>.from(json as List? ?? const []),
      );
      if (mounted) setState(() => _items = list);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _createDialog() async {
    final code = TextEditingController();
    final name = TextEditingController();
    final hsn = TextEditingController();
    final gst = TextEditingController();
    final mrp = TextEditingController();
    final manufacturer = TextEditingController();

    final proceed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('New item'),
        content: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                _field(code, 'Code *'),
                _field(name, 'Name *'),
                _field(hsn, 'HSN code'),
                _field(gst, 'GST rate (%)', number: true),
                _field(mrp, 'MRP', number: true),
                _field(manufacturer, 'Manufacturer'),
              ],
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Save'),
          ),
        ],
      ),
    );
    if (proceed != true) return;
    if (code.text.trim().isEmpty || name.text.trim().isEmpty) {
      setState(() => _error = 'Code and name are required');
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });
    try {
      if (!mounted) return;
      final api = context.read<ApiClient>();
      final item = await api.post<Map<String, dynamic>>(
        '/api/v1/inventory/items',
        {
          'code': code.text.trim(),
          'name': name.text.trim(),
          if (hsn.text.trim().isNotEmpty) 'hsnCode': hsn.text.trim(),
          if (gst.text.trim().isNotEmpty)
            'gstRate': double.tryParse(gst.text.trim()),
          if (mrp.text.trim().isNotEmpty) 'mrp': double.tryParse(mrp.text.trim()),
          if (manufacturer.text.trim().isNotEmpty)
            'manufacturer': manufacturer.text.trim(),
        },
        fromJson: (json) => json as Map<String, dynamic>,
      );
      setState(() => _info = 'Item ${item['code']} created');
      await _load();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _receiveDialog(Map<String, dynamic> item) async {
    final batch = TextEditingController();
    final expiry = TextEditingController(); // YYYY-MM-DD
    final qty = TextEditingController();
    final cost = TextEditingController();
    final mrp = TextEditingController(text: '${item['mrp'] ?? ''}');

    final proceed = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setLocal) => AlertDialog(
          title: Text('Receive stock — ${item['name']}'),
          content: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
            child: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  _field(batch, 'Batch number *'),
                  const SizedBox(height: Space.sm),
                  OutlinedButton.icon(
                    onPressed: () async {
                      final d = await showDatePicker(
                        context: context,
                        initialDate: DateTime.now().add(const Duration(days: 365)),
                        firstDate: DateTime(2000),
                        lastDate: DateTime(2100),
                      );
                      if (d != null) {
                        setLocal(() => expiry.text =
                            '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}');
                      }
                    },
                    icon: const Icon(Icons.event_outlined, size: 18),
                    label: Text(expiry.text.isEmpty ? 'Expiry date' : 'Expiry ${expiry.text}'),
                  ),
                  _field(qty, 'Quantity *', number: true),
                  _field(cost, 'Cost price', number: true),
                  _field(mrp, 'MRP', number: true),
                ],
              ),
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Cancel'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('Receive'),
            ),
          ],
        ),
      ),
    );
    if (proceed != true) return;
    if (batch.text.trim().isEmpty ||
        (double.tryParse(qty.text.trim()) ?? 0) <= 0) {
      setState(() => _error = 'Batch number and a positive quantity are required');
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });
    try {
      if (!mounted) return;
      final api = context.read<ApiClient>();
      await api.post<Map<String, dynamic>>(
        '/api/v1/inventory/items/${item['id']}/receive',
        {
          'batchNumber': batch.text.trim(),
          if (expiry.text.trim().isNotEmpty) 'expiryDate': expiry.text.trim(),
          'quantity': double.tryParse(qty.text.trim()),
          if (cost.text.trim().isNotEmpty)
            'costPrice': double.tryParse(cost.text.trim()),
          if (mrp.text.trim().isNotEmpty) 'mrp': double.tryParse(mrp.text.trim()),
        },
        fromJson: (json) => json as Map<String, dynamic>,
      );
      setState(() => _info = 'Stock received for ${item['code']}');
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _showStock(Map<String, dynamic> item) async {
    try {
      final api = context.read<ApiClient>();
      final stock = await api.get<Map<String, dynamic>>(
        '/api/v1/inventory/items/${item['id']}/stock',
        fromJson: (json) => json as Map<String, dynamic>,
      );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text('${item['name']}: ${stock['available']} on hand'),
      ));
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
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
          Text('Item Master', style: theme.textTheme.titleLarge),
          const SizedBox(height: Space.md),
          if (_error != null) ...[
            MessageBanner.error(_error!),
            const SizedBox(height: Space.md),
          ],
          if (_info != null) ...[
            MessageBanner.success(_info!),
            const SizedBox(height: Space.md),
          ],
          Row(
            children: [
              Expanded(
                child: TextField(
                  controller: _searchCtrl,
                  decoration: const InputDecoration(
                    labelText: 'Search items',
                    prefixIcon: Icon(Icons.search, size: 18),
                  ),
                  onSubmitted: (_) => _load(),
                ),
              ),
              const SizedBox(width: Space.md),
              FilledButton.icon(
                onPressed: _loading ? null : _createDialog,
                icon: const Icon(Icons.add, size: 18),
                label: const Text('New item'),
              ),
            ],
          ),
          const SizedBox(height: Space.md),
          Expanded(
            child: _items.isEmpty
                ? Center(
                    child: Text(_loading ? 'Loading…' : 'No items',
                        style: theme.textTheme.bodyMedium?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant)))
                : ListView.separated(
                    itemCount: _items.length,
                    separatorBuilder: (_, __) => const Divider(height: 1),
                    itemBuilder: (context, i) {
                      final it = _items[i];
                      return ListTile(
                        title: Text('${it['name']}',
                            style: theme.textTheme.titleSmall),
                        subtitle: Text(
                          '${it['code']} · MRP ₹${it['mrp'] ?? '—'} · '
                          'GST ${it['gstRate'] ?? 0}% · ${it['manufacturer'] ?? '—'}',
                          style: theme.textTheme.bodySmall,
                        ),
                        trailing: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            IconButton(
                              tooltip: 'Stock on hand',
                              onPressed: () => _showStock(it),
                              icon: const Icon(Icons.inventory_2_outlined,
                                  size: 20),
                            ),
                            OutlinedButton(
                              onPressed: _loading ? null : () => _receiveDialog(it),
                              child: const Text('Receive'),
                            ),
                          ],
                        ),
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }

  Widget _field(TextEditingController c, String label, {bool number = false}) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: Space.xs),
      child: TextField(
        controller: c,
        keyboardType: number ? TextInputType.number : TextInputType.text,
        decoration: InputDecoration(labelText: label),
      ),
    );
  }
}
