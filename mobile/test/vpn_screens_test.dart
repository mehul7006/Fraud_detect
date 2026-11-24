import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fraudshield/core/protection_engine.dart';
import 'package:fraudshield/screens/dashboard_screen.dart';
import 'package:fraudshield/screens/vpn_consent_screen.dart';
import 'package:fraudshield/screens/vpn_state_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'helpers.dart';

/// Phase 12 screens: consent (12.1), options (12.7), per-app view (12.8), problem states (12.10).
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late FakeNative native;

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    native = FakeNative()..install();
  });
  tearDown(() => native.remove());

  Future<void> pump(WidgetTester t, Widget w) async {
    t.view.physicalSize = const Size(1080, 2600);
    t.view.devicePixelRatio = 2.5;
    addTearDown(t.view.reset);
    await t.pumpWidget(MaterialApp(home: w));
    await t.pump(const Duration(milliseconds: 100));
  }

  testWidgets('consent explains seen / never seen / sent, then starts the VPN', (t) async {
    await pump(t, const VpnStateScreen());
    expect(find.text('Protection is off'), findsOneWidget);
    await t.tap(find.byKey(const Key('vpn-on')));
    await t.pumpAndSettle();
    expect(find.byType(VpnConsentScreen), findsOneWidget, reason: 'disclosure BEFORE the Android prompt');
    expect(find.text('What FraudShield sees'), findsOneWidget);
    expect(find.text('What it never sees'), findsOneWidget);
    expect(find.text('What leaves your phone'), findsOneWidget);
    await t.scrollUntilVisible(find.byKey(const Key('vpn-decline')), 300, scrollable: find.descendant(of: find.byType(VpnConsentScreen), matching: find.byType(Scrollable)).first);
    await t.ensureVisible(find.byKey(const Key('vpn-decline')));
    await t.pumpAndSettle();
    await t.tap(find.byKey(const Key('vpn-decline')));
    await t.pumpAndSettle();
    expect(native.count('vpnStart'), 0, reason: 'Not now = nothing started');

    await t.tap(find.byKey(const Key('vpn-on')));
    await t.pumpAndSettle();
    await t.scrollUntilVisible(find.byKey(const Key('vpn-accept')), 300, scrollable: find.descendant(of: find.byType(VpnConsentScreen), matching: find.byType(Scrollable)).first);
    await t.ensureVisible(find.byKey(const Key('vpn-accept')));
    await t.pumpAndSettle();
    await t.tap(find.byKey(const Key('vpn-accept')));
    await t.pumpAndSettle();
    await t.pump(const Duration(seconds: 1));
    await t.pumpAndSettle();
    expect(native.count('vpnStart'), 1);
    expect(find.text('Protection is on'), findsOneWidget);
    expect(find.byKey(const Key('vpn-off')), findsOneWidget);
    await t.pumpWidget(const SizedBox()); // stop the 3 s status timer
  });

  testWidgets('already-allowed VPN starts without showing the disclosure again', (t) async {
    native.vpn = {...native.vpn, 'prepared': true};
    await pump(t, const VpnStateScreen());
    await t.tap(find.byKey(const Key('vpn-on')));
    await t.pump(const Duration(seconds: 1));
    expect(find.byType(VpnConsentScreen), findsNothing);
    expect(native.count('vpnStart'), 1);
    await t.pumpWidget(const SizedBox());
  });

  testWidgets('problem states explain the fix and open the right settings page', (t) async {
    for (final (state, title, button, method) in [
      ('OTHER_VPN', 'Another VPN is connected', 'Open VPN settings', 'openVpnSettings'),
      ('PRIVATE_DNS', 'Private DNS is on', 'Open network settings', 'openPrivateDnsSettings'),
      ('REVOKED', 'Protection was switched off', 'Open VPN settings', 'openVpnSettings'),
    ]) {
      native.calls.clear();
      native.vpn = {...native.vpn, 'state': state, 'running': state == 'PRIVATE_DNS', 'privateDns': 'dns.example.org'};
      await pump(t, const VpnStateScreen());
      expect(find.text(title), findsOneWidget, reason: state);
      if (state == 'PRIVATE_DNS') expect(find.textContaining('dns.example.org'), findsOneWidget);
      expect(find.text(button), findsOneWidget);
      await t.tap(find.byKey(const Key('vpn-fix')));
      await t.pump();
      expect(native.count(method), 1, reason: state);
      await t.pumpWidget(const SizedBox());
    }
  });

  testWidgets('privacy options: local-only, DoH, DNS provider', (t) async {
    native.vpn = {...native.vpn, 'running': true, 'state': 'ON', 'prepared': true, 'queries': 120, 'blocked': 2,
      'recentBlocks': '[{"domain":"fleepkart.com","brand":"Flipkart","app":"Chrome","at":1}]'};
    await pump(t, const VpnStateScreen());
    expect(find.text('120 site names checked'), findsOneWidget);
    expect(find.text('2 fake sites blocked'), findsOneWidget);
    expect(find.text('fleepkart.com'), findsOneWidget);
    expect(find.text('≠ Flipkart · Chrome'), findsOneWidget);
    await t.ensureVisible(find.byKey(const Key('vpn-local-only')));
    await t.tap(find.byKey(const Key('vpn-local-only')));
    await t.pump(const Duration(milliseconds: 50));
    expect(native.vpn['localOnly'], true);
    await t.tap(find.byKey(const Key('vpn-block-doh')));
    await t.pump(const Duration(milliseconds: 50));
    expect(native.vpn['blockDoh'], false);
    await t.tap(find.byKey(const Key('vpn-upstream')));
    await t.pumpAndSettle();
    await t.tap(find.text('Quad9 9.9.9.9').last);
    await t.pumpAndSettle();
    expect(native.vpn['upstream'], 'quad9');
    await t.pumpWidget(const SizedBox());
  });

  testWidgets('Xiaomi-type phones get the keep-on-after-restart guidance (12.9)', (t) async {
    native.vpn = {...native.vpn, 'running': true, 'state': 'ON', 'prepared': true, 'autostartMaker': 'xiaomi'};
    await pump(t, const VpnStateScreen());
    expect(find.text('Keep protection on after a restart'), findsOneWidget);
    await t.tap(find.byKey(const Key('vpn-autostart')));
    await t.pump();
    expect(native.count('openAutostartSettings'), 1);
    await t.tap(find.byKey(const Key('vpn-always-on')));
    await t.pump();
    expect(native.count('openVpnSettings'), 1);
    native.vpn = {...native.vpn, 'autostartMaker': null};
    await pump(t, VpnStateScreen(key: UniqueKey()));
    expect(find.text('Keep protection on after a restart'), findsNothing, reason: 'other phones: no card');
    await t.pumpWidget(const SizedBox());
  });

  testWidgets('top apps: sites and data use per app', (t) async {
    native.topApps = [
      {'uid': 10123, 'app': 'Chrome', 'package': 'com.android.chrome', 'lookups': 50, 'domains': 12,
        'topDomains': ['flipkart.com', 'google.com'], 'bytesRx': 52428800, 'bytesTx': 1048576},
    ];
    await pump(t, const TopAppsScreen());
    await t.pumpAndSettle();
    expect(find.text('Chrome'), findsOneWidget);
    expect(find.text('12 sites · ↓ 50.0 MB ↑ 1.0 MB'), findsOneWidget);
    await t.tap(find.text('Chrome'));
    await t.pumpAndSettle();
    expect(find.text('flipkart.com'), findsOneWidget);
  });

  testWidgets('dashboard tile shows the protection state', (t) async {
    t.view.physicalSize = const Size(800, 3200);
    t.view.devicePixelRatio = 1.0;
    addTearDown(t.view.reset);
    final api = apiWith((r) async => jsonResponse(200, {}));
    await t.pumpWidget(MaterialApp(home: DashboardScreen(engine: ProtectionEngine(api: api), api: api, autoStart: false, onLogout: () {})));
    await t.pumpAndSettle();
    expect(find.text('Off - tap to block fake sites in every app'), findsOneWidget);
    native.vpn = {...native.vpn, 'running': true, 'state': 'ON'};
    await t.pumpWidget(MaterialApp(home: DashboardScreen(key: UniqueKey(), engine: ProtectionEngine(api: api), api: api, autoStart: false, onLogout: () {})));
    await t.pumpAndSettle();
    expect(find.text('On - checks the sites every app opens'), findsOneWidget);
  });
}
