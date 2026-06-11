import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/api/lab_models.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// Lab orders panel for doctor consultation.
class LabOrdersPanel extends StatefulWidget {
  const LabOrdersPanel({
    super.key,
    required this.visitId,
  });

  final int visitId;

  @override
  State<LabOrdersPanel> createState() => _LabOrdersPanelState();
}

class _LabOrdersPanelState extends State<LabOrdersPanel> {
  List<LabTest> _availableTests = [];
  List<String> _selectedTestCodes = [];
  bool _loading = false;
  String? _error;
  String? _success;

  @override
  void initState() {
    super.initState();
    _loadTests();
  }

  Future<void> _loadTests() async {
    try {
      final api = context.read<ApiClient>();
      final tests = await api.get<List<LabTest>>(
        '/api/v1/lab/tests',
        fromJson: (json) => (json as List)
            .map((e) => LabTest.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
      if (mounted) setState(() => _availableTests = tests);
    } catch (e) {
      if (mounted) setState(() => _error = 'Failed to load tests: $e');
    }
  }

  Future<void> _orderTests() async {
    if (_selectedTestCodes.isEmpty) {
      setState(() => _error = 'Select at least one test');
      return;
    }

    setState(() {
      _loading = true;
      _error = null;
      _success = null;
    });

    try {
      final api = context.read<ApiClient>();
      final request = CreateOrderRequest(
        sourceType: 'OPD_VISIT',
        sourceId: visitId,
        testCodes: _selectedTestCodes,
      );

      await api.post<dynamic>(
        '/api/v1/lab/orders',
        request.toJson(),
        fromJson: (json) => json,
      );

      setState(() {
        _success = 'Lab order created for ${_selectedTestCodes.length} test(s)';
        _selectedTestCodes.clear();
      });
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('Lab Tests', style: theme.textTheme.titleMedium),
        const SizedBox(height: Space.md),

        if (_error != null) ...[
          MessageBanner.error(_error!),
          const SizedBox(height: Space.md),
        ],

        if (_success != null) ...[
          MessageBanner.success(_success!),
          const SizedBox(height: Space.md),
        ],

        if (_availableTests.isEmpty)
          Text('No tests available',
              style: theme.textTheme.bodySmall
                  ?.copyWith(color: theme.colorScheme.onSurfaceVariant))
        else ...[
          // Test selection grid
          Wrap(
            spacing: Space.md,
            runSpacing: Space.md,
            children: [
              for (var test in _availableTests)
                FilterChip(
                  selected: _selectedTestCodes.contains(test.testCode),
                  label: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(test.testName, style: const TextStyle(fontSize: 12)),
                      Text('₹${test.rate}',
                          style: theme.textTheme.labelSmall),
                    ],
                  ),
                  onSelected: (selected) {
                    setState(() {
                      if (selected) {
                        _selectedTestCodes.add(test.testCode);
                      } else {
                        _selectedTestCodes.remove(test.testCode);
                      }
                    });
                  },
                ),
            ],
          ),
          const SizedBox(height: Space.md),
          if (_selectedTestCodes.isNotEmpty) ...[
            Card(
              child: Padding(
                padding: const EdgeInsets.all(Space.md),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Selected Tests',
                        style: theme.textTheme.labelLarge),
                    const SizedBox(height: Space.sm),
                    for (var code in _selectedTestCodes) ...[
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Text(_availableTests
                              .firstWhere(
                                  (t) => t.testCode == code,
                                  orElse: () =>
                                      const LabTest(
                                        id: 0,
                                        testCode: '',
                                        testName: 'Unknown',
                                        specimenType: '',
                                        rate: 0,
                                      ))
                              .testName),
                          Text(
                              '₹${_availableTests.firstWhere((t) => t.testCode == code, orElse: () => const LabTest(id: 0, testCode: '', testName: '', specimenType: '', rate: 0)).rate}'),
                        ],
                      ),
                    ],
                    const Divider(),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text('Total',
                            style: theme.textTheme.labelLarge
                                ?.copyWith(fontWeight: FontWeight.bold)),
                        Text(
                          '₹${_selectedTestCodes.fold<num>(0, (sum, code) => sum + (_availableTests.firstWhere((t) => t.testCode == code, orElse: () => const LabTest(id: 0, testCode: '', testName: '', specimenType: '', rate: 0)).rate))}',
                          style: theme.textTheme.labelLarge
                              ?.copyWith(fontWeight: FontWeight.bold),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: Space.md),
            FilledButton.icon(
              onPressed: _loading ? null : _orderTests,
              icon: const Icon(Icons.science_outlined, size: 18),
              label: const Text('Order Tests'),
            ),
          ],
        ],
      ],
    );
  }
}
