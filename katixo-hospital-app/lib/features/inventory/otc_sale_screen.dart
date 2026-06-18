import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/util/validators.dart';
import '../../core/widgets/message_banner.dart';

/// OTC quick sale: walk-in counter cash sale (no patient). Build a cart of
/// items, pick payment mode, submit — backend FEFO-issues stock and posts the
/// GST/COGS journal. Body widget — host supplies the AppShell.
class OtcSaleScreen extends StatefulWidget {
  const OtcSaleScreen({super.key});

  @override
  State<OtcSaleScreen> createState() => _OtcSaleScreenState();
}

class _OtcSaleScreenState extends State<OtcSaleScreen> {
  List<Map<String, dynamic>> _items = const [];
  final List<_CartLine> _cart = [];
  String _paymentMode = 'CASH';
  bool _interState = false;

  Map<String, dynamic>? _lastSale;
  bool _loading = false;
  String? _error;
  String? _info;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _loadItems());
  }

  Future<void> _loadItems() async {
    try {
      final api = context.read<ApiClient>();
      final list = await api.get<List<Map<String, dynamic>>>(
        '/api/v1/inventory/items',
        fromJson: (json) =>
            List<Map<String, dynamic>>.from(json as List? ?? const []),
      );
      if (mounted) setState(() => _items = list);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    }
  }

  Future<void> _addLineDialog() async {
    if (_items.isEmpty) {
      setState(() => _error = 'No items in master — create items first');
      return;
    }
    String code = '${_items.first['code']}';
    final qty = TextEditingController(text: '1');
    final proceed = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: const Text('Add item'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              DropdownButtonFormField<String>(
                value: code,
                isExpanded: true,
                decoration: const InputDecoration(labelText: 'Item *'),
                items: [
                  for (final it in _items)
                    DropdownMenuItem(
                      value: '${it['code']}',
                      child: Text('${it['name']} (${it['code']})'),
                    ),
                ],
                onChanged: (v) => setDialogState(() => code = v ?? code),
              ),
              const SizedBox(height: Space.md),
              TextField(
                controller: qty,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(labelText: 'Quantity *'),
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Cancel'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('Add'),
            ),
          ],
        ),
      ),
    );
    if (proceed != true) return;
    final qtyError = positiveAmount(qty.text, field: 'Quantity');
    if (qtyError != null) {
      setState(() => _error = qtyError);
      return;
    }
    final quantity = double.parse(qty.text.trim());
    final item = _items.firstWhere((it) => '${it['code']}' == code);
    setState(() {
      _cart.add(_CartLine(
          code: code, name: '${item['name']}', quantity: quantity));
    });
  }

  Future<void> _submit() async {
    if (_cart.isEmpty) {
      setState(() => _error = 'Add at least one item');
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });
    try {
      final api = context.read<ApiClient>();
      final sale = await api.post<Map<String, dynamic>>(
        '/api/v1/pharmacy-sales/otc',
        {
          'paymentMode': _paymentMode,
          'interState': _interState,
          'lines': [
            for (final l in _cart)
              {'itemCode': l.code, 'quantity': l.quantity},
          ],
        },
        fromJson: (json) => json as Map<String, dynamic>,
      );
      setState(() {
        _info = 'Sale ${sale['saleNumber']} recorded';
        _lastSale = sale;
        _cart.clear();
      });
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _reverseLast() async {
    final id = _lastSale?['id'];
    if (id == null) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      await api.post<Map<String, dynamic>>(
        '/api/v1/pharmacy-sales/$id/reverse',
        {'reason': 'OTC return'},
        fromJson: (json) => json as Map<String, dynamic>,
      );
      setState(() {
        _info = 'Sale ${_lastSale?['saleNumber']} reversed';
        _lastSale = null;
      });
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
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('OTC Quick Sale', style: theme.textTheme.titleLarge),
          const SizedBox(height: Space.md),
          if (_error != null) ...[
            MessageBanner.error(_error!),
            const SizedBox(height: Space.md),
          ],
          if (_info != null) ...[
            MessageBanner.success(_info!),
            const SizedBox(height: Space.md),
          ],
          Card(
            child: Padding(
              padding: const EdgeInsets.all(Space.lg),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Text('Cart', style: theme.textTheme.titleMedium),
                      const Spacer(),
                      OutlinedButton.icon(
                        onPressed: _loading ? null : _addLineDialog,
                        icon: const Icon(Icons.add, size: 18),
                        label: const Text('Add item'),
                      ),
                    ],
                  ),
                  const SizedBox(height: Space.sm),
                  if (_cart.isEmpty)
                    Padding(
                      padding: const EdgeInsets.symmetric(vertical: Space.md),
                      child: Text('No items added',
                          style: theme.textTheme.bodySmall?.copyWith(
                              color: theme.colorScheme.onSurfaceVariant)),
                    )
                  else
                    for (var i = 0; i < _cart.length; i++)
                      Padding(
                        padding:
                            const EdgeInsets.symmetric(vertical: Space.xxs),
                        child: Row(
                          children: [
                            Expanded(
                              child: Text(
                                  '${_cart[i].name} (${_cart[i].code}) ×${_cart[i].quantity}',
                                  style: theme.textTheme.bodyMedium),
                            ),
                            IconButton(
                              tooltip: 'Remove',
                              onPressed: () =>
                                  setState(() => _cart.removeAt(i)),
                              icon: const Icon(Icons.close, size: 18),
                            ),
                          ],
                        ),
                      ),
                  const Divider(),
                  Row(
                    children: [
                      SizedBox(
                        width: 180,
                        child: DropdownButtonFormField<String>(
                          value: _paymentMode,
                          decoration:
                              const InputDecoration(labelText: 'Payment mode'),
                          items: const [
                            DropdownMenuItem(
                                value: 'CASH', child: Text('Cash')),
                            DropdownMenuItem(
                                value: 'CARD', child: Text('Card')),
                            DropdownMenuItem(value: 'UPI', child: Text('UPI')),
                            DropdownMenuItem(
                                value: 'CHEQUE', child: Text('Cheque')),
                            DropdownMenuItem(
                                value: 'BANK_TRANSFER',
                                child: Text('Bank transfer')),
                          ],
                          onChanged: (v) =>
                              setState(() => _paymentMode = v ?? 'CASH'),
                        ),
                      ),
                      const SizedBox(width: Space.lg),
                      Row(
                        children: [
                          Checkbox(
                            value: _interState,
                            onChanged: (v) =>
                                setState(() => _interState = v ?? false),
                          ),
                          const Text('Inter-state (IGST)'),
                        ],
                      ),
                      const Spacer(),
                      FilledButton.icon(
                        onPressed: _loading ? null : _submit,
                        icon: const Icon(Icons.point_of_sale, size: 18),
                        label: const Text('Complete sale'),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
          if (_lastSale != null) ...[
            const SizedBox(height: Space.lg),
            _saleCard(theme, _lastSale!),
          ],
        ],
      ),
    );
  }

  Widget _saleCard(ThemeData theme, Map<String, dynamic> sale) {
    final lines =
        List<Map<String, dynamic>>.from(sale['lines'] as List? ?? const []);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(Space.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Text('${sale['saleNumber']}',
                    style: theme.textTheme.titleMedium),
                const Spacer(),
                OutlinedButton.icon(
                  onPressed: _loading ? null : _reverseLast,
                  icon: const Icon(Icons.undo, size: 18),
                  label: const Text('Reverse'),
                ),
              ],
            ),
            const Divider(),
            for (final l in lines)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: Space.xxs),
                child: Row(
                  children: [
                    Expanded(
                        child: Text('${l['itemName']} ×${l['quantity']}',
                            style: theme.textTheme.bodyMedium)),
                    Text('₹${l['lineTotal']}',
                        style: theme.textTheme.bodyMedium),
                  ],
                ),
              ),
            const Divider(),
            _row(theme, 'Taxable', sale['taxableTotal']),
            if ((num.tryParse('${sale['cgstTotal']}') ?? 0) > 0) ...[
              _row(theme, 'CGST', sale['cgstTotal']),
              _row(theme, 'SGST', sale['sgstTotal']),
            ],
            if ((num.tryParse('${sale['igstTotal']}') ?? 0) > 0)
              _row(theme, 'IGST', sale['igstTotal']),
            const SizedBox(height: Space.xs),
            Row(
              children: [
                Text('Grand Total', style: theme.textTheme.titleMedium),
                const Spacer(),
                Text('₹${sale['grandTotal']}',
                    style: theme.textTheme.titleLarge),
              ],
            ),
            if (sale['journalEntryId'] != null)
              Text('Journal #${sale['journalEntryId']}',
                  style: theme.textTheme.bodySmall
                      ?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
          ],
        ),
      ),
    );
  }

  Widget _row(ThemeData theme, String label, Object? value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: Space.xxs),
      child: Row(
        children: [
          Text(label,
              style: theme.textTheme.bodyMedium
                  ?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
          const Spacer(),
          Text('₹$value', style: theme.textTheme.bodyMedium),
        ],
      ),
    );
  }
}

class _CartLine {
  _CartLine({required this.code, required this.name, required this.quantity});

  final String code;
  final String name;
  final double quantity;
}
