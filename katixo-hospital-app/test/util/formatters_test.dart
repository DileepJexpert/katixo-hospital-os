import 'package:flutter_test/flutter_test.dart';
import 'package:katixo_hospital_app/core/util/formatters.dart';

void main() {
  group('formatMoney', () {
    test('formats with two decimals and the rupee sign', () {
      expect(formatMoney(1234.5), '₹1,234.50');
    });
    test('uses Indian 2-2-3 grouping (lakh / crore)', () {
      expect(formatMoney(123456), '₹1,23,456.00');
      expect(formatMoney(12345678), '₹1,23,45,678.00');
    });
    test('handles zero and small values', () {
      expect(formatMoney(0), '₹0.00');
      expect(formatMoney(50), '₹50.00');
    });
    test('parses numeric strings and tolerates null', () {
      expect(formatMoney('500'), '₹500.00');
      expect(formatMoney(null), '₹0.00');
      expect(formatMoney('not-a-number'), '₹0.00');
    });
    test('negative amounts keep the sign after the symbol', () {
      expect(formatMoney(-1234.5), '₹-1,234.50');
    });
    test('honours a custom decimal count', () {
      expect(formatMoney(100, decimals: 0), '₹100');
      expect(formatMoney(1234, decimals: 0), '₹1,234');
    });
  });

  group('formatDate', () {
    test('takes the date part of an ISO timestamp', () {
      expect(formatDate('2026-06-19T10:30:00'), '2026-06-19');
    });
    test('passes through a plain date', () => expect(formatDate('2026-06-19'), '2026-06-19'));
    test('formats a DateTime', () => expect(formatDate(DateTime(2026, 6, 19)), '2026-06-19'));
    test('empty by default for null, or the ifEmpty placeholder', () {
      expect(formatDate(null), '');
      expect(formatDate(null, ifEmpty: '—'), '—');
      expect(formatDate('  ', ifEmpty: '—'), '—');
    });
  });

  group('formatTime', () {
    test('extracts HH:mm from an ISO timestamp', () {
      expect(formatTime('2026-06-19T10:30:45'), '10:30');
    });
    test('ifEmpty when there is no time component', () {
      expect(formatTime('2026-06-19'), '');
      expect(formatTime('2026-06-19', ifEmpty: '—'), '—');
    });
  });

  group('formatDateTime', () {
    test('joins date and time', () {
      expect(formatDateTime('2026-06-19T10:30:00'), '2026-06-19 10:30');
    });
    test('date only when no time present', () {
      expect(formatDateTime('2026-06-19'), '2026-06-19');
    });
    test('ifEmpty for null', () {
      expect(formatDateTime(null), '');
      expect(formatDateTime(null, ifEmpty: '—'), '—');
    });
  });
}
