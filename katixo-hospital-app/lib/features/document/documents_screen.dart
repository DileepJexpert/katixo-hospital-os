import 'package:flutter/material.dart';

import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/empty_state.dart';
import '../patient/patient_picker.dart';
import 'documents_panel.dart';

/// Standalone Documents view: pick a patient, then manage that patient's
/// attachments (scanned reports, consent scans, ID proofs). Embeds the reusable
/// [DocumentsPanel]. For attaching files directly to a lab report / TPA case,
/// embed [DocumentsPanel] in those detail screens instead.
class DocumentsScreen extends StatefulWidget {
  const DocumentsScreen({super.key});

  @override
  State<DocumentsScreen> createState() => _DocumentsScreenState();
}

class _DocumentsScreenState extends State<DocumentsScreen> {
  Map<String, dynamic>? _patient;

  Future<void> _pick() async {
    final p = await showPatientPicker(context);
    if (p == null || !mounted) return;
    setState(() => _patient = p);
  }

  @override
  Widget build(BuildContext context) {
    final patient = _patient;
    return PageContainer(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text('Documents',
                    style: Theme.of(context).textTheme.headlineSmall),
              ),
              OutlinedButton.icon(
                onPressed: _pick,
                icon: const Icon(Icons.person_search, size: 16),
                label: Text(patient == null ? 'Choose patient' : 'Change patient'),
              ),
            ],
          ),
          const SizedBox(height: Space.lg),
          if (patient == null)
            const EmptyState(
              icon: Icons.folder_outlined,
              title: 'Pick a patient',
              message: 'Choose a patient to view and manage their attachments.',
            )
          else ...[
            Text('${patient['fullName']}  ·  ${patient['uhid'] ?? ''}',
                style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: Space.md),
            DocumentsPanel(
              entityType: 'PATIENT',
              entityId: (patient['id'] as num?)?.toInt(),
              title: 'Patient attachments',
            ),
          ],
        ],
      ),
    );
  }
}
