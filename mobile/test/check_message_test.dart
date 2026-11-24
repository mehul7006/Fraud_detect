import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fraudshield/core/api_client.dart';
import 'package:fraudshield/core/protection_engine.dart';
import 'package:fraudshield/screens/check_message_screen.dart';
import 'package:fraudshield/screens/dashboard_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'helpers.dart';

/// 14.4 paste box, 14.7 consent (cloud never without the tap), 14.8 one verdict for message + links.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late FakeNative native;
  late List<String> calls;

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    native = FakeNative()..install();
    calls = [];
  });
  tearDown(() => native.remove());

  ApiClient api() => apiWith((r) async {
        calls.add(r.url.path);
        if (r.url.path.endsWith('/scan/url')) {
          final u = (jsonDecode(r.body) as Map)['url'] as String;
          return jsonResponse(200, u.contains('fleepkart')
              ? {'verdict': 'MALICIOUS', 'score': 95, 'reasons': ['x'], 'impersonatedBrand': 'Flipkart', 'officialUrl': 'https://www.flipkart.com'}
              : {'verdict': 'SAFE', 'score': 0, 'reasons': []});
        }
        if (r.url.path.endsWith('/scan/message')) {
          expect((jsonDecode(r.body) as Map)['consent'], true);
          return jsonResponse(200, {'verdict': 'MALICIOUS', 'score': 92, 'category': 'LOTTERY_PRIZE', 'links': [],
            'explanation': {'en': 'Looks like a fake prize or lottery.', 'hi': 'यह नकली इनाम या लॉटरी लगता है।', 'source': 'template'}});
        }
        return jsonResponse(200, {});
      });

  Future<void> pump(WidgetTester t, String text) async {
    t.view.physicalSize = const Size(1080, 3000);
    t.view.devicePixelRatio = 2.5;
    addTearDown(t.view.reset);
    await t.pumpWidget(MaterialApp(home: CheckMessageScreen(api: api(), initialText: text)));
    await t.pumpAndSettle();
  }

  testWidgets('scam with a fake-Flipkart link: message AND link verdict in one place; text not sent', (t) async {
    native.messageModel = (_) => {'scam': 0.97, 'category': 'LOTTERY_PRIZE', 'categoryProbability': 0.9, 'ms': 3.0};
    await pump(t, 'Congratulations! You won Rs 25,00,000 in the Flipkart lucky draw. Claim now http://fleepkart.com/claim');
    expect(find.byKey(const Key('msg-verdict-MALICIOUS')), findsOneWidget);
    expect(find.textContaining('fake prize / lottery scam'), findsOneWidget);
    expect(find.text('Fake Flipkart website - do not open'), findsOneWidget);
    expect(find.textContaining('Promises a prize'), findsOneWidget, reason: 'rule explains itself');
    expect(calls.where((c) => c.endsWith('/scan/message')), isEmpty, reason: 'message text stays on the phone');
    expect(calls.where((c) => c.endsWith('/scan/url')).length, 1, reason: 'only the link is checked online');
  });

  testWidgets('cloud check never fires without the tap; consent first, then merged result', (t) async {
    native.messageModel = (_) => {'scam': 0.55, 'category': 'LOTTERY_PRIZE', 'categoryProbability': 0.6, 'ms': 3.0};
    await pump(t, 'You have been selected for a special reward, reply to know more');
    await t.ensureVisible(find.byKey(const Key('msg-cloud')));
    await t.tap(find.byKey(const Key('msg-cloud')));
    await t.pumpAndSettle();
    expect(find.textContaining('not stored, not logged and not linked to you'), findsOneWidget, reason: 'first-time explanation');
    await t.tap(find.byKey(const Key('msg-cloud-no')));
    await t.pumpAndSettle();
    expect(calls.where((c) => c.endsWith('/scan/message')), isEmpty, reason: '"Not now" = nothing sent');
    await t.ensureVisible(find.byKey(const Key('msg-cloud')));
    await t.tap(find.byKey(const Key('msg-cloud')));
    await t.pumpAndSettle();
    await t.tap(find.byKey(const Key('msg-cloud-yes')));
    await t.pumpAndSettle();
    expect(calls.where((c) => c.endsWith('/scan/message')).length, 1);
    expect(find.byKey(const Key('msg-verdict-MALICIOUS')), findsOneWidget);
    expect(find.text('Includes the cloud check (message not stored).'), findsOneWidget);
    expect(find.byKey(const Key('why-panel')), findsOneWidget);
  });

  testWidgets('a real OTP message is safe', (t) async {
    await pump(t, '482913 is your OTP for Rs 1,250 at Amazon. Do not share this OTP with anyone. - HDFC Bank');
    expect(find.byKey(const Key('msg-verdict-SAFE')), findsOneWidget);
    expect(find.text('No scam signs found'), findsOneWidget);
  });

  testWidgets('14.4: a shared whole message opens "Check a message" with its verdict', (t) async {
    native.messageModel = (_) => {'scam': 0.95, 'category': 'BANK_KYC_BLOCK', 'categoryProbability': 0.9, 'ms': 2.0};
    t.view.physicalSize = const Size(1080, 3000);
    t.view.devicePixelRatio = 2.5;
    addTearDown(t.view.reset);
    final a = api();
    final engine = ProtectionEngine(api: a);
    await t.pumpWidget(MaterialApp(home: DashboardScreen(engine: engine, api: a, autoStart: false, onLogout: () {})));
    await t.pumpAndSettle();
    await t.runAsync(() => engine.scanShared('Dear customer your SBI account will be blocked today, update KYC now http://sbi-kyc.top/'));
    await t.pumpAndSettle();
    expect(find.byType(CheckMessageScreen), findsOneWidget);
    expect(find.byKey(const Key('msg-verdict-MALICIOUS')), findsOneWidget);
  });
}
