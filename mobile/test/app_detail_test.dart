import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fraudshield/core/protection_engine.dart';
import 'package:fraudshield/screens/app_detail_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'helpers.dart';

/// 13.8: risky-app detail - access, trackers, who it talks to, and the four actions.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late FakeNative native;

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    native = FakeNative()..install();
  });
  tearDown(() => native.remove());

  const fake = RiskyApp('com.phonepe.reward.app', 'PhonePe', 92, 'MALICIOUS',
      ['Fake PhonePe app: not the official package', 'Installed from outside the app store'],
      impersonatedBrand: 'PhonePe', reasonCodes: ['FAKE_APP', 'SIDELOADED'],
      explanation: {'en': 'This app pretends to be PhonePe but is not the official PhonePe app - remove it.', 'hi': 'यह ऐप PhonePe होने का दिखावा करता है।'},
      audit: {'grantedPermissions': ['android.permission.READ_SMS', 'android.permission.READ_CONTACTS'], 'hasAccessibility': true});

  Future<void> pump(WidgetTester t) async {
    t.view.physicalSize = const Size(1080, 3200);
    t.view.devicePixelRatio = 2.5;
    addTearDown(t.view.reset);
    final engine = ProtectionEngine(api: apiWith((r) async => jsonResponse(200, {})));
    await t.pumpWidget(MaterialApp(home: AppDetailScreen(app: fake, engine: engine)));
    await t.pumpAndSettle();
  }

  testWidgets('fake app: real name, what it can access, why', (t) async {
    native.appTrackers = [{'name': 'AppsFlyer', 'category': 'attribution'}, {'name': 'Facebook Analytics', 'category': 'analytics'}];
    native.appNetwork = {'ads': 9, 'trackers': 2, 'malware': 1, 'total': 14, 'codes': ['MALWARE_DOMAIN', 'MANY_AD_DOMAINS'],
      'domains': [{'domain': 'cdn.evil-dropper.xyz', 'count': 3, 'category': 'malware'}, {'domain': 'ads.example-ads.com', 'count': 9, 'category': 'ads'}]};
    await pump(t);
    expect(find.text('This is NOT the real PhonePe app'), findsOneWidget);
    expect(find.text('Control your screen (Accessibility)'), findsOneWidget);
    expect(find.text('Read your SMS (OTPs)'), findsOneWidget);
    expect(find.text('Your contacts'), findsOneWidget);
    expect(find.byKey(const Key('tracker-AppsFlyer')), findsOneWidget);
    expect(find.text('Talks to a known malware server!'), findsOneWidget);
    expect(find.text('11 advertising / tracking servers'), findsOneWidget);
    expect(find.text('cdn.evil-dropper.xyz'), findsOneWidget);
    expect(find.byKey(const Key('why-panel')), findsOneWidget);
  });

  testWidgets('buttons: block internet (toggle), remove permissions, uninstall, ignore', (t) async {
    await pump(t);
    expect(find.text('No data - turn on fake-website protection to see this.'), findsOneWidget);
    await t.ensureVisible(find.byKey(const Key('app-block')));
    await t.tap(find.byKey(const Key('app-block')));
    await t.pumpAndSettle();
    expect(native.firewall, {'com.phonepe.reward.app'});
    expect(find.text('Allow internet again'), findsOneWidget);
    await t.tap(find.byKey(const Key('app-block')));
    await t.pumpAndSettle();
    expect(native.firewall, isEmpty);
    await t.tap(find.byKey(const Key('app-revoke')));
    await t.pump();
    expect(native.calls.where((c) => c.method == 'openAppPermissions').single.arguments, {'package': 'com.phonepe.reward.app'});
    await t.tap(find.byKey(const Key('app-uninstall')));
    await t.pump();
    expect(native.count('uninstallApp'), 1);
    await t.tap(find.byKey(const Key('app-ignore')));
    await t.pumpAndSettle();
    expect(native.calls.where((c) => c.method == 'appIgnore').single.arguments, {'package': 'com.phonepe.reward.app', 'ignore': true});
  });
}
