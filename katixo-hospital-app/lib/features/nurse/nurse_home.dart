import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/api/ipd_models.dart';
import '../../core/auth/auth_state.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/app_shell.dart';
import '../../core/widgets/status_chip.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;
import '../../core/responsive/breakpoints.dart';

/// Nurse role home: bed board with admission workflows and transfers.
class NurseHome extends StatefulWidget {
  const NurseHome({super.key});

  @override
  State<NurseHome> createState() => _NurseHomeState();
}

class _NurseHomeState extends State<NurseHome> {
  List<BedView> _beds = [];
  List<IsolationView> _isolations = [];
  bool _loading = false;
  String? _error;
  Timer? _refreshTimer;

  @override
  void initState() {
    super.initState();
    _loadBoardData();
    _refreshTimer =
        Timer.periodic(const Duration(seconds: 10), (_) => _loadBoardData());
  }

  @override
  void dispose() {
    _refreshTimer?.cancel();
    super.dispose();
  }

  Future<void> _loadBoardData() async {
    try {
      final api = context.read<ApiClient>();
      final beds = await api.get<List<BedView>>(
        '/api/v1/ipd/beds',
        fromJson: (json) => (json as List)
            .map((e) => BedView.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
      final isolations = await api.get<List<IsolationView>>(
        '/api/v1/ipd/beds/isolations',
        fromJson: (json) => (json as List)
            .map((e) => IsolationView.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
      if (mounted) {
        setState(() {
          _beds = beds;
          _isolations = isolations;
        });
      }
    } catch (_) {
      // Silent on poll errors.
    }
  }

  Future<void> _isolateBed(BedView bed) async {
    // Must match backend BedIsolation.IsolationType enum.
    const isolationTypes = [
      'CONTACT',
      'DROPLET',
      'AIRBORNE',
      'PROTECTIVE',
      'TERMINAL_CLEANING',
    ];
    String selectedType = isolationTypes.first;
    final reasonCtrl = TextEditingController();
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: const Text('Isolate Bed'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('Bed ${bed.bedNumber}', style: Theme.of(context).textTheme.bodyMedium),
              const SizedBox(height: Space.md),
              DropdownButton<String>(
                isExpanded: true,
                value: selectedType,
                items: isolationTypes
                    .map((t) => DropdownMenuItem(value: t, child: Text(t)))
                    .toList(),
                onChanged: (v) =>
                    setDialogState(() => selectedType = v ?? selectedType),
              ),
              const SizedBox(height: Space.md),
              TextField(
                controller: reasonCtrl,
                decoration: const InputDecoration(labelText: 'Reason *'),
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Cancel'),
            ),
            FilledButton(
              onPressed: reasonCtrl.text.trim().isNotEmpty
                  ? () => Navigator.pop(context, true)
                  : null,
              child: const Text('Isolate'),
            ),
          ],
        ),
      ),
    );

    if (confirmed == true && reasonCtrl.text.trim().isNotEmpty) {
      setState(() {
        _loading = true;
        _error = null;
      });

      try {
        final api = context.read<ApiClient>();
        await api.post<dynamic>(
          '/api/v1/ipd/beds/${bed.id}/isolate',
          {
            'isolationType': selectedType,
            'reason': reasonCtrl.text.trim(),
          },
          fromJson: (json) => json,
        );
        await _loadBoardData();
      } on ApiException catch (e) {
        setState(() => _error = e.error.message);
      } finally {
        if (mounted) setState(() => _loading = false);
      }
    }
  }

  Future<void> _clearIsolation(BedView bed) async {
    final notesCtrl = TextEditingController();
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Clear Isolation'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('Bed ${bed.bedNumber}',
                style: Theme.of(context).textTheme.bodyMedium),
            const SizedBox(height: Space.md),
            TextField(
              controller: notesCtrl,
              decoration:
                  const InputDecoration(labelText: 'Clearance Notes *'),
              maxLines: 2,
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
            child: const Text('Clear'),
          ),
        ],
      ),
    );

    if (confirmed == true && notesCtrl.text.trim().isNotEmpty) {
      setState(() {
        _loading = true;
        _error = null;
      });

      try {
        final api = context.read<ApiClient>();
        await api.post<dynamic>(
          '/api/v1/ipd/beds/${bed.id}/clear-isolation',
          {'clearanceNotes': notesCtrl.text.trim()},
          fromJson: (json) => json,
        );
        await _loadBoardData();
      } on ApiException catch (e) {
        setState(() => _error = e.error.message);
      } finally {
        if (mounted) setState(() => _loading = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final authState = context.watch<AuthState>();
    final theme = Theme.of(context);

    return AppShell(
      title: 'Nursing',
      destinations: const [
        ShellDestination(
          label: 'Bed Board',
          icon: Icons.bed_outlined,
          selectedIcon: Icons.bed,
        ),
      ],
      selectedIndex: 0,
      onDestinationSelected: (_) {},
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
      body: PageContainer(
        scrollable: false,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Text('Bed Board', style: theme.textTheme.titleLarge),
                const Spacer(),
                StatusChip(
                  '${_beds.where((b) => b.bedStatus == 'OCCUPIED').length} occupied',
                  kind: StatusKind.info,
                ),
                const SizedBox(width: Space.sm),
                StatusChip(
                  '${_beds.where((b) => b.bedStatus == 'VACANT').length} vacant',
                  kind: StatusKind.success,
                ),
                const SizedBox(width: Space.sm),
                IconButton(
                  tooltip: 'Refresh',
                  onPressed: _loading ? null : _loadBoardData,
                  icon: const Icon(Icons.refresh),
                ),
              ],
            ),
            const SizedBox(height: Space.md),

            if (_error != null) ...[
              MessageBanner.error(_error!),
              const SizedBox(height: Space.md),
            ],

            // Isolation alerts
            if (_isolations.isNotEmpty) ...[
              Card(
                color: theme.colorScheme.errorContainer,
                child: Padding(
                  padding: const EdgeInsets.all(Space.md),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Active Bed Isolations',
                          style: theme.textTheme.labelLarge
                              ?.copyWith(
                                  color: theme.colorScheme.onErrorContainer)
                              .copyWith(fontWeight: FontWeight.bold)),
                      const SizedBox(height: Space.sm),
                      for (var iso in _isolations) ...[
                        Row(
                          children: [
                            Icon(Icons.warning_amber,
                                size: 18,
                                color: theme.colorScheme.onErrorContainer),
                            const SizedBox(width: Space.sm),
                            Expanded(
                              child: Text(
                                'Bed ${_beds.firstWhere((b) => b.id == iso.bedId).bedNumber}: ${iso.isolationType}',
                                style: theme.textTheme.bodySmall?.copyWith(
                                    color:
                                        theme.colorScheme.onErrorContainer),
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: Space.xs),
                      ],
                    ],
                  ),
                ),
              ),
              const SizedBox(height: Space.md),
            ],

            // Bed grid layout responsive
            Expanded(
              child: SingleChildScrollView(
                child: context.isMobile
                    ? Column(
                        children: [
                          for (var bed in _beds) ...[
                            _BedCard(
                              bed: bed,
                              isIsolated: _isolations
                                  .any((i) => i.bedId == bed.id && i.isolationStatus == 'ACTIVE'),
                              loading: _loading,
                              onIsolate: () => _isolateBed(bed),
                              onClearIsolation: () => _clearIsolation(bed),
                            ),
                            const SizedBox(height: Space.sm),
                          ],
                        ],
                      )
                    : Wrap(
                        spacing: Space.md,
                        runSpacing: Space.md,
                        children: [
                          for (var bed in _beds)
                            SizedBox(
                              width: 180,
                              child: _BedCard(
                                bed: bed,
                                isIsolated: _isolations.any(
                                    (i) => i.bedId == bed.id && i.isolationStatus == 'ACTIVE'),
                                loading: _loading,
                                onIsolate: () => _isolateBed(bed),
                                onClearIsolation: () => _clearIsolation(bed),
                              ),
                            ),
                        ],
                      ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _BedCard extends StatelessWidget {
  const _BedCard({
    required this.bed,
    required this.isIsolated,
    required this.loading,
    required this.onIsolate,
    required this.onClearIsolation,
  });

  final BedView bed;
  final bool isIsolated;
  final bool loading;
  final VoidCallback onIsolate;
  final VoidCallback onClearIsolation;

  Color _statusColor(String status) {
    return switch (status) {
      'VACANT' => const Color(0xFF9E9E9E),
      'OCCUPIED' => const Color(0xFF1565C0),
      'ISOLATION' => StatusColors.danger,
      'MAINTENANCE' => StatusColors.warning,
      _ => const Color(0xFFBDBDBD),
    };
  }

  @override
  Widget build(BuildContext context) {
    final statusColor = _statusColor(bed.bedStatus);
    final isMobile = context.isMobile;

    return Card(
      shape: RoundedRectangleBorder(
        borderRadius: Corners.mdRadius,
        side: BorderSide(color: statusColor, width: 2),
      ),
      child: Padding(
        padding: const EdgeInsets.all(Space.md),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(bed.bedNumber,
                    style: context.textTheme.titleMedium
                        ?.copyWith(fontWeight: FontWeight.bold)),
                StatusChip.auto(isIsolated ? 'ISOLATION' : bed.bedStatus),
              ],
            ),
            const SizedBox(height: Space.sm),
            Text(
              '${bed.chargeModel} • ₹${bed.tariffRate ?? 0}',
              style: context.textTheme.bodySmall
                  ?.copyWith(color: context.colorScheme.onSurfaceVariant),
            ),
            if (isMobile) const SizedBox(height: Space.md),
            if (bed.bedStatus == 'OCCUPIED' && !isIsolated) ...[
              const Divider(),
              const SizedBox(height: Space.sm),
              Align(
                alignment: Alignment.centerRight,
                child: OutlinedButton.icon(
                  onPressed: loading ? null : onIsolate,
                  icon: const Icon(Icons.warning_amber, size: 16),
                  label: const Text('Isolate'),
                ),
              ),
            ],
            if (isIsolated) ...[
              const Divider(),
              const SizedBox(height: Space.sm),
              OutlinedButton.icon(
                onPressed: loading ? null : onClearIsolation,
                icon: const Icon(Icons.check, size: 16),
                label: const Text('Clear Isolation'),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
