import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/auth/auth_state.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/empty_state.dart';
import '../../core/widgets/section_card.dart';
import '../../core/widgets/status_chip.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// NABH quality governance: incident (adverse-event) reporting with a
/// REPORTED → UNDER_REVIEW → CLOSED lifecycle, and quality indicators with
/// periodic readings. Body widget. Clinical roles report incidents; ADMIN
/// reviews/closes and manages indicators.
class NabhScreen extends StatefulWidget {
  const NabhScreen({super.key});

  @override
  State<NabhScreen> createState() => _NabhScreenState();
}

class _NabhScreenState extends State<NabhScreen> {
  static const _types = <String>[
    'MEDICATION_ERROR', 'PATIENT_FALL', 'EQUIPMENT_FAILURE', 'NEEDLE_STICK',
    'ADVERSE_DRUG_REACTION', 'HOSPITAL_ACQUIRED_INFECTION', 'OTHER',
  ];
  static const _severities = <String>['NEAR_MISS', 'MINOR', 'MODERATE', 'MAJOR', 'SENTINEL'];

  String _tab = 'incidents';
  List<Map<String, dynamic>> _incidents = const [];
  List<Map<String, dynamic>> _indicators = const [];
  bool _loading = false;
  String? _error;
  String? _info;

  String get _role => context.read<AuthState>().currentUser?.role ?? '';
  bool get _isAdmin => _role == 'ADMIN' || _role == 'SUPER_ADMIN';

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      final incidents = await api.get<List<Map<String, dynamic>>>(
        '/api/v1/nabh/incidents?limit=50',
        fromJson: (j) => List<Map<String, dynamic>>.from(j as List? ?? const []),
      );
      List<Map<String, dynamic>> indicators = const [];
      if (_isAdmin || _tab == 'quality') {
        indicators = await api.get<List<Map<String, dynamic>>>(
          '/api/v1/nabh/indicators',
          fromJson: (j) => List<Map<String, dynamic>>.from(j as List? ?? const []),
        );
      }
      if (mounted) {
        setState(() {
          _incidents = incidents;
          _indicators = indicators;
        });
      }
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Could not load NABH data: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _act(Future<void> Function() action, String okMsg) async {
    setState(() {
      _loading = true;
      _error = null;
      _info = null;
    });
    try {
      await action();
      setState(() => _info = okMsg);
      await _load();
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } catch (e) {
      setState(() => _error = 'Action failed: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  // ---------------- incidents ----------------

  Future<void> _reportDialog() async {
    String type = 'PATIENT_FALL';
    String severity = 'MINOR';
    DateTime date = DateTime.now();
    final locationCtrl = TextEditingController();
    final descCtrl = TextEditingController();
    final actionCtrl = TextEditingController();
    String iso(DateTime d) => d.toIso8601String().split('T').first;

    final proceed = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setLocal) => AlertDialog(
          title: const Text('Report incident'),
          content: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
            child: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  OutlinedButton.icon(
                    onPressed: () async {
                      final p = await showDatePicker(
                        context: context, initialDate: date,
                        firstDate: DateTime(2020), lastDate: DateTime.now());
                      if (p != null) setLocal(() => date = p);
                    },
                    icon: const Icon(Icons.calendar_today, size: 16),
                    label: Text('Incident date ${iso(date)}'),
                  ),
                  const SizedBox(height: Space.sm),
                  DropdownButtonFormField<String>(
                    value: type,
                    isExpanded: true,
                    decoration: const InputDecoration(labelText: 'Type'),
                    items: [for (final t in _types) DropdownMenuItem(value: t, child: Text(_title(t)))],
                    onChanged: (v) => setLocal(() => type = v ?? 'OTHER'),
                  ),
                  const SizedBox(height: Space.sm),
                  DropdownButtonFormField<String>(
                    value: severity,
                    decoration: const InputDecoration(labelText: 'Severity'),
                    items: [for (final s in _severities) DropdownMenuItem(value: s, child: Text(_title(s)))],
                    onChanged: (v) => setLocal(() => severity = v ?? 'MINOR'),
                  ),
                  const SizedBox(height: Space.sm),
                  TextField(controller: locationCtrl, decoration: const InputDecoration(labelText: 'Location')),
                  TextField(
                    controller: descCtrl,
                    maxLines: 3,
                    decoration: const InputDecoration(labelText: 'Description *'),
                  ),
                  TextField(
                    controller: actionCtrl,
                    maxLines: 2,
                    decoration: const InputDecoration(labelText: 'Immediate action taken'),
                  ),
                ],
              ),
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
            FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Report')),
          ],
        ),
      ),
    );
    if (proceed != true) return;
    if (descCtrl.text.trim().isEmpty) {
      setState(() => _error = 'Description is required');
      return;
    }
    await _act(() async {
      final api = context.read<ApiClient>();
      await api.post<dynamic>('/api/v1/nabh/incidents', {
        'incidentDate': iso(date),
        'incidentType': type,
        'severity': severity,
        'location': locationCtrl.text.trim(),
        'description': descCtrl.text.trim(),
        'immediateAction': actionCtrl.text.trim(),
      }, fromJson: (j) => j);
    }, 'Incident reported');
  }

  Future<void> _closeDialog(Map<String, dynamic> i) async {
    final rootCtrl = TextEditingController(text: '${i['rootCause'] ?? ''}');
    final actionCtrl = TextEditingController(text: '${i['correctiveAction'] ?? ''}');
    final proceed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Close ${i['reportNumber']}'),
        content: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(controller: rootCtrl, maxLines: 3,
                    decoration: const InputDecoration(labelText: 'Root cause *')),
                const SizedBox(height: Space.sm),
                TextField(controller: actionCtrl, maxLines: 3,
                    decoration: const InputDecoration(labelText: 'Corrective action *')),
              ],
            ),
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Close incident')),
        ],
      ),
    );
    if (proceed != true) return;
    if (rootCtrl.text.trim().isEmpty || actionCtrl.text.trim().isEmpty) {
      setState(() => _error = 'Root cause and corrective action are required');
      return;
    }
    await _act(() async {
      final api = context.read<ApiClient>();
      await api.post<dynamic>('/api/v1/nabh/incidents/${i['id']}/close',
          {'rootCause': rootCtrl.text.trim(), 'correctiveAction': actionCtrl.text.trim()},
          fromJson: (j) => j);
    }, 'Incident closed');
  }

  // ---------------- quality indicators ----------------

  Future<void> _addIndicatorDialog() async {
    final codeCtrl = TextEditingController();
    final nameCtrl = TextEditingController();
    final catCtrl = TextEditingController();
    final unitCtrl = TextEditingController();
    final targetCtrl = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Add quality indicator'),
        content: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(controller: codeCtrl, decoration: const InputDecoration(labelText: 'Code *')),
                TextField(controller: nameCtrl, decoration: const InputDecoration(labelText: 'Name *')),
                TextField(controller: catCtrl, decoration: const InputDecoration(labelText: 'Category')),
                TextField(controller: unitCtrl, decoration: const InputDecoration(labelText: 'Unit (e.g. %)')),
                TextField(controller: targetCtrl, keyboardType: TextInputType.number,
                    decoration: const InputDecoration(labelText: 'Target value')),
              ],
            ),
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Add')),
        ],
      ),
    );
    if (ok != true || codeCtrl.text.trim().isEmpty || nameCtrl.text.trim().isEmpty) return;
    await _act(() async {
      final api = context.read<ApiClient>();
      await api.post<dynamic>('/api/v1/nabh/indicators', {
        'code': codeCtrl.text.trim(),
        'name': nameCtrl.text.trim(),
        'category': catCtrl.text.trim(),
        'unit': unitCtrl.text.trim(),
        if (targetCtrl.text.trim().isNotEmpty) 'targetValue': double.tryParse(targetCtrl.text.trim()),
      }, fromJson: (j) => j);
    }, 'Indicator added');
  }

  Future<void> _recordReadingDialog(Map<String, dynamic> q) async {
    final periodCtrl = TextEditingController();
    final valueCtrl = TextEditingController();
    final numCtrl = TextEditingController();
    final denCtrl = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Reading — ${q['name']}'),
        content: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: Metrics.dialogMaxWidth),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(controller: periodCtrl,
                  decoration: const InputDecoration(labelText: 'Period * (e.g. 2026-06)')),
              TextField(controller: valueCtrl, keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: 'Value *')),
              Row(children: [
                Expanded(child: TextField(controller: numCtrl, keyboardType: TextInputType.number,
                    decoration: const InputDecoration(labelText: 'Numerator'))),
                const SizedBox(width: Space.sm),
                Expanded(child: TextField(controller: denCtrl, keyboardType: TextInputType.number,
                    decoration: const InputDecoration(labelText: 'Denominator'))),
              ]),
            ],
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Save')),
        ],
      ),
    );
    final value = double.tryParse(valueCtrl.text.trim());
    if (ok != true || periodCtrl.text.trim().isEmpty || value == null) return;
    await _act(() async {
      final api = context.read<ApiClient>();
      await api.post<dynamic>('/api/v1/nabh/indicators/${q['id']}/readings', {
        'period': periodCtrl.text.trim(),
        'value': value,
        if (numCtrl.text.trim().isNotEmpty) 'numerator': double.tryParse(numCtrl.text.trim()),
        if (denCtrl.text.trim().isNotEmpty) 'denominator': double.tryParse(denCtrl.text.trim()),
      }, fromJson: (j) => j);
    }, 'Reading recorded');
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final tabs = <(String, String)>[
      ('incidents', 'Incidents'),
      if (_isAdmin) ('quality', 'Quality indicators'),
    ];
    if (!tabs.any((t) => t.$1 == _tab)) _tab = 'incidents';
    return PageContainer(
      scrollable: false,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text('NABH Quality', style: theme.textTheme.titleLarge),
              const Spacer(),
              IconButton(
                tooltip: 'Refresh',
                onPressed: _loading ? null : _load,
                icon: const Icon(Icons.refresh),
              ),
            ],
          ),
          const SizedBox(height: Space.md),
          if (tabs.length > 1) ...[
            Wrap(
              spacing: Space.sm,
              children: [
                for (final t in tabs)
                  ChoiceChip(
                    label: Text(t.$2),
                    selected: _tab == t.$1,
                    onSelected: (_) {
                      setState(() => _tab = t.$1);
                      _load();
                    },
                  ),
              ],
            ),
            const SizedBox(height: Space.md),
          ],
          if (_error != null) ...[
            MessageBanner.error(_error!),
            const SizedBox(height: Space.md),
          ],
          if (_info != null) ...[
            MessageBanner.success(_info!),
            const SizedBox(height: Space.md),
          ],
          Expanded(child: _tab == 'quality' ? _qualityTab(theme) : _incidentsTab(theme)),
        ],
      ),
    );
  }

  Widget _incidentsTab(ThemeData theme) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Align(
          alignment: Alignment.centerRight,
          child: FilledButton.icon(
            onPressed: _loading ? null : _reportDialog,
            icon: const Icon(Icons.add, size: 18),
            label: const Text('Report incident'),
          ),
        ),
        const SizedBox(height: Space.md),
        Expanded(
          child: _incidents.isEmpty
              ? EmptyState(
                  icon: Icons.report_problem_outlined,
                  title: _loading ? 'Loading…' : 'No incidents',
                  message: 'Reported adverse events appear here.')
              : ListView(children: [for (final i in _incidents) _incidentTile(theme, i)]),
        ),
      ],
    );
  }

  Widget _incidentTile(ThemeData theme, Map<String, dynamic> i) {
    final status = '${i['incidentStatus']}';
    return SectionCard(
      title: '${i['reportNumber']} · ${_title('${i['incidentType']}')}',
      icon: Icons.report_problem_outlined,
      subtitle: '${_title('${i['severity']}')} · ${i['incidentDate'] ?? ''}'
          '${i['location'] != null ? ' · ${i['location']}' : ''}',
      action: StatusChip.auto(status),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('${i['description']}', style: theme.textTheme.bodyMedium),
          if ((i['immediateAction'] ?? '').toString().isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(top: Space.xs),
              child: Text('Immediate action: ${i['immediateAction']}', style: theme.textTheme.bodySmall),
            ),
          if ((i['rootCause'] ?? '').toString().isNotEmpty) ...[
            const SizedBox(height: Space.xs),
            Text('Root cause: ${i['rootCause']}', style: theme.textTheme.bodySmall),
            Text('Corrective action: ${i['correctiveAction']}', style: theme.textTheme.bodySmall),
          ],
          if (_isAdmin && status != 'CLOSED') ...[
            const SizedBox(height: Space.sm),
            Wrap(
              spacing: Space.sm,
              children: [
                if (status == 'REPORTED')
                  OutlinedButton(
                    onPressed: _loading
                        ? null
                        : () => _act(() async {
                              await context.read<ApiClient>().post<dynamic>(
                                  '/api/v1/nabh/incidents/${i['id']}/review', const {}, fromJson: (j) => j);
                            }, 'Moved to review'),
                    child: const Text('Start review'),
                  ),
                FilledButton(
                  onPressed: _loading ? null : () => _closeDialog(i),
                  child: const Text('Close'),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }

  Widget _qualityTab(ThemeData theme) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Align(
          alignment: Alignment.centerRight,
          child: FilledButton.icon(
            onPressed: _loading ? null : _addIndicatorDialog,
            icon: const Icon(Icons.add, size: 18),
            label: const Text('Add indicator'),
          ),
        ),
        const SizedBox(height: Space.md),
        Expanded(
          child: _indicators.isEmpty
              ? const EmptyState(
                  icon: Icons.insights_outlined,
                  title: 'No quality indicators',
                  message: 'Add NABH indicators to track readings over time.')
              : ListView(children: [for (final q in _indicators) _indicatorTile(theme, q)]),
        ),
      ],
    );
  }

  Widget _indicatorTile(ThemeData theme, Map<String, dynamic> q) {
    return SectionCard(
      title: '${q['code']} · ${q['name']}',
      icon: Icons.insights_outlined,
      subtitle: '${q['category'] ?? '—'}'
          '${q['unit'] != null ? ' · ${q['unit']}' : ''}'
          '${q['targetValue'] != null ? ' · target ${q['targetValue']}' : ''}',
      action: OutlinedButton(
        onPressed: _loading ? null : () => _recordReadingDialog(q),
        child: const Text('Record'),
      ),
      child: _IndicatorReadings(indicatorId: q['id'] as int),
    );
  }

  String _title(String s) => s
      .toLowerCase()
      .split('_')
      .map((w) => w.isEmpty ? w : '${w[0].toUpperCase()}${w.substring(1)}')
      .join(' ');
}

/// Lazily loads + shows recent readings for one indicator.
class _IndicatorReadings extends StatefulWidget {
  const _IndicatorReadings({required this.indicatorId});
  final int indicatorId;

  @override
  State<_IndicatorReadings> createState() => _IndicatorReadingsState();
}

class _IndicatorReadingsState extends State<_IndicatorReadings> {
  List<Map<String, dynamic>> _readings = const [];
  bool _loaded = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final api = context.read<ApiClient>();
      final list = await api.get<List<Map<String, dynamic>>>(
        '/api/v1/nabh/indicators/${widget.indicatorId}/readings',
        fromJson: (j) => List<Map<String, dynamic>>.from(j as List? ?? const []),
      );
      if (mounted) {
        setState(() {
          _readings = list;
          _loaded = true;
        });
      }
    } catch (_) {
      if (mounted) setState(() => _loaded = true);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    if (!_loaded) return const SizedBox.shrink();
    if (_readings.isEmpty) {
      return Text('No readings yet',
          style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant));
    }
    return Column(
      children: [
        for (final r in _readings.take(6))
          Padding(
            padding: const EdgeInsets.symmetric(vertical: Space.xxs),
            child: Row(
              children: [
                Text('${r['period']}', style: theme.textTheme.bodySmall),
                const Spacer(),
                Text('${r['value']}', style: theme.textTheme.bodyMedium),
              ],
            ),
          ),
      ],
    );
  }
}
