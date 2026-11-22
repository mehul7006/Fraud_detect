import 'dart:convert';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fraudshield/core/api_client.dart';
import 'package:fraudshield/core/protection_engine.dart';
import 'package:fraudshield/core/settings_store.dart';
import 'package:fraudshield/l10n/l10n.dart';
import 'package:fraudshield/main.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'helpers.dart';

/// 8.11 English + Hindi.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late FakeNative native;

  setUp(() {
    SharedPreferences.setMockInitialValues({'consent_version': 1}); // privacy notice already accepted (9.4)
    appLocale.value = null;
    native = FakeNative()..install();
  });
  tearDown(() {
    native.remove();
    appLocale.value = null;
  });

  test('every English text has a Hindi translation (no key missing, none left empty)', () {
    Map<String, dynamic> load(String f) => jsonDecode(File('lib/l10n/$f').readAsStringSync()) as Map<String, dynamic>;
    final en = load('app_en.arb').keys.where((k) => !k.startsWith('@')).toSet();
    final hi = load('app_hi.arb');
    expect(en.difference(hi.keys.toSet()), isEmpty, reason: 'missing in app_hi.arb');
    for (final k in en) {
      expect((hi[k] as String).trim(), isNotEmpty, reason: k);
    }
  });

  testWidgets('choosing हिन्दी in Settings switches the whole app at once, and it is remembered', (t) async {
    t.view.physicalSize = const Size(800, 3200);
    t.view.devicePixelRatio = 1.0;
    addTearDown(t.view.reset);
    final store = MemoryTokenStore();
    await store.write('saved.jwt.token');
    final api = apiWith((r) async => jsonResponse(200, {}), store: store);
    await t.pumpWidget(FraudShieldApp(api: api, engine: ProtectionEngine(api: api), autoStart: false));
    await t.pumpAndSettle();
    expect(find.text('Call forwarding'), findsOneWidget);

    await t.tap(find.byTooltip('Link settings'));
    await t.pumpAndSettle();
    await t.tap(find.byKey(const Key('language-hi')));
    await t.pumpAndSettle();
    expect(find.text('लिंक सेटिंग'), findsOneWidget, reason: 'settings title already in Hindi');
    expect(await SettingsStore().language(), 'hi');

    expect(find.byType(BackButton), findsOneWidget, reason: 'still on the Settings screen: switching language keeps the screen stack');
    await t.tap(find.byType(BackButton));
    await t.pumpAndSettle();
    expect(find.text('कॉल फ़ॉरवर्डिंग'), findsOneWidget);
    expect(find.text('Call forwarding'), findsNothing);
    expect(find.text('खाते की सुरक्षा'), findsOneWidget);

    // back to English
    await t.tap(find.byTooltip('लिंक सेटिंग'));
    await t.pumpAndSettle();
    await t.tap(find.byKey(const Key('language-en')));
    await t.pumpAndSettle();
    await t.tap(find.byType(BackButton));
    await t.pumpAndSettle();
    expect(find.text('Call forwarding'), findsOneWidget);
  });

  test('saved choice maps to a locale; "phone default" follows the phone', () {
    expect(localeFromSetting('hi'), const Locale('hi'));
    expect(localeFromSetting('en'), const Locale('en'));
    expect(localeFromSetting('system'), isNull);
    expect(localeFromSetting(null), isNull);
  });

  test('protection alerts follow the chosen language (forwarding ON in Hindi)', () async {
    appLocale.value = const Locale('hi');
    final e = ProtectionEngine(api: apiWith((r) async => jsonResponse(201, {'id': 1})));
    final alerts = <Alert>[];
    e.alerts.stream.listen(alerts.add);
    native.ussd = (code) => code == '*#21#' ? 'Forwarded to +919876543210' : 'Call forwarding is not active';
    await e.checkCallForwarding();
    await Future<void>.delayed(Duration.zero);
    expect(alerts.single.title, 'कॉल फ़ॉरवर्डिंग चालू है');
    expect(alerts.single.body, contains('सभी कॉल'));
    expect(alerts.single.actions.single.label, 'फ़ॉरवर्डिंग बंद करें');
  });

  test('error messages come in Hindi too', () {
    final hi = lookupAppLocalizations(const Locale('hi'));
    expect(hi.errOffline, contains('इंटरनेट'));
    expect(hi.statusRisk('55'), 'जोखिम 55');
  });
}
