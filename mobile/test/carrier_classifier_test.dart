import 'package:flutter_test/flutter_test.dart';
import 'package:fraudshield/core/carrier_classifier.dart';

/// Same sample answers as the backend ForwardingIT corpus. SYNTHETIC: common GSM wording, not captured from
/// Jio / Airtel / Vi / BSNL. Real captures must be appended here (roadmap 3.4) before 3.4 can be closed.
const corpus = <String, CarrierState>{
  'Voice call forwarding: Active. Forwarded to +919876543210': CarrierState.active,
  'Forwarded to 9876543210': CarrierState.active,
  'Call forward unconditional: registered and active, number +14155552671': CarrierState.active,
  'Divert on busy: Active +447911123456': CarrierState.active,
  'Forward when unreachable: Forwarded to 8000123456, delay 20s': CarrierState.active,
  'Call forwarding is not active': CarrierState.inactive,
  'Unconditional forwarding: Not forwarded': CarrierState.inactive,
  'Not forwarded to 9876543210': CarrierState.inactive, // number present but NOT forwarded
  'The service was disabled': CarrierState.inactive,
  'Service not provisioned': CarrierState.inactive,
  'Forwarding is OFF': CarrierState.inactive,
  'Call forwarding: Deactivated': CarrierState.inactive,
  'Connection problem or invalid MMI code.': CarrierState.unknown,
  'Your request is being processed': CarrierState.unknown,
  'Please try again later': CarrierState.unknown,
  '': CarrierState.unknown,
};

void main() {
  group('3.4 built-in rules on the sample corpus', () {
    final c = CarrierClassifier.builtIn();
    corpus.forEach((text, expected) {
      test('"$text" -> $expected', () => expect(c.classify(text), expected));
    });
    test('corpus has at least 12 samples', () => expect(corpus.length, greaterThanOrEqualTo(12)));
    test('all 9 built-in rules compile (inline (?i) translated)', () => expect(c.ruleCount, 9));
  });

  group('classifier behaviour', () {
    test('lower priority number wins regardless of list order', () {
      final c = CarrierClassifier(const [
        CarrierRule('ACTIVE', r'\d{10}', 30),
        CarrierRule('INACTIVE', r'(?i)not forwarded', 10),
      ]);
      expect(c.classify('Not forwarded to 9876543210'), CarrierState.inactive);
      expect(c.classify('Forwarded to 9876543210'), CarrierState.active);
    });

    test('null / blank is unknown', () {
      final c = CarrierClassifier.builtIn();
      expect(c.classify(null), CarrierState.unknown);
      expect(c.classify('   '), CarrierState.unknown);
    });

    test('broken, oversized and unknown-kind rules are skipped, not fatal', () {
      final c = CarrierClassifier([
        const CarrierRule('ACTIVE', '([unclosed', 1),
        CarrierRule('ACTIVE', 'a' * 201, 2),
        const CarrierRule('BOGUS', 'x', 3),
        const CarrierRule('ACTIVE', r'(?i)forwarded', 4),
      ]);
      expect(c.ruleCount, 1);
      expect(c.classify('FORWARDED'), CarrierState.active, reason: 'leading (?i) must make it case-insensitive');
    });

    test('rules without (?i) stay case-sensitive', () {
      final c = CarrierClassifier(const [CarrierRule('ACTIVE', 'ON', 1)]);
      expect(c.classify('ON'), CarrierState.active);
      expect(c.classify('on'), CarrierState.unknown);
    });

    test('only the first 500 characters are examined', () {
      final c = CarrierClassifier(const [CarrierRule('ACTIVE', 'SECRET', 1)]);
      expect(c.classify('${'x' * 600}SECRET'), CarrierState.unknown);
      expect(c.classify('${'x' * 100}SECRET'), CarrierState.active);
    });

    test('kind is case-insensitive', () {
      expect(CarrierClassifier(const [CarrierRule('inactive', 'off', 1)]).classify('off'), CarrierState.inactive);
    });
  });

  test('CarrierConfig parses the backend JSON shape', () {
    final cfg = CarrierConfig.fromJson({
      'version': 'abcd1234abcd1234',
      'rules': [
        {'country': '*', 'carrier': '*', 'kind': 'ACTIVE', 'regex': r'\d{10}', 'priority': 30},
      ],
    });
    expect(cfg.version, 'abcd1234abcd1234');
    expect(cfg.rules.single.priority, 30);
    expect(CarrierClassifier(cfg.rules).classify('9876543210'), CarrierState.active);
  });
}
