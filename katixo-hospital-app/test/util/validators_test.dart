import 'package:flutter_test/flutter_test.dart';
import 'package:katixo_hospital_app/core/util/validators.dart';

void main() {
  group('requiredText', () {
    test('rejects null / blank, accepts content', () {
      expect(requiredText(null), isNotNull);
      expect(requiredText('   '), isNotNull);
      expect(requiredText('x'), isNull);
    });
    test('uses the field name in the message', () {
      expect(requiredText('', field: 'Reason'), contains('Reason'));
    });
  });

  group('positiveAmount', () {
    test('rejects non-numbers', () => expect(positiveAmount('abc'), isNotNull));
    test('rejects zero and negatives', () {
      expect(positiveAmount('0'), isNotNull);
      expect(positiveAmount('-5'), isNotNull);
    });
    test('accepts a positive amount', () => expect(positiveAmount('10.50'), isNull));
    test('rejects blank', () => expect(positiveAmount('  '), isNotNull));
  });

  group('positiveInt', () {
    test('rejects fractions and zero', () {
      expect(positiveInt('2.5'), isNotNull);
      expect(positiveInt('0'), isNotNull);
    });
    test('accepts a whole number >= 1', () => expect(positiveInt('3'), isNull));
  });

  group('intInRange (pain 0-10)', () {
    test('rejects out of range', () {
      expect(intInRange('11', min: 0, max: 10), isNotNull);
      expect(intInRange('-1', min: 0, max: 10), isNotNull);
    });
    test('rejects non-integers', () => expect(intInRange('x', min: 0, max: 10), isNotNull));
    test('accepts boundary values', () {
      expect(intInRange('0', min: 0, max: 10), isNull);
      expect(intInRange('10', min: 0, max: 10), isNull);
    });
  });

  group('numInRange (temp 25-45)', () {
    test('rejects out of range', () => expect(numInRange('50', min: 25, max: 45), isNotNull));
    test('accepts an in-range decimal', () => expect(numInRange('37.5', min: 25, max: 45), isNull));
  });

  group('mobileNumber', () {
    test('accepts a valid 10-digit Indian mobile', () => expect(mobileNumber('9876543210'), isNull));
    test('rejects numbers not starting 6-9', () => expect(mobileNumber('1234567890'), isNotNull));
    test('rejects wrong length / non-digits', () {
      expect(mobileNumber('98765'), isNotNull);
      expect(mobileNumber('98765abcde'), isNotNull);
    });
    test('blank is required by default but optional when asked', () {
      expect(mobileNumber(''), isNotNull);
      expect(mobileNumber('', required: false), isNull);
    });
  });

  group('firstError', () {
    test('returns null when all pass', () => expect(firstError([null, null]), isNull));
    test('returns the first failure', () {
      expect(firstError([null, 'bad amount', 'also bad']), 'bad amount');
    });
  });
}
