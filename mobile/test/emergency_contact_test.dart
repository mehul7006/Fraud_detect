import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fraudshield/core/protection_engine.dart';
import 'package:fraudshield/core/settings_store.dart';
import 'package:fraudshield/screens/dashboard_screen.dart';
import 'package:fraudshield/screens/settings_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'helpers.dart';

/// 7.4: optional emergency contact; critical alerts offer "Tell <name>" which opens the SMS app pre-filled.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late FakeNative native;

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    native = FakeNative()..install();
  });
  tearDown(() => native.remove());

  ProtectionEngine engine() => ProtectionEngine(api: apiWith((r) async => jsonResponse(200, {})));

  Future<void> tall(WidgetTester t) async {
    t.view.physicalSize = const Size(800, 3200);
    t.view.devicePixelRatio = 1.0;
    addTearDown(t.view.reset);
  }

  test('mobile numbers are normalised; anything else is refused', () {
    expect(SettingsStore.normalisePhone('+91 98765 43210'), '9876543210');
    expect(SettingsStore.normalisePhone('09876543210'), '9876543210');
    expect(SettingsStore.normalisePhone('98765-43210'), '9876543210');
    expect(SettingsStore.normalisePhone('12345'), isNull);
    expect(SettingsStore.normalisePhone('5876543210'), isNull, reason: 'Indian mobiles start with 6-9');
    expect(SettingsStore.normalisePhone('9876543210123'), isNull);
  });

  testWidgets('Settings: save, show and remove the contact; invalid input is refused', (t) async {
    await tall(t);
    await t.pumpWidget(MaterialApp(home: SettingsScreen(engine: engine())));
    await t.pumpAndSettle();
    await t.enterText(find.byKey(const Key('contact-name')), 'Papa');
    await t.enterText(find.byKey(const Key('contact-phone')), '123');
    await t.tap(find.byKey(const Key('contact-save')));
    await t.pumpAndSettle();
    expect(find.text('Enter a name and a 10-digit mobile number'), findsOneWidget);
    expect(await SettingsStore().emergencyContact(), isNull);

    await t.enterText(find.byKey(const Key('contact-phone')), '+91 98765 43210');
    await t.tap(find.byKey(const Key('contact-save')));
    await t.pumpAndSettle();
    expect(find.text('Emergency contact: Papa'), findsOneWidget);
    expect((await SettingsStore().emergencyContact())!.phone, '9876543210');

    await t.tap(find.byKey(const Key('contact-remove')));
    await t.pumpAndSettle();
    expect(await SettingsStore().emergencyContact(), isNull);
    expect(find.byKey(const Key('contact-name')), findsOneWidget);
  });

  Future<ProtectionEngine> dashboard(WidgetTester t) async {
    await tall(t);
    final e = engine();
    await t.pumpWidget(MaterialApp(home: DashboardScreen(engine: e, api: apiWith((r) async => jsonResponse(200, {})), autoStart: false, onLogout: () {})));
    await t.pumpAndSettle();
    return e;
  }

  testWidgets('critical alert + saved contact: "Tell Papa" opens the SMS app with number and alert', (t) async {
    await SettingsStore().setEmergencyContact('Papa', '9876543210');
    final e = await dashboard(t);
    e.alerts.add(Alert('Call forwarding is ON', 'Forwarding all calls is active.', 'CRITICAL', []));
    await t.pumpAndSettle();
    await t.tap(find.byKey(const Key('tell-contact')));
    await t.pumpAndSettle();
    final c = native.calls.lastWhere((x) => x.method == 'composeSms');
    expect((c.arguments as Map)['number'], '9876543210');
    expect((c.arguments as Map)['body'], contains('Call forwarding is ON'));
    expect((c.arguments as Map)['body'], contains('call me first'));
  });

  testWidgets('if the contact cannot be read, the critical alert still shows (just without the button)', (t) async {
    await tall(t);
    final e = ProtectionEngine(api: apiWith((r) async => jsonResponse(200, {})), settings: _BrokenStore());
    await t.pumpWidget(MaterialApp(home: DashboardScreen(engine: e, api: apiWith((r) async => jsonResponse(200, {})), autoStart: false, onLogout: () {})));
    await t.pumpAndSettle();
    e.alerts.add(Alert('Account takeover in progress', 'x', 'CRITICAL', []));
    await t.pumpAndSettle();
    expect(find.text('Account takeover in progress'), findsOneWidget);
    expect(find.byKey(const Key('tell-contact')), findsNothing);
  });

  testWidgets('no button for a non-critical alert, or when no contact is saved', (t) async {
    await SettingsStore().setEmergencyContact('Papa', '9876543210');
    final e = await dashboard(t);
    e.alerts.add(Alert('Suspicious link', 'http://x', 'MEDIUM', []));
    await t.pumpAndSettle();
    expect(find.byKey(const Key('tell-contact')), findsNothing);
    await t.tap(find.text('Ignore'));
    await t.pumpAndSettle();
    await SettingsStore().clearEmergencyContact();
    e.alerts.add(Alert('Call forwarding is ON', 'x', 'CRITICAL', []));
    await t.pumpAndSettle();
    expect(find.byKey(const Key('tell-contact')), findsNothing);
  });
}

class _BrokenStore extends SettingsStore {
  @override
  Future<({String name, String phone})?> emergencyContact() async => throw StateError('storage broken');
}
