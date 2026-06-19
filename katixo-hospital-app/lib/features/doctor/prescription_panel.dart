import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/api/rx_models.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/util/pdf_actions.dart';
import '../../core/widgets/status_chip.dart';
import '../prescription/medicine_picker.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// Prescription entry inside an active consultation.
/// Handles the allergy-guard flow: a blocked create surfaces an
/// override dialog where the doctor must give an audited reason.
class PrescriptionPanel extends StatefulWidget {
  const PrescriptionPanel({
    super.key,
    required this.visitId,
    this.onCreated,
  });

  final int visitId;
  final ValueChanged<RxResponse>? onCreated;

  @override
  State<PrescriptionPanel> createState() => _PrescriptionPanelState();
}

class _PrescriptionPanelState extends State<PrescriptionPanel> {
  final List<RxItemInput> _items = [];
  final _notesCtrl = TextEditingController();

  bool _saving = false;
  bool _sendingToPharmacy = false;
  String? _error;
  RxResponse? _created;

  @override
  void dispose() {
    _notesCtrl.dispose();
    super.dispose();
  }

  Future<void> _addItemDialog() async {
    final item = await showMedicinePicker(context);
    if (item != null) {
      setState(() => _items.add(item));
    }
  }

  Future<void> _create({bool override = false, String? reason}) async {
    if (_items.isEmpty) {
      setState(() => _error = 'Add at least one medicine');
      return;
    }

    setState(() {
      _saving = true;
      _error = null;
    });

    try {
      final api = context.read<ApiClient>();
      final rx = await api.post<RxResponse>(
        '/api/v1/prescriptions',
        CreateRxRequest(
          visitId: widget.visitId,
          items: _items,
          notes: _notesCtrl.text.trim().isEmpty ? null : _notesCtrl.text.trim(),
          overrideAllergy: override,
          allergyOverrideReason: reason,
        ),
        fromJson: (json) => RxResponse.fromJson(json as Map<String, dynamic>),
      );
      setState(() => _created = rx);
      widget.onCreated?.call(rx);
    } on ApiException catch (e) {
      if (e.error.error == 'ALLERGY_CONFLICT') {
        await _allergyOverrideDialog(e.error.message);
      } else {
        setState(() => _error = e.error.message);
      }
    } catch (e) {
      setState(() => _error = 'Prescription failed: $e');
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _allergyOverrideDialog(String conflictMessage) async {
    final reasonCtrl = TextEditingController();

    final proceed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        icon: const Icon(Icons.warning_amber_rounded,
            color: StatusColors.danger, size: 32),
        title: const Text('Allergy Conflict'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(conflictMessage),
            const SizedBox(height: Space.lg),
            const Text('To proceed anyway, give a clinical reason. '
                'The override is recorded in the audit log.'),
            const SizedBox(height: Space.md),
            TextField(
              controller: reasonCtrl,
              decoration:
                  const InputDecoration(labelText: 'Override Reason *'),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(
                backgroundColor: StatusColors.danger),
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Override & Prescribe'),
          ),
        ],
      ),
    );

    if (proceed == true && reasonCtrl.text.trim().isNotEmpty) {
      await _create(override: true, reason: reasonCtrl.text.trim());
    } else if (proceed == true) {
      setState(() => _error = 'Override reason is required');
    }
  }

  Future<void> _sendToPharmacy() async {
    final rx = _created;
    if (rx == null) return;

    setState(() {
      _sendingToPharmacy = true;
      _error = null;
    });

    try {
      final api = context.read<ApiClient>();
      await api.post<dynamic>(
        '/api/v1/pharmacy/queue/send',
        {'prescriptionId': rx.id},
        fromJson: (json) => json,
      );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('${rx.prescriptionNumber} sent to pharmacy')),
        );
      }
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _sendingToPharmacy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    // After creation: summary + send-to-pharmacy.
    if (_created != null) {
      final rx = _created!;
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text('Prescription ${rx.prescriptionNumber}',
                  style: theme.textTheme.titleMedium),
              const SizedBox(width: Space.sm),
              StatusChip.auto(rx.status),
            ],
          ),
          const SizedBox(height: Space.sm),
          for (final item in rx.items)
            Padding(
              padding: const EdgeInsets.only(bottom: Space.xs),
              child: Text(
                  '• ${item.medicineName} ${item.dosage ?? ''} ${item.frequency ?? ''} ×${item.quantity ?? 1}',
                  style: theme.textTheme.bodyMedium),
            ),
          const SizedBox(height: Space.md),
          if (_error != null) ...[
            MessageBanner.error(_error!),
            const SizedBox(height: Space.md),
          ],
          Wrap(
            spacing: Space.sm,
            runSpacing: Space.sm,
            children: [
              OutlinedButton.icon(
                onPressed: _sendingToPharmacy ? null : _sendToPharmacy,
                icon: const Icon(Icons.local_pharmacy_outlined, size: 18),
                label: Text(
                    _sendingToPharmacy ? 'Sending…' : 'Send to Pharmacy Queue'),
              ),
              OutlinedButton.icon(
                onPressed: () => openPdf(
                  context,
                  context.read<ApiClient>(),
                  '/api/v1/prescriptions/${rx.id}/print.pdf',
                  filename: 'prescription-${rx.id}.pdf',
                ),
                icon: const Icon(Icons.print_outlined, size: 18),
                label: const Text('Print'),
              ),
            ],
          ),
        ],
      );
    }

    // Entry form.
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Text('Prescription', style: theme.textTheme.titleMedium),
            const Spacer(),
            OutlinedButton.icon(
              onPressed: _saving ? null : _addItemDialog,
              icon: const Icon(Icons.add, size: 18),
              label: const Text('Add Medicine'),
            ),
          ],
        ),
        const SizedBox(height: Space.sm),

        if (_error != null) ...[
          MessageBanner.error(_error!),
          const SizedBox(height: Space.sm),
        ],

        if (_items.isEmpty)
          Text('No medicines added',
              style: theme.textTheme.bodySmall
                  ?.copyWith(color: theme.colorScheme.onSurfaceVariant))
        else
          for (var i = 0; i < _items.length; i++)
            ListTile(
              dense: true,
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.medication_outlined, size: 20),
              title: Text(
                  '${_items[i].medicineName} (${_items[i].medicineCode})'),
              subtitle: Text(
                  '${_items[i].dosage ?? '-'} · ${_items[i].frequency ?? '-'} · qty ${_items[i].quantity}'),
              trailing: IconButton(
                icon: const Icon(Icons.close, size: 18),
                onPressed: () => setState(() => _items.removeAt(i)),
              ),
            ),

        const SizedBox(height: Space.sm),
        TextField(
          controller: _notesCtrl,
          enabled: !_saving,
          decoration: const InputDecoration(labelText: 'Notes'),
        ),
        const SizedBox(height: Space.md),
        FilledButton.icon(
          onPressed: _saving || _items.isEmpty ? null : () => _create(),
          icon: const Icon(Icons.receipt_long_outlined, size: 18),
          label: Text(_saving ? 'Creating…' : 'Create Prescription'),
        ),
      ],
    );
  }
}
