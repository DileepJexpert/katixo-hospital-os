import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/api/staff_models.dart';
import '../../core/theme/design_tokens.dart';

class StaffManagementScreen extends StatefulWidget {
  const StaffManagementScreen({super.key});

  @override
  State<StaffManagementScreen> createState() => _StaffManagementScreenState();
}

class _StaffManagementScreenState extends State<StaffManagementScreen> {
  List<StaffResponse> _staff = [];
  bool _loading = false;
  String? _error;

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
        '/api/v1/staff',
        fromJson: (json) {
          if (json is List) {
            return json
                .map((s) => StaffResponse.fromJson(s as Map<String, dynamic>))
                .toList();
          }
          return [];
        },
      );
      setState(() {
        _staff = staff;
        _error = null;
      });
    } catch (e) {
      setState(() => _error = 'Failed to load staff: $e');
    } finally {
      setState(() => _loading = false);
    }
  }

  void _showAddStaffDialog() {
    showDialog(
      context: context,
      builder: (context) => _AddStaffDialog(
        onAdd: (request) async {
          try {
            final api = context.read<ApiClient>();
            await api.post(
              '/api/v1/staff',
              request.toJson(),
              fromJson: (json) =>
                  StaffResponse.fromJson(json as Map<String, dynamic>),
            );
            _loadStaff();
            if (mounted) Navigator.pop(context);
            if (mounted) {
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text('Staff member added')),
              );
            }
          } catch (e) {
            if (mounted) {
              ScaffoldMessenger.of(context)
                  .showSnackBar(SnackBar(content: Text('Error: $e')));
            }
          }
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(Space.md),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text('Staff Management',
                  style: Theme.of(context).textTheme.titleLarge),
              ElevatedButton.icon(
                onPressed: _showAddStaffDialog,
                icon: const Icon(Icons.add),
                label: const Text('Add Staff'),
              ),
            ],
          ),
          const SizedBox(height: Space.md),
          if (_error != null)
            Padding(
              padding: const EdgeInsets.only(bottom: Space.md),
              child: Text(_error!, style: const TextStyle(color: Colors.red)),
            ),
          Expanded(
            child: _loading
                ? const Center(child: CircularProgressIndicator())
                : _staff.isEmpty
                    ? const Center(child: Text('No staff members'))
                    : ListView.builder(
                        itemCount: _staff.length,
                        itemBuilder: (context, index) {
                          final member = _staff[index];
                          return _StaffCard(
                            staff: member,
                            onTap: () {
                              Navigator.push(
                                context,
                                MaterialPageRoute(
                                  builder: (context) => _StaffDetailScreen(
                                    staff: member,
                                    onUpdate: _loadStaff,
                                  ),
                                ),
                              );
                            },
                          );
                        },
                      ),
          ),
        ],
      ),
    );
  }
}

class _StaffCard extends StatelessWidget {
  final StaffResponse staff;
  final VoidCallback onTap;

  const _StaffCard({required this.staff, required this.onTap});

  Color _getRoleColor(String role) {
    switch (role) {
      case 'DOCTOR':
        return Colors.blue;
      case 'NURSE':
        return Colors.green;
      case 'ADMIN':
        return Colors.red;
      case 'FRONT_DESK':
        return Colors.orange;
      default:
        return Colors.grey;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: Space.sm),
      child: ListTile(
        title: Text(staff.fullName),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const SizedBox(height: Space.xs),
            Text(staff.email),
            Text(staff.department),
            const SizedBox(height: Space.xs),
            Chip(
              label: Text(staff.role),
              backgroundColor: _getRoleColor(staff.role),
              labelStyle: const TextStyle(color: Colors.white),
            ),
          ],
        ),
        isThreeLine: true,
        trailing: Chip(
          label: Text(staff.statusDisplay),
          backgroundColor: staff.isActive ? Colors.green : Colors.grey,
          labelStyle: const TextStyle(color: Colors.white),
        ),
        onTap: onTap,
      ),
    );
  }
}

class _StaffDetailScreen extends StatefulWidget {
  final StaffResponse staff;
  final VoidCallback onUpdate;

  const _StaffDetailScreen({required this.staff, required this.onUpdate});

  @override
  State<_StaffDetailScreen> createState() => _StaffDetailScreenState();
}

class _StaffDetailScreenState extends State<_StaffDetailScreen> {
  late StaffResponse _staff;
  bool _isLoading = false;
  late TextEditingController _phoneController;
  late TextEditingController _departmentController;
  bool _canApproveDiscount = false;
  bool _canApproveDischargeSummary = false;
  bool _canApproveLabReport = false;

  @override
  void initState() {
    super.initState();
    _staff = widget.staff;
    _phoneController = TextEditingController(text: _staff.phone);
    _departmentController =
        TextEditingController(text: _staff.department);
    _canApproveDiscount = _staff.canApproveDiscount;
    _canApproveDischargeSummary = _staff.canApproveDischargeSummary;
    _canApproveLabReport = _staff.canApproveLabReport;
  }

  @override
  void dispose() {
    _phoneController.dispose();
    _departmentController.dispose();
    super.dispose();
  }

  Future<void> _updateStaff() async {
    setState(() => _isLoading = true);
    try {
      final api = context.read<ApiClient>();
      final updated = await api.put(
        '/api/v1/staff/${_staff.id}',
        UpdateStaffRequest(
          phone: _phoneController.text,
          department: _departmentController.text,
          canApproveDiscount: _canApproveDiscount,
          canApproveDischargeSummary: _canApproveDischargeSummary,
          canApproveLabReport: _canApproveLabReport,
        ).toJson(),
        fromJson: (json) =>
            StaffResponse.fromJson(json as Map<String, dynamic>),
      );
      setState(() => _staff = updated);
      widget.onUpdate();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Staff updated')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('Error: $e')));
      }
    } finally {
      setState(() => _isLoading = false);
    }
  }

  Future<void> _deactivateStaff() async {
    setState(() => _isLoading = true);
    try {
      final api = context.read<ApiClient>();
      await api.delete(
        '/api/v1/staff/${_staff.id}',
        fromJson: (_) => null,
      );
      widget.onUpdate();
      if (mounted) {
        Navigator.pop(context);
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Staff deactivated')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('Error: $e')));
      }
    } finally {
      setState(() => _isLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(_staff.fullName)),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : SingleChildScrollView(
              child: Padding(
                padding: const EdgeInsets.all(Space.md),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Card(
                      child: Padding(
                        padding: const EdgeInsets.all(Space.md),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            _buildRow('Email', _staff.email),
                            _buildRow('Role', _staff.role),
                            _buildRow('Status', _staff.statusDisplay),
                            const Divider(),
                            TextField(
                              controller: _phoneController,
                              decoration: const InputDecoration(
                                labelText: 'Phone',
                              ),
                            ),
                            const SizedBox(height: Space.md),
                            TextField(
                              controller: _departmentController,
                              decoration: const InputDecoration(
                                labelText: 'Department',
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: Space.md),
                    Text('Permissions',
                        style: Theme.of(context).textTheme.titleMedium),
                    const SizedBox(height: Space.md),
                    CheckboxListTile(
                      title: const Text('Can Approve Discount'),
                      value: _canApproveDiscount,
                      onChanged: (value) {
                        setState(() => _canApproveDiscount = value ?? false);
                      },
                    ),
                    CheckboxListTile(
                      title: const Text('Can Approve Discharge Summary'),
                      value: _canApproveDischargeSummary,
                      onChanged: (value) {
                        setState(
                            () => _canApproveDischargeSummary = value ?? false);
                      },
                    ),
                    CheckboxListTile(
                      title: const Text('Can Approve Lab Report'),
                      value: _canApproveLabReport,
                      onChanged: (value) {
                        setState(() => _canApproveLabReport = value ?? false);
                      },
                    ),
                    const SizedBox(height: Space.lg),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                      children: [
                        ElevatedButton(
                          onPressed: _updateStaff,
                          child: const Text('Save Changes'),
                        ),
                        ElevatedButton(
                          onPressed: _deactivateStaff,
                          style: ElevatedButton.styleFrom(
                            backgroundColor: Colors.red,
                          ),
                          child: const Text('Deactivate'),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
    );
  }

  Widget _buildRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.only(bottom: Space.sm),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: const TextStyle(fontWeight: FontWeight.bold)),
          Text(value),
        ],
      ),
    );
  }
}

class _AddStaffDialog extends StatefulWidget {
  final Function(CreateStaffRequest) onAdd;

  const _AddStaffDialog({required this.onAdd});

  @override
  State<_AddStaffDialog> createState() => _AddStaffDialogState();
}

class _AddStaffDialogState extends State<_AddStaffDialog> {
  late TextEditingController _firstNameController;
  late TextEditingController _lastNameController;
  late TextEditingController _emailController;
  late TextEditingController _phoneController;
  late TextEditingController _departmentController;
  String _selectedRole = 'NURSE';

  @override
  void initState() {
    super.initState();
    _firstNameController = TextEditingController();
    _lastNameController = TextEditingController();
    _emailController = TextEditingController();
    _phoneController = TextEditingController();
    _departmentController = TextEditingController();
  }

  @override
  void dispose() {
    _firstNameController.dispose();
    _lastNameController.dispose();
    _emailController.dispose();
    _phoneController.dispose();
    _departmentController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Add Staff Member'),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: _firstNameController,
              decoration: const InputDecoration(labelText: 'First Name'),
            ),
            const SizedBox(height: Space.md),
            TextField(
              controller: _lastNameController,
              decoration: const InputDecoration(labelText: 'Last Name'),
            ),
            const SizedBox(height: Space.md),
            TextField(
              controller: _emailController,
              decoration: const InputDecoration(labelText: 'Email'),
              keyboardType: TextInputType.emailAddress,
            ),
            const SizedBox(height: Space.md),
            TextField(
              controller: _phoneController,
              decoration: const InputDecoration(labelText: 'Phone'),
            ),
            const SizedBox(height: Space.md),
            TextField(
              controller: _departmentController,
              decoration: const InputDecoration(labelText: 'Department'),
            ),
            const SizedBox(height: Space.md),
            DropdownButton<String>(
              value: _selectedRole,
              isExpanded: true,
              items: const [
                'DOCTOR',
                'NURSE',
                'LAB_TECHNICIAN',
                'RADIOLOGIST',
                'PHARMACIST',
                'FRONT_DESK',
              ]
                  .map((r) => DropdownMenuItem(value: r, child: Text(r)))
                  .toList(),
              onChanged: (value) {
                if (value != null) setState(() => _selectedRole = value);
              },
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Cancel'),
        ),
        ElevatedButton(
          onPressed: () {
            widget.onAdd(
              CreateStaffRequest(
                firstName: _firstNameController.text,
                lastName: _lastNameController.text,
                email: _emailController.text,
                phone: _phoneController.text,
                role: _selectedRole,
                department: _departmentController.text,
              ),
            );
          },
          child: const Text('Add'),
        ),
      ],
    );
  }
}
