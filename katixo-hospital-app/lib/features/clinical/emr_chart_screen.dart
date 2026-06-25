import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/auth/auth_state.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/util/pdf_actions.dart';
import '../../core/widgets/empty_state.dart';
import '../../core/widgets/section_card.dart';
import '../../core/widgets/status_chip.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;
import '../patient/patient_picker.dart';

/// EMR chart — the clinical workspace for one patient encounter: structured SOAP
/// notes + CPOE order entry with the CDS gate (a CRITICAL alert blocks placement
/// until an override reason is given). Surfaces the `clinical/` backend
/// (Encounter / ClinicalNote / ClinicalOrder + CDS) that was previously API-only.
class EmrChartScreen extends StatefulWidget {
  const EmrChartScreen({super.key, this.patientId, this.patientName});

  final int? patientId;
  final String? patientName;

  @override
  State<EmrChartScreen> createState() => _EmrChartScreenState();
}

class _EmrChartScreenState extends State<EmrChartScreen> {
  int? _patientId;
  String _patientName = '';
  bool _loading = false;
  String? _error;
  String? _info;

  Map<String, dynamic>? _encounter; // current OPEN encounter
  List<Map<String, dynamic>> _notes = [];
  List<Map<String, dynamic>> _orders = [];
  List<Map<String, dynamic>> _vitals = [];

  // open-encounter
  final _ccCtrl = TextEditingController();
  // note
  String _noteType = 'SOAP';
  final _subj = TextEditingController();
  final _obj = TextEditingController();
  final _assess = TextEditingController();
  final _plan = TextEditingController();
  // order
  String _ordType = 'LAB';
  String _ordPriority = 'ROUTINE';
  final _ordName = TextEditingController();
  final _ordCode = TextEditingController();
  final _ordInstr = TextEditingController();

  int? get _doctorId => context.read<AuthState>().currentUser?.staffId;

  @override
  void initState() {
    super.initState();
    _patientId = widget.patientId;
    _patientName = widget.patientName ?? '';
    if (_patientId != null) {
      WidgetsBinding.instance.addPostFrameCallback((_) => _loadEncounter());
    }
  }

  @override
  void dispose() {
    for (final c in [_ccCtrl, _subj, _obj, _assess, _plan, _ordName, _ordCode, _ordInstr]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _pick() async {
    final p = await showPatientPicker(context);
    if (p == null) return;
    setState(() {
      _patientId = (p['id'] as num?)?.toInt();
      _patientName = '${p['fullName'] ?? '${p['firstName'] ?? ''} ${p['lastName'] ?? ''}'}'.trim();
      _encounter = null;
      _notes = [];
      _orders = [];
    });
    await _loadEncounter();
  }

  Future<void> _loadEncounter() async {
    if (_patientId == null) return;
    setState(() { _loading = true; _error = null; });
    try {
      final api = context.read<ApiClient>();
      final list = await api.get<List<Map<String, dynamic>>>(
        '/api/v1/clinical/patients/$_patientId/encounters',
        fromJson: (j) => List<Map<String, dynamic>>.from(j as List? ?? const []),
      );
      Map<String, dynamic>? open;
      for (final e in list) {
        if ('${e['encounterStatus']}' == 'OPEN') { open = e; break; }
      }
      _encounter = open;
      if (open != null) {
        await _loadNotesOrders((open['id'] as num).toInt());
      }
      await _loadVitals();
    } on ApiException catch (e) {
      _error = e.error.message;
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _loadNotesOrders(int encId) async {
    final api = context.read<ApiClient>();
    final notes = await api.get<List<Map<String, dynamic>>>(
      '/api/v1/clinical/encounters/$encId/notes',
      fromJson: (j) => List<Map<String, dynamic>>.from(j as List? ?? const []),
    );
    final orders = await api.get<List<Map<String, dynamic>>>(
      '/api/v1/clinical/encounters/$encId/orders',
      fromJson: (j) => List<Map<String, dynamic>>.from(j as List? ?? const []),
    );
    if (mounted) setState(() { _notes = notes; _orders = orders; });
  }

  Future<void> _loadVitals() async {
    if (_patientId == null) return;
    final api = context.read<ApiClient>();
    final v = await api.get<List<Map<String, dynamic>>>(
      '/api/v1/nursing/vitals?patientId=$_patientId&limit=5',
      fromJson: (j) => List<Map<String, dynamic>>.from(j as List? ?? const []),
    );
    if (mounted) setState(() => _vitals = v);
  }

  String _vitalSummary(Map<String, dynamic> v) {
    final parts = <String>[];
    if (v['temperatureCelsius'] != null) parts.add('T ${v['temperatureCelsius']}°C');
    if (v['pulseBpm'] != null) parts.add('P ${v['pulseBpm']}');
    if (v['systolicBp'] != null || v['diastolicBp'] != null) {
      parts.add('BP ${v['systolicBp'] ?? '—'}/${v['diastolicBp'] ?? '—'}');
    }
    if (v['spo2'] != null) parts.add('SpO₂ ${v['spo2']}%');
    if (v['respiratoryRate'] != null) parts.add('RR ${v['respiratoryRate']}');
    if (v['weightKg'] != null) parts.add('Wt ${v['weightKg']}kg');
    return parts.isEmpty ? '—' : parts.join(' · ');
  }

  Future<void> _run(Future<void> Function() action, String okMsg) async {
    setState(() { _loading = true; _error = null; _info = null; });
    try {
      await action();
      setState(() => _info = okMsg);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Failed: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _openEncounter() => _run(() async {
        final api = context.read<ApiClient>();
        await api.post<Map<String, dynamic>>('/api/v1/clinical/encounters', {
          'patientId': _patientId,
          'encounterType': 'OPD',
          'sourceType': 'STANDALONE',
          'attendingDoctorId': _doctorId,
          'chiefComplaint': _ccCtrl.text.trim(),
        }, fromJson: (j) => Map<String, dynamic>.from(j as Map));
        _ccCtrl.clear();
        await _loadEncounter();
      }, 'Encounter opened');

  Future<void> _closeEncounter() => _run(() async {
        final api = context.read<ApiClient>();
        await api.post<Map<String, dynamic>>(
            '/api/v1/clinical/encounters/${_encounter!['id']}/close', const {},
            fromJson: (j) => Map<String, dynamic>.from(j as Map? ?? const {}));
        await _loadEncounter();
      }, 'Encounter closed');

  Future<void> _saveNote() {
    if (_subj.text.trim().isEmpty && _obj.text.trim().isEmpty &&
        _assess.text.trim().isEmpty && _plan.text.trim().isEmpty) {
      setState(() => _error = 'Enter at least one note field');
      return Future.value();
    }
    return _run(() async {
      final api = context.read<ApiClient>();
      await api.post<Map<String, dynamic>>(
          '/api/v1/clinical/encounters/${_encounter!['id']}/notes', {
        'noteType': _noteType,
        'subjective': _subj.text.trim(),
        'objective': _obj.text.trim(),
        'assessment': _assess.text.trim(),
        'plan': _plan.text.trim(),
      }, fromJson: (j) => Map<String, dynamic>.from(j as Map));
      for (final c in [_subj, _obj, _assess, _plan]) {
        c.clear();
      }
      await _loadNotesOrders((_encounter!['id'] as num).toInt());
    }, 'Note saved');
  }

  Future<void> _placeOrder({String? overrideReason}) async {
    if (_ordName.text.trim().isEmpty) {
      setState(() => _error = 'Order name is required');
      return;
    }
    setState(() { _loading = true; _error = null; _info = null; });
    try {
      final api = context.read<ApiClient>();
      final res = await api.post<Map<String, dynamic>>(
          '/api/v1/clinical/encounters/${_encounter!['id']}/orders', {
        'orderType': _ordType,
        'code': _ordCode.text.trim(),
        'name': _ordName.text.trim(),
        'priority': _ordPriority,
        'instructions': _ordInstr.text.trim(),
        'overrideReason': overrideReason,
      }, fromJson: (j) => Map<String, dynamic>.from(j as Map));
      final alerts = (res['alerts'] as List? ?? const [])
          .map((a) => '${(a as Map)['severity']}: ${a['message']}')
          .join(' · ');
      for (final c in [_ordName, _ordCode, _ordInstr]) {
        c.clear();
      }
      await _loadNotesOrders((_encounter!['id'] as num).toInt());
      setState(() => _info = alerts.isEmpty ? 'Order placed' : 'Order placed — $alerts');
    } on ApiException catch (e) {
      if (e.error.error == 'CDS_BLOCKED') {
        await _cdsOverrideDialog(e.error.message);
      } else {
        setState(() => _error = e.error.message);
      }
    } catch (e) {
      setState(() => _error = 'Failed: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _cdsOverrideDialog(String message) async {
    final reason = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        icon: const Icon(Icons.warning_amber_rounded, color: StatusColors.danger, size: 32),
        title: const Text('Clinical safety alert'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(message),
            const SizedBox(height: Space.md),
            const Text('To place this order anyway, record a clinical reason. '
                'The override is audited.'),
            const SizedBox(height: Space.sm),
            TextField(
              controller: reason,
              decoration: const InputDecoration(labelText: 'Override reason *'),
            ),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Override & place')),
        ],
      ),
    );
    if (ok == true && reason.text.trim().isNotEmpty) {
      await _placeOrder(overrideReason: reason.text.trim());
    }
  }

  Future<void> _setOrderStatus(Map<String, dynamic> order, String status) => _run(() async {
        final api = context.read<ApiClient>();
        await api.put<Map<String, dynamic>>(
            '/api/v1/clinical/orders/${order['id']}/status', {'status': status},
            fromJson: (j) => Map<String, dynamic>.from(j as Map? ?? const {}));
        await _loadNotesOrders((_encounter!['id'] as num).toInt());
      }, 'Order updated');

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return PageContainer(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text('EMR Chart', style: theme.textTheme.titleLarge),
              const SizedBox(width: Space.md),
              if (_patientName.isNotEmpty)
                Expanded(child: Text(_patientName, style: theme.textTheme.titleMedium, overflow: TextOverflow.ellipsis)),
              OutlinedButton.icon(
                onPressed: _loading ? null : _pick,
                icon: const Icon(Icons.person_search_outlined, size: 18),
                label: Text(_patientId == null ? 'Select patient' : 'Change'),
              ),
            ],
          ),
          const SizedBox(height: Space.md),
          if (_error != null) ...[MessageBanner.error(_error!), const SizedBox(height: Space.sm)],
          if (_info != null) ...[MessageBanner.success(_info!), const SizedBox(height: Space.sm)],
          if (_patientId == null)
            const Expanded(
              child: EmptyState(
                icon: Icons.assignment_ind_outlined,
                title: 'No patient selected',
                message: 'Pick a patient to open their clinical chart.',
              ),
            )
          else if (_encounter == null)
            _openEncounterCard(theme)
          else
            Expanded(child: ListView(children: _chart(theme))),
        ],
      ),
    );
  }

  Widget _openEncounterCard(ThemeData theme) {
    return SectionCard(
      title: 'No open encounter',
      icon: Icons.note_add_outlined,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Open an encounter to start charting for $_patientName.',
              style: theme.textTheme.bodyMedium),
          const SizedBox(height: Space.sm),
          TextField(
            controller: _ccCtrl,
            decoration: const InputDecoration(labelText: 'Chief complaint'),
          ),
          const SizedBox(height: Space.sm),
          Align(
            alignment: Alignment.centerRight,
            child: FilledButton.icon(
              onPressed: _loading ? null : _openEncounter,
              icon: const Icon(Icons.add, size: 18),
              label: const Text('Open encounter'),
            ),
          ),
        ],
      ),
    );
  }

  List<Widget> _chart(ThemeData theme) {
    final enc = _encounter!;
    return [
      SectionCard(
        title: 'Encounter',
        icon: Icons.event_note_outlined,
        action: Row(mainAxisSize: MainAxisSize.min, children: [
          TextButton.icon(
            onPressed: () => openPdf(context, context.read<ApiClient>(),
                '/api/v1/clinical/encounters/${enc['id']}/summary.pdf',
                filename: 'encounter-${enc['id']}.pdf'),
            icon: const Icon(Icons.picture_as_pdf_outlined, size: 18),
            label: const Text('Summary PDF'),
          ),
          if ('${enc['encounterStatus']}' == 'OPEN')
            TextButton.icon(
              onPressed: _loading ? null : _closeEncounter,
              icon: const Icon(Icons.lock_outline, size: 18),
              label: const Text('Close'),
            ),
        ]),
        child: Wrap(spacing: Space.xl, runSpacing: Space.sm, crossAxisAlignment: WrapCrossAlignment.center, children: [
          StatusChip.auto('${enc['encounterStatus']}'),
          Text('${enc['encounterType'] ?? ''}', style: theme.textTheme.bodyMedium),
          if ((enc['chiefComplaint'] ?? '').toString().isNotEmpty)
            Text('CC: ${enc['chiefComplaint']}', style: theme.textTheme.bodyMedium),
        ]),
      ),
      const SizedBox(height: Space.md),
      _vitalsCard(theme),
      const SizedBox(height: Space.md),
      _noteCard(theme),
      const SizedBox(height: Space.md),
      _orderCard(theme),
    ];
  }

  Widget _vitalsCard(ThemeData theme) {
    return SectionCard(
      title: 'Recent vitals',
      icon: Icons.monitor_heart_outlined,
      child: _vitals.isEmpty
          ? Text('No vitals recorded.',
              style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant))
          : Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                for (final v in _vitals)
                  Padding(
                    padding: const EdgeInsets.only(bottom: Space.xs),
                    child: Text(
                      '${('${v['recordedAt'] ?? ''}').replaceFirst('T', ' ')}  ·  ${_vitalSummary(v)}',
                      style: theme.textTheme.bodyMedium,
                    ),
                  ),
              ],
            ),
    );
  }

  Widget _noteCard(ThemeData theme) {
    return SectionCard(
      title: 'Clinical note (SOAP)',
      icon: Icons.description_outlined,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          DropdownButtonFormField<String>(
            initialValue: _noteType,
            decoration: const InputDecoration(labelText: 'Note type'),
            items: const [
              DropdownMenuItem(value: 'SOAP', child: Text('SOAP')),
              DropdownMenuItem(value: 'PROGRESS', child: Text('Progress')),
              DropdownMenuItem(value: 'NURSING', child: Text('Nursing')),
              DropdownMenuItem(value: 'PROCEDURE', child: Text('Procedure')),
            ],
            onChanged: (v) => setState(() => _noteType = v ?? 'SOAP'),
          ),
          const SizedBox(height: Space.sm),
          _multiline(_subj, 'Subjective'),
          _multiline(_obj, 'Objective'),
          _multiline(_assess, 'Assessment (+ diagnosis)'),
          _multiline(_plan, 'Plan'),
          Align(
            alignment: Alignment.centerRight,
            child: FilledButton(onPressed: _loading ? null : _saveNote, child: const Text('Save note')),
          ),
          if (_notes.isNotEmpty) const Divider(height: Space.xl),
          for (final n in _notes)
            Padding(
              padding: const EdgeInsets.only(bottom: Space.sm),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('${n['noteType']} · v${n['version']}  ${n['authorName'] ?? ''}',
                      style: theme.textTheme.labelMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
                  if ((n['subjective'] ?? '').toString().isNotEmpty) Text('S: ${n['subjective']}'),
                  if ((n['objective'] ?? '').toString().isNotEmpty) Text('O: ${n['objective']}'),
                  if ((n['assessment'] ?? '').toString().isNotEmpty) Text('A: ${n['assessment']}'),
                  if ((n['plan'] ?? '').toString().isNotEmpty) Text('P: ${n['plan']}'),
                ],
              ),
            ),
        ],
      ),
    );
  }

  Widget _orderCard(ThemeData theme) {
    return SectionCard(
      title: 'Orders (CPOE)',
      icon: Icons.playlist_add_outlined,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(children: [
            Expanded(
              child: DropdownButtonFormField<String>(
                initialValue: _ordType,
                decoration: const InputDecoration(labelText: 'Type'),
                items: const [
                  DropdownMenuItem(value: 'LAB', child: Text('Lab')),
                  DropdownMenuItem(value: 'RADIOLOGY', child: Text('Radiology')),
                  DropdownMenuItem(value: 'PHARMACY', child: Text('Pharmacy')),
                  DropdownMenuItem(value: 'PROCEDURE', child: Text('Procedure')),
                  DropdownMenuItem(value: 'NURSING', child: Text('Nursing')),
                ],
                onChanged: (v) => setState(() => _ordType = v ?? 'LAB'),
              ),
            ),
            const SizedBox(width: Space.sm),
            Expanded(
              child: DropdownButtonFormField<String>(
                initialValue: _ordPriority,
                decoration: const InputDecoration(labelText: 'Priority'),
                items: const [
                  DropdownMenuItem(value: 'ROUTINE', child: Text('Routine')),
                  DropdownMenuItem(value: 'URGENT', child: Text('Urgent')),
                  DropdownMenuItem(value: 'STAT', child: Text('STAT')),
                ],
                onChanged: (v) => setState(() => _ordPriority = v ?? 'ROUTINE'),
              ),
            ),
          ]),
          const SizedBox(height: Space.sm),
          TextField(controller: _ordName, decoration: const InputDecoration(labelText: 'Order name *')),
          const SizedBox(height: Space.sm),
          TextField(controller: _ordCode, decoration: const InputDecoration(labelText: 'Code (optional)')),
          const SizedBox(height: Space.sm),
          TextField(controller: _ordInstr, decoration: const InputDecoration(labelText: 'Instructions')),
          Align(
            alignment: Alignment.centerRight,
            child: FilledButton(onPressed: _loading ? null : () => _placeOrder(), child: const Text('Place order')),
          ),
          if (_orders.isNotEmpty) const Divider(height: Space.xl),
          for (final o in _orders)
            ListTile(
              contentPadding: EdgeInsets.zero,
              dense: true,
              title: Text('${o['orderType']} · ${o['name']}'),
              subtitle: Text('${o['priority'] ?? ''}'
                  '${(o['cdsOverrideReason'] ?? '').toString().isNotEmpty ? '  · override: ${o['cdsOverrideReason']}' : ''}'),
              trailing: Row(mainAxisSize: MainAxisSize.min, children: [
                StatusChip.auto('${o['orderStatus']}'),
                PopupMenuButton<String>(
                  onSelected: (s) => _setOrderStatus(o, s),
                  itemBuilder: (_) => const [
                    PopupMenuItem(value: 'IN_PROGRESS', child: Text('Mark in progress')),
                    PopupMenuItem(value: 'COMPLETED', child: Text('Mark completed')),
                    PopupMenuItem(value: 'CANCELLED', child: Text('Cancel order')),
                  ],
                ),
              ]),
            ),
        ],
      ),
    );
  }

  Widget _multiline(TextEditingController c, String label) {
    return Padding(
      padding: const EdgeInsets.only(bottom: Space.sm),
      child: TextField(
        controller: c,
        minLines: 1,
        maxLines: 3,
        decoration: InputDecoration(labelText: label),
      ),
    );
  }
}
