import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/api/radiology_models.dart';
import '../../core/theme/design_tokens.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// Radiology (imaging) orders panel for doctor consultation.
class RadiologyOrdersPanel extends StatefulWidget {
  const RadiologyOrdersPanel({
    super.key,
    required this.visitId,
  });

  final int visitId;

  @override
  State<RadiologyOrdersPanel> createState() => _RadiologyOrdersPanelState();
}

class _RadiologyOrdersPanelState extends State<RadiologyOrdersPanel> {
  List<RadiologyTest> _availableTests = [];
  final List<String> _selectedTestCodes = [];
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
      final tests = await api.get<List<RadiologyTest>>(
        '/api/v1/radiology/tests',
        fromJson: (json) => (json as List)
            .map((e) => RadiologyTest.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
      if (mounted) setState(() => _availableTests = tests);
    } catch (_) {
      // No tests configured yet — panel stays in its empty state.
    }
  }

  Future<void> _orderTests() async {
    if (_selectedTestCodes.isEmpty) {
      setState(() => _error = 'Select at least one imaging test');
      return;
    }

    setState(() {
      _loading = true;
      _error = null;
      _success = null;
    });

    try {
      final api = context.read<ApiClient>();
      await api.post<dynamic>(
        '/api/v1/radiology/orders',
        {
          'sourceType': 'OPD_VISIT',
          'sourceId': widget.visitId,
          'testCodes': _selectedTestCodes,
        },
        fromJson: (json) => json,
      );

      setState(() {
        _success =
            'Imaging order created for ${_selectedTestCodes.length} test(s)';
        _selectedTestCodes.clear();
      });
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  num get _selectedTotal => _selectedTestCodes.fold<num>(
      0,
      (sum, code) =>
          sum +
          _availableTests
              .firstWhere((t) => t.testCode == code,
                  orElse: () => const RadiologyTest(
                      id: 0, testCode: '', testName: '', rate: 0))
              .rate);

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('Imaging / Radiology', style: theme.textTheme.titleMedium),
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
          Text('No imaging tests configured',
              style: theme.textTheme.bodySmall
                  ?.copyWith(color: theme.colorScheme.onSurfaceVariant))
        else ...[
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
                      Text('₹${test.rate}', style: theme.textTheme.labelSmall),
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
          if (_selectedTestCodes.isNotEmpty) ...[
            const SizedBox(height: Space.md),
            Row(
              children: [
                Text('Total: ₹$_selectedTotal',
                    style: theme.textTheme.labelLarge
                        ?.copyWith(fontWeight: FontWeight.bold)),
                const SizedBox(width: Space.lg),
                FilledButton.icon(
                  onPressed: _loading ? null : _orderTests,
                  icon: const Icon(Icons.image_outlined, size: 18),
                  label: const Text('Order Imaging'),
                ),
              ],
            ),
          ],
        ],
      ],
    );
  }
}
