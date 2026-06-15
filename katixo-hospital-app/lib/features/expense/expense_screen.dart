import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/status_chip.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// Operating-expense screen: record an expense (DR category / CR Cash|Bank|
/// Trade Payables in-process), list expenses, settle a credit expense (AP
/// loop), reverse, and view the voucher details. Body widget — the host home
/// supplies the AppShell.
class ExpenseScreen extends StatefulWidget {
  const ExpenseScreen({super.key});

  @override
  State<ExpenseScreen> createState() => _ExpenseScreenState();
}

class _ExpenseScreenState extends State<ExpenseScreen> {
  static const _categories = [
    'RENT',
    'UTILITIES',
    'SUPPLIES',
    'MAINTENANCE',
    'MISCELLANEOUS',
  ];

  final _payeeCtrl = TextEditingController();
  final _amountCtrl = TextEditingController();
  final _referenceCtrl = TextEditingController();
  final _notesCtrl = TextEditingController();
  String _category = 'SUPPLIES';
  String _paymentMode = 'CASH';

  List<Map<String, dynamic>> _expenses = const [];
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
    _payeeCtrl.dispose();
    _amountCtrl.dispose();
    _referenceCtrl.dispose();
    _notesCtrl.dispose();
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
        '/api/v1/expenses',
        fromJson: (json) =>
            List<Map<String, dynamic>>.from(json as List? ?? const []),
      );
      if (mounted) setState(() => _expenses = list);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _record() async {
    final amount = double.tryParse(_amountCtrl.text.trim());
    if (amount == null || amount <= 0) {
      setState(() => _error = 'Enter a valid amount');
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });
    try {
      final api = context.read<ApiClient>();
      final e = await api.post<Map<String, dynamic>>(
        '/api/v1/expenses',
        {
          'category': _category,
          'amount': amount,
          'paymentMode': _paymentMode,
          if (_payeeCtrl.text.trim().isNotEmpty)
            'payeeName': _payeeCtrl.text.trim(),
          if (_referenceCtrl.text.trim().isNotEmpty)
            'reference': _referenceCtrl.text.trim(),
          if (_notesCtrl.text.trim().isNotEmpty) 'notes': _notesCtrl.text.trim(),
        },
        fromJson: (json) => json as Map<String, dynamic>,
      );
      _payeeCtrl.clear();
      _amountCtrl.clear();
      _referenceCtrl.clear();
      _notesCtrl.clear();
      setState(() => _info = 'Expense ${e['expenseNumber']} recorded');
      await _load();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _payDialog(Map<String, dynamic> expense) async {
    final referenceCtrl = TextEditingController();
    var mode = 'BANK';
    final proceed = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: Text('Pay ${expense['expenseNumber']}'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('Amount: ₹${expense['amount']}'),
              const SizedBox(height: Space.md),
              DropdownButtonFormField<String>(
                value: mode,
                decoration: const InputDecoration(labelText: 'Pay via *'),
                items: const [
                  DropdownMenuItem(value: 'BANK', child: Text('Bank')),
                  DropdownMenuItem(value: 'CASH', child: Text('Cash')),
                ],
                onChanged: (v) => setDialogState(() => mode = v ?? 'BANK'),
              ),
              const SizedBox(height: Space.md),
              TextField(
                controller: referenceCtrl,
                decoration: const InputDecoration(
                    labelText: 'Reference (NEFT/cheque no.)'),
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
              child: const Text('Pay'),
            ),
          ],
        ),
      ),
    );
    if (proceed != true) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      await api.post<Map<String, dynamic>>(
        '/api/v1/expenses/${expense['id']}/pay',
        {
          'mode': mode,
          if (referenceCtrl.text.trim().isNotEmpty)
            'reference': referenceCtrl.text.trim(),
        },
        fromJson: (json) => json as Map<String, dynamic>,
      );
      setState(() => _info = '${expense['expenseNumber']} paid');
      await _load();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _reverseDialog(Map<String, dynamic> expense) async {
    final reasonCtrl = TextEditingController();
    final proceed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Reverse ${expense['expenseNumber']}'),
        content: TextField(
          controller: reasonCtrl,
          decoration: const InputDecoration(labelText: 'Reason'),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Reverse'),
          ),
        ],
      ),
    );
    if (proceed != true) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      await api.post<Map<String, dynamic>>(
        '/api/v1/expenses/${expense['id']}/reverse',
        {'reason': reasonCtrl.text.trim()},
        fromJson: (json) => json as Map<String, dynamic>,
      );
      setState(() => _info = '${expense['expenseNumber']} reversed');
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
          Text('Expenses', style: theme.textTheme.titleLarge),
          const SizedBox(height: Space.md),
          if (_error != null) ...[
            MessageBanner.error(_error!),
            const SizedBox(height: Space.md),
          ],
          if (_info != null) ...[
            MessageBanner.success(_info!),
            const SizedBox(height: Space.md),
          ],
          _recordCard(theme),
          const SizedBox(height: Space.lg),
          Row(
            children: [
              Text('Recent expenses', style: theme.textTheme.titleMedium),
              const Spacer(),
              IconButton(
                tooltip: 'Refresh',
                onPressed: _loading ? null : _load,
                icon: const Icon(Icons.refresh, size: 20),
              ),
            ],
          ),
          const SizedBox(height: Space.sm),
          Expanded(child: _list(theme)),
        ],
      ),
    );
  }

  Widget _recordCard(ThemeData theme) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(Space.lg),
        child: Column(
          children: [
            Row(
              children: [
                SizedBox(
                  width: 180,
                  child: DropdownButtonFormField<String>(
                    value: _category,
                    decoration: const InputDecoration(labelText: 'Category'),
                    items: [
                      for (final c in _categories)
                        DropdownMenuItem(value: c, child: Text(_titleCase(c))),
                    ],
                    onChanged: (v) =>
                        setState(() => _category = v ?? 'SUPPLIES'),
                  ),
                ),
                const SizedBox(width: Space.md),
                Expanded(
                  child: TextField(
                    controller: _payeeCtrl,
                    decoration: const InputDecoration(labelText: 'Paid to'),
                  ),
                ),
                const SizedBox(width: Space.md),
                SizedBox(
                  width: 160,
                  child: TextField(
                    controller: _amountCtrl,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(
                        labelText: 'Amount *', prefixText: '₹ '),
                  ),
                ),
              ],
            ),
            const SizedBox(height: Space.md),
            Row(
              children: [
                SizedBox(
                  width: 180,
                  child: DropdownButtonFormField<String>(
                    value: _paymentMode,
                    decoration: const InputDecoration(labelText: 'Mode'),
                    items: const [
                      DropdownMenuItem(value: 'CASH', child: Text('Cash')),
                      DropdownMenuItem(value: 'BANK', child: Text('Bank')),
                      DropdownMenuItem(
                          value: 'CREDIT', child: Text('Credit (payable)')),
                    ],
                    onChanged: (v) =>
                        setState(() => _paymentMode = v ?? 'CASH'),
                  ),
                ),
                const SizedBox(width: Space.md),
                Expanded(
                  child: TextField(
                    controller: _referenceCtrl,
                    decoration: const InputDecoration(labelText: 'Reference'),
                  ),
                ),
                const SizedBox(width: Space.md),
                Expanded(
                  child: TextField(
                    controller: _notesCtrl,
                    decoration: const InputDecoration(labelText: 'Notes'),
                  ),
                ),
                const SizedBox(width: Space.md),
                FilledButton.icon(
                  onPressed: _loading ? null : _record,
                  icon: const Icon(Icons.add, size: 18),
                  label: const Text('Record'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _list(ThemeData theme) {
    if (_expenses.isEmpty) {
      return Center(
        child: Text(
          _loading ? 'Loading…' : 'No expenses recorded yet',
          style: theme.textTheme.bodyMedium
              ?.copyWith(color: theme.colorScheme.onSurfaceVariant),
        ),
      );
    }
    return ListView.separated(
      itemCount: _expenses.length,
      separatorBuilder: (_, __) => const Divider(height: 1),
      itemBuilder: (context, i) {
        final e = _expenses[i];
        final reversed = e['reversed'] == true;
        final paid = e['paid'] == true;
        final mode = '${e['paymentMode']}';
        final canPay = mode == 'CREDIT' && !paid && !reversed;
        return ListTile(
          title: Row(
            children: [
              Text('${e['expenseNumber']}',
                  style: theme.textTheme.titleSmall),
              const SizedBox(width: Space.sm),
              StatusChip.auto(reversed
                  ? 'REVERSED'
                  : (paid ? 'PAID' : 'UNPAID')),
            ],
          ),
          subtitle: Text(
            '${_titleCase('${e['category']}')} · ${e['payeeName'] ?? '—'} · '
            '$mode · ${e['expenseDate']}',
            style: theme.textTheme.bodySmall,
          ),
          trailing: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('₹${e['amount']}', style: theme.textTheme.titleSmall),
              const SizedBox(width: Space.sm),
              if (canPay)
                OutlinedButton(
                  onPressed: _loading ? null : () => _payDialog(e),
                  child: const Text('Pay'),
                ),
              IconButton(
                tooltip: 'Voucher',
                onPressed: () => _voucherDialog(e),
                icon: const Icon(Icons.description_outlined, size: 20),
              ),
              if (!reversed)
                IconButton(
                  tooltip: 'Reverse',
                  onPressed: _loading ? null : () => _reverseDialog(e),
                  icon: const Icon(Icons.undo, size: 20),
                ),
            ],
          ),
        );
      },
    );
  }

  Future<void> _voucherDialog(Map<String, dynamic> e) async {
    final theme = Theme.of(context);
    await showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Voucher ${e['expenseNumber']}'),
        content: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _kv(theme, 'Date', '${e['expenseDate']}'),
              _kv(theme, 'Category', _titleCase('${e['category']}')),
              _kv(theme, 'Paid to', '${e['payeeName'] ?? '—'}'),
              _kv(theme, 'Amount', '₹${e['amount']}'),
              _kv(theme, 'Mode', '${e['paymentMode']}'),
              _kv(theme, 'Journal', '${e['journalNumber'] ?? '—'}'),
              if (e['paid'] == true)
                _kv(theme, 'Paid via',
                    '${e['paidMode'] ?? ''} ${e['paidJournalNumber'] ?? ''}'),
              const SizedBox(height: Space.sm),
              Text(
                'PDF voucher: GET /api/v1/expenses/${e['id']}/voucher.pdf',
                style: theme.textTheme.bodySmall?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Close'),
          ),
        ],
      ),
    );
  }

  Widget _kv(ThemeData theme, String k, String v) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: Space.xxs),
      child: Row(
        children: [
          SizedBox(
            width: 120,
            child: Text(k,
                style: theme.textTheme.bodySmall
                    ?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
          ),
          Expanded(child: Text(v, style: theme.textTheme.bodyMedium)),
        ],
      ),
    );
  }

  String _titleCase(String s) =>
      s.isEmpty ? s : s[0] + s.substring(1).toLowerCase();
}
