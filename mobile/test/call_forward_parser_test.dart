import 'dart:convert';
import 'dart:io';
import 'package:flutter_test/flutter_test.dart';
import 'package:fraudshield/core/carrier_classifier.dart';
import 'package:fraudshield/core/protection_engine.dart';

/// Roadmap 3.4: every row of test/fixtures/ussd_responses.json must classify as expected.
/// The 3.4 gate (>=10 REAL carrier texts) is a separate test that is skipped, with the count, until enough
/// real captures exist - synthetic rows never count.
const realNeeded = 10;

List<Map<String, dynamic>> loadRows() {
  final j = jsonDecode(File('test/fixtures/ussd_responses.json').readAsStringSync()) as Map<String, dynamic>;
  return (j['rows'] as List).cast<Map<String, dynamic>>();
}

CarrierState stateOf(String s) => switch (s) {
      'ACTIVE' => CarrierState.active,
      'INACTIVE' => CarrierState.inactive,
      'UNKNOWN' => CarrierState.unknown,
      _ => throw ArgumentError('bad expected "$s"'),
    };

void main() {
  final rows = loadRows();
  final real = rows.where((r) => r['source'] == 'real').toList();
  final c = CarrierClassifier.builtIn();

  group('fixture rows', () {
    for (final r in rows) {
      test('[${r['source']}] ${r['carrier']} ${r['code']} "${r['text']}" -> ${r['expected']}',
          () => expect(c.classify(r['text'] as String), stateOf(r['expected'] as String)));
    }
  });

  test('fixture is well formed', () {
    expect(rows.length, greaterThanOrEqualTo(20));
    for (final r in rows) {
      expect(['real', 'emulator', 'synthetic'], contains(r['source']), reason: '$r');
      expect(['*#21#', '*#67#', '*#61#', '*#62#'], contains(r['code']), reason: '$r');
      stateOf(r['expected'] as String);
      if (r['source'] != 'synthetic') {
        expect(ProtectionEngine.maskPhoneNumbers(r['text'] as String), r['text'],
            reason: 'real captures must have phone numbers masked (digits -> 9): ${r['text']}');
      }
    }
  });

  test('3.4.5 Hindi, mixed language and spaced +91 numbers are covered', () {
    expect(rows.where((r) => RegExp(r'[ऀ-ॿ]').hasMatch(r['text'] as String)).length, greaterThanOrEqualTo(4));
    expect(rows.where((r) => (r['text'] as String).contains('+91 ')).length, greaterThanOrEqualTo(2));
  });

  test('3.4 gate: at least $realNeeded REAL carrier texts (Jio / Airtel / Vi / BSNL)', () {
    expect(real.length, greaterThanOrEqualTo(realNeeded));
    expect(real.map((r) => r['expected']).toSet(), containsAll(['ACTIVE', 'INACTIVE']),
        reason: 'real captures must include forwarding ON and OFF');
  }, skip: real.length < realNeeded ? 'only ${real.length} of $realNeeded real carrier texts so far - capture on a SIM phone' : false);

  test('emulator captures: the bare Android reply is never read as OFF', () {
    final emu = rows.where((r) => r['source'] == 'emulator').toList();
    expect(emu.length, greaterThanOrEqualTo(4));
    for (final r in emu) {
      expect(c.classify(r['text'] as String), isNot(CarrierState.inactive), reason: '$r');
    }
  });

  group('masking for captures', () {
    test('phone numbers become 9s, format kept', () {
      expect(ProtectionEngine.maskPhoneNumbers('Forwarded to +91 98765 43210'), 'Forwarded to +99 99999 99999');
      expect(ProtectionEngine.maskPhoneNumbers('Forwarded to 9876543210'), 'Forwarded to 9999999999');
    });
    test('short numbers (delays, codes) are kept', () {
      expect(ProtectionEngine.maskPhoneNumbers('delay 20s, code 61'), 'delay 20s, code 61');
    });
    test('masked text still classifies the same', () {
      for (final r in rows) {
        final t = r['text'] as String;
        expect(c.classify(ProtectionEngine.maskPhoneNumbers(t)), c.classify(t), reason: t);
      }
    });
  });
}
