import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/util/formatters.dart';
import '../../core/widgets/section_card.dart';
import '../../core/widgets/status_chip.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// Statutory financial statements off the hospital ledger: trial balance,
/// profit & loss and balance sheet. ADMIN/BILLING. Body widget.
class FinancialReportsScreen extends StatefulWidget {
  const FinancialReportsScreen({super.key});

  @override
  State<FinancialReportsScreen> createState() => _FinancialReportsScreenState();
}

class _FinancialReportsScreenState extends State<FinancialReportsScreen> {
  String _tab = 'tb';
  bool _loading = false;
  String? _error;
  Map<String, dynamic>? _data;

  // Date range — P&L uses both; TB / balance sheet use only "to" (as-of).
  DateTime _from = DateTime(DateTime.now().year, DateTime.now().month, 1);
  DateTime _to = DateTime.now();

  @override
  void initState() {
    super.initState();
    _load();
  }

  String _iso(DateTime d) => formatDate(d);

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      final range = 'from=${_iso(_from)}&to=${_iso(_to)}';
      final path = switch (_tab) {
        'pl' => '/api/v1/reports/profit-and-loss?$range',
        'bs' => '/api/v1/reports/balance-sheet?asOf=${_iso(_to)}',
        'gst' => '/api/v1/reports/gst-summary?$range',
        'day' => '/api/v1/reports/day-book?$range',
        'cash' => '/api/v1/reports/cash-book?$range',
        'bank' => '/api/v1/reports/bank-book?$range',
        _ => '/api/v1/reports/trial-balance?asOf=${_iso(_to)}',
      };
      final data = await api.get<Map<String, dynamic>>(
        path,
        fromJson: (j) => j as Map<String, dynamic>,
      );
      if (mounted) setState(() => _data = data);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Could not load report: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _pickDate({required bool isFrom}) async {
    final picked = await showDatePicker(
      context: context,
      initialDate: isFrom ? _from : _to,
      firstDate: DateTime(2020),
      lastDate: DateTime(2100),
    );
    if (picked != null) {
      setState(() => isFrom ? _from = picked : _to = picked);
      _load();
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    const tabs = <(String, String)>[
      ('tb', 'Trial balance'),
      ('pl', 'Profit & loss'),
      ('bs', 'Balance sheet'),
      ('gst', 'GST summary'),
      ('day', 'Day book'),
      ('cash', 'Cash book'),
      ('bank', 'Bank book'),
    ];
    return PageContainer(
      scrollable: false,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text('Financial Reports', style: theme.textTheme.titleLarge),
              const Spacer(),
              IconButton(
                tooltip: 'Refresh',
                onPressed: _loading ? null : _load,
                icon: const Icon(Icons.refresh),
              ),
            ],
          ),
          const SizedBox(height: Space.md),
          Wrap(
            spacing: Space.sm,
            children: [
              for (final t in tabs)
                ChoiceChip(
                  label: Text(t.$2),
                  selected: _tab == t.$1,
                  onSelected: (_) {
                    setState(() => _tab = t.$1);
                    _load();
                  },
                ),
            ],
          ),
          const SizedBox(height: Space.md),
          Builder(builder: (context) {
            final isRange = _tab != 'tb' && _tab != 'bs';
            return Wrap(
              spacing: Space.md,
              runSpacing: Space.sm,
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                if (isRange)
                  OutlinedButton.icon(
                    onPressed: () => _pickDate(isFrom: true),
                    icon: const Icon(Icons.calendar_today, size: 16),
                    label: Text('From ${_iso(_from)}'),
                  ),
                OutlinedButton.icon(
                  onPressed: () => _pickDate(isFrom: false),
                  icon: const Icon(Icons.calendar_today, size: 16),
                  label: Text('${isRange ? 'To' : 'As of'} ${_iso(_to)}'),
                ),
              ],
            );
          }),
          const SizedBox(height: Space.md),
          if (_error != null) ...[
            MessageBanner.error(_error!),
            const SizedBox(height: Space.md),
          ],
          Expanded(
            child: _loading
                ? const Center(child: CircularProgressIndicator())
                : _data == null
                    ? const SizedBox.shrink()
                    : SingleChildScrollView(child: _report(theme)),
          ),
        ],
      ),
    );
  }

  Widget _report(ThemeData theme) {
    return switch (_tab) {
      'pl' => _profitAndLoss(theme),
      'bs' => _balanceSheet(theme),
      'gst' => _gstSummary(theme),
      'day' => _dayBook(theme),
      'cash' || 'bank' => _accountBook(theme),
      _ => _trialBalance(theme),
    };
  }

  Widget _gstSummary(ThemeData theme) {
    return SectionCard(
      title: 'GST output summary',
      icon: Icons.request_quote_outlined,
      subtitle: '${_data!['invoiceCount']} taxable sale(s) · ${_data!['fromDate']} → ${_data!['toDate']}',
      child: Column(
        children: [
          _row(theme, 'Taxable value', _money(_data!['taxableValue']), ''),
          _row(theme, 'CGST', _money(_data!['cgst']), ''),
          _row(theme, 'SGST', _money(_data!['sgst']), ''),
          _row(theme, 'IGST', _money(_data!['igst']), ''),
          const Divider(),
          _row(theme, 'Total tax', _money(_data!['totalTax']), '', bold: true),
          _row(theme, 'Total value (incl. tax)', _money(_data!['totalValue']), '', bold: true),
          const SizedBox(height: Space.sm),
          Text('${_data!['note'] ?? ''}',
              style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
        ],
      ),
    );
  }

  Widget _dayBook(ThemeData theme) {
    final entries = List<Map<String, dynamic>>.from(_data!['entries'] as List? ?? const []);
    if (entries.isEmpty) {
      return Padding(
        padding: const EdgeInsets.all(Space.lg),
        child: Text('No journal entries in this range',
            style: theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
      );
    }
    return Column(
      children: [
        for (final e in entries)
          SectionCard(
            title: '${e['entryNumber']} · ${e['entryDate']}',
            icon: Icons.menu_book_outlined,
            subtitle: '${e['description']} (${e['sourceModule']})',
            child: Column(
              children: [
                for (final l in List<Map<String, dynamic>>.from(e['lines'] as List? ?? const []))
                  _row(theme, '${l['accountCode']} ${l['description'] ?? ''}',
                      _money(l['debit']), _money(l['credit'])),
              ],
            ),
          ),
      ],
    );
  }

  Widget _accountBook(ThemeData theme) {
    final entries = List<Map<String, dynamic>>.from(_data!['entries'] as List? ?? const []);
    return SectionCard(
      title: '${_data!['account']} book',
      icon: Icons.account_balance_wallet_outlined,
      subtitle: '${_data!['fromDate']} → ${_data!['toDate']}',
      child: Column(
        children: [
          _row(theme, 'Opening balance', '', _money(_data!['openingBalance']), bold: true),
          const Divider(),
          _row(theme, 'Entry', 'In', 'Out / Balance', header: true),
          for (final e in entries)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: Space.xs),
              child: Row(
                children: [
                  Expanded(flex: 4, child: Text('${e['entryDate']} · ${e['description']}',
                      style: theme.textTheme.bodySmall)),
                  Expanded(flex: 2, child: Text(_money(e['inflow']), textAlign: TextAlign.right,
                      style: theme.textTheme.bodySmall)),
                  Expanded(flex: 2, child: Text(_money(e['outflow']), textAlign: TextAlign.right,
                      style: theme.textTheme.bodySmall)),
                  Expanded(flex: 2, child: Text(_money(e['balance']), textAlign: TextAlign.right,
                      style: theme.textTheme.bodyMedium)),
                ],
              ),
            ),
          const Divider(),
          _row(theme, 'Total', _money(_data!['totalInflow']), _money(_data!['totalOutflow'])),
          _row(theme, 'Closing balance', '', _money(_data!['closingBalance']), bold: true),
        ],
      ),
    );
  }

  Widget _trialBalance(ThemeData theme) {
    final rows = List<Map<String, dynamic>>.from(_data!['rows'] as List? ?? const []);
    final balanced = _data!['balanced'] == true;
    return SectionCard(
      title: 'Trial balance',
      icon: Icons.balance_outlined,
      action: StatusChip.auto(balanced ? 'BALANCED' : 'UNBALANCED'),
      child: Column(
        children: [
          _row(theme, 'Account', 'Debit', 'Credit', header: true),
          const Divider(),
          for (final r in rows)
            _row(theme, '${r['code']} · ${r['name']}',
                _money(r['debit']), _money(r['credit'])),
          const Divider(),
          _row(theme, 'Total', _money(_data!['totalDebit']), _money(_data!['totalCredit']),
              bold: true),
        ],
      ),
    );
  }

  Widget _profitAndLoss(ThemeData theme) {
    final income = List<Map<String, dynamic>>.from(_data!['income'] as List? ?? const []);
    final expense = List<Map<String, dynamic>>.from(_data!['expense'] as List? ?? const []);
    final net = num.tryParse('${_data!['netSurplus']}') ?? 0;
    return Column(
      children: [
        SectionCard(
          title: 'Income',
          icon: Icons.trending_up_outlined,
          child: Column(
            children: [
              for (final r in income)
                _row(theme, '${r['code']} · ${r['name']}', _money(r['amount']), ''),
              const Divider(),
              _row(theme, 'Total income', _money(_data!['totalIncome']), '', bold: true),
            ],
          ),
        ),
        const SizedBox(height: Space.md),
        SectionCard(
          title: 'Expense',
          icon: Icons.trending_down_outlined,
          child: Column(
            children: [
              for (final r in expense)
                _row(theme, '${r['code']} · ${r['name']}', _money(r['amount']), ''),
              const Divider(),
              _row(theme, 'Total expense', _money(_data!['totalExpense']), '', bold: true),
            ],
          ),
        ),
        const SizedBox(height: Space.md),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(Space.lg),
            child: Row(
              children: [
                Text('Net surplus', style: theme.textTheme.titleMedium),
                const Spacer(),
                Text('₹${_data!['netSurplus']}',
                    style: theme.textTheme.titleLarge?.copyWith(
                        color: net >= 0 ? theme.colorScheme.primary : theme.colorScheme.error)),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _balanceSheet(ThemeData theme) {
    final assets = List<Map<String, dynamic>>.from(_data!['assets'] as List? ?? const []);
    final liabilities = List<Map<String, dynamic>>.from(_data!['liabilities'] as List? ?? const []);
    final equity = List<Map<String, dynamic>>.from(_data!['equity'] as List? ?? const []);
    final balanced = _data!['balanced'] == true;
    return Column(
      children: [
        SectionCard(
          title: 'Assets',
          icon: Icons.account_balance_outlined,
          action: StatusChip.auto(balanced ? 'BALANCED' : 'UNBALANCED'),
          child: Column(
            children: [
              for (final r in assets)
                _row(theme, '${r['code']} · ${r['name']}', _money(r['amount']), ''),
              const Divider(),
              _row(theme, 'Total assets', _money(_data!['totalAssets']), '', bold: true),
            ],
          ),
        ),
        const SizedBox(height: Space.md),
        SectionCard(
          title: 'Liabilities & equity',
          icon: Icons.account_balance_wallet_outlined,
          child: Column(
            children: [
              for (final r in liabilities)
                _row(theme, '${r['code']} · ${r['name']}', _money(r['amount']), ''),
              _row(theme, 'Total liabilities', _money(_data!['totalLiabilities']), '', bold: true),
              const Divider(),
              for (final r in equity)
                _row(theme, '${r['code']} · ${r['name']}', _money(r['amount']), ''),
              _row(theme, 'Total equity', _money(_data!['totalEquity']), '', bold: true),
              const Divider(),
              _row(theme, 'Total liabilities & equity',
                  _money(_data!['totalLiabilitiesAndEquity']), '', bold: true),
            ],
          ),
        ),
      ],
    );
  }

  Widget _row(ThemeData theme, String label, String c1, String c2,
      {bool header = false, bool bold = false}) {
    final style = header
        ? theme.textTheme.labelMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant)
        : (bold ? theme.textTheme.titleSmall : theme.textTheme.bodyMedium);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: Space.xs),
      child: Row(
        children: [
          Expanded(flex: 4, child: Text(label, style: style)),
          Expanded(flex: 2, child: Text(c1, textAlign: TextAlign.right, style: style)),
          if (c2.isNotEmpty)
            Expanded(flex: 2, child: Text(c2, textAlign: TextAlign.right, style: style)),
        ],
      ),
    );
  }

  String _money(Object? v) => v == null ? '' : '₹$v';
}
