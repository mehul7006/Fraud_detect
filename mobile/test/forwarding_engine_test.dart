import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:fraudshield/core/carrier_classifier.dart';
import 'package:fraudshield/core/protection_engine.dart';
import 'package:http/http.dart' as http;
import 'helpers.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late FakeNative native;
  late List<http.Request> requests;
  late bool carrierServerUp;
  late String serverVersion;
  late List<Map<String, dynamic>> serverRules;
  late bool reportFails;

  ProtectionEngine newEngine() {
    final api = apiWith((r) async {
      requests.add(r);
      if (r.url.path.endsWith('/config/carriers')) {
        if (!carrierServerUp) return http.Response('down', 503);
        if (r.headers['If-None-Match'] == '"$serverVersion"') return http.Response('', 304);
        return jsonResponse(200, {'version': serverVersion, 'rules': serverRules});
      }
      if (r.url.path.endsWith('/threats')) {
        if (reportFails) return http.Response('boom', 500);
        return jsonResponse(201, {'id': 1});
      }
      return jsonResponse(200, {});
    });
    return ProtectionEngine(api: api);
  }

  setUp(() {
    native = FakeNative()..install();
    requests = [];
    carrierServerUp = false; // by default the built-in rules are used
    serverVersion = 'v1';
    serverRules = [];
    reportFails = false;
  });
  tearDown(() => native.remove());

  List<http.Request> reports() => requests.where((r) => r.method == 'POST' && r.url.path.endsWith('/threats')).toList();

  group('3.4 capturing carrier replies for the fixture file', () {
    test('asks all four codes even when the first is ACTIVE, masks numbers, sends nothing', () async {
      final e = newEngine();
      native.ussd = (code) => code == '*#21#' ? 'Forwarded to +91 98765 43210' : 'Call forwarding is not active';
      final rows = await e.captureCarrierReplies();
      expect(rows.map((r) => r['code']), ['*#21#', '*#67#', '*#61#', '*#62#']);
      expect(rows.first['text'], 'Forwarded to +99 99999 99999');
      expect(rows.first['classifiedAs'], 'ACTIVE');
      expect(rows[1]['classifiedAs'], 'INACTIVE');
      expect(requests.where((r) => r.body.contains('98765')), isEmpty, reason: 'raw carrier text never leaves the phone');
      expect(reports(), isEmpty, reason: 'capturing is not a check: no alert, no report');
    });
  });

  group('3.3 querying the carrier', () {
    test('asks all four forwarding codes, in order, when nothing is forwarded -> OFF', () async {
      final e = newEngine();
      await e.checkCallForwarding();
      expect(native.ussdCodes(), ['*#21#', '*#67#', '*#61#', '*#62#']);
      expect(e.status.value['forwarding'], 'OFF');
      expect(reports(), isEmpty);
    });

    test('forwarding on the FIRST code -> ACTIVE, alert, one backend report, native notification', () async {
      final e = newEngine();
      final alerts = <Alert>[];
      e.alerts.stream.listen(alerts.add);
      native.ussd = (code) => code == '*#21#' ? 'Voice call forwarding: Active. Forwarded to +919876543210' : 'not active';

      await e.checkCallForwarding();
      await Future<void>.delayed(Duration.zero);

      expect(e.status.value['forwarding'], 'ACTIVE');
      expect(native.ussdCodes(), ['*#21#'], reason: 'stops at the first active condition');
      expect(alerts, hasLength(1));
      expect(alerts.single.severity, 'CRITICAL');
      expect(alerts.single.actions.map((a) => a.label), ['Turn OFF forwarding']);
      expect(alerts.single.body, contains('all calls'));
      expect(native.count('showAlert'), 1);
      final body = jsonDecode(reports().single.body) as Map<String, dynamic>;
      expect(body['type'], 'CALL_FORWARDING');
      expect(body['severity'], 'CRITICAL');
    });

    test('forwarding only "when not answered" (3rd code) is still found', () async {
      final e = newEngine();
      final alerts = <Alert>[];
      e.alerts.stream.listen(alerts.add);
      native.ussd = (code) => code == '*#61#' ? 'Forwarded to 9876543210' : 'Call forwarding is not active';
      await e.checkCallForwarding();
      await Future<void>.delayed(Duration.zero);
      expect(e.status.value['forwarding'], 'ACTIVE');
      expect(native.ussdCodes(), ['*#21#', '*#67#', '*#61#']);
      expect(alerts.single.body, contains('when not answered'));
    });

    test('answer we cannot understand -> UNKNOWN, no alert', () async {
      final e = newEngine();
      final alerts = <Alert>[];
      e.alerts.stream.listen(alerts.add);
      native.ussd = (_) => 'Your request is being processed';
      await e.checkCallForwarding();
      await Future<void>.delayed(Duration.zero);
      expect(e.status.value['forwarding'], 'UNKNOWN');
      expect(alerts, isEmpty);
      expect(reports(), isEmpty);
    });

    test('the unrecognised carrier wording is kept so it can be shown (and never sent to the backend)', () async {
      final e = newEngine();
      native.ussd = (code) => code == '*#21#' ? 'Call forwarding query result: state=weird' : 'Call forwarding is not active';
      await e.checkCallForwarding();
      expect(e.status.value['forwarding'], 'UNKNOWN');
      expect(e.unrecognisedAnswer.value, 'Call forwarding query result: state=weird');
      expect(requests.where((r) => r.body.contains('weird')), isEmpty, reason: 'raw carrier text must stay on the phone');
    });

    test('unrecognised answer is cleared by the next successful check', () async {
      final e = newEngine();
      native.ussd = (_) => 'gibberish';
      await e.checkCallForwarding();
      expect(e.unrecognisedAnswer.value, 'gibberish');
      native.ussd = (_) => 'Call forwarding is not active';
      await e.checkCallForwarding();
      expect(e.unrecognisedAnswer.value, isNull);
      expect(e.status.value['forwarding'], 'OFF');
    });

    test('one unknown + rest inactive stays UNKNOWN (cannot confirm OFF)', () async {
      final e = newEngine();
      native.ussd = (code) => code == '*#67#' ? 'Please try again later' : 'Call forwarding is not active';
      await e.checkCallForwarding();
      expect(e.status.value['forwarding'], 'UNKNOWN');
    });
  });

  // Captured on the Android 13 emulator (real telephony stack, simulated network): with service class 0 Android
  // answers only "Call forwarding\n" for forwarding ON and OFF alike. See docs/evidence/3.3-3.4-results.md.
  group('3.3 bare "Call forwarding" reply + Android forwarding flag', () {
    const bare = 'Call forwarding\n';

    test('bare reply, flag ON -> ACTIVE, alert and report', () async {
      final e = newEngine();
      final alerts = <Alert>[];
      e.alerts.stream.listen(alerts.add);
      native.ussd = (_) => bare;
      native.forwardingIndicator = true;
      await e.checkCallForwarding();
      await Future<void>.delayed(Duration.zero);
      expect(e.status.value['forwarding'], 'ACTIVE');
      expect(alerts.single.body, contains('all calls'));
      expect(alerts.single.body, contains('call forwarding icon'));
      expect(reports(), hasLength(1));
    });

    for (final flag in [false, null]) {
      test('bare reply, flag $flag -> UNKNOWN, never OFF (the network may still forward)', () async {
        final e = newEngine();
        native.ussd = (_) => bare;
        native.forwardingIndicator = flag;
        await e.checkCallForwarding();
        expect(e.status.value['forwarding'], 'UNKNOWN');
        expect(reports(), isEmpty);
      });
    }

    test('flag ON wins over "not active" texts (unconditional forwarding set by another app or the network)', () async {
      final e = newEngine();
      native.forwardingIndicator = true;
      await e.checkCallForwarding();
      expect(e.status.value['forwarding'], 'ACTIVE');
    });

    test('flag ON still reported when the carrier cannot be asked (no CALL_PHONE)', () async {
      final e = newEngine();
      native.ussd = (_) => 'NO_PERMISSION';
      native.forwardingIndicator = true;
      await e.checkCallForwarding();
      expect(e.status.value['forwarding'], 'ACTIVE');
    });

    test('clear text still decides when the flag is OFF', () async {
      final e = newEngine();
      native.forwardingIndicator = false;
      await e.checkCallForwarding();
      expect(e.status.value['forwarding'], 'OFF');
      native.ussd = (code) => code == '*#61#' ? 'Forwarded to 9876543210' : 'Call forwarding is not active';
      await e.checkCallForwarding();
      expect(e.status.value['forwarding'], 'ACTIVE', reason: 'conditional forwarding never sets the flag');
    });

    test('capture rows include the flag', () async {
      final e = newEngine();
      native.ussd = (_) => bare;
      native.forwardingIndicator = true;
      final rows = await e.captureCarrierReplies();
      expect(rows.map((r) => r['androidForwardingIcon']).toSet(), {'true'});
    });
  });

  group('3.6 cannot query -> UNAVAILABLE + manual fallback', () {
    for (final failure in ['NO_PERMISSION', 'UNSUPPORTED', 'ERROR:2']) {
      test('$failure -> UNAVAILABLE after a single attempt', () async {
        final e = newEngine();
        native.ussd = (_) => failure;
        await e.checkCallForwarding();
        expect(e.status.value['forwarding'], 'UNAVAILABLE');
        expect(native.ussdCodes(), ['*#21#'], reason: 'other codes would fail the same way');
      });
    }

    test('native exception -> UNAVAILABLE, no crash', () async {
      final e = newEngine();
      native.ussdThrows = () => null;
      await e.checkCallForwarding();
      expect(e.status.value['forwarding'], 'UNAVAILABLE');
    });

    test('dialForwardingCheck opens the dialer with *#21#', () async {
      await newEngine().dialForwardingCheck();
      final dial = native.calls.singleWhere((c) => c.method == 'dialCode');
      expect((dial.arguments as Map)['code'], '*#21#');
    });
  });

  group('3.5 one-tap disable', () {
    test('"Turn OFF forwarding" runs ##002#, re-checks, and the status becomes OFF', () async {
      final e = newEngine();
      var forwardingOn = true;
      native.ussd = (_) => forwardingOn ? 'Forwarded to +919876543210' : 'Call forwarding is not active';
      native.onDisableForwarding = () => forwardingOn = false;
      final alerts = <Alert>[];
      e.alerts.stream.listen(alerts.add);

      await e.checkCallForwarding();
      await Future<void>.delayed(Duration.zero);
      expect(e.status.value['forwarding'], 'ACTIVE');

      await alerts.single.actions.single.run();

      expect(native.count('disableCallForwarding'), 1);
      expect(e.status.value['forwarding'], 'OFF', reason: 'tile must reflect the re-check, not assume success');
    });

    test('if the carrier refuses to turn it off, the status stays ACTIVE and the user is alerted again', () async {
      final e = newEngine();
      native.ussd = (_) => 'Forwarded to +919876543210'; // carrier ignores ##002#
      final alerts = <Alert>[];
      e.alerts.stream.listen(alerts.add);
      await e.checkCallForwarding();
      await Future<void>.delayed(Duration.zero);
      await alerts.first.actions.single.run();
      await Future<void>.delayed(Duration.zero);
      expect(e.status.value['forwarding'], 'ACTIVE');
      expect(alerts, hasLength(2));
    });
  });

  group('3.2 server rules (3.4 mechanism)', () {
    test('server rules replace the built-in ones: wording the built-in rules do not know becomes ACTIVE', () async {
      carrierServerUp = true;
      serverRules = [
        {'country': '*', 'carrier': 'X', 'kind': 'ACTIVE', 'regex': 'FWD-ON', 'priority': 1},
      ];
      final e = newEngine();
      native.ussd = (_) => 'FWD-ON';
      await e.checkCallForwarding();
      expect(e.status.value['forwarding'], 'ACTIVE');
    });

    test('second check sends If-None-Match with the version and keeps rules on 304', () async {
      carrierServerUp = true;
      serverRules = [
        {'country': '*', 'carrier': 'X', 'kind': 'ACTIVE', 'regex': 'FWD-ON', 'priority': 1},
      ];
      final e = newEngine();
      native.ussd = (_) => 'FWD-ON';
      await e.checkCallForwarding();
      await e.checkCallForwarding();
      final cfg = requests.where((r) => r.url.path.endsWith('/config/carriers')).toList();
      expect(cfg, hasLength(2));
      expect(cfg.first.headers.containsKey('If-None-Match'), false);
      expect(cfg.last.headers['If-None-Match'], '"v1"');
      expect(e.status.value['forwarding'], 'ACTIVE', reason: '304 must not drop the downloaded rules');
    });

    test('server down -> built-in rules still work', () async {
      carrierServerUp = false;
      final e = newEngine();
      native.ussd = (_) => 'Forwarded to 9876543210';
      await e.checkCallForwarding();
      expect(e.status.value['forwarding'], 'ACTIVE');
    });

    test('server sends an empty rule list -> ignored, built-in rules kept', () async {
      carrierServerUp = true;
      serverRules = [];
      final e = newEngine();
      native.ussd = (_) => 'Forwarded to 9876543210';
      await e.checkCallForwarding();
      expect(e.status.value['forwarding'], 'ACTIVE');
    });
  });

  test('a failing backend report does not hide the alert from the user', () async {
    reportFails = true;
    final e = newEngine();
    final alerts = <Alert>[];
    e.alerts.stream.listen(alerts.add);
    native.ussd = (_) => 'Forwarded to 9876543210';
    await e.checkCallForwarding();
    await Future<void>.delayed(Duration.zero);
    expect(alerts, hasLength(1));
    expect(e.status.value['forwarding'], 'ACTIVE');
  });

  test('looksForwarded (old API) agrees with the classifier', () {
    expect(ProtectionEngine.looksForwarded('Forwarded to +919876543210'), true);
    expect(ProtectionEngine.looksForwarded('Not forwarded to 9876543210'), false);
    expect(CarrierClassifier.builtIn().ruleCount, 9);
  });
}
