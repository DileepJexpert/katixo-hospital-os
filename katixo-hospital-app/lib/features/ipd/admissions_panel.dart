import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/api/ipd_models.dart';
import '../../core/auth/auth_state.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/status_chip.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// Ward worklist of active admissions with transfer + discharge actions.
///
/// Shared between the nurse and doctor shells. Actions are gated by the
/// signed-in role to match backend authorization:
///   * Transfer  → NURSE, FRONT_DESK, ADMIN
///   * Discharge → DOCTOR, ADMIN
class AdmissionsPanel extends StatefulWidget {
  const AdmissionsPanel({super.key});

  @override
  State<AdmissionsPanel> createState() => _AdmissionsPanelState();
}

class _AdmissionsPanelState extends State<AdmissionsPanel> {
  List<ActiveAdmission> _admissions = [];
  List<BedView> _beds = [];
  List<String> _checklist = [];
  bool _loading = false;
  String? _error;
  String? _success;
  Timer? _refreshTimer;

  @override
  void initState() {
    super.initState();
    _loadAll();
    _refreshTimer =
        Timer.periodic(const Duration(seconds: 15), (_) => _loadAdmissions());
  }

  @override
  void dispose() {
    _refreshTimer?.cancel();
    super.dispose();
  }

  Future<void> _loadAll() async {
    await Future.wait([_loadAdmissions(), _loadBeds(), _loadChecklist()]);
  }

  Future<void> _loadAdmissions() async {
    try {
      final api = context.read<ApiClient>();
      final rows = await api.get<List<ActiveAdmission>>(
        '/api/v1/ipd/admissions',
        fromJson: (json) => (json as List)
            .map((e) => ActiveAdmission.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
      if (mounted) setState(() => _admissions = rows);
    } catch (_) {
      // Silent on poll errors.
    }
  }

  Future<void> _loadBeds() async {
    try {
      final api = context.read<ApiClient>();
      final beds = await api.get<List<BedView>>(
        '/api/v1/ipd/beds',
        fromJson: (json) => (json as List)
            .map((e) => BedView.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
      if (mounted) setState(() => _beds = beds);
    } catch (_) {
      // Beds are only needed for the transfer dialog; ignore poll errors.
    }
  }

  Future<void> _loadChecklist() async {
    try {
      final api = context.read<ApiClient>();
      final items = await api.get<List<String>>(
        '/api/v1/ipd/discharge-checklist',
        fromJson: (json) =>
            (json as List).map((e) => e as String).toList(),
      );
      if (mounted) setState(() => _checklist = items);
    } catch (_) {
      // Empty checklist = nothing blocks; fine to ignore.
    }
  }

  Future<void> _transfer(ActiveAdmission adm) async {
    final vacant = _beds.where((b) => b.bedStatus == 'VACANT').toList();
    if (vacant.isEmpty) {
      setState(() => _error = 'No vacant beds available for transfer');
      return;
    }
    int? selectedBedId = vacant.first.id;

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: const Text('Transfer Patient'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('${adm.patientName} — currently in bed ${adm.bedNumber}',
                  style: Theme.of(context).textTheme.bodyMedium),
              const SizedBox(height: Space.md),
              DropdownButton<int>(
                isExpanded: true,
                value: selectedBedId,
                items: vacant
                    .map((b) => DropdownMenuItem(
                          value: b.id,
                          child: Text(
                              'Bed ${b.bedNumber} • ${b.chargeModel} • ₹${b.tariffRate ?? 0}'),
                        ))
                    .toList(),
                onChanged: (v) =>
                    setDialogState(() => selectedBedId = v ?? selectedBedId),
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
              child: const Text('Transfer'),
            ),
          ],
        ),
      ),
    );

    if (confirmed == true && selectedBedId != null) {
      await _submit(
        '/api/v1/ipd/admissions/${adm.admissionId}/transfer',
        {'newBedId': selectedBedId},
        'Patient transferred',
      );
    }
  }

  Future<void> _discharge(ActiveAdmission adm) async {
    const dischargeTypes = ['NORMAL', 'LAMA', 'DEATH'];
    String selectedType = dischargeTypes.first;
    final acknowledged = <String>{};
    final isolateOnDischarge = ValueNotifier<bool>(false);
    const isolationTypes = [
      'CONTACT',
      'DROPLET',
      'AIRBORNE',
      'PROTECTIVE',
      'TERMINAL_CLEANING',
    ];
    String isolationType = isolationTypes.first;
    final isolationReasonCtrl = TextEditingController();

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setDialogState) {
          // Blocking checklist only applies to a NORMAL discharge.
          final showChecklist =
              selectedType == 'NORMAL' && _checklist.isNotEmpty;
          final allAcknowledged = !showChecklist ||
              _checklist.every((c) => acknowledged.contains(c));

          return AlertDialog(
            title: const Text('Discharge Patient'),
            content: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('${adm.patientName} (${adm.uhid})',
                      style: Theme.of(context).textTheme.bodyMedium),
                  Text('Bed ${adm.bedNumber} • ${adm.admissionNumber}',
                      style: Theme.of(context).textTheme.bodySmall),
                  const SizedBox(height: Space.md),
                  const Text('Discharge Type'),
                  DropdownButton<String>(
                    isExpanded: true,
                    value: selectedType,
                    items: dischargeTypes
                        .map((t) =>
                            DropdownMenuItem(value: t, child: Text(t)))
                        .toList(),
                    onChanged: (v) => setDialogState(
                        () => selectedType = v ?? selectedType),
                  ),
                  if (showChecklist) ...[
                    const SizedBox(height: Space.md),
                    const Text('Discharge Checklist (all required)'),
                    for (final item in _checklist)
                      CheckboxListTile(
                        dense: true,
                        contentPadding: EdgeInsets.zero,
                        value: acknowledged.contains(item),
                        title: Text(_humanize(item),
                            style: const TextStyle(fontSize: 13)),
                        onChanged: (v) => setDialogState(() {
                          if (v == true) {
                            acknowledged.add(item);
                          } else {
                            acknowledged.remove(item);
                          }
                        }),
                      ),
                  ],
                  if (selectedType != 'NORMAL' && _checklist.isNotEmpty) ...[
                    const SizedBox(height: Space.sm),
                    Text(
                      '$selectedType bypasses the blocking checklist.',
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                  const SizedBox(height: Space.md),
                  CheckboxListTile(
                    dense: true,
                    contentPadding: EdgeInsets.zero,
                    value: isolateOnDischarge.value,
                    title: const Text('Bed needs isolation after discharge',
                        style: TextStyle(fontSize: 13)),
                    onChanged: (v) => setDialogState(
                        () => isolateOnDischarge.value = v ?? false),
                  ),
                  if (isolateOnDischarge.value) ...[
                    DropdownButton<String>(
                      isExpanded: true,
                      value: isolationType,
                      items: isolationTypes
                          .map((t) =>
                              DropdownMenuItem(value: t, child: Text(t)))
                          .toList(),
                      onChanged: (v) => setDialogState(
                          () => isolationType = v ?? isolationType),
                    ),
                    TextField(
                      controller: isolationReasonCtrl,
                      decoration: const InputDecoration(
                          labelText: 'Isolation reason'),
                    ),
                  ],
                ],
              ),
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context, false),
                child: const Text('Cancel'),
              ),
              FilledButton(
                onPressed: allAcknowledged
                    ? () => Navigator.pop(context, true)
                    : null,
                child: const Text('Discharge'),
              ),
            ],
          );
        },
      ),
    );

    if (confirmed == true) {
      final body = <String, dynamic>{
        'dischargeType': selectedType,
        if (selectedType == 'NORMAL')
          'acknowledgedChecklistItems': acknowledged.toList(),
        if (isolateOnDischarge.value) 'bedIsolationType': isolationType,
        if (isolateOnDischarge.value &&
            isolationReasonCtrl.text.trim().isNotEmpty)
          'bedIsolationReason': isolationReasonCtrl.text.trim(),
      };
      await _submit(
        '/api/v1/ipd/admissions/${adm.admissionId}/discharge',
        body,
        'Patient discharged',
      );
    }
  }

  Future<void> _submit(
      String path, Map<String, dynamic> body, String successMsg) async {
    setState(() {
      _loading = true;
      _error = null;
      _success = null;
    });
    try {
      final api = context.read<ApiClient>();
      await api.post<dynamic>(path, body, fromJson: (json) => json);
      setState(() => _success = successMsg);
      await _loadAll();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  String _humanize(String code) => code
      .split('_')
      .map((w) => w.isEmpty
          ? w
          : '${w[0].toUpperCase()}${w.substring(1).toLowerCase()}')
      .join(' ');

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final role = context.watch<AuthState>().currentUser?.role;
    final canTransfer =
        role == 'NURSE' || role == 'FRONT_DESK' || role == 'ADMIN';
    final canDischarge = role == 'DOCTOR' || role == 'ADMIN';

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Text('Admitted Patients', style: theme.textTheme.titleLarge),
            const Spacer(),
            StatusChip('${_admissions.length} admitted', kind: StatusKind.info),
            const SizedBox(width: Space.sm),
            IconButton(
              tooltip: 'Refresh',
              onPressed: _loading ? null : _loadAll,
              icon: const Icon(Icons.refresh),
            ),
          ],
        ),
        const SizedBox(height: Space.md),
        if (_error != null) ...[
          MessageBanner.error(_error!),
          const SizedBox(height: Space.md),
        ],
        if (_success != null) ...[
          MessageBanner.success(_success!),
          const SizedBox(height: Space.md),
        ],
        if (_admissions.isEmpty)
          Card(
            child: Padding(
              padding: const EdgeInsets.all(Space.xl),
              child: Center(
                child: Text('No admitted patients',
                    style: theme.textTheme.bodyMedium?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant)),
              ),
            ),
          )
        else
          Expanded(
            child: Card(
              child: ListView.separated(
                itemCount: _admissions.length,
                separatorBuilder: (_, __) => const Divider(height: 1),
                itemBuilder: (context, i) {
                  final adm = _admissions[i];
                  return ListTile(
                    leading: Icon(Icons.personal_injury_outlined,
                        color: theme.colorScheme.primary),
                    title: Text('${adm.patientName}  •  Bed ${adm.bedNumber}'),
                    subtitle: Text(
                      [
                        adm.admissionNumber,
                        if (adm.uhid.isNotEmpty) adm.uhid,
                        if (adm.diagnosis.isNotEmpty) adm.diagnosis,
                      ].join('  •  '),
                    ),
                    trailing: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        if (canTransfer)
                          OutlinedButton(
                            onPressed:
                                _loading ? null : () => _transfer(adm),
                            child: const Text('Transfer',
                                style: TextStyle(fontSize: 12)),
                          ),
                        if (canTransfer && canDischarge)
                          const SizedBox(width: Space.sm),
                        if (canDischarge)
                          FilledButton(
                            onPressed:
                                _loading ? null : () => _discharge(adm),
                            child: const Text('Discharge',
                                style: TextStyle(fontSize: 12)),
                          ),
                      ],
                    ),
                  );
                },
              ),
            ),
          ),
      ],
    );
  }
}
