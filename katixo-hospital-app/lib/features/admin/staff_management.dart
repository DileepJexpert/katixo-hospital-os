import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/api/staff_models.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/app_shell.dart';
import '../../core/widgets/status_chip.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// Admin staff management (body only — lives inside OwnerDashboard shell).
/// Dense directory list with inline expandable edit; no full-screen pushes.
class StaffManagementScreen extends StatefulWidget {
  const StaffManagementScreen({super.key});

  @override
  State<StaffManagementScreen> createState() => _StaffManagementScreenState();
}

class _StaffManagementScreenState extends State<StaffManagementScreen> {
  List<StaffResponse> _staff = [];
  bool _loading = false;
  String? _error;
  String? _success;

  @override
  void initState() {
    super.initState();
    _loadStaff();
  }

  Future<void> _loadStaff() async {
    setState(() => _loading = true);
    try {
      final api = context.read<ApiClient>();
      final staff = await api.get<List<StaffResponse>>(
        '/api/v1/staff-management',
        fromJson: (json) => (json as List? ?? [])
            .map((e) => StaffResponse.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
      if (mounted) {
        setState(() {
          _staff = staff;
          _error = null;
        });
      }
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.error.message);
    } catch (e) {
      if (mounted) setState(() => _error = 'Failed to load staff: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _saveStaff(StaffResponse member, UpdateStaffRequest req) async {
    setState(() {
      _loading = true;
      _error = null;
      _success = null;
    });
    try {
      final api = context.read<ApiClient>();
      await api.put<StaffResponse>(
        '/api/v1/staff-management/${member.id}',
        req.toJson(),
        fromJson: (json) =>
            StaffResponse.fromJson(json as Map<String, dynamic>),
      );
      setState(() => _success = '${member.fullName} updated');
      await _loadStaff();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Update failed: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _deactivate(StaffResponse member) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Deactivate Staff'),
        content: Text('Deactivate ${member.fullName}? '
            'They will no longer appear in staff pickers.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Deactivate'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;

    setState(() {
      _loading = true;
      _error = null;
      _success = null;
    });
    try {
      final api = context.read<ApiClient>();
      await api.delete('/api/v1/staff-management/${member.id}',
          fromJson: (_) => null);
      setState(() => _success = '${member.fullName} deactivated');
      await _loadStaff();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _showAddDialog() {
    showDialog(
      context: context,
      builder: (context) => _AddStaffDialog(onSubmit: (req) async {
        Navigator.pop(context);
        setState(() {
          _loading = true;
          _error = null;
          _success = null;
        });
        try {
          final api = context.read<ApiClient>();
          await api.post<StaffResponse>(
            '/api/v1/staff-management',
            req.toJson(),
            fromJson: (json) =>
                StaffResponse.fromJson(json as Map<String, dynamic>),
          );
          setState(() => _success = 'Staff member added');
          await _loadStaff();
        } on ApiException catch (e) {
          setState(() => _error = e.error.message);
        } catch (e) {
          setState(() => _error = 'Add failed: $e');
        } finally {
          if (mounted) setState(() => _loading = false);
        }
      }),
    );
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
              Text('Staff Directory', style: theme.textTheme.titleLarge),
              const Spacer(),
              IconButton(
                tooltip: 'Refresh',
                onPressed: _loading ? null : _loadStaff,
                icon: const Icon(Icons.refresh),
              ),
              const SizedBox(width: Space.sm),
              FilledButton.icon(
                onPressed: _loading ? null : _showAddDialog,
                icon: const Icon(Icons.person_add_outlined, size: 18),
                label: const Text('Add Staff'),
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
          if (_staff.isEmpty && !_loading)
            Card(
              child: Padding(
                padding: const EdgeInsets.all(Space.xl),
                child: Center(
                  child: Text('No staff members configured',
                      style: theme.textTheme.bodyMedium?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant)),
                ),
              ),
            )
          else
            Expanded(
              child: Card(
                child: ListView.separated(
                  itemCount: _staff.length,
                  separatorBuilder: (_, __) => const Divider(height: 1),
                  itemBuilder: (context, i) => _staffRow(_staff[i], theme),
                ),
              ),
            ),
        ],
      ),
    );
  }

  Widget _staffRow(StaffResponse m, ThemeData theme) {
    return ExpansionTile(
      dense: true,
      tilePadding: const EdgeInsets.symmetric(horizontal: Space.md),
      childrenPadding:
          const EdgeInsets.fromLTRB(Space.lg, 0, Space.lg, Space.md),
      title: Row(
        children: [
          SizedBox(
            width: 180,
            child: Text(m.fullName,
                style: theme.textTheme.bodyMedium
                    ?.copyWith(fontWeight: FontWeight.w600),
                overflow: TextOverflow.ellipsis),
          ),
          const SizedBox(width: Space.md),
          Expanded(
            child: Text('${m.department} • ${m.email}',
                style: theme.textTheme.bodySmall?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant),
                overflow: TextOverflow.ellipsis),
          ),
          StatusChip(m.role.replaceAll('_', ' '), kind: StatusKind.info),
          const SizedBox(width: Space.sm),
          StatusChip.auto(m.isActive ? 'ACTIVE' : 'INACTIVE'),
        ],
      ),
      children: [_StaffEditor(member: m, onSave: _saveStaff, onDeactivate: _deactivate)],
    );
  }
}

/// Inline editor shown when a staff row expands: contact, department,
/// approval permissions, save/deactivate.
class _StaffEditor extends StatefulWidget {
  const _StaffEditor({
    required this.member,
    required this.onSave,
    required this.onDeactivate,
  });

  final StaffResponse member;
  final Future<void> Function(StaffResponse, UpdateStaffRequest) onSave;
  final Future<void> Function(StaffResponse) onDeactivate;

  @override
  State<_StaffEditor> createState() => _StaffEditorState();
}

class _StaffEditorState extends State<_StaffEditor> {
  late final TextEditingController _phoneCtrl;
  late final TextEditingController _departmentCtrl;
  late bool _canDiscount;
  late bool _canDischarge;
  late bool _canLab;

  @override
  void initState() {
    super.initState();
    _phoneCtrl = TextEditingController(text: widget.member.phone);
    _departmentCtrl = TextEditingController(text: widget.member.department);
    _canDiscount = widget.member.canApproveDiscount;
    _canDischarge = widget.member.canApproveDischargeSummary;
    _canLab = widget.member.canApproveLabReport;
  }

  @override
  void dispose() {
    _phoneCtrl.dispose();
    _departmentCtrl.dispose();
    super.dispose();
  }

  Widget _permission(String label, bool value, ValueChanged<bool> onChanged) {
    return FilterChip(
      label: Text(label, style: Theme.of(context).textTheme.labelSmall),
      selected: value,
      visualDensity: VisualDensity.compact,
      onSelected: onChanged,
    );
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Expanded(
              child: TextField(
                controller: _phoneCtrl,
                decoration: const InputDecoration(labelText: 'Phone'),
              ),
            ),
            const SizedBox(width: Space.md),
            Expanded(
              child: TextField(
                controller: _departmentCtrl,
                decoration: const InputDecoration(labelText: 'Department'),
              ),
            ),
          ],
        ),
        const SizedBox(height: Space.md),
        Wrap(
          spacing: Space.sm,
          children: [
            _permission('Approve discount', _canDiscount,
                (v) => setState(() => _canDiscount = v)),
            _permission('Approve discharge', _canDischarge,
                (v) => setState(() => _canDischarge = v)),
            _permission('Approve lab report', _canLab,
                (v) => setState(() => _canLab = v)),
          ],
        ),
        const SizedBox(height: Space.sm),
        Row(
          mainAxisAlignment: MainAxisAlignment.end,
          children: [
            TextButton(
              onPressed: () => widget.onDeactivate(widget.member),
              style:
                  TextButton.styleFrom(foregroundColor: StatusColors.danger),
              child: const Text('Deactivate'),
            ),
            const SizedBox(width: Space.sm),
            FilledButton(
              onPressed: () => widget.onSave(
                widget.member,
                UpdateStaffRequest(
                  phone: _phoneCtrl.text.trim(),
                  department: _departmentCtrl.text.trim(),
                  canApproveDiscount: _canDiscount,
                  canApproveDischargeSummary: _canDischarge,
                  canApproveLabReport: _canLab,
                ),
              ),
              child: const Text('Save'),
            ),
          ],
        ),
      ],
    );
  }
}

/// Compact add-staff dialog: two-column rows to stay short.
class _AddStaffDialog extends StatefulWidget {
  const _AddStaffDialog({required this.onSubmit});

  final void Function(CreateStaffRequest) onSubmit;

  @override
  State<_AddStaffDialog> createState() => _AddStaffDialogState();
}

class _AddStaffDialogState extends State<_AddStaffDialog> {
  final _firstCtrl = TextEditingController();
  final _lastCtrl = TextEditingController();
  final _emailCtrl = TextEditingController();
  final _phoneCtrl = TextEditingController();
  final _departmentCtrl = TextEditingController();
  String _role = 'NURSE';
  String? _error;

  static const _roles = [
    'DOCTOR',
    'NURSE',
    'NURSE_SUPERVISOR',
    'LAB_TECHNICIAN',
    'RADIOLOGIST',
    'PHARMACIST',
    'FRONT_DESK',
  ];

  @override
  void dispose() {
    _firstCtrl.dispose();
    _lastCtrl.dispose();
    _emailCtrl.dispose();
    _phoneCtrl.dispose();
    _departmentCtrl.dispose();
    super.dispose();
  }

  void _submit() {
    if (_firstCtrl.text.trim().isEmpty ||
        _lastCtrl.text.trim().isEmpty ||
        _emailCtrl.text.trim().isEmpty ||
        _phoneCtrl.text.trim().isEmpty ||
        _departmentCtrl.text.trim().isEmpty) {
      setState(() => _error = 'All fields are required');
      return;
    }
    widget.onSubmit(CreateStaffRequest(
      firstName: _firstCtrl.text.trim(),
      lastName: _lastCtrl.text.trim(),
      email: _emailCtrl.text.trim(),
      phone: _phoneCtrl.text.trim(),
      role: _role,
      department: _departmentCtrl.text.trim(),
    ));
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Add Staff Member'),
      content: SizedBox(
        width: 420,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (_error != null) ...[
              MessageBanner.error(_error!),
              const SizedBox(height: Space.md),
            ],
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _firstCtrl,
                    decoration:
                        const InputDecoration(labelText: 'First name *'),
                  ),
                ),
                const SizedBox(width: Space.md),
                Expanded(
                  child: TextField(
                    controller: _lastCtrl,
                    decoration:
                        const InputDecoration(labelText: 'Last name *'),
                  ),
                ),
              ],
            ),
            const SizedBox(height: Space.md),
            TextField(
              controller: _emailCtrl,
              keyboardType: TextInputType.emailAddress,
              decoration: const InputDecoration(labelText: 'Email *'),
            ),
            const SizedBox(height: Space.md),
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _phoneCtrl,
                    decoration: const InputDecoration(labelText: 'Phone *'),
                  ),
                ),
                const SizedBox(width: Space.md),
                Expanded(
                  child: TextField(
                    controller: _departmentCtrl,
                    decoration:
                        const InputDecoration(labelText: 'Department *'),
                  ),
                ),
              ],
            ),
            const SizedBox(height: Space.md),
            DropdownButtonFormField<String>(
              value: _role,
              decoration: const InputDecoration(labelText: 'Role *'),
              items: [
                for (final r in _roles)
                  DropdownMenuItem(
                      value: r, child: Text(r.replaceAll('_', ' '))),
              ],
              onChanged: (v) => setState(() => _role = v ?? 'NURSE'),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Cancel'),
        ),
        FilledButton(onPressed: _submit, child: const Text('Add')),
      ],
    );
  }
}
