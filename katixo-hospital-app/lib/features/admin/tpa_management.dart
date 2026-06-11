import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/api/tpa_models.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/app_shell.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

class TPAManagementScreen extends StatefulWidget {
  const TPAManagementScreen({super.key});

  @override
  State<TPAManagementScreen> createState() => _TPAManagementScreenState();
}

class _TPAManagementScreenState extends State<TPAManagementScreen> {
  List<TPACase> _cases = [];
  bool _loading = false;
  String _selectedStatus = 'REGISTERED';
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadCases();
  }

  Future<void> _loadCases() async {
    setState(() => _loading = true);

    try {
      final api = context.read<ApiClient>();
      final response = await api.get<List<TPACase>>(
        '/api/v1/tpa/cases/status/$_selectedStatus',
        fromJson: (json) {
          if (json is List) {
            return json
                .map((item) => TPACase.fromJson(item as Map<String, dynamic>))
                .toList();
          }
          return [];
        },
      );
      setState(() {
        _cases = response;
        _error = null;
      });
    } catch (e) {
      setState(() => _error = 'Failed to load cases: $e');
    } finally {
      setState(() => _loading = false);
    }
  }

  void _showRegisterDialog() {
    showDialog(
      context: context,
      builder: (context) => _RegisterCaseDialog(
        onRegister: (request) async {
          try {
            final api = context.read<ApiClient>();
            await api.post(
              '/api/v1/tpa/cases',
              request.toJson(),
              fromJson: (json) => TPACase.fromJson(json as Map<String, dynamic>),
            );
            _loadCases();
            if (mounted) Navigator.pop(context);
            if (mounted) {
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text('Case registered successfully')),
              );
            }
          } catch (e) {
            if (mounted) {
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(content: Text('Error: $e')),
              );
            }
          }
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(Space.md),
          child: Row(
            children: [
              Expanded(
                child: DropdownButton<String>(
                  value: _selectedStatus,
                  isExpanded: true,
                  items: const [
                    'REGISTERED',
                    'PREAUTH_PENDING',
                    'PREAUTH_APPROVED',
                    'CLAIM_SUBMITTED',
                  ]
                      .map((status) =>
                          DropdownMenuItem(value: status, child: Text(status)))
                      .toList(),
                  onChanged: (value) {
                    if (value != null) {
                      setState(() => _selectedStatus = value);
                      _loadCases();
                    }
                  },
                ),
              ),
              const SizedBox(width: Space.md),
              ElevatedButton.icon(
                onPressed: _showRegisterDialog,
                icon: const Icon(Icons.add),
                label: const Text('Register Case'),
              ),
            ],
          ),
        ),
        if (_error != null) ...[
          MessageBanner.error(_error!),
          const SizedBox(height: Space.md),
        ],
        Expanded(
          child: _loading
              ? const Center(child: CircularProgressIndicator())
              : _cases.isEmpty
                  ? const Center(child: Text('No cases found'))
                  : ListView.builder(
                      itemCount: _cases.length,
                      itemBuilder: (context, index) {
                        final tpaCase = _cases[index];
                        return _TPACaseCard(
                          tpaCase: tpaCase,
                          onTap: () {
                            Navigator.push(
                              context,
                              MaterialPageRoute(
                                builder: (context) => _TPACaseDetailScreen(
                                  tpaCase: tpaCase,
                                  onUpdate: _loadCases,
                                ),
                              ),
                            );
                          },
                        );
                      },
                    ),
        ),
      ],
    );
  }
}

class _TPACaseCard extends StatelessWidget {
  final TPACase tpaCase;
  final VoidCallback onTap;

  const _TPACaseCard({
    required this.tpaCase,
    required this.onTap,
  });

  Color _getStatusColor(String status) {
    switch (status) {
      case 'REGISTERED':
        return Colors.blue;
      case 'PREAUTH_PENDING':
        return Colors.orange;
      case 'PREAUTH_APPROVED':
      case 'CLAIM_APPROVED':
        return Colors.green;
      case 'PREAUTH_REJECTED':
      case 'CLAIM_REJECTED':
        return Colors.red;
      default:
        return Colors.grey;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.all(Space.sm),
      child: ListTile(
        title: Text('${tpaCase.caseNumber}'),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const SizedBox(height: Space.xs),
            Text('${tpaCase.insurerName} - ${tpaCase.policyNumber}'),
            const SizedBox(height: Space.xs),
            Chip(
              label: Text(tpaCase.statusDisplay),
              backgroundColor: _getStatusColor(tpaCase.caseStatus),
              labelStyle: const TextStyle(color: Colors.white),
            ),
            if (tpaCase.pendingDocumentCount > 0) ...[
              const SizedBox(height: Space.xs),
              Text(
                '${tpaCase.pendingDocumentCount} documents pending',
                style: const TextStyle(color: Colors.orange),
              ),
            ],
          ],
        ),
        isThreeLine: true,
        onTap: onTap,
      ),
    );
  }
}

class _TPACaseDetailScreen extends StatefulWidget {
  final TPACase initialCase;
  final VoidCallback onUpdate;

  const _TPACaseDetailScreen({
    required this.initialCase,
    required this.onUpdate,
  });

  @override
  State<_TPACaseDetailScreen> createState() => _TPACaseDetailScreenState();
}

class _TPACaseDetailScreenState extends State<_TPACaseDetailScreen> {
  late TPACase _tpaCase;
  bool _isLoading = false;

  @override
  void initState() {
    super.initState();
    _tpaCase = widget.initialCase;
  }

  Future<void> _submitPreauth() async {
    final refNum = await showDialog<String>(
      context: context,
      builder: (context) => _PreauthRefDialog(),
    );

    if (refNum == null || refNum.isEmpty) return;

    setState(() => _isLoading = true);
    try {
      final api = context.read<ApiClient>();
      final updatedCase = await api.post(
        '/api/v1/tpa/cases/${_tpaCase.id}/preauth',
        SubmitPreauthRequest(preauthRefNumber: refNum).toJson(),
        fromJson: (json) => TPACase.fromJson(json as Map<String, dynamic>),
      );
      setState(() => _tpaCase = updatedCase);
      widget.onUpdate();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Preauth submitted')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error: $e')),
        );
      }
    } finally {
      setState(() => _isLoading = false);
    }
  }

  List<Widget> _getActionButtons() {
    if (_isLoading) return [];

    final buttons = <Widget>[];
    switch (_tpaCase.caseStatus) {
      case 'REGISTERED':
        buttons.add(
          ElevatedButton.icon(
            onPressed: _submitPreauth,
            icon: const Icon(Icons.upload),
            label: const Text('Submit Preauth'),
          ),
        );
        break;
      case 'PREAUTH_PENDING':
        buttons.addAll([
          ElevatedButton.icon(
            onPressed: () {
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text('Feature coming soon')),
              );
            },
            icon: const Icon(Icons.check),
            label: const Text('Approve'),
          ),
          const SizedBox(width: Space.sm),
          ElevatedButton.icon(
            onPressed: () {
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text('Feature coming soon')),
              );
            },
            icon: const Icon(Icons.close),
            label: const Text('Reject'),
            style: ElevatedButton.styleFrom(backgroundColor: Colors.red),
          ),
        ]);
        break;
      case 'PREAUTH_APPROVED':
        buttons.add(
          ElevatedButton.icon(
            onPressed: () {
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text('Feature coming soon')),
              );
            },
            icon: const Icon(Icons.receipt),
            label: const Text('Submit Claim'),
          ),
        );
        break;
    }
    return buttons;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('Case: ${_tpaCase.caseNumber}'),
      ),
      body: SingleChildScrollView(
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
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Text(
                            _tpaCase.statusDisplay,
                            style: Theme.of(context).textTheme.titleMedium,
                          ),
                          Chip(
                            label: Text(
                              '${_tpaCase.submittedDocumentCount}/${_tpaCase.documents.length} docs',
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: Space.md),
                      _buildRow('Case', _tpaCase.caseNumber),
                      _buildRow('Insurer', _tpaCase.insurerName),
                      _buildRow('Policy', _tpaCase.policyNumber),
                      if (_tpaCase.sumInsured != null)
                        _buildRow(
                          'Sum Insured',
                          '₹${_tpaCase.sumInsured!.toStringAsFixed(0)}',
                        ),
                      if (_tpaCase.approvedAmount != null)
                        _buildRow(
                          'Approved',
                          '₹${_tpaCase.approvedAmount!.toStringAsFixed(0)}',
                        ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: Space.md),
              if (_tpaCase.documents.isNotEmpty) ...[
                Text(
                  'Documents',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                const SizedBox(height: Space.md),
                ..._tpaCase.documents.map((doc) => Padding(
                      padding: const EdgeInsets.only(bottom: Space.sm),
                      child: Row(
                        children: [
                          Icon(
                            doc.submitted
                                ? Icons.check_circle
                                : Icons.circle_outlined,
                            color: doc.submitted ? Colors.green : Colors.orange,
                          ),
                          const SizedBox(width: Space.sm),
                          Expanded(child: Text(doc.documentType)),
                        ],
                      ),
                    )),
                const SizedBox(height: Space.md),
              ],
              Wrap(spacing: Space.sm, children: _getActionButtons()),
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

class _PreauthRefDialog extends StatefulWidget {
  @override
  State<_PreauthRefDialog> createState() => _PreauthRefDialogState();
}

class _PreauthRefDialogState extends State<_PreauthRefDialog> {
  late TextEditingController _controller;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Submit Preauth'),
      content: TextField(
        controller: _controller,
        decoration: const InputDecoration(
          labelText: 'Preauth Reference Number',
          hintText: 'From insurer',
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Cancel'),
        ),
        ElevatedButton(
          onPressed: () => Navigator.pop(context, _controller.text),
          child: const Text('Submit'),
        ),
      ],
    );
  }
}

class _RegisterCaseDialog extends StatefulWidget {
  final Function(RegisterTPACaseRequest) onRegister;

  const _RegisterCaseDialog({required this.onRegister});

  @override
  State<_RegisterCaseDialog> createState() => _RegisterCaseDialogState();
}

class _RegisterCaseDialogState extends State<_RegisterCaseDialog> {
  late TextEditingController _admissionController;
  late TextEditingController _insurerController;
  late TextEditingController _policyController;

  @override
  void initState() {
    super.initState();
    _admissionController = TextEditingController();
    _insurerController = TextEditingController();
    _policyController = TextEditingController();
  }

  @override
  void dispose() {
    _admissionController.dispose();
    _insurerController.dispose();
    _policyController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Register TPA Case'),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: _admissionController,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(labelText: 'Admission ID'),
            ),
            const SizedBox(height: Space.md),
            TextField(
              controller: _insurerController,
              decoration: const InputDecoration(labelText: 'Insurer Name'),
            ),
            const SizedBox(height: Space.md),
            TextField(
              controller: _policyController,
              decoration: const InputDecoration(labelText: 'Policy Number'),
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
            final request = RegisterTPACaseRequest(
              admissionId: int.tryParse(_admissionController.text) ?? 0,
              patientId: 0,
              insurerName: _insurerController.text,
              policyNumber: _policyController.text,
              requiredDocuments: [
                'Discharge Summary',
                'Lab Reports',
                'Imaging',
              ],
            );
            widget.onRegister(request);
            Navigator.pop(context);
          },
          child: const Text('Register'),
        ),
      ],
    );
  }
}
