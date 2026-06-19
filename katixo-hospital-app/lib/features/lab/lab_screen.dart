import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/auth/auth_state.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/util/pdf_actions.dart';
import '../../core/util/validators.dart';
import '../../core/widgets/empty_state.dart';
import '../../core/widgets/section_card.dart';
import '../../core/widgets/status_chip.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// Lab console: worklist (collect sample → enter result), doctor approvals,
/// order creation, test master and report viewer. Role-aware. Production-grade.
class LabScreen extends StatefulWidget {
  const LabScreen({super.key});

  @override
  State<LabScreen> createState() => _LabScreenState();
}

class _LabScreenState extends State<LabScreen> {
  String _tab = 'worklist';

  List<Map<String, dynamic>> _worklist = const [];
  List<Map<String, dynamic>> _approvals = const [];
  List<Map<String, dynamic>> _tests = const [];
  Map<String, dynamic>? _order; // last created/loaded order view
  Map<String, dynamic>? _report;
  int? _reportOrderId; // order id backing the loaded report (for PDF)

  bool _loading = false;
  String? _error;
  String? _info;
  String _role = '';

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _role = context.read<AuthState>().currentUser?.role ?? '';
      _tab = _canLab ? 'worklist' : (_canApprove ? 'approvals' : 'order');
      _loadAll();
    });
  }

  bool get _isAdmin => _role == 'ADMIN';
  bool get _canLab => _role == 'LAB_TECH' || _role == 'NURSE' || _role == 'ADMIN';
  bool get _canResult => _role == 'LAB_TECH' || _role == 'ADMIN';
  bool get _canApprove => _role == 'DOCTOR' || _role == 'ADMIN';
  bool get _canOrder => _role == 'DOCTOR' || _role == 'ADMIN';

  Future<void> _loadAll() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      List<Map<String, dynamic>> l(dynamic j) =>
          List<Map<String, dynamic>>.from(j as List? ?? const []);
      final tests = await api.get('/api/v1/lab/tests', fromJson: l);
      List<Map<String, dynamic>> work = const [];
      List<Map<String, dynamic>> appr = const [];
      if (_canLab) work = await api.get('/api/v1/lab/worklist', fromJson: l);
      if (_canApprove) {
        appr = await api.get('/api/v1/lab/worklist/pending-approval', fromJson: l);
      }
      if (mounted) {
        setState(() {
          _tests = tests;
          _worklist = work;
          _approvals = appr;
        });
      }
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  int _countWork(String s) => _worklist.where((w) => '${w['itemStatus']}' == s).length;

  // ---------------- build ----------------

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final tabs = <(String, String)>[
      if (_canLab) ('worklist', 'Worklist'),
      if (_canApprove) ('approvals', 'Approvals'),
      if (_canOrder) ('order', 'New order'),
      if (_isAdmin) ('tests', 'Test master'),
      ('report', 'Report'),
    ];
    if (!tabs.any((t) => t.$1 == _tab)) _tab = tabs.first.$1;

    return PageContainer(
      scrollable: false,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text('Laboratory', style: theme.textTheme.titleLarge),
              const Spacer(),
              IconButton(
                tooltip: 'Refresh',
                onPressed: _loading ? null : _loadAll,
                icon: const Icon(Icons.refresh),
              ),
            ],
          ),
          const SizedBox(height: Space.md),
          _kpiStrip(theme),
          const SizedBox(height: Space.md),
          Wrap(
            spacing: Space.sm,
            children: [
              for (final t in tabs)
                ChoiceChip(
                  label: Text(t.$2),
                  selected: _tab == t.$1,
                  onSelected: (_) => setState(() => _tab = t.$1),
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
          Expanded(child: _body(theme)),
        ],
      ),
    );
  }

  Widget _kpiStrip(ThemeData theme) {
    return Wrap(
      spacing: Space.md,
      runSpacing: Space.sm,
      children: [
        _stat(theme, 'Awaiting sample', '${_countWork('PENDING')}', StatusColors.warning, Icons.colorize_outlined),
        _stat(theme, 'Awaiting result', '${_countWork('SAMPLE_COLLECTED')}', StatusColors.info, Icons.science_outlined),
        _stat(theme, 'Awaiting approval', '${_approvals.length}', StatusColors.danger, Icons.fact_check_outlined),
        _stat(theme, 'Tests on menu', '${_tests.length}', theme.colorScheme.primary, Icons.biotech_outlined),
      ],
    );
  }

  Widget _stat(ThemeData theme, String label, String value, Color color, IconData icon) {
    return Container(
      width: 184,
      padding: const EdgeInsets.all(Space.md),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.08),
        borderRadius: Corners.mdRadius,
        border: Border.all(color: color.withValues(alpha: 0.25)),
      ),
      child: Row(
        children: [
          Icon(icon, size: 22, color: color),
          const SizedBox(width: Space.sm),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(label,
                    style: theme.textTheme.labelSmall
                        ?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
                Text(value, style: theme.textTheme.titleLarge),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _body(ThemeData theme) => switch (_tab) {
        'approvals' => _approvalsTab(theme),
        'order' => _orderTab(theme),
        'tests' => _testsTab(theme),
        'report' => _reportTab(theme),
        _ => _worklistTab(theme),
      };

  // ---------------- worklist ----------------

  Widget _worklistTab(ThemeData theme) {
    if (_worklist.isEmpty) {
      return EmptyState(
        icon: Icons.checklist_outlined,
        title: _loading ? 'Loading…' : 'Worklist is clear',
        message: 'No samples pending collection or results.',
      );
    }
    return ListView.separated(
      itemCount: _worklist.length,
      separatorBuilder: (_, __) => const SizedBox(height: Space.sm),
      itemBuilder: (context, i) {
        final w = _worklist[i];
        final status = '${w['itemStatus']}';
        return Card(
          child: ListTile(
            leading: CircleAvatar(
              backgroundColor: theme.colorScheme.primaryContainer,
              child: Icon(_specimenIcon('${w['specimenType']}'),
                  color: theme.colorScheme.onPrimaryContainer),
            ),
            title: Row(
              children: [
                Text('${w['testName']}', style: theme.textTheme.titleSmall),
                const SizedBox(width: Space.sm),
                StatusChip.auto(status),
              ],
            ),
            subtitle: Text('${w['testCode']} · ${w['specimenType']}',
                style: theme.textTheme.bodySmall),
            trailing: status == 'PENDING'
                ? FilledButton.icon(
                    onPressed: _loading ? null : () => _collectSample(w['itemId'] as int),
                    icon: const Icon(Icons.colorize, size: 18),
                    label: const Text('Collect'),
                  )
                : status == 'SAMPLE_COLLECTED' && _canResult
                    ? FilledButton.icon(
                        onPressed: _loading ? null : () => _resultDialog(w['itemId'] as int, '${w['testName']}'),
                        icon: const Icon(Icons.edit_note, size: 18),
                        label: const Text('Result'),
                      )
                    : const Chip(label: Text('Awaiting review')),
          ),
        );
      },
    );
  }

  // ---------------- approvals ----------------

  Widget _approvalsTab(ThemeData theme) {
    if (_approvals.isEmpty) {
      return EmptyState(
        icon: Icons.fact_check_outlined,
        title: _loading ? 'Loading…' : 'Nothing to approve',
        message: 'Resulted reports awaiting a doctor sign-off appear here.',
      );
    }
    return ListView.separated(
      itemCount: _approvals.length,
      separatorBuilder: (_, __) => const SizedBox(height: Space.sm),
      itemBuilder: (context, i) {
        final a = _approvals[i];
        final abnormal = a['isAbnormal'] == true;
        final wait = a['waitingMinutes'];
        return Card(
          child: ListTile(
            leading: Icon(
              abnormal ? Icons.warning_amber : Icons.assignment_turned_in_outlined,
              color: abnormal ? theme.colorScheme.error : theme.colorScheme.primary,
            ),
            title: Row(
              children: [
                Text('${a['testName']}', style: theme.textTheme.titleSmall),
                const SizedBox(width: Space.sm),
                if (abnormal)
                  Text('ABNORMAL',
                      style: theme.textTheme.labelSmall
                          ?.copyWith(color: theme.colorScheme.error, fontWeight: FontWeight.bold)),
              ],
            ),
            subtitle: Text(
              'Result: ${a['result']} · order #${a['orderId']}'
              '${wait != null ? ' · waiting ${wait}m' : ''}',
              style: theme.textTheme.bodySmall?.copyWith(
                  color: abnormal ? theme.colorScheme.error : null),
            ),
            trailing: FilledButton.icon(
              onPressed: _loading ? null : () => _approve(a['itemId'] as int),
              icon: const Icon(Icons.check, size: 18),
              label: const Text('Approve'),
            ),
          ),
        );
      },
    );
  }

  // ---------------- new order ----------------

  Widget _orderTab(ThemeData theme) {
    return SingleChildScrollView(
      child: Column(
        children: [
          _NewOrderForm(
            tests: _tests,
            onSubmit: (sourceType, sourceId, codes, notes) =>
                _createOrder(sourceType, sourceId, codes, notes),
          ),
          if (_order != null) ...[
            const SizedBox(height: Space.md),
            _orderDetail(theme, _order!),
          ],
        ],
      ),
    );
  }

  Widget _orderDetail(ThemeData theme, Map<String, dynamic> o) {
    final items = List<Map<String, dynamic>>.from(o['items'] as List? ?? const []);
    return SectionCard(
      title: 'Order ${o['orderNumber']}',
      icon: Icons.receipt_long_outlined,
      subtitle: 'Patient #${o['patientId']} · ${o['orderStatus']}',
      child: Column(
        children: [
          for (final it in items)
            ListTile(
              dense: true,
              contentPadding: EdgeInsets.zero,
              title: Text('${it['testName']}'),
              subtitle: Text(
                '${it['testCode']} · ₹${it['rate']}'
                '${(it['result'] ?? '').toString().isNotEmpty ? ' · ${it['result']}' : ''}',
                style: theme.textTheme.bodySmall,
              ),
              trailing: StatusChip.auto('${it['itemStatus']}'),
            ),
        ],
      ),
    );
  }

  // ---------------- tests ----------------

  Widget _testsTab(ThemeData theme) {
    return SectionCard(
      title: 'Test master',
      icon: Icons.biotech_outlined,
      subtitle: '${_tests.length} tests',
      action: FilledButton.icon(
        onPressed: _loading ? null : _addTestDialog,
        icon: const Icon(Icons.add, size: 18),
        label: const Text('Add test'),
      ),
      child: _tests.isEmpty
          ? Text('No tests configured',
              style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant))
          : Column(
              children: [
                for (final t in _tests)
                  ListTile(
                    dense: true,
                    contentPadding: EdgeInsets.zero,
                    leading: Icon(_specimenIcon('${t['specimenType']}'), size: 20),
                    title: Text('${t['testName']}'),
                    subtitle: Text('${t['testCode']} · ${t['specimenType']}',
                        style: theme.textTheme.bodySmall),
                    trailing: Text('₹${t['rate']}', style: theme.textTheme.titleSmall),
                  ),
              ],
            ),
    );
  }

  // ---------------- report ----------------

  Widget _reportTab(ThemeData theme) {
    final orderIdCtrl = TextEditingController();
    return SingleChildScrollView(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SectionCard(
            title: 'Lab report',
            icon: Icons.description_outlined,
            child: Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: orderIdCtrl,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(labelText: 'Lab order ID *'),
                    onSubmitted: (v) => _loadReport(int.tryParse(v.trim())),
                  ),
                ),
                const SizedBox(width: Space.md),
                FilledButton.icon(
                  onPressed: _loading ? null : () => _loadReport(int.tryParse(orderIdCtrl.text.trim())),
                  icon: const Icon(Icons.search, size: 18),
                  label: const Text('Load'),
                ),
              ],
            ),
          ),
          if (_report != null) ...[
            const SizedBox(height: Space.md),
            _reportCard(theme, _report!),
          ],
        ],
      ),
    );
  }

  Widget _reportCard(ThemeData theme, Map<String, dynamic> r) {
    final results = List<Map<String, dynamic>>.from(r['results'] as List? ?? const []);
    return SectionCard(
      title: '${r['orderNumber']}',
      icon: Icons.assignment_outlined,
      subtitle: '${r['patientName']} · UHID ${r['uhid']} · ${r['orderStatus']}',
      child: Column(
        children: [
          Align(
            alignment: Alignment.centerRight,
            child: FilledButton.icon(
              onPressed: _reportOrderId == null ? null : _openReportPdf,
              icon: const Icon(Icons.picture_as_pdf_outlined, size: 18),
              label: const Text('Open PDF'),
            ),
          ),
          const SizedBox(height: Space.sm),
          Row(
            children: [
              Expanded(flex: 3, child: _h(theme, 'Test')),
              Expanded(flex: 2, child: _h(theme, 'Result')),
              Expanded(flex: 1, child: _h(theme, 'Unit')),
              Expanded(flex: 2, child: _h(theme, 'Reference')),
              Expanded(flex: 2, child: _h(theme, 'Status')),
            ],
          ),
          const Divider(),
          for (final res in results)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: Space.sm),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(flex: 3, child: Text('${res['testName']}')),
                  Expanded(
                    flex: 2,
                    child: Text('${res['result']}',
                        style: TextStyle(
                          color: res['isAbnormal'] == true ? theme.colorScheme.error : null,
                          fontWeight: res['isAbnormal'] == true ? FontWeight.bold : null,
                        )),
                  ),
                  Expanded(flex: 1, child: Text('${res['unit']}')),
                  Expanded(flex: 2, child: Text('${res['referenceRange']}')),
                  Expanded(flex: 2, child: StatusChip.auto('${res['status']}')),
                ],
              ),
            ),
        ],
      ),
    );
  }

  Widget _h(ThemeData theme, String s) => Text(s,
      style: theme.textTheme.labelSmall
          ?.copyWith(color: theme.colorScheme.onSurfaceVariant, fontWeight: FontWeight.bold));

  // ---------------- actions ----------------

  Future<void> _collectSample(int itemId) async {
    await _post('/api/v1/lab/order-items/$itemId/collect-sample', const {}, 'Sample collected');
  }

  Future<void> _resultDialog(int itemId, String testName) async {
    final value = TextEditingController();
    var abnormal = false;
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setD) => AlertDialog(
          title: Text('Enter result — $testName'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(controller: value,
                  decoration: const InputDecoration(labelText: 'Result value *')),
              const SizedBox(height: Space.sm),
              CheckboxListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('Flag as abnormal'),
                value: abnormal,
                onChanged: (v) => setD(() => abnormal = v ?? false),
              ),
            ],
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
            FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Save result')),
          ],
        ),
      ),
    );
    if (ok != true || value.text.trim().isEmpty) return;
    await _post('/api/v1/lab/order-items/$itemId/result',
        {'resultValue': value.text.trim(), 'isAbnormal': abnormal}, 'Result entered');
  }

  Future<void> _approve(int itemId) async {
    await _post('/api/v1/lab/order-items/$itemId/approve', const {}, 'Report released');
  }

  Future<void> _createOrder(String sourceType, int sourceId, List<String> codes, String notes) async {
    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });
    try {
      final api = context.read<ApiClient>();
      final created = await api.post<Map<String, dynamic>>(
        '/api/v1/lab/orders',
        {
          'sourceType': sourceType,
          'sourceId': sourceId,
          'testCodes': codes,
          if (notes.trim().isNotEmpty) 'notes': notes.trim(),
        },
        fromJson: (j) => j as Map<String, dynamic>,
      );
      final view = await api.get<Map<String, dynamic>>(
        '/api/v1/lab/orders/${created['id']}',
        fromJson: (j) => j as Map<String, dynamic>,
      );
      setState(() {
        _info = 'Lab order ${created['orderNumber']} created';
        _order = view;
      });
      await _loadAll();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _addTestDialog() async {
    final code = TextEditingController();
    final name = TextEditingController();
    final rate = TextEditingController();
    final unit = TextEditingController();
    final range = TextEditingController();
    String specimen = 'BLOOD';
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setD) => AlertDialog(
          title: const Text('Add lab test'),
          content: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
            child: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  TextField(controller: code, decoration: const InputDecoration(labelText: 'Test code *')),
                  TextField(controller: name, decoration: const InputDecoration(labelText: 'Test name *')),
                  const SizedBox(height: Space.sm),
                  DropdownButtonFormField<String>(
                    initialValue: specimen,
                    decoration: const InputDecoration(labelText: 'Specimen'),
                    items: const [
                      DropdownMenuItem(value: 'BLOOD', child: Text('Blood')),
                      DropdownMenuItem(value: 'URINE', child: Text('Urine')),
                      DropdownMenuItem(value: 'SWAB', child: Text('Swab')),
                      DropdownMenuItem(value: 'STOOL', child: Text('Stool')),
                      DropdownMenuItem(value: 'OTHER', child: Text('Other')),
                    ],
                    onChanged: (v) => setD(() => specimen = v ?? 'BLOOD'),
                  ),
                  TextField(controller: rate, keyboardType: TextInputType.number,
                      decoration: const InputDecoration(labelText: 'Rate *', prefixText: '₹ ')),
                  TextField(controller: unit, decoration: const InputDecoration(labelText: 'Unit')),
                  TextField(controller: range, decoration: const InputDecoration(labelText: 'Reference range')),
                ],
              ),
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
            FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Save')),
          ],
        ),
      ),
    );
    if (ok != true) return;
    final testError = firstError([
      requiredText(code.text, field: 'Test code'),
      requiredText(name.text, field: 'Test name'),
      positiveAmount(rate.text, field: 'Rate'),
    ]);
    if (testError != null) {
      setState(() => _error = testError);
      return;
    }
    await _post('/api/v1/lab/tests', {
      'testCode': code.text.trim(),
      'testName': name.text.trim(),
      'specimenType': specimen,
      'rate': double.parse(rate.text.trim()),
      if (unit.text.trim().isNotEmpty) 'unit': unit.text.trim(),
      if (range.text.trim().isNotEmpty) 'referenceRange': range.text.trim(),
    }, 'Test added');
  }

  Future<void> _loadReport(int? orderId) async {
    if (orderId == null) {
      setState(() => _error = 'Enter a valid lab order ID');
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      final r = await api.get<Map<String, dynamic>>('/api/v1/lab/orders/$orderId/report',
          fromJson: (j) => j as Map<String, dynamic>);
      if (mounted) {
        setState(() {
          _report = r;
          _reportOrderId = orderId;
        });
      }
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _openReportPdf() async {
    final orderId = _reportOrderId;
    if (orderId == null) return;
    await openPdf(
      context,
      context.read<ApiClient>(),
      '/api/v1/lab/orders/$orderId/report.pdf',
      filename: 'lab-report-$orderId.pdf',
    );
  }

  Future<void> _post(String path, Map<String, dynamic> body, String ok) async {
    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });
    try {
      final api = context.read<ApiClient>();
      await api.post<Map<String, dynamic>>(path, body, fromJson: (j) => j as Map<String, dynamic>);
      setState(() => _info = ok);
      await _loadAll();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  IconData _specimenIcon(String s) => switch (s) {
        'BLOOD' => Icons.bloodtype_outlined,
        'URINE' => Icons.science_outlined,
        'SWAB' => Icons.colorize_outlined,
        'STOOL' => Icons.biotech_outlined,
        _ => Icons.science_outlined,
      };
}

/// New lab order form (source + tests). Stateful so test selection persists.
class _NewOrderForm extends StatefulWidget {
  const _NewOrderForm({required this.tests, required this.onSubmit});

  final List<Map<String, dynamic>> tests;
  final void Function(String sourceType, int sourceId, List<String> codes, String notes) onSubmit;

  @override
  State<_NewOrderForm> createState() => _NewOrderFormState();
}

class _NewOrderFormState extends State<_NewOrderForm> {
  String _sourceType = 'OPD_VISIT';
  final _sourceId = TextEditingController();
  final _notes = TextEditingController();
  final Set<String> _selected = {};

  @override
  void dispose() {
    _sourceId.dispose();
    _notes.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return SectionCard(
      title: 'New lab order',
      icon: Icons.add_chart_outlined,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              SizedBox(
                width: 200,
                child: DropdownButtonFormField<String>(
                  initialValue: _sourceType,
                  decoration: const InputDecoration(labelText: 'Source'),
                  items: const [
                    DropdownMenuItem(value: 'OPD_VISIT', child: Text('OPD visit')),
                    DropdownMenuItem(value: 'IPD_ADMISSION', child: Text('IPD admission')),
                  ],
                  onChanged: (v) => setState(() => _sourceType = v ?? 'OPD_VISIT'),
                ),
              ),
              const SizedBox(width: Space.md),
              Expanded(
                child: TextField(
                  controller: _sourceId,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: 'Visit / Admission ID *'),
                ),
              ),
            ],
          ),
          const SizedBox(height: Space.md),
          Text('Select tests', style: theme.textTheme.titleSmall),
          const SizedBox(height: Space.xs),
          if (widget.tests.isEmpty)
            Text('No tests in master — add tests first',
                style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant))
          else
            Wrap(
              spacing: Space.sm,
              runSpacing: Space.xs,
              children: [
                for (final t in widget.tests)
                  FilterChip(
                    label: Text('${t['testName']} · ₹${t['rate']}'),
                    selected: _selected.contains('${t['testCode']}'),
                    onSelected: (sel) => setState(() {
                      final code = '${t['testCode']}';
                      sel ? _selected.add(code) : _selected.remove(code);
                    }),
                  ),
              ],
            ),
          const SizedBox(height: Space.md),
          TextField(controller: _notes, decoration: const InputDecoration(labelText: 'Notes')),
          const SizedBox(height: Space.md),
          Align(
            alignment: Alignment.centerRight,
            child: FilledButton.icon(
              onPressed: () {
                final sid = int.tryParse(_sourceId.text.trim());
                if (sid == null || _selected.isEmpty) return;
                widget.onSubmit(_sourceType, sid, _selected.toList(), _notes.text);
                setState(() => _selected.clear());
              },
              icon: const Icon(Icons.send, size: 18),
              label: const Text('Create order'),
            ),
          ),
        ],
      ),
    );
  }
}
