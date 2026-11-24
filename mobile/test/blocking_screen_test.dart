import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fraudshield/core/flavour.dart';
import 'package:fraudshield/screens/blocking_screen.dart';
import 'package:fraudshield/screens/vpn_state_screen.dart';
import 'helpers.dart';

/// 18.3 switches (Play build: no ads switch), 18.4 escape hatches, 18.5 stats, 18.9 default flavour.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late FakeNative native;

  setUp(() => native = FakeNative()..install());
  tearDown(() => native.remove());

  Future<void> pump(WidgetTester t, Widget w) async {
    t.view.physicalSize = const Size(1080, 6000);
    t.view.devicePixelRatio = 2.5;
    addTearDown(t.view.reset);
    await t.pumpWidget(MaterialApp(home: w));
    await t.pumpAndSettle();
  }

  test('a build without --dart-define is the Play build (no ad blocking)', () {
    expect(kFlavour, 'play');
    expect(kAdBlockingInBuild, isFalse);
  });

  testWidgets('Play build: no ads switch at all, trackers + malware switch live', (t) async {
    await pump(t, const BlockingScreen());
    expect(find.byKey(const Key('block-cat-ads')), findsNothing);
    expect(find.byKey(const Key('block-no-ads')), findsOneWidget);
    expect(find.text('Tracker & malware blocking'), findsOneWidget);
    await t.tap(find.byKey(const Key('block-cat-trackers')));
    await t.pumpAndSettle();
    expect(native.block['categories'], ['malware']);
    expect(t.widget<SwitchListTile>(find.byKey(const Key('block-cat-trackers'))).value, isFalse);
  });

  testWidgets('website build: ads switch shown and works', (t) async {
    native.block = {...native.block, 'flavour': 'web', 'adsAvailable': true, 'categories': ['trackers', 'malware']};
    await pump(t, const BlockingScreen(adsInBuild: true));
    expect(find.text('Ad & tracker blocking'), findsOneWidget);
    await t.tap(find.byKey(const Key('block-cat-ads')));
    await t.pumpAndSettle();
    expect(native.block['categories'], contains('ads'));
  });

  testWidgets('even a web screen shows no ads switch when the native side says the build has none', (t) async {
    await pump(t, const BlockingScreen(adsInBuild: true)); // native: adsAvailable false
    expect(find.byKey(const Key('block-cat-ads')), findsNothing);
  });

  testWidgets('pause 5 min and resume', (t) async {
    await pump(t, const BlockingScreen());
    await t.tap(find.byKey(const Key('block-pause')));
    await t.pumpAndSettle();
    expect(find.byKey(const Key('block-paused')), findsOneWidget);
    expect(native.calls.where((c) => c.method == 'blockPause').last.arguments, {'minutes': 5});
    await t.tap(find.byKey(const Key('block-resume')));
    await t.pumpAndSettle();
    expect(find.byKey(const Key('block-pause')), findsOneWidget);
  });

  testWidgets('allow an app, "this app broke", allow a site, remove again', (t) async {
    native.topApps = [
      {'uid': 10100, 'app': 'Hotstar', 'package': 'in.startv.hotstar', 'lookups': 50, 'domains': 9},
      {'uid': 10200, 'app': 'Paytm', 'package': 'net.one97.paytm', 'lookups': 20, 'domains': 4},
    ];
    await pump(t, const BlockingScreen());
    await t.tap(find.byKey(const Key('block-allow-app')));
    await t.pumpAndSettle();
    await t.tap(find.byKey(const Key('allow-in.startv.hotstar')));
    await t.pumpAndSettle();
    expect(find.byKey(const Key('allowed-app-in.startv.hotstar')), findsOneWidget);

    await t.tap(find.byKey(const Key('block-allow-app')));
    await t.pumpAndSettle();
    expect(find.byKey(const Key('pick-in.startv.hotstar')), findsNothing, reason: 'already allowed');
    await t.tap(find.byKey(const Key('broke-net.one97.paytm')));
    await t.pumpAndSettle();
    expect(find.text('Paytm is no longer blocked. The note stays on this phone.'), findsOneWidget);
    expect(native.count('blockAppBroke'), 1);

    await t.enterText(find.byKey(const Key('block-site-input')), 'example-cdn.com');
    await t.tap(find.byKey(const Key('block-site-add')));
    await t.pumpAndSettle();
    expect(find.byKey(const Key('allowed-site-example-cdn.com')), findsOneWidget);
    await t.tap(find.descendant(of: find.byKey(const Key('allowed-site-example-cdn.com')), matching: find.byIcon(Icons.close)));
    await t.pumpAndSettle();
    expect(find.byKey(const Key('allowed-site-example-cdn.com')), findsNothing);
  });

  testWidgets('update now reports the result', (t) async {
    await pump(t, const BlockingScreen());
    await t.tap(find.byKey(const Key('block-update')));
    await t.pumpAndSettle();
    expect(find.text('Block list is up to date'), findsOneWidget);
    native.blockSyncOk = false;
    await t.tap(find.byKey(const Key('block-update')));
    await t.pumpAndSettle();
    expect(find.text('Could not update the block list right now'), findsOneWidget);
  });

  testWidgets('stats: totals, estimate label, per type, per app, top sites, day filter', (t) async {
    native.blockStats = {
      'total': 46, 'estimatedBytesSaved': 46 * 20000,
      'byCategory': {'ads': 0, 'trackers': 45, 'malware': 1},
      'byApp': [{'uid': 10100, 'package': 'in.startv.hotstar', 'label': 'Hotstar', 'count': 40}],
      'topDomains': [{'domain': 'app-measurement.com', 'category': 'trackers', 'count': 40}],
    };
    await pump(t, const BlockingStatsScreen());
    expect(find.text('46 requests blocked'), findsOneWidget);
    expect(find.textContaining('(estimate)'), findsOneWidget);
    expect(find.byKey(const Key('stats-cat-trackers')), findsOneWidget);
    expect(find.byKey(const Key('stats-cat-ads')), findsNothing, reason: 'zero rows hidden');
    expect(find.byKey(const Key('stats-app-in.startv.hotstar')), findsOneWidget);
    expect(find.text('app-measurement.com'), findsOneWidget);
    await t.tap(find.byKey(const Key('days-30')));
    await t.pumpAndSettle();
    expect(native.calls.where((c) => c.method == 'blockStats').last.arguments, {'days': 30});
  });

  testWidgets('VPN screen links to blocking with the right title', (t) async {
    native.vpn = {...native.vpn, 'running': true, 'prepared': true, 'state': 'ON', 'adBlocked': 12};
    await pump(t, const VpnStateScreen());
    await t.scrollUntilVisible(find.byKey(const Key('vpn-blocking')), 200);
    expect(find.text('Tracker & malware blocking'), findsOneWidget);
    expect(find.text('12 requests blocked since the VPN started'), findsOneWidget);
    await t.tap(find.byKey(const Key('vpn-blocking')));
    await t.pumpAndSettle();
    expect(find.byType(BlockingScreen), findsOneWidget);
  });
}
