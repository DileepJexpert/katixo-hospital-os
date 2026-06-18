/// Shared display formatters — money, dates, times.
///
/// Screens used to reinvent these (10+ private `_isoDate` / `_formatTime` /
/// inline `'₹$x'` helpers, each slightly different). Use these so currency and
/// dates render the same everywhere.
library;

/// Indian-grouped rupee amount, e.g. `₹1,23,456.00`.
///
/// Accepts a num or anything stringifiable to a number (the API hands back
/// money as raw `num`/`String` in `Map<String,dynamic>`). Falls back to 0 for
/// unparseable input rather than throwing in the UI.
String formatMoney(Object? amount, {int decimals = 2}) {
  final n = amount is num
      ? amount.toDouble()
      : double.tryParse('${amount ?? ''}'.trim()) ?? 0;
  final fixed = n.abs().toStringAsFixed(decimals);
  final dot = fixed.indexOf('.');
  final intDigits = dot == -1 ? fixed : fixed.substring(0, dot);
  final frac = dot == -1 ? '' : fixed.substring(dot); // includes the '.'
  final sign = n < 0 ? '-' : '';
  return '₹$sign${_groupIndian(intDigits)}$frac';
}

/// 2-2-3 (lakh/crore) digit grouping: `12345678` -> `1,23,45,678`.
String _groupIndian(String digits) {
  if (digits.length <= 3) return digits;
  final last3 = digits.substring(digits.length - 3);
  final rest = digits.substring(0, digits.length - 3);
  final buf = StringBuffer();
  for (var i = 0; i < rest.length; i++) {
    if (i > 0 && (rest.length - i) % 2 == 0) buf.write(',');
    buf.write(rest[i]);
  }
  return '$buf,$last3';
}

/// `YYYY-MM-DD` for an ISO timestamp string or DateTime (or '' if null/blank).
/// Mirrors the common `'$iso'.split('T').first` the screens were doing.
String formatDate(Object? value) {
  final s = _iso(value);
  if (s.isEmpty) return '';
  return s.split('T').first;
}

/// `HH:mm` extracted from an ISO timestamp string or DateTime.
String formatTime(Object? value) {
  final s = _iso(value);
  if (!s.contains('T')) return '';
  final time = s.split('T')[1];
  final hhmm = time.length >= 5 ? time.substring(0, 5) : time;
  return hhmm;
}

/// `YYYY-MM-DD HH:mm` for a timestamp (or '' if null/blank).
String formatDateTime(Object? value) {
  final d = formatDate(value);
  final t = formatTime(value);
  if (d.isEmpty) return '';
  return t.isEmpty ? d : '$d $t';
}

String _iso(Object? value) {
  if (value == null) return '';
  if (value is DateTime) return value.toIso8601String();
  return '$value'.trim();
}
