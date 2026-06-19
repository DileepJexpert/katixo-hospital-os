import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:katixo_hospital_app/core/widgets/empty_state.dart';
import 'package:katixo_hospital_app/core/widgets/kpi_tile.dart';
import 'package:katixo_hospital_app/core/widgets/message_banner.dart';
import 'package:katixo_hospital_app/core/widgets/status_chip.dart';

/// Wraps a body in the minimum Material scaffolding a leaf widget needs
/// (theme + Material ancestor for Card/InkWell).
Widget _wrap(Widget child) => MaterialApp(home: Scaffold(body: child));

void main() {
  group('MessageBanner', () {
    testWidgets('error shows the message and the error icon', (tester) async {
      await tester.pumpWidget(_wrap(MessageBanner.error('Something went wrong')));
      expect(find.text('Something went wrong'), findsOneWidget);
      expect(find.byIcon(Icons.error_outline), findsOneWidget);
    });
    testWidgets('success shows the message and the success icon', (tester) async {
      await tester.pumpWidget(_wrap(MessageBanner.success('Saved')));
      expect(find.text('Saved'), findsOneWidget);
      expect(find.byIcon(Icons.check_circle_outline), findsOneWidget);
    });
  });

  group('StatusChip.auto', () {
    testWidgets('humanises the status label (underscores to spaces)', (tester) async {
      await tester.pumpWidget(_wrap(StatusChip.auto('IN_QUEUE')));
      expect(find.text('IN QUEUE'), findsOneWidget);
    });
    testWidgets('renders a known status', (tester) async {
      await tester.pumpWidget(_wrap(StatusChip.auto('PAID')));
      expect(find.text('PAID'), findsOneWidget);
    });
  });

  group('EmptyState', () {
    testWidgets('shows icon, title and message', (tester) async {
      await tester.pumpWidget(_wrap(const EmptyState(
        icon: Icons.inbox_outlined,
        title: 'Nothing here',
        message: 'Add something to get started.',
      )));
      expect(find.text('Nothing here'), findsOneWidget);
      expect(find.text('Add something to get started.'), findsOneWidget);
      expect(find.byIcon(Icons.inbox_outlined), findsOneWidget);
    });
  });

  group('KpiTile', () {
    testWidgets('shows label and value', (tester) async {
      await tester.pumpWidget(_wrap(const KpiTile(label: 'Occupancy', value: '82%')));
      expect(find.text('Occupancy'), findsOneWidget);
      expect(find.text('82%'), findsOneWidget);
    });
  });
}
