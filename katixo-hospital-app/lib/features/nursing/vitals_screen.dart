import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/auth/auth_state.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/util/validators.dart';
import '../../core/widgets/empty_state.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;
import '../patient/patient_picker.dart';

/// Nursing vitals charting: nurses record a patient's vital signs over time and
/// clinicians view the trend. Can be embedded for a single IPD admission
/// (pass [admissionId] + [patientId]) or stand alone (pick a patient first).
class VitalsScreen extends StatefulWidget {
  const VitalsScreen({super.key, this.patientId, this.admissionId});

  final int? patientId;
  final int? admissionId;

  @override
  State<VitalsScreen> createState() => _VitalsScreenState();
}

class _VitalsScreenState extends State<VitalsScreen> {
  List<Map<String, dynamic>> _vitals = const [];
  bool _loading = false;
  String? _error;
  String? _info;
  String _role = '';

  int? _patientId;
  Map<String, dynamic>? _patient; // picked patient (stand-alone mode)

  @override
  void initState() {
    super.initState();
    _patientId = widget.patientId;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _role = context.read<AuthState>().currentUser?.role ?? '';
      if (_patientId != null || widget.admissionId != null) _load();
    });
  }

  bool get _canEdit => _role == 'NURSE' || _role == 'DOCTOR' || _role == 'ADMIN' || _role == 'SUPER_ADMIN';

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      final params = <String>[];
      if (widget.admissionId != null) params.add('admissionId=${widget.admissionId}');
      if (_patientId != null) params.add('patientId=$_patientId');
      final query = params.isEmpty ? '' : '?${params.join('&')}';
      final list = await api.get<List<Map<String, dynamic>>>(
        '/api/v1/nursing/vitals$query',
        fromJson: (j) => List<Map<String, dynamic>>.from(j as List? ?? const []),
      );
      if (mounted) setState(() => _vitals = list);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _pickPatient() async {
    final p = await showPatientPicker(context);
    if (p == null) return;
    setState(() {
      _patient = p;
      _patientId = p['id'] as int?;
    });
    if (_patientId != null) await _load();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final hasPatient = _patientId != null || widget.admissionId != null;
    return PageContainer(
      scrollable: false,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text('Nursing Vitals', style: theme.textTheme.titleLarge),
              if (widget.admissionId != null) ...[
                const SizedBox(width: Space.sm),
                Text('Admission #${widget.admissionId}',
                    style: theme.textTheme.bodySmall
                        ?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
              ] else if (_patient != null) ...[
                const SizedBox(width: Space.sm),
                Text('${_patient!['fullName'] ?? 'Patient #$_patientId'}',
                    style: theme.textTheme.bodySmall
                        ?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
              ],
              const Spacer(),
              if (widget.patientId == null && widget.admissionId == null)
                OutlinedButton.icon(
                  onPressed: _loading ? null : _pickPatient,
                  icon: const Icon(Icons.person_search_outlined, size: 18),
                  label: Text(_patientId == null ? 'Select patient' : 'Change patient'),
                ),
              const SizedBox(width: Space.sm),
              IconButton(
                tooltip: 'Refresh',
                onPressed: _loading || !hasPatient ? null : _load,
                icon: const Icon(Icons.refresh),
              ),
              if (_canEdit)
                FilledButton.icon(
                  onPressed: _loading || !hasPatient ? null : () => _vitalDialog(),
                  icon: const Icon(Icons.add, size: 18),
                  label: const Text('Record vitals'),
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
          Expanded(child: !hasPatient ? _selectPrompt(theme) : _list(theme)),
        ],
      ),
    );
  }

  Widget _selectPrompt(ThemeData theme) {
    return EmptyState(
      icon: Icons.monitor_heart_outlined,
      title: 'Select a patient',
      message: 'Choose a patient to view and record their vital signs.',
      action: FilledButton.icon(
        onPressed: _pickPatient,
        icon: const Icon(Icons.person_search_outlined, size: 18),
        label: const Text('Select patient'),
      ),
    );
  }

  Widget _list(ThemeData theme) {
    if (_vitals.isEmpty) {
      return EmptyState(
        icon: Icons.monitor_heart_outlined,
        title: _loading ? 'Loading…' : 'No vitals recorded',
        message: _canEdit ? 'Record the first set of vital signs.' : null,
      );
    }
    return ListView.separated(
      itemCount: _vitals.length,
      separatorBuilder: (_, __) => const SizedBox(height: Space.sm),
      itemBuilder: (context, i) {
        final v = _vitals[i];
        return Card(
          child: ListTile(
            leading: CircleAvatar(
              backgroundColor: theme.colorScheme.primaryContainer,
              child: Icon(Icons.monitor_heart_outlined,
                  color: theme.colorScheme.onPrimaryContainer),
            ),
            title: Text(_summary(v), style: theme.textTheme.bodyMedium),
            subtitle: Text(
              '${_formatTime('${v['recordedAt'] ?? ''}')}'
              '${v['recordedByName'] != null ? ' · ${v['recordedByName']}' : ''}'
              '${v['notes'] != null && '${v['notes']}'.isNotEmpty ? '\n${v['notes']}' : ''}',
              style: theme.textTheme.bodySmall,
            ),
            isThreeLine: v['notes'] != null && '${v['notes']}'.isNotEmpty,
            trailing: _canEdit
                ? PopupMenuButton<String>(
                    onSelected: (action) {
                      if (action == 'edit') {
                        _vitalDialog(existing: v);
                      } else if (action == 'delete') {
                        _confirmDelete(v);
                      }
                    },
                    itemBuilder: (_) => const [
                      PopupMenuItem(value: 'edit', child: Text('Edit')),
                      PopupMenuItem(value: 'delete', child: Text('Delete')),
                    ],
                  )
                : null,
          ),
        );
      },
    );
  }

  /// Compact one-line vitals summary, omitting absent values.
  String _summary(Map<String, dynamic> v) {
    final parts = <String>[];
    if (v['temperatureCelsius'] != null) parts.add('T ${v['temperatureCelsius']}°C');
    if (v['pulseBpm'] != null) parts.add('P ${v['pulseBpm']}');
    if (v['systolicBp'] != null || v['diastolicBp'] != null) {
      parts.add('BP ${v['systolicBp'] ?? '—'}/${v['diastolicBp'] ?? '—'}');
    }
    if (v['spo2'] != null) parts.add('SpO₂ ${v['spo2']}%');
    if (v['respiratoryRate'] != null) parts.add('RR ${v['respiratoryRate']}');
    if (v['bloodSugarMgDl'] != null) parts.add('BSL ${v['bloodSugarMgDl']}');
    if (v['weightKg'] != null) parts.add('Wt ${v['weightKg']}kg');
    if (v['painScore'] != null) parts.add('Pain ${v['painScore']}/10');
    return parts.isEmpty ? '—' : parts.join(' · ');
  }

  String _formatTime(String iso) {
    if (iso.isEmpty) return '—';
    return iso.replaceFirst('T', ' ').split('.').first;
  }

  Future<void> _confirmDelete(Map<String, dynamic> v) async {
    final id = v['id'] as int;
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Delete vitals'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('${_formatTime('${v['recordedAt'] ?? ''}')}  ·  ${_summary(v)}',
                style: Theme.of(context).textTheme.bodyMedium),
            const SizedBox(height: Space.sm),
            const Text('Delete this vitals record? This cannot be undone.'),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Delete')),
        ],
      ),
    );
    if (ok != true) return;
    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });
    try {
      final api = context.read<ApiClient>();
      await api.delete<Map<String, dynamic>>('/api/v1/nursing/vitals/$id',
          fromJson: (j) => j as Map<String, dynamic>? ?? const {});
      setState(() => _info = 'Vitals deleted');
      await _load();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _vitalDialog({Map<String, dynamic>? existing}) async {
    final isEdit = existing != null;
    TextEditingController c(String key) =>
        TextEditingController(text: existing?[key]?.toString() ?? '');
    final temp = c('temperatureCelsius');
    final pulse = c('pulseBpm');
    final rr = c('respiratoryRate');
    final sys = c('systolicBp');
    final dia = c('diastolicBp');
    final spo2 = c('spo2');
    final bsl = c('bloodSugarMgDl');
    final weight = c('weightKg');
    final pain = c('painScore');
    final notes = c('notes');

    Widget numField(TextEditingController ctrl, String label, {bool decimal = false}) {
      return Padding(
        padding: const EdgeInsets.only(bottom: Space.sm),
        child: TextField(
          controller: ctrl,
          keyboardType: TextInputType.numberWithOptions(decimal: decimal),
          decoration: InputDecoration(labelText: label),
        ),
      );
    }

    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(isEdit ? 'Edit vitals' : 'Record vitals'),
        content: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                numField(temp, 'Temperature (°C)', decimal: true),
                numField(pulse, 'Pulse (bpm)'),
                numField(rr, 'Respiratory rate'),
                numField(sys, 'Systolic BP'),
                numField(dia, 'Diastolic BP'),
                numField(spo2, 'SpO₂ (%)'),
                numField(bsl, 'Blood sugar (mg/dL)'),
                numField(weight, 'Weight (kg)', decimal: true),
                numField(pain, 'Pain score (0–10)'),
                TextField(
                  controller: notes,
                  decoration: const InputDecoration(labelText: 'Notes'),
                  maxLines: 2,
                ),
              ],
            ),
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: Text(isEdit ? 'Save' : 'Record')),
        ],
      ),
    );
    if (ok != true) return;

    num? n(TextEditingController ctrl) {
      final t = ctrl.text.trim();
      if (t.isEmpty) return null;
      return num.tryParse(t);
    }

    int? iv(TextEditingController ctrl) {
      final t = ctrl.text.trim();
      if (t.isEmpty) return null;
      return int.tryParse(t);
    }

    // Validate any field the nurse actually filled — empty fields stay null.
    String? rangeIfPresent(TextEditingController ctrl, String? Function() check) =>
        ctrl.text.trim().isEmpty ? null : check();
    final vitalError = firstError([
      rangeIfPresent(temp, () => numInRange(temp.text, min: 25, max: 45, field: 'Temperature')),
      rangeIfPresent(pulse, () => intInRange(pulse.text, min: 0, max: 300, field: 'Pulse')),
      rangeIfPresent(rr, () => intInRange(rr.text, min: 0, max: 120, field: 'Respiratory rate')),
      rangeIfPresent(sys, () => intInRange(sys.text, min: 0, max: 400, field: 'Systolic BP')),
      rangeIfPresent(dia, () => intInRange(dia.text, min: 0, max: 400, field: 'Diastolic BP')),
      rangeIfPresent(spo2, () => intInRange(spo2.text, min: 0, max: 100, field: 'SpO₂')),
      rangeIfPresent(pain, () => intInRange(pain.text, min: 0, max: 10, field: 'Pain score')),
    ]);
    if (vitalError != null) {
      setState(() => _error = vitalError);
      return;
    }

    final body = <String, dynamic>{
      'temperatureCelsius': n(temp),
      'pulseBpm': iv(pulse),
      'respiratoryRate': iv(rr),
      'systolicBp': iv(sys),
      'diastolicBp': iv(dia),
      'spo2': iv(spo2),
      'bloodSugarMgDl': iv(bsl),
      'weightKg': n(weight),
      'painScore': iv(pain),
      'notes': notes.text.trim().isEmpty ? null : notes.text.trim(),
    };
    if (!isEdit) {
      body['patientId'] = _patientId;
      if (widget.admissionId != null) body['admissionId'] = widget.admissionId;
    }

    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });
    try {
      final api = context.read<ApiClient>();
      if (isEdit) {
        await api.put<Map<String, dynamic>>('/api/v1/nursing/vitals/${existing['id']}', body,
            fromJson: (j) => j as Map<String, dynamic>);
        setState(() => _info = 'Vitals updated');
      } else {
        await api.post<Map<String, dynamic>>('/api/v1/nursing/vitals', body,
            fromJson: (j) => j as Map<String, dynamic>);
        setState(() => _info = 'Vitals recorded');
      }
      await _load();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }
}
