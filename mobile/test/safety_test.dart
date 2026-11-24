import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart' show MethodCall;
import 'package:flutter_test/flutter_test.dart';
import 'package:fraudshield/core/api_client.dart';
import 'package:fraudshield/core/drills.dart';
import 'package:fraudshield/core/protection_engine.dart';
import 'package:fraudshield/core/safety.dart';
import 'package:fraudshield/core/settings_store.dart';
import 'package:fraudshield/screens/dashboard_screen.dart';
import 'package:fraudshield/screens/drills_screen.dart';
import 'package:fraudshield/screens/guardian_screen.dart';
import 'package:fraudshield/screens/recovery_kit_screen.dart';
import 'package:fraudshield/screens/safety_screen.dart';
import 'package:fraudshield/screens/scam_stop_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'helpers.dart';

/// Phase 15 phone side: STOP screen (15.3), event -> STOP (15.2), recovery kit (15.6), guardian (15.8),
/// drills (15.10), UPI check + report (15.5 / 15.7), care number (15.9), guard opt-in (15.2).
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late FakeNative native;
  late List<Req> requests;

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    native = FakeNative()..install();
    requests = [];
  });
  tearDown(() => native.remove());

  Future<void> pump(WidgetTester t, Widget w) async {
    t.view.physicalSize = const Size(1080, 3200);
    t.view.devicePixelRatio = 2.5;
    addTearDown(t.view.reset);
    await t.pumpWidget(MaterialApp(home: w));
    await t.pumpAndSettle();
  }

  List<MethodCall> calls(String m) => native.calls.where((c) => c.method == m).toList();

  group('15.3 STOP screen', () {
    testWidgets('plain steps, speaks on open, 1930 one tap', (t) async {
      await pump(t, const ScamStopScreen());
      expect(find.byKey(const Key('stop-title')), findsOneWidget);
      expect(find.text('Hang up the call now.'), findsOneWidget);
      expect(find.text('Do not share any OTP, PIN or password.'), findsOneWidget);
      final spoken = calls('speak').single.arguments as Map;
      expect(spoken['lang'], 'en');
      expect(spoken['text'], contains('Hang up the call now.'));
      await t.tap(find.byKey(const Key('stop-1930')));
      expect((calls('dialNumber').single.arguments as Map)['number'], '1930');
      expect(find.byKey(const Key('stop-call-guardian')), findsNothing, reason: 'no guardian set');
    });

    testWidgets('no voice for the language -> says so, never blocks', (t) async {
      native.speakOk = false;
      await pump(t, const ScamStopScreen());
      expect(find.byKey(const Key('stop-novoice')), findsOneWidget);
    });

    testWidgets('15.8 guardian: one tap call / message, user sends', (t) async {
      await SettingsStore().setEmergencyContact('Asha', '98765 43210');
      await pump(t, const ScamStopScreen(speakOnOpen: false));
      await t.ensureVisible(find.byKey(const Key('stop-call-guardian')));
      await t.tap(find.byKey(const Key('stop-call-guardian')));
      expect((calls('dialNumber').single.arguments as Map)['number'], '9876543210');
      await t.tap(find.byKey(const Key('stop-tell-guardian')));
      final sms = calls('composeSms').single.arguments as Map;
      expect(sms['number'], '9876543210');
      expect(sms['body'], 'FraudShield alert: I may be talking to a scammer right now. Please call me.',
          reason: 'no message content, no scam details');
    });
  });

  testWidgets('15.2 LIVE_SCAM event from the native guard opens the STOP screen', (t) async {
    t.view.physicalSize = const Size(1080, 3200);
    t.view.devicePixelRatio = 2.5;
    addTearDown(t.view.reset);
    final api = apiWith((r) async => jsonResponse(200, {}));
    final engine = ProtectionEngine(api: api);
    await t.pumpWidget(MaterialApp(home: DashboardScreen(engine: engine, api: api, autoStart: false, onLogout: () {})));
    await t.pumpAndSettle();
    native.pendingEvents = [{'kind': 'LIVE_SCAM', 'level': 'CRITICAL'}];
    await t.runAsync(engine.runFast);
    await t.pumpAndSettle();
    expect(find.byType(ScamStopScreen), findsOneWidget);
    expect(find.text('This looks like a scam happening right now'), findsOneWidget);
  });

  group('15.6 recovery kit', () {
    testWidgets('golden-hour clock, checklist kept, 1930 + portal, bank block numbers from the verified list', (t) async {
      await pump(t, const RecoveryKitScreen());
      await t.tap(find.byKey(const Key('rec-start')));
      await t.pumpAndSettle();
      expect(find.byKey(const Key('rec-timer')), findsOneWidget);
      expect(find.text('0 min since you started'), findsOneWidget);
      await t.tap(find.byKey(const Key('rec-1930')));
      expect((calls('dialNumber').single.arguments as Map)['number'], '1930');
      await t.tap(find.byKey(const Key('rec-portal')));
      expect((calls('openUrl').single.arguments as Map)['url'], 'https://cybercrime.gov.in/');
      await t.ensureVisible(find.byKey(const Key('rec-step-bank')));
      await t.tap(find.byKey(const Key('rec-step-bank')));
      await t.pumpAndSettle();
      expect(await RecoveryState.done(), {'bank'});
      await t.ensureVisible(find.byKey(const Key('rec-bank-State Bank of India')));
      await t.tap(find.byKey(const Key('rec-bank-State Bank of India')));
      await t.pumpAndSettle();
      expect(find.text('1800 11 1109'), findsOneWidget, reason: 'SBI unauthorised-transaction line, read on sbi.bank.in');
      expect(find.text('8008 20 20 20'), findsNothing, reason: 'SMS-only line is not offered for calling');
    });

    testWidgets('evidence stays on the phone until the user shares it', (t) async {
      await pump(t, const RecoveryKitScreen());
      await t.enterText(find.byKey(const Key('ev-who')), 'refund.desk@ybl');
      await t.enterText(find.byKey(const Key('ev-money')), 'Rs 4999 at 10:42');
      expect(calls('shareText'), isEmpty);
      await t.scrollUntilVisible(find.byKey(const Key('rec-share')), 300, scrollable: find.byType(Scrollable).first);
      await t.tap(find.byKey(const Key('rec-share')));
      final text = (calls('shareText').single.arguments as Map)['text'] as String;
      expect(text, contains('refund.desk@ybl'));
      expect(text, contains('Rs 4999 at 10:42'));
    });

    test('block numbers: banks only, fraud lines first, no SMS-only or outgoing-call numbers', () async {
      final all = CareDirectory.parse(await rootBundleString());
      final b = CareDirectory.blockNumbers(all);
      expect(b.keys, contains('State Bank of India'));
      expect(b.keys, isNot(contains('LIC')));
      expect(b['State Bank of India']!.first.forFraud, isTrue);
      for (final l in b.values) {
        for (final e in l) {
          expect(e.smsOnly, isFalse);
          expect(e.purpose, isNot(contains('outgoing')));
        }
      }
      expect(all.where((e) => e.number == '1930'), hasLength(1));
    });
  });

  group('15.8 guardian screen', () {
    testWidgets('invalid number refused, valid saved, remove switches it off', (t) async {
      await pump(t, const GuardianScreen());
      await t.enterText(find.byKey(const Key('guardian-name')), 'Asha');
      await t.enterText(find.byKey(const Key('guardian-phone')), '12345');
      await t.tap(find.byKey(const Key('guardian-save')));
      await t.pumpAndSettle();
      expect(find.text('Enter a name and a valid Indian mobile number'), findsOneWidget);
      await t.enterText(find.byKey(const Key('guardian-phone')), '+91 98765 43210');
      await t.tap(find.byKey(const Key('guardian-save')));
      await t.pumpAndSettle();
      expect(find.text('Asha is your guardian'), findsOneWidget);
      await t.tap(find.byKey(const Key('guardian-remove')));
      await t.pumpAndSettle();
      expect(await SettingsStore().emergencyContact(), isNull);
    });
  });

  group('15.10 drills', () {
    test('5 scenarios, every step has exactly one safe choice', () {
      expect(drills.map((d) => d.id), ['arrest', 'kyc', 'lottery', 'upi', 'job']);
      for (final d in drills) {
        for (final s in d.steps) {
          expect(s.choices.where((c) => c.safe), hasLength(1), reason: s.en);
          expect(s.hi, isNotEmpty);
        }
      }
    });

    testWidgets('play a drill, score saved locally', (t) async {
      await pump(t, const DrillsScreen());
      expect(find.text('Not played yet'), findsNWidgets(5));
      await t.tap(find.byKey(const Key('drill-upi')));
      await t.pumpAndSettle();
      final safe = drills.firstWhere((d) => d.id == 'upi').steps.first.choices.indexWhere((c) => c.safe);
      await t.tap(find.byKey(Key('drill-choice-$safe')));
      await t.pumpAndSettle();
      expect(find.textContaining('Safe choice'), findsOneWidget);
      await t.tap(find.byKey(const Key('drill-next')));
      await t.pumpAndSettle();
      expect(find.text('You scored 100%'), findsOneWidget);
      expect(await DrillStore.best('upi'), 100);
      await t.tap(find.text('Finish'));
      await t.pumpAndSettle();
      expect(find.text('Best: 100%'), findsOneWidget);
    });
  });

  group('15.5 / 15.7 UPI check + report', () {
    ApiClient api() => apiWith((r) async {
          requests.add(Req(r.url.path, r.method == 'POST' ? jsonDecode(r.body) as Map : const {}));
          if (r.url.path.endsWith('/scan/upi')) {
            final b = jsonDecode(r.body) as Map;
            return jsonResponse(200, b.containsKey('qrPayload')
                ? {'verdict': 'MALICIOUS', 'score': 90, 'kind': 'COLLECT', 'reasonCodes': ['COLLECT_REQUEST'],
                    'reasons': ['This QR asks to COLLECT money from you.'], 'reasonsHi': ['यह QR आपसे पैसा लेता है।'],
                    'payee': {'vpa': 'refund.desk@ybl', 'name': 'Refund'}}
                : {'verdict': 'SAFE', 'score': 0, 'kind': 'VPA', 'reasonCodes': [], 'reasons': [], 'payee': {'vpa': b['vpa']}});
          }
          if (r.url.path.endsWith('/reports')) return jsonResponse(202, {'counted': true, 'status': 'UNVERIFIED'});
          return jsonResponse(200, {});
        });

    testWidgets('QR text is sent as qrPayload; bad verdict offers Report (type UPI)', (t) async {
      await pump(t, UpiCheckScreen(api: api()));
      await t.enterText(find.byKey(const Key('upi-input')), 'upi://pay?pa=refund.desk@ybl&pn=Refund&am=4999&mode=collect');
      await t.tap(find.byKey(const Key('upi-check')));
      await t.pumpAndSettle();
      expect(find.byKey(const Key('upi-verdict-MALICIOUS')), findsOneWidget);
      expect(find.text('Do not pay'), findsOneWidget);
      expect(find.text('• This QR asks to COLLECT money from you.'), findsOneWidget);
      await t.tap(find.byKey(const Key('upi-report')));
      await t.pumpAndSettle();
      expect(requests.last.path, endsWith('/reports'));
      expect(requests.last.body, {'type': 'UPI', 'value': 'refund.desk@ybl'});
      expect(find.byKey(const Key('upi-reported')), findsOneWidget);
    });

    testWidgets('plain UPI ID is sent as vpa; safe has no report button', (t) async {
      await pump(t, UpiCheckScreen(api: api()));
      await t.enterText(find.byKey(const Key('upi-input')), 'shop.owner@okaxis');
      await t.tap(find.byKey(const Key('upi-check')));
      await t.pumpAndSettle();
      expect(requests.single.body, {'vpa': 'shop.owner@okaxis'});
      expect(find.text('No warning signs found'), findsOneWidget);
      expect(find.byKey(const Key('upi-report')), findsNothing);
    });
  });

  testWidgets('15.9 fake customer-care number shows the real ones', (t) async {
    String? asked;
    final api = apiWith((r) async {
      asked = r.url.toString();
      return jsonResponse(200, {'number': '9876543210', 'official': false, 'brand': 'State Bank of India', 'verdict': 'FAKE', 'score': 100,
        'reasonCodes': ['PERSONAL_MOBILE_FOR_BRAND'], 'reasons': ['A bank helpline is never a personal mobile number.'],
        'officialNumbers': [{'number': '1800 1234', 'purpose': 'toll-free customer care'}]});
    });
    await pump(t, CareNumberScreen(api: api));
    await t.enterText(find.byKey(const Key('care-number')), '98765 43210');
    await t.enterText(find.byKey(const Key('care-brand')), 'SBI');
    await t.tap(find.byKey(const Key('care-check')));
    await t.pumpAndSettle();
    expect(asked, contains('/care-number/98765%2043210?brand=SBI'));
    expect(find.text('Fake customer-care number'), findsOneWidget);
    expect(find.text('1800 1234'), findsOneWidget);
    await t.tap(find.text('1800 1234'));
    expect((calls('dialNumber').single.arguments as Map)['number'], '18001234');
  });

  testWidgets('15.2 guard opt-in: switch asks for the phone permissions and turns the guard on', (t) async {
    await pump(t, const ScamGuardScreen());
    expect(find.byKey(const Key('perm-phone')), findsOneWidget);
    await t.tap(find.byKey(const Key('guard-switch')));
    await t.pumpAndSettle();
    expect(calls('scamGuardPermissions'), hasLength(1));
    expect((calls('scamGuardSet').single.arguments as Map)['enabled'], true);
    expect(native.guardStatus['enabled'], true);
  });
}

class Req {
  final String path;
  final Map body;
  Req(this.path, this.body);
}

Future<String> rootBundleString() async => File('assets/data/care-numbers.csv').readAsStringSync();
