import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/api/opd_models.dart';
import '../../core/auth/auth_state.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/app_shell.dart';
import '../../core/widgets/status_chip.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;
import '../ipd/admissions_panel.dart';
import 'lab_orders_panel.dart';
import 'ot_booking_panel.dart';
import 'prescription_panel.dart';
import 'radiology_orders_panel.dart';

/// Doctor role home: live queue worklist with call-next / start / complete.
class DoctorHome extends StatefulWidget {
  const DoctorHome({super.key});

  @override
  State<DoctorHome> createState() => _DoctorHomeState();
}

class _DoctorHomeState extends State<DoctorHome> {
  List<QueueTokenResponse> _tokens = [];
  bool _loading = false;
  String? _error;
  String? _info;
  Timer? _refreshTimer;
  int _navIndex = 0;

  /// Visit currently being consulted (start → complete flow).
  VisitResponse? _activeVisit;
  final _diagnosisCtrl = TextEditingController();
  final _adviceCtrl = TextEditingController();

  int? get _doctorId => context.read<AuthState>().currentUser?.staffId;

  @override
  void initState() {
    super.initState();
    _loadWorklist();
    // Poll until WebSocket queue board lands.
    _refreshTimer =
        Timer.periodic(const Duration(seconds: 10), (_) => _loadWorklist());
  }

  @override
  void dispose() {
    _refreshTimer?.cancel();
    _diagnosisCtrl.dispose();
    _adviceCtrl.dispose();
    super.dispose();
  }

  Future<void> _loadWorklist() async {
    final doctorId = _doctorId;
    if (doctorId == null) return;

    try {
      final api = context.read<ApiClient>();
      final tokens = await api.get<List<QueueTokenResponse>>(
        '/api/v1/opd/queue/doctor/$doctorId',
        fromJson: (json) => (json as List)
            .map((e) => QueueTokenResponse.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
      if (mounted) setState(() => _tokens = tokens);
    } catch (_) {
      // Silent on poll errors; surfaced on user actions instead.
    }
  }

  Future<void> _callNext() async {
    final doctorId = _doctorId;
    if (doctorId == null) return;

    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });

    try {
      final api = context.read<ApiClient>();
      final token = await api.post<QueueTokenResponse>(
        '/api/v1/opd/queue/doctor/$doctorId/call-next',
        const <String, dynamic>{},
        fromJson: (json) =>
            QueueTokenResponse.fromJson(json as Map<String, dynamic>),
      );
      setState(() => _info = 'Token ${token.tokenNumber} called');
      await _loadWorklist();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Call next failed: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _startConsultation(int visitId) async {
    setState(() {
      _loading = true;
      _error = null;
    });

    try {
      final api = context.read<ApiClient>();
      final visit = await api.put<VisitResponse>(
        '/api/v1/opd/visits/$visitId/start',
        const <String, dynamic>{},
        fromJson: (json) =>
            VisitResponse.fromJson(json as Map<String, dynamic>),
      );
      setState(() {
        _activeVisit = visit;
        _diagnosisCtrl.clear();
        _adviceCtrl.clear();
      });
      await _loadWorklist();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _completeConsultation() async {
    final visit = _activeVisit;
    if (visit == null) return;

    setState(() {
      _loading = true;
      _error = null;
    });

    try {
      final api = context.read<ApiClient>();
      await api.put<VisitResponse>(
        '/api/v1/opd/visits/${visit.id}/complete',
        {
          'diagnosis': _diagnosisCtrl.text.trim(),
          'advice': _adviceCtrl.text.trim(),
        },
        fromJson: (json) =>
            VisitResponse.fromJson(json as Map<String, dynamic>),
      );
      setState(() {
        _info = 'Visit ${visit.visitNumber} completed';
        _activeVisit = null;
      });
      await _loadWorklist();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final authState = context.watch<AuthState>();
    final theme = Theme.of(context);

    return AppShell(
      title: 'Doctor',
      destinations: const [
        ShellDestination(
          label: 'Worklist',
          icon: Icons.list_alt_outlined,
          selectedIcon: Icons.list_alt,
        ),
        ShellDestination(
          label: 'Ward',
          icon: Icons.king_bed_outlined,
          selectedIcon: Icons.king_bed,
        ),
      ],
      selectedIndex: _navIndex,
      onDestinationSelected: (i) => setState(() => _navIndex = i),
      actions: [
        if (authState.currentUser != null)
          Center(
            child: Padding(
              padding: const EdgeInsets.only(right: Space.sm),
              child: Text(authState.currentUser!.name,
                  style: theme.textTheme.labelLarge),
            ),
          ),
        IconButton(
          tooltip: 'Sign out',
          icon: const Icon(Icons.logout_outlined),
          onPressed: () => authState.logout(),
        ),
      ],
      body: _navIndex == 1
          ? const PageContainer(
              scrollable: false,
              child: AdmissionsPanel(),
            )
          : PageContainer(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Text('My Queue', style: theme.textTheme.titleLarge),
                const Spacer(),
                IconButton(
                  tooltip: 'Refresh',
                  onPressed: _loading ? null : _loadWorklist,
                  icon: const Icon(Icons.refresh),
                ),
                const SizedBox(width: Space.sm),
                FilledButton.icon(
                  onPressed: _loading ? null : _callNext,
                  icon: const Icon(Icons.campaign_outlined, size: 18),
                  label: const Text('Call Next'),
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

            // Active consultation panel
            if (_activeVisit != null) ...[
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(Space.lg),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Text('Consultation — ${_activeVisit!.visitNumber}',
                              style: theme.textTheme.titleMedium),
                          const Spacer(),
                          StatusChip.auto('IN_CONSULTATION'),
                        ],
                      ),
                      if (_activeVisit!.chiefComplaint != null) ...[
                        const SizedBox(height: Space.sm),
                        Text('Complaint: ${_activeVisit!.chiefComplaint}',
                            style: theme.textTheme.bodyMedium),
                      ],
                      const SizedBox(height: Space.md),
                      TextField(
                        controller: _diagnosisCtrl,
                        enabled: !_loading,
                        decoration:
                            const InputDecoration(labelText: 'Diagnosis'),
                      ),
                      const SizedBox(height: Space.md),
                      TextField(
                        controller: _adviceCtrl,
                        enabled: !_loading,
                        maxLines: 2,
                        decoration:
                            const InputDecoration(labelText: 'Advice'),
                      ),
                      const SizedBox(height: Space.lg),
                      const Divider(),
                      const SizedBox(height: Space.lg),
                      PrescriptionPanel(visitId: _activeVisit!.id),
                      const SizedBox(height: Space.lg),
                      const Divider(),
                      const SizedBox(height: Space.lg),
                      LabOrdersPanel(visitId: _activeVisit!.id),
                      const SizedBox(height: Space.lg),
                      const Divider(),
                      const SizedBox(height: Space.lg),
                      RadiologyOrdersPanel(visitId: _activeVisit!.id),
                      const SizedBox(height: Space.lg),
                      const Divider(),
                      const SizedBox(height: Space.lg),
                      OTBookingPanel(visitId: _activeVisit!.id),
                      const SizedBox(height: Space.lg),
                      const Divider(),
                      const SizedBox(height: Space.lg),
                      FilledButton.icon(
                        onPressed: _loading ? null : _completeConsultation,
                        icon: const Icon(Icons.check, size: 18),
                        label: const Text('Complete Consultation'),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: Space.md),
            ],

            // Queue list
            if (_tokens.isEmpty)
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(Space.xl),
                  child: Center(
                    child: Text('Queue is empty',
                        style: theme.textTheme.bodyMedium?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant)),
                  ),
                ),
              )
            else
              Card(
                child: Column(
                  children: [
                    for (var i = 0; i < _tokens.length; i++) ...[
                      ListTile(
                        leading: CircleAvatar(
                          radius: 18,
                          child: Text('${_tokens[i].tokenNumber}',
                              style: theme.textTheme.labelLarge),
                        ),
                        title: Text('Token ${_tokens[i].tokenNumber}'
                            '${(_tokens[i].priority ?? 0) > 0 ? '  ⚡ priority' : ''}'),
                        subtitle: Text('Visit #${_tokens[i].visitId}'),
                        trailing: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            StatusChip.auto(_tokens[i].queueStatus),
                            if (_tokens[i].queueStatus == 'CALLED') ...[
                              const SizedBox(width: Space.sm),
                              OutlinedButton(
                                onPressed: _loading
                                    ? null
                                    : () =>
                                        _startConsultation(_tokens[i].visitId),
                                child: const Text('Start'),
                              ),
                            ],
                          ],
                        ),
                      ),
                      if (i < _tokens.length - 1) const Divider(),
                    ],
                  ],
                ),
              ),
          ],
        ),
      ),
    );
  }
}
