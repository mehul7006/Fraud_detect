import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fraudshield/core/protection_engine.dart';
import 'package:fraudshield/l10n/l10n.dart';
import 'package:fraudshield/screens/dashboard_screen.dart';
import 'package:fraudshield/screens/login_screen.dart';
import 'package:fraudshield/screens/risky_apps_screen.dart';
import 'package:fraudshield/screens/settings_screen.dart';
import 'package:fraudshield/screens/sms_audit_screen.dart';
import 'package:fraudshield/screens/sms_forwarding_screen.dart';
import 'package:fraudshield/screens/takeover_wizard_screen.dart';
import 'package:fraudshield/screens/threats_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'helpers.dart';

/// 8.12: every screen passes Flutter's accessibility guidelines (TalkBack labels, 48 dp touch targets, text contrast)
/// and still works with very large text (200 %), in English and Hindi.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late FakeNative native;

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    native = FakeNative()
      ..notifAccess = true
      ..filesAccess = true
      ..phone = true
      ..apps = [
        {'packageName': 'com.spam.app', 'label': 'Spam App', 'hasSmsPermission': true, 'hasNotificationListener': false, 'isSystem': false},
      ]
      ..install();
  });
  tearDown(() => native.remove());

  ProtectionEngine engine() => ProtectionEngine(api: apiWith((r) async => jsonResponse(200, [])));

  final screens = <String, Widget Function()>{
    'login': () => LoginScreen(onDone: () {}, api: apiWith((r) async => jsonResponse(200, {}))),
    'dashboard': () => DashboardScreen(engine: engine(), api: apiWith((r) async => jsonResponse(200, {})), autoStart: false, onLogout: () {}),
    'dashboard (worst case: forwarding unknown, offline, risky apps)': () {
      final e = engine()..status.value = {'forwarding': 'UNKNOWN', 'clone': 'ERROR', 'apps': '2 risky'};
      e.unrecognisedAnswer.value = 'कॉल आगे भेजना\nVoice: अग्रेषित नहीं किया गया\nडेटा: अग्रेषित नहीं किया गया';
      return DashboardScreen(engine: e, api: apiWith((r) async => jsonResponse(200, {})), autoStart: false, onLogout: () {});
    },
    'threats': () => ThreatsScreen(api: apiWith((r) async => jsonResponse(200, [
          {'id': 1, 'type': 'MALICIOUS_LINK', 'severity': 'HIGH', 'title': 'Dangerous link', 'detail': 'http://x', 'status': 'OPEN', 'createdAt': '2026-11-22'}
        ]))),
    'settings': () => SettingsScreen(engine: engine()),
    'risky apps': () => RiskyAppsScreen(engine: engine()),
    'sms audit': () => const SmsAuditScreen(),
    'sms forwarding': () => const SmsForwardingScreen(),
    'whatsapp wizard': () => const TakeoverWizardScreen(app: 'whatsapp'),
  };

  Future<void> pump(WidgetTester t, Widget w, {Locale locale = const Locale('en'), double textScale = 1}) async {
    t.view.physicalSize = const Size(1080, 2340); // a common phone (Pixel 5 / Redmi Note 12), 2.75 dpr
    t.view.devicePixelRatio = 2.75;
    addTearDown(t.view.reset);
    await t.pumpWidget(MaterialApp(
      locale: locale,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      theme: ThemeData(colorSchemeSeed: const Color(0xFF0B6E4F), useMaterial3: true),
      builder: (c, child) => MediaQuery(data: MediaQuery.of(c).copyWith(textScaler: TextScaler.linear(textScale)), child: child!),
      home: w,
    ));
    await t.pumpAndSettle();
  }

  for (final e in screens.entries) {
    testWidgets('${e.key}: TalkBack labels, 48 dp touch targets, readable contrast', (t) async {
      final h = t.ensureSemantics();
      await pump(t, e.value());
      await expectLater(t, meetsGuideline(labeledTapTargetGuideline));
      await expectLater(t, meetsGuideline(androidTapTargetGuideline));
      await expectLater(t, meetsGuideline(textContrastGuideline));
      h.dispose();
    });

    for (final locale in const [Locale('en'), Locale('hi')]) {
      testWidgets('${e.key}: 200 % text in ${locale.languageCode} - nothing overflows', (t) async {
        await pump(t, e.value(), locale: locale, textScale: 2.0);
        expect(t.takeException(), isNull, reason: 'layout overflow / error at 200 % text');
      });
    }
  }
}
