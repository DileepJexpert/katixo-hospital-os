import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/status_chip.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// TPA / insurance claims: payer master + case lifecycle (pre-auth → approve →
/// submit → settle) + outstanding ageing. Body widget — host supplies AppShell.
class TpaScreen extends StatefulWidget {
  const TpaScreen({super.key});

  @override
  State<TpaScreen> createState() => _TpaScreenState();
}

class _TpaScreenState extends State<TpaScreen> {
  int _tab = 0; // 0 = cases, 1 = payers

  List<Map<String, dynamic>> _cases = const [];
  List<Map<String, dynamic>> _payers = const [];
  Map<String, dynamic>? _selectedCase;
  Map<String, dynamic>? _ageing;

  bool _loading = false;
  String? _error;
  String? _info;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _loadAll());
  }

  Future<void> _loadAll() async {
    await _loadPayers();
    await _loadCases();
    await _loadAgeing();
  }

  Future<void> _loadPayers() async {
    try {
      final api = context.read<ApiClient>();
      final list = await api.get<List<Map<String, dynamic>>>(
        '/api/v1/tpa/payers',
        fromJson: (json) =>
            List<Map<String, dynamic>>.from(json as List? ?? const []),
      );
      if (mounted) setState(() => _payers = list);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    }
  }

  Future<void> _loadCases() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      final list = await api.get<List<Map<String, dynamic>>>(
        '/api/v1/tpa/cases',
        fromJson: (json) =>
            List<Map<String, dynamic>>.from(json as List? ?? const []),
      );
      if (mounted) setState(() => _cases = list);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _loadAgeing() async {
    try {
      final api = context.read<ApiClient>();
      final a = await api.get<Map<String, dynamic>>(
        '/api/v1/tpa/ageing',
        fromJson: (json) => json as Map<String, dynamic>,
      );
      if (mounted) setState(() => _ageing = a);
    } on ApiException catch (_) {/* non-critical */}
  }

  Future<void> _openCase(int id) async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      final c = await api.get<Map<String, dynamic>>(
        '/api/v1/tpa/cases/$id',
        fromJson: (json) => json as Map<String, dynamic>,
      );
      if (mounted) setState(() => _selectedCase = c);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _post(String path, Map<String, dynamic> body, String ok, int caseId) async {
    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });
    try {
      final api = context.read<ApiClient>();
      await api.post<Map<String, dynamic>>(path, body,
          fromJson: (json) => json as Map<String, dynamic>);
      setState(() => _info = ok);
      await _loadCases();
      await _loadAgeing();
      await _openCase(caseId);
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
          Row(
            children: [
              Text('TPA / Insurance', style: theme.textTheme.titleLarge),
              const Spacer(),
              SegmentedButton<int>(
                segments: const [
                  ButtonSegment(value: 0, label: Text('Cases')),
                  ButtonSegment(value: 1, label: Text('Payers')),
                ],
                selected: {_tab},
                onSelectionChanged: (s) => setState(() => _tab = s.first),
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
          Expanded(child: _tab == 0 ? _casesTab(theme) : _payersTab(theme)),
        ],
      ),
    );
  }

  // ---------------- payers ----------------

  Widget _payersTab(ThemeData theme) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Text('${_payers.length} payers', style: theme.textTheme.titleMedium),
            const Spacer(),
            FilledButton.icon(
              onPressed: _loading ? null : _payerDialog,
              icon: const Icon(Icons.add, size: 18),
              label: const Text('Add payer'),
            ),
          ],
        ),
        const SizedBox(height: Space.sm),
        Expanded(
          child: _payers.isEmpty
              ? Center(
                  child: Text(_loading ? 'Loading…' : 'No payers yet',
                      style: theme.textTheme.bodyMedium?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant)))
              : ListView.separated(
                  itemCount: _payers.length,
                  separatorBuilder: (_, __) => const Divider(height: 1),
                  itemBuilder: (context, i) {
                    final p = _payers[i];
                    return ListTile(
                      title: Text('${p['name']}',
                          style: theme.textTheme.titleSmall),
                      subtitle: Text(
                        '${p['payerCode']} · ${p['payerType']} · ${p['contactPhone'] ?? '—'}',
                        style: theme.textTheme.bodySmall,
                      ),
                    );
                  },
                ),
        ),
      ],
    );
  }

  Future<void> _payerDialog() async {
    final name = TextEditingController();
    final phone = TextEditingController();
    final person = TextEditingController();
    var type = 'INSURER';
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setD) => AlertDialog(
          title: const Text('Add payer'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(controller: name,
                  decoration: const InputDecoration(labelText: 'Name *')),
              const SizedBox(height: Space.sm),
              DropdownButtonFormField<String>(
                value: type,
                decoration: const InputDecoration(labelText: 'Type'),
                items: const [
                  DropdownMenuItem(value: 'INSURER', child: Text('Insurer')),
                  DropdownMenuItem(value: 'TPA', child: Text('TPA')),
                  DropdownMenuItem(
                      value: 'GOVT_SCHEME', child: Text('Govt scheme')),
                ],
                onChanged: (v) => setD(() => type = v ?? 'INSURER'),
              ),
              const SizedBox(height: Space.sm),
              TextField(controller: person,
                  decoration: const InputDecoration(labelText: 'Contact person')),
              const SizedBox(height: Space.sm),
              TextField(controller: phone,
                  decoration: const InputDecoration(labelText: 'Contact phone')),
            ],
          ),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(context, false),
                child: const Text('Cancel')),
            FilledButton(
                onPressed: () => Navigator.pop(context, true),
                child: const Text('Save')),
          ],
        ),
      ),
    );
    if (ok != true || name.text.trim().isEmpty) return;
    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });
    try {
      final api = context.read<ApiClient>();
      await api.post<Map<String, dynamic>>(
        '/api/v1/tpa/payers',
        {
          'name': name.text.trim(),
          'payerType': type,
          if (person.text.trim().isNotEmpty) 'contactPerson': person.text.trim(),
          if (phone.text.trim().isNotEmpty) 'contactPhone': phone.text.trim(),
        },
        fromJson: (json) => json as Map<String, dynamic>,
      );
      setState(() => _info = 'Payer added');
      await _loadPayers();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  // ---------------- cases ----------------

  Widget _casesTab(ThemeData theme) {
    if (_selectedCase != null) return _caseDetail(theme, _selectedCase!);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (_ageing != null) _ageingCard(theme, _ageing!),
        const SizedBox(height: Space.sm),
        Row(
          children: [
            Text('${_cases.length} cases', style: theme.textTheme.titleMedium),
            const Spacer(),
            FilledButton.icon(
              onPressed: _loading || _payers.isEmpty ? null : _caseDialog,
              icon: const Icon(Icons.add, size: 18),
              label: const Text('New case'),
            ),
          ],
        ),
        const SizedBox(height: Space.sm),
        Expanded(
          child: _cases.isEmpty
              ? Center(
                  child: Text(_loading ? 'Loading…' : 'No TPA cases yet',
                      style: theme.textTheme.bodyMedium?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant)))
              : ListView.separated(
                  itemCount: _cases.length,
                  separatorBuilder: (_, __) => const Divider(height: 1),
                  itemBuilder: (context, i) {
                    final c = _cases[i];
                    return ListTile(
                      onTap: () => _openCase(c['id'] as int),
                      title: Row(
                        children: [
                          Text('${c['caseNumber']}',
                              style: theme.textTheme.titleSmall),
                          const SizedBox(width: Space.sm),
                          StatusChip.auto('${c['status']}'),
                        ],
                      ),
                      subtitle: Text(
                        'Patient #${c['patientId']} · claimed ₹${c['claimedAmount']} · '
                        'approved ₹${c['approvedAmount']}',
                        style: theme.textTheme.bodySmall,
                      ),
                      trailing: Text('₹${c['outstanding']}',
                          style: theme.textTheme.titleSmall),
                    );
                  },
                ),
        ),
      ],
    );
  }

  Widget _ageingCard(ThemeData theme, Map<String, dynamic> a) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(Space.md),
        child: Row(
          children: [
            _stat(theme, 'Outstanding', '₹${a['totalOutstanding']}'),
            const SizedBox(width: Space.lg),
            _stat(theme, '0-30d', '₹${a['bucket0to30']}'),
            const SizedBox(width: Space.lg),
            _stat(theme, '31-60d', '₹${a['bucket31to60']}'),
            const SizedBox(width: Space.lg),
            _stat(theme, '61-90d', '₹${a['bucket61to90']}'),
            const SizedBox(width: Space.lg),
            _stat(theme, '90d+', '₹${a['bucket90plus']}'),
          ],
        ),
      ),
    );
  }

  Widget _caseDetail(ThemeData theme, Map<String, dynamic> c) {
    final status = '${c['status']}';
    final id = c['id'] as int;
    final events =
        List<Map<String, dynamic>>.from(c['events'] as List? ?? const []);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        TextButton.icon(
          onPressed: () => setState(() => _selectedCase = null),
          icon: const Icon(Icons.arrow_back, size: 18),
          label: const Text('Back'),
        ),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(Space.lg),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Text('${c['caseNumber']}',
                        style: theme.textTheme.titleMedium),
                    const SizedBox(width: Space.sm),
                    StatusChip.auto(status),
                    const Spacer(),
                    ..._actionsFor(theme, status, id, c),
                  ],
                ),
                const SizedBox(height: Space.sm),
                Wrap(
                  spacing: Space.lg,
                  runSpacing: Space.sm,
                  children: [
                    _stat(theme, 'Claimed', '₹${c['claimedAmount']}'),
                    _stat(theme, 'Approved', '₹${c['approvedAmount']}'),
                    _stat(theme, 'Settled', '₹${c['settledAmount']}'),
                    _stat(theme, 'Disallowed', '₹${c['disallowedAmount']}'),
                    _stat(theme, 'Outstanding', '₹${c['outstanding']}'),
                  ],
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: Space.md),
        Text('History', style: theme.textTheme.titleMedium),
        const SizedBox(height: Space.sm),
        Expanded(
          child: events.isEmpty
              ? Center(
                  child: Text('No events',
                      style: theme.textTheme.bodySmall?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant)))
              : ListView.separated(
                  itemCount: events.length,
                  separatorBuilder: (_, __) => const Divider(height: 1),
                  itemBuilder: (context, i) {
                    final e = events[i];
                    return ListTile(
                      dense: true,
                      title: Text('${e['eventType']}'
                          '${e['amount'] != null ? ' · ₹${e['amount']}' : ''}'),
                      subtitle: e['note'] == null
                          ? null
                          : Text('${e['note']}',
                              style: theme.textTheme.bodySmall),
                      trailing: Text('${e['at'] ?? ''}'.split('T').first,
                          style: theme.textTheme.bodySmall),
                    );
                  },
                ),
        ),
      ],
    );
  }

  List<Widget> _actionsFor(
      ThemeData theme, String status, int id, Map<String, dynamic> c) {
    switch (status) {
      case 'PREAUTH_REQUESTED':
      case 'QUERY_RAISED':
        return [
          OutlinedButton(
            onPressed: _loading ? null : () => _amountDialog(id, 'approve'),
            child: const Text('Approve'),
          ),
          const SizedBox(width: Space.sm),
          TextButton(
            onPressed: _loading
                ? null
                : () => _post('/api/v1/tpa/cases/$id/reject', {'note': 'Rejected'},
                    'Case rejected', id),
            child: const Text('Reject'),
          ),
        ];
      case 'APPROVED':
        return [
          FilledButton(
            onPressed: _loading
                ? null
                : () => _post('/api/v1/tpa/cases/$id/submit', const {},
                    'Claim submitted', id),
            child: const Text('Submit claim'),
          ),
          const SizedBox(width: Space.sm),
          OutlinedButton(
            onPressed: _loading ? null : () => _settleDialog(id),
            child: const Text('Settle'),
          ),
        ];
      case 'CLAIM_SUBMITTED':
      case 'PARTIALLY_SETTLED':
        return [
          FilledButton(
            onPressed: _loading ? null : () => _settleDialog(id),
            child: const Text('Settle'),
          ),
        ];
      default:
        return const [];
    }
  }

  Future<void> _amountDialog(int id, String action) async {
    final amount = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Approved amount'),
        content: TextField(
          controller: amount,
          keyboardType: TextInputType.number,
          decoration:
              const InputDecoration(labelText: 'Amount *', prefixText: '₹ '),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Cancel')),
          FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('Approve')),
        ],
      ),
    );
    if (ok != true) return;
    await _post('/api/v1/tpa/cases/$id/$action',
        {'amount': double.tryParse(amount.text.trim()) ?? 0},
        'Approved & receivable recognized', id);
  }

  Future<void> _settleDialog(int id) async {
    final received = TextEditingController();
    final disallowed = TextEditingController(text: '0');
    var fromCash = false;
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setD) => AlertDialog(
          title: const Text('Record settlement'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: received,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(
                    labelText: 'Amount received *', prefixText: '₹ '),
              ),
              const SizedBox(height: Space.sm),
              TextField(
                controller: disallowed,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(
                    labelText: 'Disallowed (write-off)', prefixText: '₹ '),
              ),
              const SizedBox(height: Space.sm),
              Row(
                children: [
                  Checkbox(
                    value: fromCash,
                    onChanged: (v) => setD(() => fromCash = v ?? false),
                  ),
                  const Text('Received in cash'),
                ],
              ),
            ],
          ),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(context, false),
                child: const Text('Cancel')),
            FilledButton(
                onPressed: () => Navigator.pop(context, true),
                child: const Text('Settle')),
          ],
        ),
      ),
    );
    if (ok != true) return;
    await _post('/api/v1/tpa/cases/$id/settle', {
      'receivedAmount': double.tryParse(received.text.trim()) ?? 0,
      'disallowedAmount': double.tryParse(disallowed.text.trim()) ?? 0,
      'fromCash': fromCash,
    }, 'Settlement recorded', id);
  }

  Future<void> _caseDialog() async {
    String payerId = '${_payers.first['id']}';
    final patientId = TextEditingController();
    final claimed = TextEditingController();
    final policy = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setD) => AlertDialog(
          title: const Text('New TPA case'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              DropdownButtonFormField<String>(
                value: payerId,
                isExpanded: true,
                decoration: const InputDecoration(labelText: 'Payer *'),
                items: [
                  for (final p in _payers)
                    DropdownMenuItem(
                        value: '${p['id']}', child: Text('${p['name']}')),
                ],
                onChanged: (v) => setD(() => payerId = v ?? payerId),
              ),
              const SizedBox(height: Space.sm),
              TextField(
                controller: patientId,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(labelText: 'Patient ID *'),
              ),
              const SizedBox(height: Space.sm),
              TextField(
                controller: claimed,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(
                    labelText: 'Claimed amount', prefixText: '₹ '),
              ),
              const SizedBox(height: Space.sm),
              TextField(controller: policy,
                  decoration: const InputDecoration(labelText: 'Policy number')),
            ],
          ),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(context, false),
                child: const Text('Cancel')),
            FilledButton(
                onPressed: () => Navigator.pop(context, true),
                child: const Text('Create')),
          ],
        ),
      ),
    );
    if (ok != true) return;
    final pid = int.tryParse(patientId.text.trim());
    if (pid == null) {
      setState(() => _error = 'Enter a valid patient ID');
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });
    try {
      final api = context.read<ApiClient>();
      final c = await api.post<Map<String, dynamic>>(
        '/api/v1/tpa/cases',
        {
          'payerId': int.parse(payerId),
          'patientId': pid,
          if (claimed.text.trim().isNotEmpty)
            'claimedAmount': double.tryParse(claimed.text.trim()),
          if (policy.text.trim().isNotEmpty) 'policyNumber': policy.text.trim(),
        },
        fromJson: (json) => json as Map<String, dynamic>,
      );
      setState(() => _info = 'Case ${c['caseNumber']} created');
      await _loadCases();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Widget _stat(ThemeData theme, String label, String value) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label,
            style: theme.textTheme.bodySmall
                ?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
        Text(value, style: theme.textTheme.titleMedium),
      ],
    );
  }
}
