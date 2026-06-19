import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/gender_field.dart';

/// Opens a searchable patient picker. Returns the selected patient map
/// (id, uhid, fullName, mobile, age, gender) or null if cancelled. If the
/// patient is new, "New patient" registers one inline and returns it.
/// Reuse anywhere a patient must be chosen (IPD admit, TPA case, billing, lab).
Future<Map<String, dynamic>?> showPatientPicker(BuildContext context) {
  return showDialog<Map<String, dynamic>>(
    context: context,
    builder: (_) => const _PatientPickerDialog(),
  );
}

class _PatientPickerDialog extends StatefulWidget {
  const _PatientPickerDialog();

  @override
  State<_PatientPickerDialog> createState() => _PatientPickerDialogState();
}

class _PatientPickerDialogState extends State<_PatientPickerDialog> {
  final _q = TextEditingController();
  List<Map<String, dynamic>> _results = const [];
  bool _loading = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _search());
  }

  @override
  void dispose() {
    _q.dispose();
    super.dispose();
  }

  Future<void> _search() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      final term = _q.text.trim();
      final list = await api.get<List<Map<String, dynamic>>>(
        '/api/v1/patients${term.isEmpty ? '' : '?q=$term'}',
        fromJson: (json) =>
            List<Map<String, dynamic>>.from(json as List? ?? const []),
      );
      if (mounted) setState(() => _results = list);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return AlertDialog(
      title: const Text('Select patient'),
      content: SizedBox(
        width: 480,
        height: 440,
        child: Column(
          children: [
            TextField(
              controller: _q,
              autofocus: true,
              decoration: InputDecoration(
                labelText: 'Search name / mobile / UHID',
                prefixIcon: const Icon(Icons.search, size: 18),
                suffixIcon: IconButton(
                  icon: const Icon(Icons.arrow_forward, size: 18),
                  onPressed: _loading ? null : _search,
                ),
              ),
              onSubmitted: (_) => _search(),
            ),
            const SizedBox(height: Space.sm),
            if (_error != null)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: Space.sm),
                child: Text(_error!,
                    style: theme.textTheme.bodySmall
                        ?.copyWith(color: theme.colorScheme.error)),
              ),
            Expanded(
              child: _results.isEmpty
                  ? Center(
                      child: Text(_loading ? 'Searching…' : 'No patients found',
                          style: theme.textTheme.bodyMedium?.copyWith(
                              color: theme.colorScheme.onSurfaceVariant)))
                  : ListView.separated(
                      itemCount: _results.length,
                      separatorBuilder: (_, __) => const Divider(height: 1),
                      itemBuilder: (context, i) {
                        final p = _results[i];
                        return ListTile(
                          dense: true,
                          title: Text('${p['fullName'] ?? ''}'),
                          subtitle: Text(
                            'UHID ${p['uhid'] ?? '—'} · ${p['mobile'] ?? '—'}'
                            '${p['age'] != null ? ' · ${p['age']}y' : ''}'
                            '${p['gender'] != null ? ' · ${p['gender']}' : ''}',
                            style: theme.textTheme.bodySmall,
                          ),
                          onTap: () => Navigator.pop(context, p),
                        );
                      },
                    ),
            ),
          ],
        ),
      ),
      actions: [
        TextButton.icon(
          onPressed: _loading ? null : _addNewPatient,
          icon: const Icon(Icons.person_add_alt_1, size: 18),
          label: const Text('New patient'),
        ),
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Cancel'),
        ),
      ],
    );
  }

  String _iso(DateTime d) =>
      '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

  /// Inline quick-registration for a patient who isn't in the system yet.
  /// On success, closes the picker returning the newly created patient.
  Future<void> _addNewPatient() async {
    final first = TextEditingController(text: _q.text.trim());
    final last = TextEditingController();
    final mobile = TextEditingController();
    DateTime? dob;
    String? gender;
    bool consent = false;
    bool saving = false;
    String? localError;

    final created = await showDialog<Map<String, dynamic>>(
      context: context,
      builder: (dialogCtx) => StatefulBuilder(
        builder: (dialogCtx, setLocal) {
          Future<void> submit() async {
            if (first.text.trim().isEmpty ||
                last.text.trim().isEmpty ||
                mobile.text.trim().isEmpty ||
                gender == null ||
                dob == null) {
              setLocal(() =>
                  localError = 'Name, mobile, gender and date of birth are required');
              return;
            }
            if (!consent) {
              setLocal(() => localError = 'Privacy consent is required');
              return;
            }
            setLocal(() {
              saving = true;
              localError = null;
            });
            try {
              final api = context.read<ApiClient>();
              final p = await api.post<Map<String, dynamic>>(
                '/api/v1/patients',
                {
                  'firstName': first.text.trim(),
                  'lastName': last.text.trim(),
                  'mobile': mobile.text.trim(),
                  'dateOfBirth': _iso(dob!),
                  'gender': gender,
                  'privacyConsentGiven': true,
                },
                fromJson: (j) => Map<String, dynamic>.from(j as Map),
              );
              if (dialogCtx.mounted) Navigator.pop(dialogCtx, p);
            } on ApiException catch (e) {
              setLocal(() {
                saving = false;
                localError = e.error.message;
              });
            } catch (e) {
              setLocal(() {
                saving = false;
                localError = 'Registration failed: $e';
              });
            }
          }

          return AlertDialog(
            title: const Text('New patient'),
            content: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
              child: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (localError != null) ...[
                      Text(localError!,
                          style: TextStyle(
                              color: Theme.of(dialogCtx).colorScheme.error)),
                      const SizedBox(height: Space.sm),
                    ],
                    Row(
                      children: [
                        Expanded(
                          child: TextField(
                            controller: first,
                            autofocus: true,
                            decoration:
                                const InputDecoration(labelText: 'First name *'),
                          ),
                        ),
                        const SizedBox(width: Space.md),
                        Expanded(
                          child: TextField(
                            controller: last,
                            decoration:
                                const InputDecoration(labelText: 'Last name *'),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: Space.sm),
                    TextField(
                      controller: mobile,
                      keyboardType: TextInputType.phone,
                      decoration: const InputDecoration(
                          labelText: 'Mobile *', prefixText: '+91 '),
                    ),
                    const SizedBox(height: Space.sm),
                    GenderField(
                      value: gender,
                      enabled: !saving,
                      onChanged: (v) => setLocal(() => gender = v),
                    ),
                    const SizedBox(height: Space.sm),
                    OutlinedButton.icon(
                      onPressed: saving
                          ? null
                          : () async {
                              final d = await showDatePicker(
                                context: dialogCtx,
                                initialDate: DateTime(2000),
                                firstDate: DateTime(1900),
                                lastDate: DateTime.now(),
                              );
                              if (d != null) setLocal(() => dob = d);
                            },
                      icon: const Icon(Icons.cake_outlined, size: 18),
                      label: Text(dob == null ? 'Date of birth *' : _iso(dob!)),
                    ),
                    CheckboxListTile(
                      contentPadding: EdgeInsets.zero,
                      dense: true,
                      value: consent,
                      onChanged: saving
                          ? null
                          : (v) => setLocal(() => consent = v ?? false),
                      title:
                          const Text('Patient consents to data privacy terms'),
                    ),
                  ],
                ),
              ),
            ),
            actions: [
              TextButton(
                onPressed: saving ? null : () => Navigator.pop(dialogCtx),
                child: const Text('Cancel'),
              ),
              FilledButton(
                onPressed: saving ? null : submit,
                child: Text(saving ? 'Saving…' : 'Register'),
              ),
            ],
          );
        },
      ),
    );

    if (created != null && mounted) {
      Navigator.pop(context, created); // return the new patient to the caller
    }
  }
}
