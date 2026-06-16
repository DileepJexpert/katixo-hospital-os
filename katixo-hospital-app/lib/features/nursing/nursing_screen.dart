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

/// Nursing station: ward medicine/consumable indents for inpatients —
/// raise (nurse) → approve/reject (doctor) → dispense (pharmacy, posts a CREDIT
/// pharmacy sale on the patient's AR). Role-aware. Production-grade.
class NursingScreen extends StatefulWidget {
  const NursingScreen({super.key});

  @override
  State<NursingScreen> createState() => _NursingScreenState();
}

class _NursingScreenState extends State<NursingScreen> {
  static const _statuses = ['REQUESTED', 'APPROVED', 'DISPENSED', 'REJECTED', 'CANCELLED'];

  String _filter = 'REQUESTED';
  List<Map<String, dynamic>> _indents = const [];
  List<Map<String, dynamic>> _admissions = const [];
  List<Map<String, dynamic>> _items = const [];
  Map<String, dynamic>? _selected; // indent detail (with items)
  int _requestedCount = 0;
  int _approvedCount = 0;

  bool _loading = false;
  String? _error;
  String? _info;
  String _role = '';

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _role = context.read<AuthState>().currentUser?.role ?? '';
      _filter = _role == 'PHARMACIST' ? 'APPROVED' : 'REQUESTED';
      _loadAll();
    });
  }

  bool get _canCreate => _role == 'NURSE' || _role == 'DOCTOR' || _role == 'ADMIN';
  bool get _canApprove => _role == 'DOCTOR' || _role == 'ADMIN';
  bool get _canCancel => _role == 'NURSE' || _role == 'DOCTOR' || _role == 'ADMIN';
  bool get _canDispense => _role == 'PHARMACIST' || _role == 'ADMIN';

  Future<void> _loadAll() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      List<Map<String, dynamic>> l(dynamic j) =>
          List<Map<String, dynamic>>.from(j as List? ?? const []);
      final indents = await api.get('/api/v1/nursing/indents?status=$_filter', fromJson: l);
      final requested = await api.get('/api/v1/nursing/indents?status=REQUESTED', fromJson: l);
      final approved = await api.get('/api/v1/nursing/indents?status=APPROVED', fromJson: l);
      List<Map<String, dynamic>> adm = const [];
      List<Map<String, dynamic>> items = const [];
      if (_canCreate) {
        adm = await api.get('/api/v1/ipd/admissions?status=ADMITTED', fromJson: l);
        items = await api.get('/api/v1/inventory/items', fromJson: l);
      }
      if (mounted) {
        setState(() {
          _indents = indents;
          _requestedCount = requested.length;
          _approvedCount = approved.length;
          _admissions = adm;
          _items = items;
        });
      }
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _setFilter(String s) async {
    setState(() => _filter = s);
    await _loadAll();
  }

  Future<void> _open(int id) async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      final v = await api.get<Map<String, dynamic>>('/api/v1/nursing/indents/$id',
          fromJson: (j) => j as Map<String, dynamic>);
      if (mounted) setState(() => _selected = v);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  // ---------------- build ----------------

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
              Text('Nursing Station — Ward Indents', style: theme.textTheme.titleLarge),
              const Spacer(),
              IconButton(
                tooltip: 'Refresh',
                onPressed: _loading ? null : _loadAll,
                icon: const Icon(Icons.refresh),
              ),
              if (_canCreate)
                FilledButton.icon(
                  onPressed: _loading || _admissions.isEmpty ? null : _createDialog,
                  icon: const Icon(Icons.add, size: 18),
                  label: const Text('Raise indent'),
                ),
            ],
          ),
          const SizedBox(height: Space.md),
          Wrap(
            spacing: Space.md,
            runSpacing: Space.sm,
            children: [
              _stat(theme, 'Awaiting approval', '$_requestedCount', StatusColors.warning, Icons.pending_actions_outlined),
              _stat(theme, 'Awaiting dispense', '$_approvedCount', StatusColors.info, Icons.local_pharmacy_outlined),
            ],
          ),
          const SizedBox(height: Space.md),
          Wrap(
            spacing: Space.sm,
            children: [
              for (final s in _statuses)
                ChoiceChip(
                  label: Text('${s[0]}${s.substring(1).toLowerCase()}'),
                  selected: _filter == s,
                  onSelected: _loading ? null : (_) => _setFilter(s),
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

  Widget _stat(ThemeData theme, String label, String value, Color color, IconData icon) {
    return Container(
      width: 200,
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

  Widget _list(ThemeData theme) {
    if (_indents.isEmpty) {
      return EmptyState(
        icon: Icons.assignment_outlined,
        title: _loading ? 'Loading…' : 'No ${_filter.toLowerCase()} indents',
        message: _canCreate && _filter == 'REQUESTED'
            ? 'Raise an indent for an inpatient to get started.'
            : null,
      );
    }
    return ListView.separated(
      itemCount: _indents.length,
      separatorBuilder: (_, __) => const SizedBox(height: Space.sm),
      itemBuilder: (context, i) {
        final n = _indents[i];
        return Card(
          child: ListTile(
            onTap: () => _open(n['id'] as int),
            leading: CircleAvatar(
              backgroundColor: theme.colorScheme.primaryContainer,
              child: Icon(Icons.medication_outlined,
                  color: theme.colorScheme.onPrimaryContainer),
            ),
            title: Row(
              children: [
                Text('${n['indentNumber']}', style: theme.textTheme.titleSmall),
                const SizedBox(width: Space.sm),
                StatusChip.auto('${n['status']}'),
              ],
            ),
            subtitle: Text(
              'Admission #${n['admissionId']} · patient #${n['patientId']} · '
              '${n['totalItems']} item(s)'
              '${n['saleTotal'] != null ? ' · ₹${n['saleTotal']}' : ''}',
              style: theme.textTheme.bodySmall,
            ),
            trailing: const Icon(Icons.chevron_right),
          ),
        );
      },
    );
  }

  Widget _detail(ThemeData theme, Map<String, dynamic> n) {
    final id = n['id'] as int;
    final status = '${n['status']}';
    final items = List<Map<String, dynamic>>.from(n['items'] as List? ?? const []);
    return SingleChildScrollView(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          TextButton.icon(
            onPressed: () => setState(() => _selected = null),
            icon: const Icon(Icons.arrow_back, size: 18),
            label: const Text('Back to indents'),
          ),
          SectionCard(
            title: '${n['indentNumber']}',
            icon: Icons.assignment_outlined,
            subtitle: 'Admission #${n['admissionId']} · patient #${n['patientId']}',
            action: Wrap(
              spacing: Space.sm,
              children: [
                StatusChip.auto(status),
                if (status == 'REQUESTED' && _canApprove) ...[
                  FilledButton.icon(
                    onPressed: _loading ? null : () => _act(id, 'approve', const {}, 'Indent approved'),
                    icon: const Icon(Icons.check, size: 18),
                    label: const Text('Approve'),
                  ),
                  OutlinedButton.icon(
                    onPressed: _loading ? null : () => _rejectDialog(id),
                    icon: const Icon(Icons.close, size: 18),
                    label: const Text('Reject'),
                  ),
                ],
                if (status == 'APPROVED' && _canDispense)
                  FilledButton.icon(
                    onPressed: _loading ? null : () => _act(id, 'dispense', const {}, 'Indent dispensed'),
                    icon: const Icon(Icons.local_pharmacy, size: 18),
                    label: const Text('Dispense'),
                  ),
                if ((status == 'REQUESTED' || status == 'APPROVED') && _canCancel)
                  TextButton(
                    onPressed: _loading ? null : () => _act(id, 'cancel', const {}, 'Indent cancelled'),
                    child: const Text('Cancel'),
                  ),
              ],
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (n['notes'] != null && '${n['notes']}'.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.only(bottom: Space.sm),
                    child: Text('Notes: ${n['notes']}', style: theme.textTheme.bodyMedium),
                  ),
                if (status == 'REJECTED' && n['rejectionReason'] != null)
                  Padding(
                    padding: const EdgeInsets.only(bottom: Space.sm),
                    child: Text('Rejected: ${n['rejectionReason']}',
                        style: theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.error)),
                  ),
                if (status == 'DISPENSED')
                  Padding(
                    padding: const EdgeInsets.only(bottom: Space.sm),
                    child: Text('Dispensed as ${n['saleNumber']} · ₹${n['saleTotal']} '
                        '(charged to patient AR)', style: theme.textTheme.bodyMedium),
                  ),
                const Divider(),
                for (final it in items)
                  ListTile(
                    dense: true,
                    contentPadding: EdgeInsets.zero,
                    leading: Icon(_categoryIcon('${it['category']}'), size: 20),
                    title: Text('${it['medicineName']}'),
                    subtitle: Text('${it['medicineCode']} · ${it['category']}',
                        style: theme.textTheme.bodySmall),
                    trailing: Text('×${it['quantity']}', style: theme.textTheme.titleSmall),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  IconData _categoryIcon(String c) => switch (c) {
        'MEDICINE' => Icons.medication_outlined,
        'CONSUMABLE' => Icons.inventory_2_outlined,
        'IMPLANT' => Icons.healing_outlined,
        'NARCOTIC' => Icons.gpp_maybe_outlined,
        _ => Icons.medical_services_outlined,
      };

  // ---------------- actions ----------------

  Future<void> _act(int id, String action, Map<String, dynamic> body, String ok) async {
    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });
    try {
      final api = context.read<ApiClient>();
      await api.post<Map<String, dynamic>>('/api/v1/nursing/indents/$id/$action', body,
          fromJson: (j) => j as Map<String, dynamic>);
      setState(() => _info = ok);
      await _loadAll();
      await _open(id);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _rejectDialog(int id) async {
    final reason = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Reject indent'),
        content: TextField(controller: reason,
            decoration: const InputDecoration(labelText: 'Reason *')),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Reject')),
        ],
      ),
    );
    if (ok != true || reason.text.trim().isEmpty) return;
    await _act(id, 'reject', {'reason': reason.text.trim()}, 'Indent rejected');
  }

  Future<void> _createDialog() async {
    String admissionId = '${_admissions.first['id']}';
    final notes = TextEditingController();
    final cart = <Map<String, dynamic>>[];
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setD) {
          Future<void> addItem() async {
            if (_items.isEmpty) return;
            String code = '${_items.first['code']}';
            final qty = TextEditingController(text: '1');
            String category = 'MEDICINE';
            final added = await showDialog<bool>(
              context: context,
              builder: (context) => StatefulBuilder(
                builder: (context, setI) => AlertDialog(
                  title: const Text('Add item'),
                  content: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      DropdownButtonFormField<String>(
                        value: code,
                        isExpanded: true,
                        decoration: const InputDecoration(labelText: 'Medicine / item *'),
                        items: [
                          for (final it in _items)
                            DropdownMenuItem(value: '${it['code']}',
                                child: Text('${it['name']} (${it['code']})')),
                        ],
                        onChanged: (v) => setI(() => code = v ?? code),
                      ),
                      const SizedBox(height: Space.sm),
                      TextField(controller: qty, keyboardType: TextInputType.number,
                          decoration: const InputDecoration(labelText: 'Quantity *')),
                      const SizedBox(height: Space.sm),
                      DropdownButtonFormField<String>(
                        value: category,
                        decoration: const InputDecoration(labelText: 'Category'),
                        items: const [
                          DropdownMenuItem(value: 'MEDICINE', child: Text('Medicine')),
                          DropdownMenuItem(value: 'CONSUMABLE', child: Text('Consumable')),
                          DropdownMenuItem(value: 'IMPLANT', child: Text('Implant')),
                          DropdownMenuItem(value: 'NARCOTIC', child: Text('Narcotic')),
                        ],
                        onChanged: (v) => setI(() => category = v ?? 'MEDICINE'),
                      ),
                    ],
                  ),
                  actions: [
                    TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
                    FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Add')),
                  ],
                ),
              ),
            );
            if (added != true) return;
            final q = int.tryParse(qty.text.trim()) ?? 0;
            if (q <= 0) return;
            final item = _items.firstWhere((it) => '${it['code']}' == code);
            setD(() => cart.add({
                  'medicineCode': code,
                  'medicineName': '${item['name']}',
                  'quantity': q,
                  'category': category,
                }));
          }

          return AlertDialog(
            title: const Text('Raise ward indent'),
            content: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
              child: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    DropdownButtonFormField<String>(
                      value: admissionId,
                      isExpanded: true,
                      decoration: const InputDecoration(labelText: 'Inpatient (admission) *'),
                      items: [
                        for (final a in _admissions)
                          DropdownMenuItem(
                            value: '${a['id']}',
                            child: Text('${a['admissionNumber']} · patient #${a['patientId']} · bed #${a['currentBedId']}'),
                          ),
                      ],
                      onChanged: (v) => setD(() => admissionId = v ?? admissionId),
                    ),
                    const SizedBox(height: Space.md),
                    Row(
                      children: [
                        Text('Items (${cart.length})', style: Theme.of(context).textTheme.titleSmall),
                        const Spacer(),
                        OutlinedButton.icon(
                          onPressed: addItem,
                          icon: const Icon(Icons.add, size: 18),
                          label: const Text('Add item'),
                        ),
                      ],
                    ),
                    for (var i = 0; i < cart.length; i++)
                      ListTile(
                        dense: true,
                        contentPadding: EdgeInsets.zero,
                        title: Text('${cart[i]['medicineName']} ×${cart[i]['quantity']}'),
                        subtitle: Text('${cart[i]['category']}'),
                        trailing: IconButton(
                          icon: const Icon(Icons.close, size: 18),
                          onPressed: () => setD(() => cart.removeAt(i)),
                        ),
                      ),
                    const SizedBox(height: Space.sm),
                    TextField(controller: notes, decoration: const InputDecoration(labelText: 'Notes')),
                  ],
                ),
              ),
            ),
            actions: [
              TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
              FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Raise')),
            ],
          );
        },
      ),
    );
    if (ok != true) return;
    if (cart.isEmpty) {
      setState(() => _error = 'Add at least one item to the indent');
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });
    try {
      final api = context.read<ApiClient>();
      final created = await api.post<Map<String, dynamic>>(
        '/api/v1/nursing/indents',
        {'admissionId': int.parse(admissionId), 'notes': notes.text.trim(), 'items': cart},
        fromJson: (j) => j as Map<String, dynamic>,
      );
      setState(() => _info = 'Indent ${created['indentNumber']} (${created['status']})');
      await _loadAll();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }
}
