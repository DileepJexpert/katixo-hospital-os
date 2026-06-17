import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/auth/auth_state.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/empty_state.dart';
import '../../core/widgets/status_chip.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;
import '../patient/patient_picker.dart';
import '../staff/doctor_picker.dart';

/// Radiology orders + reporting (mirrors the lab flow): order a study, mark it
/// performed, file the report (findings + impression). Body widget. DOCTOR/ADMIN
/// order + report; LAB_TECH marks performed; clinical roles view.
class RadiologyScreen extends StatefulWidget {
  const RadiologyScreen({super.key});

  @override
  State<RadiologyScreen> createState() => _RadiologyScreenState();
}

class _RadiologyScreenState extends State<RadiologyScreen> {
  static const _modalities = <String>[
    'XRAY', 'CT', 'MRI', 'ULTRASOUND', 'MAMMOGRAPHY', 'FLUOROSCOPY', 'OTHER',
  ];

  List<Map<String, dynamic>> _orders = const [];
  bool _loading = false;
  String? _error;
  String? _info;

  String get _role => context.read<AuthState>().currentUser?.role ?? '';
  bool get _isDoctor => _role == 'DOCTOR' || _role == 'ADMIN' || _role == 'SUPER_ADMIN';
  bool get _canPerform =>
      _role == 'LAB_TECH' || _isDoctor;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      final list = await api.get<List<Map<String, dynamic>>>(
        '/api/v1/radiology/orders?limit=50',
        fromJson: (j) => List<Map<String, dynamic>>.from(j as List? ?? const []),
      );
      if (mounted) setState(() => _orders = list);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Could not load radiology orders: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _act(Future<void> Function() action, String okMsg) async {
    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });
    try {
      await action();
      setState(() => _info = okMsg);
      await _load();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Action failed: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _orderDialog() async {
    final patient = await showPatientPicker(context);
    if (patient == null) return;
    final doctor = await showDoctorPicker(context);
    if (doctor == null) return;
    String modality = 'XRAY';
    final studyCtrl = TextEditingController();
    final notesCtrl = TextEditingController();

    final proceed = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setLocal) => AlertDialog(
          title: const Text('Order radiology study'),
          content: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text('${patient['fullName']} · referred by Dr ${doctor['name']}',
                    style: Theme.of(context).textTheme.bodySmall),
                const SizedBox(height: Space.sm),
                DropdownButtonFormField<String>(
                  value: modality,
                  decoration: const InputDecoration(labelText: 'Modality'),
                  items: [
                    for (final m in _modalities)
                      DropdownMenuItem(value: m, child: Text(m)),
                  ],
                  onChanged: (v) => setLocal(() => modality = v ?? 'XRAY'),
                ),
                const SizedBox(height: Space.sm),
                TextField(
                  controller: studyCtrl,
                  decoration: const InputDecoration(labelText: 'Study *', hintText: 'e.g. CT Head plain'),
                ),
                TextField(
                  controller: notesCtrl,
                  decoration: const InputDecoration(labelText: 'Clinical notes'),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
            FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Order')),
          ],
        ),
      ),
    );
    if (proceed != true) return;
    if (studyCtrl.text.trim().isEmpty) {
      setState(() => _error = 'Study name is required');
      return;
    }
    await _act(() async {
      final api = context.read<ApiClient>();
      await api.post<dynamic>('/api/v1/radiology/orders', {
        'patientId': patient['id'],
        'referringDoctorId': doctor['id'],
        'modality': modality,
        'studyName': studyCtrl.text.trim(),
        'notes': notesCtrl.text.trim(),
      }, fromJson: (j) => j);
    }, 'Study ordered');
  }

  Future<void> _reportDialog(Map<String, dynamic> o) async {
    final findingsCtrl = TextEditingController(text: '${o['findings'] ?? ''}');
    final impressionCtrl = TextEditingController(text: '${o['impression'] ?? ''}');
    final proceed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Report — ${o['studyName']}'),
        content: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: findingsCtrl,
                  maxLines: 4,
                  decoration: const InputDecoration(labelText: 'Findings'),
                ),
                const SizedBox(height: Space.sm),
                TextField(
                  controller: impressionCtrl,
                  maxLines: 3,
                  decoration: const InputDecoration(labelText: 'Impression *'),
                ),
              ],
            ),
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('File report')),
        ],
      ),
    );
    if (proceed != true) return;
    if (impressionCtrl.text.trim().isEmpty) {
      setState(() => _error = 'An impression is required');
      return;
    }
    await _act(() async {
      final api = context.read<ApiClient>();
      await api.post<dynamic>('/api/v1/radiology/orders/${o['id']}/report',
          {'findings': findingsCtrl.text.trim(), 'impression': impressionCtrl.text.trim()},
          fromJson: (j) => j);
    }, 'Report filed');
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
              Text('Radiology', style: theme.textTheme.titleLarge),
              const Spacer(),
              IconButton(
                tooltip: 'Refresh',
                onPressed: _loading ? null : _load,
                icon: const Icon(Icons.refresh),
              ),
              if (_isDoctor) ...[
                const SizedBox(width: Space.sm),
                FilledButton.icon(
                  onPressed: _loading ? null : _orderDialog,
                  icon: const Icon(Icons.add, size: 18),
                  label: const Text('Order study'),
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
          Expanded(
            child: _orders.isEmpty
                ? EmptyState(
                    icon: Icons.radio_button_checked_outlined,
                    title: _loading ? 'Loading…' : 'No radiology orders',
                    message: _isDoctor ? 'Order a study with "Order study".' : 'No studies yet.')
                : Card(
                    child: ListView.separated(
                      itemCount: _orders.length,
                      separatorBuilder: (_, __) => const Divider(height: 1),
                      itemBuilder: (context, i) => _tile(theme, _orders[i]),
                    ),
                  ),
          ),
        ],
      ),
    );
  }

  Widget _tile(ThemeData theme, Map<String, dynamic> o) {
    final status = '${o['radiologyStatus']}';
    return ExpansionTile(
      leading: const Icon(Icons.scanner_outlined),
      title: Row(
        children: [
          Flexible(child: Text('${o['orderNumber']} · ${o['studyName']}', style: theme.textTheme.titleSmall)),
          const SizedBox(width: Space.sm),
          StatusChip.auto(status),
        ],
      ),
      subtitle: Text(
        '${o['modality']} · patient #${o['patientId']} · ${o['orderDate'] ?? ''}',
        style: theme.textTheme.bodySmall,
      ),
      childrenPadding: const EdgeInsets.fromLTRB(Space.lg, 0, Space.lg, Space.md),
      expandedCrossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if ((o['notes'] ?? '').toString().isNotEmpty)
          Text('Clinical: ${o['notes']}', style: theme.textTheme.bodySmall),
        if ((o['impression'] ?? '').toString().isNotEmpty) ...[
          const SizedBox(height: Space.xs),
          Text('Findings: ${o['findings'] ?? '—'}', style: theme.textTheme.bodyMedium),
          Text('Impression: ${o['impression']}',
              style: theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w600)),
        ],
        const SizedBox(height: Space.sm),
        Wrap(
          spacing: Space.sm,
          children: [
            if (status == 'ORDERED' && _canPerform)
              OutlinedButton(
                onPressed: _loading
                    ? null
                    : () => _act(() async {
                          await context.read<ApiClient>().post<dynamic>(
                              '/api/v1/radiology/orders/${o['id']}/performed', const {}, fromJson: (j) => j);
                        }, 'Marked performed'),
                child: const Text('Mark performed'),
              ),
            if ((status == 'ORDERED' || status == 'PERFORMED') && _isDoctor)
              FilledButton(
                onPressed: _loading ? null : () => _reportDialog(o),
                child: const Text('File report'),
              ),
            if ((status == 'ORDERED' || status == 'PERFORMED') && _isDoctor)
              OutlinedButton(
                onPressed: _loading
                    ? null
                    : () => _act(() async {
                          await context.read<ApiClient>().post<dynamic>(
                              '/api/v1/radiology/orders/${o['id']}/cancel', const {}, fromJson: (j) => j);
                        }, 'Order cancelled'),
                child: const Text('Cancel'),
              ),
          ],
        ),
      ],
    );
  }
}
