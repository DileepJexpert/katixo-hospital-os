import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/api/nursing_models.dart';
import '../../core/auth/auth_state.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/status_chip.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// Nursing Indent management: create requests and approve pending indents.
class NursingIndentScreen extends StatefulWidget {
  const NursingIndentScreen({super.key});

  @override
  State<NursingIndentScreen> createState() => _NursingIndentScreenState();
}

class _NursingIndentScreenState extends State<NursingIndentScreen> {
  List<NursingIndentResponse> _pendingIndents = [];
  bool _loading = false;
  bool _creating = false;
  String? _error;
  String? _success;
  Timer? _refreshTimer;

  // Form state for creating indent
  String _wardSection = 'General';
  final _notesCtrl = TextEditingController();
  final _itemNameCtrl = TextEditingController();
  final _quantityCtrl = TextEditingController();
  final _reasonCtrl = TextEditingController();
  String _selectedItemType = 'CONSUMABLE';
  String _selectedUnit = 'pcs';
  List<_IndentItemRow> _items = [];

  @override
  void initState() {
    super.initState();
    _loadPendingIndents();
    _refreshTimer =
        Timer.periodic(const Duration(seconds: 15), (_) => _loadPendingIndents());
  }

  @override
  void dispose() {
    _refreshTimer?.cancel();
    _notesCtrl.dispose();
    _itemNameCtrl.dispose();
    _quantityCtrl.dispose();
    _reasonCtrl.dispose();
    super.dispose();
  }

  Future<void> _loadPendingIndents() async {
    try {
      final api = context.read<ApiClient>();
      final indents = await api.get<List<NursingIndentResponse>>(
        '/api/v1/nursing/indents/pending',
        fromJson: (json) => (json as List)
            .map((e) => NursingIndentResponse.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
      if (mounted) setState(() => _pendingIndents = indents);
    } catch (_) {
      // Silent on poll errors.
    }
  }

  void _addItem() {
    if (_itemNameCtrl.text.isEmpty || _quantityCtrl.text.isEmpty) {
      setState(() => _error = 'Item name and quantity required');
      return;
    }

    setState(() {
      _items.add(_IndentItemRow(
        itemType: _selectedItemType,
        itemName: _itemNameCtrl.text.trim(),
        quantity: double.tryParse(_quantityCtrl.text) ?? 0,
        unit: _selectedUnit,
        reason: _reasonCtrl.text.trim().isEmpty ? null : _reasonCtrl.text.trim(),
      ));
      _itemNameCtrl.clear();
      _quantityCtrl.clear();
      _reasonCtrl.clear();
      _selectedUnit = 'pcs';
    });
  }

  void _removeItem(int index) {
    setState(() => _items.removeAt(index));
  }

  Future<void> _submitIndent() async {
    if (_items.isEmpty) {
      setState(() => _error = 'Add at least one item to the indent');
      return;
    }

    setState(() {
      _creating = true;
      _error = null;
      _success = null;
    });

    try {
      final api = context.read<ApiClient>();
      final request = CreateIndentRequest(
        wardSection: _wardSection,
        items: _items
            .map((row) => CreateIndentItemRequest(
                  itemType: row.itemType,
                  itemName: row.itemName,
                  quantity: row.quantity,
                  unit: row.unit,
                  reason: row.reason,
                ))
            .toList(),
        notes: _notesCtrl.text.trim().isEmpty ? null : _notesCtrl.text.trim(),
      );

      final response = await api.post<NursingIndentResponse>(
        '/api/v1/nursing/indents',
        request,
        fromJson: (json) =>
            NursingIndentResponse.fromJson(json as Map<String, dynamic>),
      );

      setState(() {
        _success = 'Indent ${response.indentNumber} created';
        _items.clear();
        _notesCtrl.clear();
        _wardSection = 'General';
      });

      await _loadPendingIndents();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Indent creation failed: $e');
    } finally {
      if (mounted) setState(() => _creating = false);
    }
  }

  Future<void> _approveIndent(NursingIndentResponse indent) async {
    setState(() {
      _loading = true;
      _error = null;
    });

    try {
      final api = context.read<ApiClient>();
      await api.post<NursingIndentResponse>(
        '/api/v1/nursing/indents/${indent.id}/approve',
        {},
        fromJson: (json) =>
            NursingIndentResponse.fromJson(json as Map<String, dynamic>),
      );
      setState(() => _success = '${indent.indentNumber} approved');
      await _loadPendingIndents();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _rejectIndent(NursingIndentResponse indent) async {
    final reasonCtrl = TextEditingController();
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Reject Indent'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('${indent.indentNumber}',
                style: Theme.of(context).textTheme.bodyMedium),
            const SizedBox(height: Space.md),
            TextField(
              controller: reasonCtrl,
              decoration: const InputDecoration(labelText: 'Rejection Reason *'),
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
            child: const Text('Reject'),
          ),
        ],
      ),
    );

    if (confirmed == true && reasonCtrl.text.trim().isNotEmpty) {
      setState(() {
        _loading = true;
        _error = null;
      });

      try {
        final api = context.read<ApiClient>();
        await api.post<NursingIndentResponse>(
          '/api/v1/nursing/indents/${indent.id}/reject',
          {'reason': reasonCtrl.text.trim()},
          fromJson: (json) =>
              NursingIndentResponse.fromJson(json as Map<String, dynamic>),
        );
        setState(() => _success = '${indent.indentNumber} rejected');
        await _loadPendingIndents();
      } on ApiException catch (e) {
        setState(() => _error = e.error.message);
      } finally {
        if (mounted) setState(() => _loading = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final role = context.watch<AuthState>().currentUser?.role;
    final isSupervisor = role == 'NURSE_SUPERVISOR' || role == 'ADMIN';

    return PageContainer(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Nursing Indents', style: theme.textTheme.titleLarge),
          const SizedBox(height: Space.md),

          if (_error != null) ...[
            MessageBanner.error(_error!),
            const SizedBox(height: Space.md),
          ],
          if (_success != null) ...[
            MessageBanner.success(_success!),
            const SizedBox(height: Space.md),
          ],

          // Section 1: Pending indents (for supervisors)
          if (isSupervisor && _pendingIndents.isNotEmpty) ...[
            Card(
              child: Padding(
                padding: const EdgeInsets.all(Space.lg),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Text('Pending Approval',
                            style: theme.textTheme.titleMedium),
                        const Spacer(),
                        StatusChip('${_pendingIndents.length} pending',
                            kind: StatusKind.warning),
                      ],
                    ),
                    const SizedBox(height: Space.md),
                    for (var i = 0; i < _pendingIndents.length; i++) ...[
                      _buildIndentCard(_pendingIndents[i], isSupervisor),
                      if (i < _pendingIndents.length - 1)
                        const Divider(height: Space.lg),
                    ],
                  ],
                ),
              ),
            ),
            const SizedBox(height: Space.md),
          ],

          // Section 2: Create indent form
          Card(
            child: Padding(
              padding: const EdgeInsets.all(Space.lg),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Create Indent Request',
                      style: theme.textTheme.titleMedium),
                  const SizedBox(height: Space.md),
                  DropdownButtonFormField<String>(
                    value: _wardSection,
                    decoration:
                        const InputDecoration(labelText: 'Ward Section'),
                    items: const [
                      DropdownMenuItem(value: 'General', child: Text('General')),
                      DropdownMenuItem(value: 'ICU', child: Text('ICU')),
                      DropdownMenuItem(
                          value: 'Emergency', child: Text('Emergency')),
                      DropdownMenuItem(value: 'OPD', child: Text('OPD')),
                    ],
                    onChanged: _creating
                        ? null
                        : (v) => setState(() => _wardSection = v ?? 'General'),
                  ),
                  const SizedBox(height: Space.md),
                  TextField(
                    controller: _notesCtrl,
                    enabled: !_creating,
                    maxLines: 2,
                    decoration: const InputDecoration(
                        labelText: 'Notes (optional)'),
                  ),
                  const SizedBox(height: Space.lg),
                  Text('Items', style: theme.textTheme.titleSmall),
                  const SizedBox(height: Space.md),
                  Row(
                    children: [
                      Expanded(
                        flex: 2,
                        child: DropdownButtonFormField<String>(
                          value: _selectedItemType,
                          decoration: const InputDecoration(labelText: 'Type'),
                          items: const [
                            DropdownMenuItem(
                                value: 'CONSUMABLE', child: Text('Consumable')),
                            DropdownMenuItem(
                                value: 'EQUIPMENT', child: Text('Equipment')),
                            DropdownMenuItem(
                                value: 'MEDICATION', child: Text('Medication')),
                          ],
                          onChanged: _creating
                              ? null
                              : (v) => setState(
                                  () => _selectedItemType = v ?? 'CONSUMABLE'),
                        ),
                      ),
                      const SizedBox(width: Space.sm),
                      Expanded(
                        flex: 3,
                        child: TextField(
                          controller: _itemNameCtrl,
                          enabled: !_creating,
                          decoration:
                              const InputDecoration(labelText: 'Item Name'),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: Space.md),
                  Row(
                    children: [
                      Expanded(
                        child: TextField(
                          controller: _quantityCtrl,
                          enabled: !_creating,
                          keyboardType: TextInputType.number,
                          decoration:
                              const InputDecoration(labelText: 'Qty'),
                        ),
                      ),
                      const SizedBox(width: Space.sm),
                      Expanded(
                        child: DropdownButtonFormField<String>(
                          value: _selectedUnit,
                          decoration: const InputDecoration(labelText: 'Unit'),
                          items: const [
                            DropdownMenuItem(value: 'pcs', child: Text('pcs')),
                            DropdownMenuItem(value: 'ml', child: Text('ml')),
                            DropdownMenuItem(value: 'gm', child: Text('gm')),
                            DropdownMenuItem(value: 'box', child: Text('box')),
                          ],
                          onChanged: _creating
                              ? null
                              : (v) => setState(
                                  () => _selectedUnit = v ?? 'pcs'),
                        ),
                      ),
                      const SizedBox(width: Space.sm),
                      Expanded(
                        flex: 2,
                        child: TextField(
                          controller: _reasonCtrl,
                          enabled: !_creating,
                          decoration:
                              const InputDecoration(labelText: 'Reason'),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: Space.md),
                  SizedBox(
                    width: double.infinity,
                    child: OutlinedButton.icon(
                      onPressed: _creating ? null : _addItem,
                      icon: const Icon(Icons.add, size: 18),
                      label: const Text('Add Item'),
                    ),
                  ),
                  if (_items.isNotEmpty) ...[
                    const SizedBox(height: Space.md),
                    const Divider(),
                    const SizedBox(height: Space.md),
                    for (var i = 0; i < _items.length; i++)
                      Padding(
                        padding: const EdgeInsets.only(bottom: Space.sm),
                        child: ListTile(
                          dense: true,
                          leading: Text('${i + 1}'),
                          title: Text(_items[i].itemName),
                          subtitle: Text(
                              '${_items[i].quantity} ${_items[i].unit} • ${_items[i].itemType}'),
                          trailing: IconButton(
                            icon: const Icon(Icons.close, size: 18),
                            onPressed: () => _removeItem(i),
                          ),
                        ),
                      ),
                    const SizedBox(height: Space.lg),
                    SizedBox(
                      width: double.infinity,
                      height: Metrics.buttonHeight,
                      child: FilledButton(
                        onPressed: _creating ? null : _submitIndent,
                        child: _creating
                            ? const SizedBox(
                                width: 20,
                                height: 20,
                                child: CircularProgressIndicator(
                                    strokeWidth: 2),
                              )
                            : const Text('Submit Indent Request'),
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildIndentCard(NursingIndentResponse indent, bool isSupervisor) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(indent.indentNumber,
                      style: theme.textTheme.labelLarge),
                  Text('${indent.wardSection} • ${indent.items.length} items',
                      style: theme.textTheme.bodySmall),
                ],
              ),
            ),
            StatusChip.auto(indent.indentStatus),
          ],
        ),
        const SizedBox(height: Space.sm),
        for (final item in indent.items)
          Padding(
            padding: const EdgeInsets.only(left: Space.md, bottom: Space.xs),
            child: Text('• ${item.itemName} (${item.quantity} ${item.unit})',
                style: theme.textTheme.bodySmall),
          ),
        if (isSupervisor && indent.indentStatus == 'PENDING') ...[
          const SizedBox(height: Space.md),
          Row(
            mainAxisAlignment: MainAxisAlignment.end,
            children: [
              OutlinedButton(
                onPressed: _loading ? null : () => _rejectIndent(indent),
                child: const Text('Reject'),
              ),
              const SizedBox(width: Space.sm),
              FilledButton(
                onPressed: _loading ? null : () => _approveIndent(indent),
                child: const Text('Approve'),
              ),
            ],
          ),
        ],
      ],
    );
  }
}

class _IndentItemRow {
  final String itemType;
  final String itemName;
  final double quantity;
  final String unit;
  final String? reason;

  _IndentItemRow({
    required this.itemType,
    required this.itemName,
    required this.quantity,
    required this.unit,
    this.reason,
  });
}
