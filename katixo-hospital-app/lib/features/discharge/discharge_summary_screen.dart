import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/discharge_models.dart';
import '../../core/api/http_client.dart';
import '../../core/theme/design_tokens.dart';

class DischargeSummaryScreen extends StatefulWidget {
  final int admissionId;
  final int patientId;
  final bool isViewOnly;

  const DischargeSummaryScreen({
    required this.admissionId,
    required this.patientId,
    this.isViewOnly = false,
    super.key,
  });

  @override
  State<DischargeSummaryScreen> createState() => _DischargeSummaryScreenState();
}

class _DischargeSummaryScreenState extends State<DischargeSummaryScreen> {
  DischargeSummaryResponse? _summary;
  bool _loading = true;
  String? _error;
  bool _isEditing = false;

  late TextEditingController _diagnosisController;
  late TextEditingController _treatmentController;
  late TextEditingController _medicationsController;
  late TextEditingController _followUpController;

  @override
  void initState() {
    super.initState();
    _diagnosisController = TextEditingController();
    _treatmentController = TextEditingController();
    _medicationsController = TextEditingController();
    _followUpController = TextEditingController();
    _loadSummary();
  }

  @override
  void dispose() {
    _diagnosisController.dispose();
    _treatmentController.dispose();
    _medicationsController.dispose();
    _followUpController.dispose();
    super.dispose();
  }

  Future<void> _loadSummary() async {
    setState(() => _loading = true);
    try {
      final api = context.read<ApiClient>();
      final summary = await api.get<DischargeSummaryResponse>(
        '/api/v1/discharge/admissions/${widget.admissionId}',
        fromJson: (json) =>
            DischargeSummaryResponse.fromJson(json as Map<String, dynamic>),
      );
      setState(() {
        _summary = summary;
        _diagnosisController.text = summary.diagnosis ?? '';
        _treatmentController.text = summary.treatmentSummary ?? '';
        _medicationsController.text = summary.medications ?? '';
        _followUpController.text = summary.followUpInstructions ?? '';
        _error = null;
      });
    } catch (e) {
      setState(() {
        if (e.toString().contains('404')) {
          _summary = null;
        } else {
          _error = 'Failed to load discharge summary: $e';
        }
      });
    } finally {
      setState(() => _loading = false);
    }
  }

  Future<void> _createSummary() async {
    try {
      final api = context.read<ApiClient>();
      final request = CreateDischargeSummaryRequest(
        admissionId: widget.admissionId,
        patientId: widget.patientId,
        diagnosis: _diagnosisController.text,
        treatmentSummary: _treatmentController.text,
        medications: _medicationsController.text,
        followUpInstructions: _followUpController.text,
      );

      final summary = await api.post(
        '/api/v1/discharge/summaries',
        request.toJson(),
        fromJson: (json) =>
            DischargeSummaryResponse.fromJson(json as Map<String, dynamic>),
      );

      setState(() => _summary = summary);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Discharge summary created')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('Error: $e')));
      }
    }
  }

  Future<void> _updateSummary() async {
    try {
      final api = context.read<ApiClient>();
      final request = UpdateDischargeSummaryRequest(
        diagnosis: _diagnosisController.text,
        treatmentSummary: _treatmentController.text,
        medications: _medicationsController.text,
        followUpInstructions: _followUpController.text,
      );

      final updated = await api.put(
        '/api/v1/discharge/summaries/${_summary!.id}',
        request.toJson(),
        fromJson: (json) =>
            DischargeSummaryResponse.fromJson(json as Map<String, dynamic>),
      );

      setState(() {
        _summary = updated;
        _isEditing = false;
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Discharge summary updated')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('Error: $e')));
      }
    }
  }

  Future<void> _submitForApproval() async {
    try {
      final api = context.read<ApiClient>();
      final updated = await api.post(
        '/api/v1/discharge/summaries/${_summary!.id}/submit',
        {},
        fromJson: (json) =>
            DischargeSummaryResponse.fromJson(json as Map<String, dynamic>),
      );

      setState(() => _summary = updated);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Discharge summary submitted for approval')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('Error: $e')));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Discharge Summary'),
        actions: [
          if (!widget.isViewOnly &&
              _summary != null &&
              _summary!.dischargeStatus == 'DRAFT' &&
              !_isEditing)
            TextButton(
              onPressed: () => setState(() => _isEditing = true),
              child: const Text('Edit'),
            ),
          if (_isEditing)
            TextButton(
              onPressed: _updateSummary,
              child: const Text('Save'),
            ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(child: Text(_error!))
              : _summary == null
                  ? Center(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          const Text('No discharge summary yet'),
                          const SizedBox(height: Space.md),
                          if (!widget.isViewOnly)
                            ElevatedButton(
                              onPressed: () {
                                setState(() => _isEditing = true);
                                _createSummary();
                              },
                              child: const Text('Create Summary'),
                            ),
                        ],
                      ),
                    )
                  : SingleChildScrollView(
                      child: Padding(
                        padding: const EdgeInsets.all(Space.md),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            _buildStatusCard(),
                            const SizedBox(height: Space.lg),
                            if (_isEditing)
                              _buildEditForm()
                            else
                              _buildSummaryView(),
                            if (!widget.isViewOnly &&
                                _summary!.dischargeStatus == 'DRAFT' &&
                                !_isEditing)
                              Padding(
                                padding: const EdgeInsets.only(top: Space.lg),
                                child: ElevatedButton.icon(
                                  onPressed: _submitForApproval,
                                  icon: const Icon(Icons.upload),
                                  label: const Text('Submit for Approval'),
                                ),
                              ),
                          ],
                        ),
                      ),
                    ),
    );
  }

  Widget _buildStatusCard() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(Space.md),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Status', style: Theme.of(context).textTheme.labelMedium),
                Text(_summary!.statusDisplay,
                    style: Theme.of(context).textTheme.titleMedium),
              ],
            ),
            Chip(
              label: Text(_summary!.dischargeType),
              backgroundColor: Colors.blue.shade100,
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildEditForm() {
    return Column(
      children: [
        _buildField('Diagnosis', _diagnosisController, maxLines: 4),
        const SizedBox(height: Space.md),
        _buildField('Treatment Summary', _treatmentController, maxLines: 4),
        const SizedBox(height: Space.md),
        _buildField('Medications', _medicationsController, maxLines: 3),
        const SizedBox(height: Space.md),
        _buildField('Follow-up Instructions', _followUpController, maxLines: 3),
      ],
    );
  }

  Widget _buildSummaryView() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _buildSection('Diagnosis', _summary!.diagnosis),
        _buildSection('Treatment Summary', _summary!.treatmentSummary),
        _buildSection('Medications', _summary!.medications),
        _buildSection('Follow-up Instructions', _summary!.followUpInstructions),
        if (_summary!.restrictions != null)
          _buildSection('Restrictions', _summary!.restrictions),
        if (_summary!.warningSymptoms != null)
          _buildSection('Warning Symptoms', _summary!.warningSymptoms),
      ],
    );
  }

  Widget _buildField(
    String label,
    TextEditingController controller, {
    int maxLines = 1,
  }) {
    return TextField(
      controller: controller,
      maxLines: maxLines,
      decoration: InputDecoration(
        labelText: label,
        border: const OutlineInputBorder(),
      ),
    );
  }

  Widget _buildSection(String title, String? content) {
    if (content == null || content.isEmpty) return const SizedBox();

    return Padding(
      padding: const EdgeInsets.only(bottom: Space.lg),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: Space.sm),
          Container(
            padding: const EdgeInsets.all(Space.md),
            decoration: BoxDecoration(
              border: Border.all(color: Colors.grey.shade300),
              borderRadius: BorderRadius.circular(Corners.sm),
            ),
            child: Text(content),
          ),
        ],
      ),
    );
  }
}
