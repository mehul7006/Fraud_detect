import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fraudshield/core/protection_engine.dart';
import 'package:fraudshield/l10n/app_localizations.dart';
import 'package:fraudshield/screens/dashboard_screen.dart';
import 'package:fraudshield/screens/threats_screen.dart';
import 'package:fraudshield/screens/why_panel.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'helpers.dart';

/// 11.10: "Why is this risky?" in alerts and threat history, English and Hindi.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late FakeNative native;
  const en = 'This link pretends to be Flipkart, but it is not the real Flipkart website.';
  const hi = 'यह लिंक Flipkart होने का दिखावा करता है, लेकिन यह Flipkart की असली वेबसाइट नहीं है।';

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    native = FakeNative()..install();
  });
  tearDown(() => native.remove());

  Widget app(Widget home, {Locale locale = const Locale('en')}) => MaterialApp(
        locale: locale,
        supportedLocales: AppLocalizations.supportedLocales,
        localizationsDelegates: const [AppLocalizations.delegate, GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate, GlobalCupertinoLocalizations.delegate],
        home: home,
      );

  testWidgets('switches between English and Hindi; starts in the app language', (t) async {
    await t.pumpWidget(app(const Scaffold(body: WhyPanel(explanation: {'en': en, 'hi': hi}))));
    expect(find.text('Why is this risky?'), findsOneWidget);
    expect(find.text(en), findsOneWidget);
    await t.tap(find.text('हिंदी'));
    await t.pumpAndSettle();
    expect(find.text(hi), findsOneWidget);
    await t.tap(find.text('English'));
    await t.pumpAndSettle();
    expect(find.text(en), findsOneWidget);

    await t.pumpWidget(app(Scaffold(body: WhyPanel(key: UniqueKey(), explanation: const {'en': en, 'hi': hi})), locale: const Locale('hi')));
    await t.pumpAndSettle();
    expect(find.text(hi), findsOneWidget, reason: 'Hindi app -> Hindi first');
    expect(find.text('यह खतरनाक क्यों है?'), findsOneWidget);
    expect(WhyPanel.from(null), isNull);
    expect(WhyPanel.from({'en': null, 'hi': null}), isNull);
  });

  testWidgets('alert dialog shows the explanation of a flagged link', (t) async {
    t.view.physicalSize = const Size(1080, 2400);
    t.view.devicePixelRatio = 2.5;
    addTearDown(t.view.reset);
    final api = apiWith((r) async => jsonResponse(200, {}));
    final engine = ProtectionEngine(api: api);
    await t.pumpWidget(app(DashboardScreen(engine: engine, api: api, autoStart: false, onLogout: () {})));
    await t.pumpAndSettle();
    engine.alerts.add(Alert('Suspicious link', 'http://x.top', 'MEDIUM', [],
        explanation: {'en': 'Be careful with this link.', 'hi': 'इस लिंक से सावधान रहें।'}));
    await t.pumpAndSettle();
    expect(find.byKey(const Key('why-panel')), findsOneWidget);
    expect(find.text('Be careful with this link.'), findsOneWidget);
    await t.tap(find.text('हिंदी'));
    await t.pumpAndSettle();
    expect(find.text('इस लिंक से सावधान रहें।'), findsOneWidget);
  });

  testWidgets('threat history shows it for link threats only', (t) async {
    final api = apiWith((r) async => jsonResponse(200, [
          {'id': 1, 'type': 'MALICIOUS_LINK', 'severity': 'CRITICAL', 'title': 'Dangerous link blocked', 'detail': 'fleepkart.com',
            'status': 'OPEN', 'createdAt': '2026-11-23', 'explanationEn': en, 'explanationHi': hi},
          {'id': 2, 'type': 'CALL_FORWARDING', 'severity': 'HIGH', 'title': 'Call forwarding', 'detail': '',
            'status': 'OPEN', 'createdAt': '2026-11-23', 'explanationEn': null, 'explanationHi': null},
        ]));
    await t.pumpWidget(app(ThreatsScreen(api: api)));
    await t.pumpAndSettle();
    expect(find.byKey(const Key('why-panel')), findsOneWidget);
    expect(find.text(en), findsOneWidget);
    await t.tap(find.text('हिंदी'));
    await t.pumpAndSettle();
    expect(find.text(hi), findsOneWidget);
  });
}
