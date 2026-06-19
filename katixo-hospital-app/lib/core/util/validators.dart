/// Pure input validators for forms and dialogs.
///
/// Each returns a human-readable error message when the value is invalid, or
/// `null` when it is acceptable — so the same function works both as a
/// `TextFormField.validator` and for the "validate after the dialog pops, set
/// an error banner" pattern used across the billing/clinical screens.
library;

/// A required free-text field.
String? requiredText(String? value, {String field = 'This field'}) {
  if (value == null || value.trim().isEmpty) return '$field is required';
  return null;
}

/// A positive monetary/numeric amount ( > 0 ).
String? positiveAmount(String? value, {String field = 'Amount'}) {
  final n = double.tryParse((value ?? '').trim());
  if (n == null) return '$field must be a number';
  if (n <= 0) return '$field must be greater than zero';
  return null;
}

/// A positive integer quantity ( >= 1 ).
String? positiveInt(String? value, {String field = 'Quantity'}) {
  final n = int.tryParse((value ?? '').trim());
  if (n == null) return '$field must be a whole number';
  if (n < 1) return '$field must be at least 1';
  return null;
}

/// An integer within [min, max] inclusive (e.g. a 0–10 pain score).
String? intInRange(String? value,
    {required int min, required int max, String field = 'Value'}) {
  final n = int.tryParse((value ?? '').trim());
  if (n == null) return '$field must be a whole number';
  if (n < min || n > max) return '$field must be between $min and $max';
  return null;
}

/// A number within [min, max] inclusive (e.g. a plausible temperature).
String? numInRange(String? value,
    {required double min, required double max, String field = 'Value'}) {
  final n = double.tryParse((value ?? '').trim());
  if (n == null) return '$field must be a number';
  if (n < min || n > max) return '$field must be between $min and $max';
  return null;
}

/// An Indian 10-digit mobile number. When [required] is false an empty value
/// passes (for optional contact fields).
String? mobileNumber(String? value,
    {bool required = true, String field = 'Mobile number'}) {
  final v = (value ?? '').trim();
  if (v.isEmpty) return required ? '$field is required' : null;
  if (!RegExp(r'^[6-9]\d{9}$').hasMatch(v)) {
    return 'Enter a valid 10-digit mobile number';
  }
  return null;
}

/// Returns the first non-null error among [results], or null if all pass.
/// Handy for validating several dialog fields after the dialog pops:
/// `final err = firstError([positiveAmount(a), requiredText(r, field: 'Reason')]);`
String? firstError(List<String?> results) {
  for (final r in results) {
    if (r != null) return r;
  }
  return null;
}
