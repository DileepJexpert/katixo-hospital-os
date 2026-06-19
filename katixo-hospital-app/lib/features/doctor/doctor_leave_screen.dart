import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/auth/auth_state.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/empty_state.dart';
import '../../core/widgets/status_chip.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;
import '../staff/doctor_picker.dart';

/// Doctor leave management. Doctors raise + cancel their own leave; admins see
/// a pending-approval inbox (approve/reject) and can view/raise leave for any
/// doctor via the picker. Body widget. Server enforces the role rules too.
class DoctorLeaveScreen extends StatefulWidget {
  const DoctorLeaveScreen({super.key});

  @override
  State<DoctorLeaveScreen> createState() => _DoctorLeaveScreenState();
}

class _DoctorLeaveScreenState extends State<DoctorLeaveScreen> {
  static const _leaveTypes = <String>[
    'CASUAL', 'SICK', 'EARNED', 'UNPAID', 'CONFERENCE', 'SABBATICAL',
  ];

  String _tab = 'mine';
  bool _loading = false;
  String? _error;
  String? _info;

  int? _doctorId;
  String _doctorName = '';
  List<Map<String, dynamic>> _leaves = const [];
  List<Map<String, dynamic>> _pending = const [];

  String get _role => context.read<AuthState>().currentUser?.role ?? '';
  bool get _isAdmin => _role == 'ADMIN' || _role == 'SUPER_ADMIN';
  bool get _isDoctor => _role == 'DOCTOR';

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final user = context.read<AuthState>().currentUser;
      if (_isDoctor && user?.staffId != null) {
        _doctorId = user!.staffId;
        _doctorName = user.name;
        _loadHistory();
      } else if (_isAdmin) {
        _tab = 'approvals';
        _loadPending();
      }
    });
  }

  Future<void> _loadHistory() async {
    final id = _doctorId;
    if (id == null) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      final page = await api.get<Map<String, dynamic>>(
        '/api/v1/doctors/$id/leave?page=0&size=50',
        fromJson: (j) => j as Map<String, dynamic>,
      );
      if (mounted) {
        setState(() => _leaves =
            List<Map<String, dynamic>>.from(page['content'] as List? ?? const []));
      }
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _loadPending() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      // doctorId in the path is ignored by the pending endpoint (lists all).
      final page = await api.get<Map<String, dynamic>>(
        '/api/v1/doctors/0/leave/pending?page=0&size=50',
        fromJson: (j) => j as Map<String, dynamic>,
      );
      if (mounted) {
        setState(() => _pending =
            List<Map<String, dynamic>>.from(page['content'] as List? ?? const []));
      }
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _pickDoctor() async {
    final d = await showDoctorPicker(context);
    if (d == null) return;
    setState(() {
      _doctorId = d['id'] as int?;
      _doctorName = '${d['name'] ?? ''}';
    });
    await _loadHistory();
  }

  Future<void> _requestLeaveDialog() async {
    final id = _doctorId;
    if (id == null) return;
    DateTime start = DateTime.now();
    DateTime end = DateTime.now();
    String type = 'CASUAL';
    final reasonCtrl = TextEditingController();
    String iso(DateTime d) => d.toIso8601String().split('T').first;

    final proceed = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setLocal) => AlertDialog(
          title: Text('Request leave — $_doctorName'),
          content: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton(
                        onPressed: () async {
                          final p = await showDatePicker(
                            context: context, initialDate: start,
                            firstDate: DateTime(2020), lastDate: DateTime(2100));
                          if (p != null) setLocal(() => start = p);
                        },
                        child: Text('From ${iso(start)}'),
                      ),
                    ),
                    const SizedBox(width: Space.sm),
                    Expanded(
                      child: OutlinedButton(
                        onPressed: () async {
                          final p = await showDatePicker(
                            context: context, initialDate: end,
                            firstDate: DateTime(2020), lastDate: DateTime(2100));
                          if (p != null) setLocal(() => end = p);
                        },
                        child: Text('To ${iso(end)}'),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: Space.sm),
                DropdownButtonFormField<String>(
                  initialValue: type,
                  decoration: const InputDecoration(labelText: 'Leave type'),
                  items: [
                    for (final t in _leaveTypes)
                      DropdownMenuItem(value: t, child: Text(_titleCase(t))),
                  ],
                  onChanged: (v) => setLocal(() => type = v ?? 'CASUAL'),
                ),
                const SizedBox(height: Space.sm),
                TextField(
                  controller: reasonCtrl,
                  decoration: const InputDecoration(labelText: 'Reason'),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
            FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Submit')),
          ],
        ),
      ),
    );
    if (proceed != true) return;
    if (end.isBefore(start)) {
      setState(() => _error = 'End date cannot be before start date');
      return;
    }
    await _mutate(() async {
      final api = context.read<ApiClient>();
      await api.post<dynamic>('/api/v1/doctors/$id/leave', {
        'leaveStartDate': iso(start),
        'leaveEndDate': iso(end),
        'leaveType': type,
        'reason': reasonCtrl.text.trim(),
      }, fromJson: (j) => j);
    }, 'Leave requested', refreshHistory: true);
  }

  Future<void> _approve(Map<String, dynamic> leave) async {
    await _mutate(() async {
      final api = context.read<ApiClient>();
      await api.put<dynamic>(
        '/api/v1/doctors/${leave['doctorId']}/leave/${leave['id']}/approve',
        const <String, dynamic>{}, fromJson: (j) => j);
    }, 'Leave approved', refreshPending: true);
  }

  Future<void> _rejectDialog(Map<String, dynamic> leave) async {
    final reasonCtrl = TextEditingController();
    final proceed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Reject leave'),
        content: TextField(
          controller: reasonCtrl,
          decoration: const InputDecoration(labelText: 'Rejection reason *'),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Back')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Reject')),
        ],
      ),
    );
    if (proceed != true) return;
    if (reasonCtrl.text.trim().isEmpty) {
      setState(() => _error = 'Rejection reason is required');
      return;
    }
    await _mutate(() async {
      final api = context.read<ApiClient>();
      await api.put<dynamic>(
        '/api/v1/doctors/${leave['doctorId']}/leave/${leave['id']}/reject',
        {'rejectionReason': reasonCtrl.text.trim()}, fromJson: (j) => j);
    }, 'Leave rejected', refreshPending: true);
  }

  Future<void> _cancel(Map<String, dynamic> leave) async {
    await _mutate(() async {
      final api = context.read<ApiClient>();
      await api.delete<dynamic>(
        '/api/v1/doctors/${leave['doctorId']}/leave/${leave['id']}',
        fromJson: (j) => j);
    }, 'Leave cancelled', refreshHistory: true);
  }

  Future<void> _mutate(Future<void> Function() action, String okMsg,
      {bool refreshHistory = false, bool refreshPending = false}) async {
    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });
    try {
      await action();
      setState(() => _info = okMsg);
      if (refreshHistory) await _loadHistory();
      if (refreshPending) await _loadPending();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Action failed: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final tabs = <(String, String)>[
      ('mine', _isDoctor ? 'My leave' : 'Doctor leave'),
      if (_isAdmin) ('approvals', 'Approvals'),
    ];
    if (!tabs.any((t) => t.$1 == _tab)) _tab = tabs.first.$1;

    return PageContainer(
      scrollable: false,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text('Doctor Leave', style: theme.textTheme.titleLarge),
              const Spacer(),
              IconButton(
                tooltip: 'Refresh',
                onPressed: _loading
                    ? null
                    : () => _tab == 'approvals' ? _loadPending() : _loadHistory(),
                icon: const Icon(Icons.refresh),
              ),
            ],
          ),
          const SizedBox(height: Space.md),
          if (tabs.length > 1) ...[
            Wrap(
              spacing: Space.sm,
              children: [
                for (final t in tabs)
                  ChoiceChip(
                    label: Text(t.$2),
                    selected: _tab == t.$1,
                    onSelected: (_) {
                      setState(() => _tab = t.$1);
                      _tab == 'approvals' ? _loadPending() : _loadHistory();
                    },
                  ),
              ],
            ),
            const SizedBox(height: Space.md),
          ],
          if (_error != null) ...[
            MessageBanner.error(_error!),
            const SizedBox(height: Space.md),
          ],
          if (_info != null) ...[
            MessageBanner.success(_info!),
            const SizedBox(height: Space.md),
          ],
          Expanded(child: _tab == 'approvals' ? _approvals(theme) : _mine(theme)),
        ],
      ),
    );
  }

  Widget _mine(ThemeData theme) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            if (_isAdmin)
              OutlinedButton.icon(
                onPressed: _loading ? null : _pickDoctor,
                icon: const Icon(Icons.person_search_outlined, size: 18),
                label: Text(_doctorId == null ? 'Select doctor' : 'Doctor: $_doctorName'),
              ),
            const Spacer(),
            if (_doctorId != null)
              FilledButton.icon(
                onPressed: _loading ? null : _requestLeaveDialog,
                icon: const Icon(Icons.add, size: 18),
                label: const Text('Request leave'),
              ),
          ],
        ),
        const SizedBox(height: Space.md),
        Expanded(
          child: _doctorId == null
              ? const EmptyState(
                  icon: Icons.event_busy_outlined,
                  title: 'Select a doctor',
                  message: 'Pick a doctor to view and manage their leave.')
              : _leaves.isEmpty
                  ? EmptyState(
                      icon: Icons.event_available_outlined,
                      title: 'No leave records',
                      message: '$_doctorName has no leave on file.')
                  : ListView.separated(
                      itemCount: _leaves.length,
                      separatorBuilder: (_, __) => const Divider(height: 1),
                      itemBuilder: (context, i) => _leaveTile(theme, _leaves[i], canCancel: true),
                    ),
        ),
      ],
    );
  }

  Widget _approvals(ThemeData theme) {
    if (_pending.isEmpty) {
      return EmptyState(
        icon: Icons.fact_check_outlined,
        title: _loading ? 'Loading…' : 'No pending requests',
        message: 'Doctor leave awaiting approval appears here.',
      );
    }
    return ListView.separated(
      itemCount: _pending.length,
      separatorBuilder: (_, __) => const Divider(height: 1),
      itemBuilder: (context, i) => _leaveTile(theme, _pending[i], showApprove: true),
    );
  }

  Widget _leaveTile(ThemeData theme, Map<String, dynamic> l,
      {bool canCancel = false, bool showApprove = false}) {
    final status = '${l['status']}';
    final pending = status == 'PENDING';
    return ListTile(
      leading: const Icon(Icons.event_busy_outlined),
      title: Row(
        children: [
          Text('${_titleCase('${l['leaveType']}')} leave', style: theme.textTheme.titleSmall),
          const SizedBox(width: Space.sm),
          StatusChip.auto(status),
        ],
      ),
      subtitle: Text(
        '${l['leaveStartDate']} → ${l['leaveEndDate']}'
        '${showApprove ? ' · doctor #${l['doctorId']}' : ''}'
        '${(l['reason'] ?? '').toString().isNotEmpty ? '\n${l['reason']}' : ''}'
        '${(l['rejectionReason'] ?? '').toString().isNotEmpty ? '\nRejected: ${l['rejectionReason']}' : ''}',
        style: theme.textTheme.bodySmall,
      ),
      isThreeLine: (l['reason'] ?? '').toString().isNotEmpty,
      trailing: Wrap(
        spacing: Space.xs,
        children: [
          if (showApprove && pending) ...[
            FilledButton(
              onPressed: _loading ? null : () => _approve(l),
              child: const Text('Approve'),
            ),
            OutlinedButton(
              onPressed: _loading ? null : () => _rejectDialog(l),
              child: const Text('Reject'),
            ),
          ],
          if (canCancel && pending)
            IconButton(
              tooltip: 'Cancel request',
              icon: const Icon(Icons.delete_outline, size: 20),
              onPressed: _loading ? null : () => _cancel(l),
            ),
        ],
      ),
    );
  }

  String _titleCase(String s) => s.isEmpty
      ? s
      : '${s[0].toUpperCase()}${s.substring(1).toLowerCase()}';
}
