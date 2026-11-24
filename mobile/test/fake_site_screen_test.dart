import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fraudshield/core/protection_engine.dart';
import 'package:fraudshield/screens/dashboard_screen.dart';
import 'package:fraudshield/screens/fake_site_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'helpers.dart';

/// 10.11: "This looks like Flipkart but is NOT Flipkart".
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late FakeNative native;

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    native = FakeNative()..install();
  });
  tearDown(() => native.remove());

  const site = FakeSite(
    url: 'https://fleepkart.com/big-billion-days',
    brand: 'Flipkart',
    officialUrl: 'https://www.flipkart.com',
    reasons: ['Looks like Flipkart but is not Flipkart: Misspells "flipkart" by 2 letters', 'Website was created only 3 days ago'],
  );

  Future<void> pump(WidgetTester t, Widget w) async {
    t.view.physicalSize = const Size(1080, 2400);
    t.view.devicePixelRatio = 2.5;
    addTearDown(t.view.reset);
    await t.pumpWidget(MaterialApp(home: Builder(builder: (c) => Scaffold(
          body: Center(child: FilledButton(onPressed: () => Navigator.push(c, MaterialPageRoute(builder: (_) => w)), child: const Text('open'))),
        ))));
    await t.tap(find.text('open'));
    await t.pumpAndSettle();
  }

  test('letters that differ are marked', () {
    final d = differingLetters('fleepkart.com', 'flipkart.com');
    final marked = [for (var i = 0; i < d.length; i++) if (d[i]) 'fleepkart.com'[i]];
    expect(marked, ['e', 'e']);
    expect(differingLetters('flipkart.com', 'flipkart.com').contains(true), isFalse);
    expect(differingLetters('f1ipkart.com', 'flipkart.com').where((x) => x).length, 1, reason: '1 instead of l');
  });

  test('fromScan needs a brand and an https official address', () {
    expect(FakeSite.fromScan('x', {'verdict': 'MALICIOUS', 'reasons': []}), isNull);
    expect(FakeSite.fromScan('x', {'impersonatedBrand': 'Flipkart', 'officialUrl': 'javascript:alert(1)', 'reasons': []}), isNull);
    final f = FakeSite.fromScan('https://fleepkart.com', {'impersonatedBrand': 'Flipkart', 'officialUrl': 'https://www.flipkart.com', 'reasons': ['a']})!;
    expect(f.brand, 'Flipkart');
    expect(FakeSite.host(f.officialUrl), 'flipkart.com');
  });

  testWidgets('shows both addresses and the reasons; Go to real site opens the official URL', (t) async {
    await pump(t, const FakeSiteScreen(site: site));
    expect(find.text('This looks like Flipkart but is NOT Flipkart'), findsOneWidget);
    expect(find.text('flipkart.com'), findsOneWidget);
    expect(find.textContaining('Misspells'), findsOneWidget);
    expect(find.textContaining('1930'), findsOneWidget);
    final rich = t.widget<RichText>(find.descendant(of: find.byKey(const Key('fake-host')), matching: find.byType(RichText)));
    expect(rich.text.toPlainText(), 'fleepkart.com');
    expect(find.bySemanticsLabel(RegExp('fleepkart.com is different from the real address flipkart.com')), findsOneWidget);

    await t.tap(find.byKey(const Key('go-real')));
    await t.pumpAndSettle();
    final open = native.calls.where((c) => c.method == 'openUrl').toList();
    expect(open.single.arguments, {'url': 'https://www.flipkart.com'});
    expect(find.byType(FakeSiteScreen), findsNothing, reason: 'screen closes after opening the real site');
  });

  testWidgets('Report: wrong warning goes to the server, cyber crime opens the portal; Close closes', (t) async {
    final sent = <Map<String, dynamic>>[];
    final api = apiWith((r) async {
      if (r.url.path.endsWith('/scan/url/feedback')) sent.add(jsonDecode(r.body) as Map<String, dynamic>);
      return jsonResponse(204, {});
    });
    await pump(t, FakeSiteScreen(site: site, api: api));
    await t.tap(find.byKey(const Key('report')));
    await t.pumpAndSettle();
    await t.tap(find.byKey(const Key('report-wrong')));
    await t.pumpAndSettle();
    expect(sent.single['kind'], 'FALSE_POSITIVE');
    expect(sent.single['url'], 'https://fleepkart.com/big-billion-days');
    expect(find.text('Thank you - we will check it.'), findsOneWidget);
    ScaffoldMessenger.of(t.element(find.byType(FakeSiteScreen))).hideCurrentSnackBar();
    await t.pumpAndSettle();

    await t.ensureVisible(find.byKey(const Key('report')));
    await t.tap(find.byKey(const Key('report')));
    await t.pumpAndSettle();
    await t.tap(find.byKey(const Key('report-cyber')));
    await t.pumpAndSettle();
    expect(native.calls.where((c) => c.method == 'openUrl').single.arguments, {'url': 'https://cybercrime.gov.in/'});

    await t.tap(find.byKey(const Key('close')));
    await t.pumpAndSettle();
    expect(find.byType(FakeSiteScreen), findsNothing);
  });

  testWidgets('a scanned fake-brand link opens this screen from the dashboard', (t) async {
    t.view.physicalSize = const Size(1080, 2400);
    t.view.devicePixelRatio = 2.5;
    addTearDown(t.view.reset);
    final api = apiWith((r) async => r.url.path.endsWith('/scan/url')
        ? jsonResponse(200, {
            'verdict': 'MALICIOUS', 'score': 95, 'reasons': ['Looks like Flipkart but is not Flipkart'],
            'reasonCodes': ['LOOKALIKE_BRAND'], 'impersonatedBrand': 'Flipkart', 'officialUrl': 'https://www.flipkart.com',
          })
        : jsonResponse(200, {}));
    final engine = ProtectionEngine(api: api);
    await t.pumpWidget(MaterialApp(home: DashboardScreen(engine: engine, api: api, autoStart: false, onLogout: () {})));
    await t.pumpAndSettle();
    await t.runAsync(() => engine.scanShared('Big Billion Days! https://fleepkart.com/sale'));
    await t.pumpAndSettle();
    expect(find.byType(FakeSiteScreen), findsOneWidget);
    expect(find.text('This looks like Flipkart but is NOT Flipkart'), findsOneWidget);
    expect(native.calls.where((c) => c.method == 'showAlert').single.arguments['title'], 'Fake Flipkart site!');
  });
}
