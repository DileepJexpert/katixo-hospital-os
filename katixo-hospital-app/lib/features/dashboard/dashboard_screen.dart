import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/http_client.dart';
import '../../core/responsive/responsive_builder.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/kpi_tile.dart';
import '../front_desk/registration_screen.dart' show MessageBanner;

/// Owner / MIS dashboard: financial, receivables, volume and occupancy KPIs
/// for a date range. Reads `/api/v1/dashboard/summary`. Body widget.
class DashboardScreen extends StatefulWidget {
  const DashboardScreen({super.key});

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> {
  Map<String, dynamic>? _data;
  bool _loading = false;
  String? _error;
  late DateTimeRange _range;

  @override
  void initState() {
    super.initState();
    final now = DateTime.now();
    _range = DateTimeRange(start: DateTime(now.year, now.month, 1), end: now);
    WidgetsBinding.instance.addPostFrameCallback((_) => _load());
  }

  String _fmt(DateTime d) =>
      '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiClient>();
      final data = await api.get<Map<String, dynamic>>(
        '/api/v1/dashboard/summary?from=${_fmt(_range.start)}&to=${_fmt(_range.end)}',
        fromJson: (json) => json as Map<String, dynamic>,
      );
      if (mounted) setState(() => _data = data);
    } on ApiException catch (e) {
      setState(() => _error = e.error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _pickRange() async {
    final now = DateTime.now();
    final picked = await showDateRangePicker(
      context: context,
      firstDate: DateTime(now.year - 5),
      lastDate: now,
      initialDateRange: _range,
    );
    if (picked != null) {
      setState(() => _range = picked);
      await _load();
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final financial = (_data?['financial'] as Map?)?.cast<String, dynamic>() ?? const {};
    final receivables = (_data?['receivables'] as Map?)?.cast<String, dynamic>() ?? const {};
    final volumes = (_data?['volumes'] as Map?)?.cast<String, dynamic>() ?? const {};
    final occupancy = (_data?['occupancy'] as Map?)?.cast<String, dynamic>() ?? const {};

    return PageContainer(
      child: ListView(
        children: [
          Row(
            children: [
              Text('Dashboard', style: theme.textTheme.titleLarge),
              const Spacer(),
              OutlinedButton.icon(
                onPressed: _loading ? null : _pickRange,
                icon: const Icon(Icons.date_range, size: 18),
                label: Text('${_fmt(_range.start)} → ${_fmt(_range.end)}'),
              ),
              const SizedBox(width: Space.sm),
              IconButton(
                tooltip: 'Refresh',
                onPressed: _loading ? null : _load,
                icon: const Icon(Icons.refresh),
              ),
            ],
          ),
          const SizedBox(height: Space.md),
          if (_error != null) ...[
            MessageBanner.error(_error!),
            const SizedBox(height: Space.md),
          ],
          if (_data == null)
            Center(
              child: Padding(
                padding: const EdgeInsets.all(Space.xl),
                child: Text(_loading ? 'Loading…' : 'No data',
                    style: theme.textTheme.bodyMedium
                        ?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
              ),
            )
          else ...[
            _section(theme, 'Financial (period)'),
            _grid([
              _kpi('Revenue', _money(financial['revenue']), Icons.trending_up),
              _kpi('Expense', _money(financial['expense']), Icons.trending_down),
              _kpi('Net surplus', _money(financial['netSurplus']), Icons.account_balance_wallet_outlined),
              _kpi('Pharmacy revenue', _money(financial['pharmacyRevenue']), Icons.medication_outlined),
              _kpi('Service revenue', _money(financial['serviceRevenue']), Icons.local_hospital_outlined),
            ]),
            _section(theme, 'Receivables & cash (current)'),
            _grid([
              _kpi('Cash & bank', _money(receivables['cashAndBank']), Icons.savings_outlined),
              _kpi('Patient receivable', _money(receivables['patientReceivable']), Icons.person_outline),
              _kpi('Insurance receivable', _money(receivables['insuranceReceivable']), Icons.health_and_safety_outlined),
            ]),
            _section(theme, 'Volumes (period)'),
            _grid([
              _kpi('OPD visits', '${volumes['opdVisits'] ?? 0}', Icons.directions_walk),
              _kpi('IPD admissions', '${volumes['ipdAdmissions'] ?? 0}', Icons.bed_outlined),
              _kpi('New patients', '${volumes['newPatients'] ?? 0}', Icons.person_add_outlined),
              _kpi('Pharmacy sales', '${volumes['pharmacySalesCount'] ?? 0}', Icons.point_of_sale_outlined),
              _kpi('Pharmacy value', _money(volumes['pharmacySalesValue']), Icons.receipt_long_outlined),
            ]),
            _section(theme, 'Occupancy (current)'),
            _grid([
              _kpi('Inpatients', '${occupancy['currentInpatients'] ?? 0}', Icons.airline_seat_individual_suite_outlined),
              _kpi('Total beds', '${occupancy['totalBeds'] ?? 0}', Icons.hotel_outlined),
              _kpi('Occupancy', '${occupancy['occupancyPct'] ?? 0}%', Icons.pie_chart_outline),
            ]),
          ],
        ],
      ),
    );
  }

  Widget _section(ThemeData theme, String title) => Padding(
        padding: const EdgeInsets.only(top: Space.lg, bottom: Space.sm),
        child: Text(title, style: theme.textTheme.titleMedium),
      );

  Widget _grid(List<Widget> tiles) => Wrap(
        spacing: Space.md,
        runSpacing: Space.md,
        children: [for (final t in tiles) SizedBox(width: 240, child: t)],
      );

  Widget _kpi(String label, String value, IconData icon) =>
      KpiTile(label: label, value: value, icon: icon);

  String _money(Object? v) {
    final n = num.tryParse('${v ?? 0}') ?? 0;
    return '₹${n.toStringAsFixed(2)}';
  }
}
