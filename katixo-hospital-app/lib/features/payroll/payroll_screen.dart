import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/util/pdf_actions.dart';
import '../../core/widgets/status_chip.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// HR / payroll screen: employee master + monthly payroll runs
/// (DRAFT → APPROVED → PAID), statutory remittance and payslip details.
/// Body widget — the host home supplies the AppShell.
class PayrollScreen extends StatefulWidget {
  const PayrollScreen({super.key});

  @override
  State<PayrollScreen> createState() => _PayrollScreenState();
}

class _PayrollScreenState extends State<PayrollScreen> {
  int _tab = 0; // 0 = employees, 1 = runs

  List<Map<String, dynamic>> _employees = const [];
  List<Map<String, dynamic>> _runs = const [];
  Map<String, dynamic>? _selectedRun;

  bool _loading = false;
  String? _error;
  String? _info;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _loadAll());
  }

  Future<void> _loadAll() async {
    await _loadEmployees();
    await _loadRuns();
  }

  Future<void> _loadEmployees() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      final list = await api.get<List<Map<String, dynamic>>>(
        '/api/v1/payroll/employees',
        fromJson: (json) =>
            List<Map<String, dynamic>>.from(json as List? ?? const []),
      );
      if (mounted) setState(() => _employees = list);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _loadRuns() async {
    try {
      final api = context.read<ApiClient>();
      final list = await api.get<List<Map<String, dynamic>>>(
        '/api/v1/payroll/runs',
        fromJson: (json) =>
            List<Map<String, dynamic>>.from(json as List? ?? const []),
      );
      if (mounted) setState(() => _runs = list);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    }
  }

  Future<void> _openRun(int id) async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      final run = await api.get<Map<String, dynamic>>(
        '/api/v1/payroll/runs/$id',
        fromJson: (json) => json as Map<String, dynamic>,
      );
      if (mounted) setState(() => _selectedRun = run);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _runAction(int id, String action, String okMessage) async {
    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });
    try {
      final api = context.read<ApiClient>();
      await api.post<Map<String, dynamic>>(
        '/api/v1/payroll/runs/$id/$action',
        const <String, dynamic>{},
        fromJson: (json) => json as Map<String, dynamic>,
      );
      setState(() => _info = okMessage);
      await _loadRuns();
      await _openRun(id);
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
          Row(
            children: [
              Text('Payroll', style: theme.textTheme.titleLarge),
              const Spacer(),
              SegmentedButton<int>(
                segments: const [
                  ButtonSegment(value: 0, label: Text('Employees')),
                  ButtonSegment(value: 1, label: Text('Payroll runs')),
                ],
                selected: {_tab},
                onSelectionChanged: (s) =>
                    setState(() => _tab = s.first),
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
          Expanded(child: _tab == 0 ? _employeesTab(theme) : _runsTab(theme)),
        ],
      ),
    );
  }

  // ---------------- Employees ----------------

  Widget _employeesTab(ThemeData theme) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Text('${_employees.length} employees',
                style: theme.textTheme.titleMedium),
            const Spacer(),
            FilledButton.icon(
              onPressed: _loading ? null : _employeeDialog,
              icon: const Icon(Icons.person_add_outlined, size: 18),
              label: const Text('Add employee'),
            ),
          ],
        ),
        const SizedBox(height: Space.sm),
        Expanded(
          child: _employees.isEmpty
              ? Center(
                  child: Text(_loading ? 'Loading…' : 'No employees yet',
                      style: theme.textTheme.bodyMedium?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant)))
              : ListView.separated(
                  itemCount: _employees.length,
                  separatorBuilder: (_, __) => const Divider(height: 1),
                  itemBuilder: (context, i) {
                    final e = _employees[i];
                    return ListTile(
                      title: Text('${e['name']}',
                          style: theme.textTheme.titleSmall),
                      subtitle: Text(
                        '${e['employeeCode']} · ${e['designation'] ?? '—'} · '
                        '${e['department'] ?? '—'}',
                        style: theme.textTheme.bodySmall,
                      ),
                      trailing: Text('Basic ₹${e['basicSalary']}',
                          style: theme.textTheme.bodyMedium),
                    );
                  },
                ),
        ),
      ],
    );
  }

  Future<void> _employeeDialog() async {
    final name = TextEditingController();
    final designation = TextEditingController();
    final department = TextEditingController();
    final basic = TextEditingController();
    final hra = TextEditingController();
    final allowances = TextEditingController();
    final pt = TextEditingController();
    final tds = TextEditingController();
    var pf = true;
    var esi = true;

    final proceed = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: const Text('Add employee'),
          content: ConstrainedBox(
            constraints:
                const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
            child: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  _field(name, 'Name *'),
                  _field(designation, 'Designation'),
                  _field(department, 'Department'),
                  _field(basic, 'Basic salary *', number: true),
                  _field(hra, 'HRA', number: true),
                  _field(allowances, 'Other allowances', number: true),
                  _field(pt, 'Professional tax (monthly)', number: true),
                  _field(tds, 'TDS (monthly)', number: true),
                  CheckboxListTile(
                    contentPadding: EdgeInsets.zero,
                    title: const Text('PF applicable'),
                    value: pf,
                    onChanged: (v) => setDialogState(() => pf = v ?? true),
                  ),
                  CheckboxListTile(
                    contentPadding: EdgeInsets.zero,
                    title: const Text('ESI applicable'),
                    value: esi,
                    onChanged: (v) => setDialogState(() => esi = v ?? true),
                  ),
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
      ),
    );
    if (proceed != true) return;
    if (name.text.trim().isEmpty) {
      setState(() => _error = 'Employee name is required');
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
      final e = await api.post<Map<String, dynamic>>(
        '/api/v1/payroll/employees',
        {
          'name': name.text.trim(),
          if (designation.text.trim().isNotEmpty)
            'designation': designation.text.trim(),
          if (department.text.trim().isNotEmpty)
            'department': department.text.trim(),
          'basicSalary': double.tryParse(basic.text.trim()) ?? 0,
          'hra': double.tryParse(hra.text.trim()) ?? 0,
          'otherAllowances': double.tryParse(allowances.text.trim()) ?? 0,
          'professionalTax': double.tryParse(pt.text.trim()) ?? 0,
          'monthlyTds': double.tryParse(tds.text.trim()) ?? 0,
          'pfApplicable': pf,
          'esiApplicable': esi,
        },
        fromJson: (json) => json as Map<String, dynamic>,
      );
      setState(() => _info = 'Employee ${e['employeeCode']} added');
      await _loadEmployees();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  // ---------------- Runs ----------------

  Widget _runsTab(ThemeData theme) {
    if (_selectedRun != null) return _runDetail(theme, _selectedRun!);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Text('${_runs.length} runs', style: theme.textTheme.titleMedium),
            const Spacer(),
            FilledButton.icon(
              onPressed: _loading ? null : _createRunDialog,
              icon: const Icon(Icons.add, size: 18),
              label: const Text('New run'),
            ),
          ],
        ),
        const SizedBox(height: Space.sm),
        Expanded(
          child: _runs.isEmpty
              ? Center(
                  child: Text(_loading ? 'Loading…' : 'No payroll runs yet',
                      style: theme.textTheme.bodyMedium?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant)))
              : ListView.separated(
                  itemCount: _runs.length,
                  separatorBuilder: (_, __) => const Divider(height: 1),
                  itemBuilder: (context, i) {
                    final r = _runs[i];
                    return ListTile(
                      onTap: () => _openRun(r['id'] as int),
                      title: Row(
                        children: [
                          Text('${r['period']}',
                              style: theme.textTheme.titleSmall),
                          const SizedBox(width: Space.sm),
                          StatusChip.auto('${r['status']}'),
                          if (r['statutoryPaid'] == true) ...[
                            const SizedBox(width: Space.xs),
                            StatusChip.auto('STAT PAID'),
                          ],
                        ],
                      ),
                      subtitle: Text(
                        '${r['employeeCount']} employees · '
                        'net ₹${r['totalNet']}',
                        style: theme.textTheme.bodySmall,
                      ),
                      trailing: const Icon(Icons.chevron_right),
                    );
                  },
                ),
        ),
      ],
    );
  }

  Widget _runDetail(ThemeData theme, Map<String, dynamic> run) {
    final status = '${run['status']}';
    final statutoryPaid = run['statutoryPaid'] == true;
    final payslips =
        List<Map<String, dynamic>>.from(run['payslips'] as List? ?? const []);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            TextButton.icon(
              onPressed: () => setState(() => _selectedRun = null),
              icon: const Icon(Icons.arrow_back, size: 18),
              label: const Text('Back'),
            ),
            const Spacer(),
          ],
        ),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(Space.lg),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Text('${run['period']}',
                        style: theme.textTheme.titleMedium),
                    const SizedBox(width: Space.sm),
                    StatusChip.auto(status),
                    if (statutoryPaid) ...[
                      const SizedBox(width: Space.xs),
                      StatusChip.auto('STAT PAID'),
                    ],
                    const Spacer(),
                    if (status == 'DRAFT')
                      FilledButton(
                        onPressed: _loading
                            ? null
                            : () => _runAction(run['id'] as int, 'approve',
                                'Payroll approved & posted'),
                        child: const Text('Approve'),
                      ),
                    if (status == 'APPROVED') ...[
                      FilledButton(
                        onPressed: _loading
                            ? null
                            : () => _runAction(
                                run['id'] as int, 'pay', 'Net salaries paid'),
                        child: const Text('Pay salaries'),
                      ),
                      const SizedBox(width: Space.sm),
                    ],
                    if ((status == 'APPROVED' || status == 'PAID') &&
                        !statutoryPaid)
                      OutlinedButton(
                        onPressed: _loading
                            ? null
                            : () => _runAction(run['id'] as int,
                                'pay-statutory', 'Statutory dues remitted'),
                        child: const Text('Remit statutory'),
                      ),
                  ],
                ),
                const SizedBox(height: Space.sm),
                Wrap(
                  spacing: Space.lg,
                  children: [
                    _stat(theme, 'Employees', '${run['employeeCount']}'),
                    _stat(theme, 'Gross', '₹${run['totalGross']}'),
                    _stat(theme, 'Deductions', '₹${run['totalDeductions']}'),
                    _stat(theme, 'Net', '₹${run['totalNet']}'),
                  ],
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: Space.md),
        Text('Payslips', style: theme.textTheme.titleMedium),
        const SizedBox(height: Space.sm),
        Expanded(
          child: payslips.isEmpty
              ? Center(
                  child: Text('No payslips',
                      style: theme.textTheme.bodyMedium?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant)))
              : ListView.separated(
                  itemCount: payslips.length,
                  separatorBuilder: (_, __) => const Divider(height: 1),
                  itemBuilder: (context, i) {
                    final p = payslips[i];
                    return ListTile(
                      title: Text('${p['employeeName']}',
                          style: theme.textTheme.titleSmall),
                      subtitle: Text(
                        'Gross ₹${p['gross']} · PF ₹${p['pf']} · '
                        'ESI ₹${p['esi']} · PT ₹${p['professionalTax']} · '
                        'TDS ₹${p['tds']}',
                        style: theme.textTheme.bodySmall,
                      ),
                      trailing: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Text('Net ₹${p['netPay']}',
                              style: theme.textTheme.titleSmall),
                          const SizedBox(width: Space.sm),
                          IconButton(
                            tooltip: 'Open payslip PDF',
                            onPressed: () => _openPayslipPdf(run, p),
                            icon: const Icon(Icons.picture_as_pdf_outlined,
                                size: 20),
                          ),
                        ],
                      ),
                    );
                  },
                ),
        ),
      ],
    );
  }

  Future<void> _openPayslipPdf(
      Map<String, dynamic> run, Map<String, dynamic> p) async {
    await openPdf(
      context,
      context.read<ApiClient>(),
      '/api/v1/payroll/runs/${run['id']}/payslips/${p['employeeId']}.pdf',
      filename: 'payslip-${run['id']}-${p['employeeId']}.pdf',
    );
  }

  Future<void> _createRunDialog() async {
    final now = DateTime.now();
    final year = TextEditingController(text: '${now.year}');
    final month = TextEditingController(text: '${now.month}');
    final proceed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('New payroll run'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            _field(year, 'Year *', number: true),
            _field(month, 'Month (1-12) *', number: true),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Create'),
          ),
        ],
      ),
    );
    if (proceed != true) return;
    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });
    try {
      if (!mounted) return;
      final api = context.read<ApiClient>();
      final run = await api.post<Map<String, dynamic>>(
        '/api/v1/payroll/runs',
        {
          'year': int.tryParse(year.text.trim()) ?? now.year,
          'month': int.tryParse(month.text.trim()) ?? now.month,
        },
        fromJson: (json) => json as Map<String, dynamic>,
      );
      setState(() => _info = 'Run ${run['period']} created');
      await _loadRuns();
      await _openRun(run['id'] as int);
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
