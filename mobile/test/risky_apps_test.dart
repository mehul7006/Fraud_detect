import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fraudshield/core/api_client.dart';
import 'package:fraudshield/core/ignored_apps.dart';
import 'package:fraudshield/core/protection_engine.dart';
import 'package:fraudshield/core/settings_store.dart';
import 'package:fraudshield/screens/risky_apps_screen.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';
import 'helpers.dart';

/// Roadmap 6.6 (selfLaunchCount sent), 6.7 (uninstall + ignore), 6.8 (risky apps screen).
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late FakeNative native;
  late List<Map<String, dynamic>> sent;
  late List<Map<String, dynamic>> results;
  late List<Map<String, dynamic>> labels;

  Map<String, dynamic> app(String pkg, {int notif = 0, bool overlay = false, int self = 0}) => {
        'packageName': pkg, 'label': 'L-$pkg', 'notifPerHour': notif, 'hasOverlayPermission': overlay,
        'hasSmsPermission': false, 'hasAccessibility': false, 'hasNotificationListener': false,
        'hasFullScreenIntent': false, 'selfLaunchCount': self, 'isSystem': false,
      };
  Map<String, dynamic> res(String pkg, int score, String verdict, List<String> reasons, {bool isNew = false}) =>
      {'packageName': pkg, 'adAbuseScore': score, 'verdict': verdict, 'reasons': reasons, 'newSinceLastScan': isNew};

  ApiClient api() => apiWith((req) async {
        if (req.url.path.endsWith('/scan/apps/label')) {
          labels.add(jsonDecode(req.body) as Map<String, dynamic>);
          return http.Response('', 204);
        }
        if (req.url.path.endsWith('/scan/apps')) {
          sent.add(jsonDecode(req.body) as Map<String, dynamic>);
          return jsonResponse(200, {'results': results});
        }
        return jsonResponse(200, {'cloneRiskScore': 0, 'reasons': []});
      });

  ProtectionEngine engine() => ProtectionEngine(api: api(), settings: SettingsStore(), ignored: IgnoredApps());

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    native = FakeNative()..install();
    sent = [];
    labels = [];
    native.apps = [app('com.spam', notif: 30, overlay: true), app('com.self', self: 4), app('com.meh', notif: 9), app('com.calm')];
    results = [
      res('com.meh', 30, 'SUSPICIOUS', ['9 notifications/hour']),
      res('com.spam', 90, 'MALICIOUS', ['30 notifications/hour', 'Can draw over other apps'], isNew: true),
      res('com.self', 45, 'SUSPICIOUS', ['Opened itself 4 times in 24 h']),
    ];
  });
  tearDown(() => native.remove());

  group('engine', () {
    test('sends deviceId and selfLaunchCount; calm apps are not sent', () async {
      await engine().checkApps();
      expect(sent.single['deviceId'], 'dev1');
      final pkgs = (sent.single['apps'] as List).map((a) => a['packageName']).toList();
      expect(pkgs, containsAll(['com.spam', 'com.self', 'com.meh']));
      expect(pkgs, isNot(contains('com.calm')));
      expect((sent.single['apps'] as List).firstWhere((a) => a['packageName'] == 'com.self')['selfLaunchCount'], 4);
    });

    test('risky list is sorted by score, SAFE left out, status counts MALICIOUS', () async {
      final e = engine();
      await e.checkApps();
      expect(e.riskyApps.value.map((a) => a.packageName), ['com.spam', 'com.self', 'com.meh']);
      expect(e.riskyApps.value.first.isNew, true);
      expect(e.status.value['apps'], '1 risky');
    });

    test('one popup per bad app, not on every hourly check', () async {
      final e = engine();
      final got = <Alert>[];
      e.alerts.stream.listen(got.add);
      results = [res('com.spam', 90, 'MALICIOUS', ['x'])];
      await e.checkApps();
      await e.checkApps();
      await Future<void>.delayed(Duration.zero);
      expect(got, hasLength(1));
      expect(got.single.actions.map((a) => a.label), ['Uninstall', 'Ignore this app']);
    });

    test('ignored app disappears and stays hidden until its risk goes up', () async {
      final e = engine();
      await e.checkApps();
      await e.ignoreApp(e.riskyApps.value.first); // com.spam at 90
      expect(e.riskyApps.value.map((a) => a.packageName), isNot(contains('com.spam')));
      await e.checkApps();
      expect(e.riskyApps.value.map((a) => a.packageName), isNot(contains('com.spam')));
      results = [res('com.spam', 100, 'MALICIOUS', ['Known harmful app'])];
      await e.checkApps();
      expect(e.riskyApps.value.map((a) => a.packageName), contains('com.spam'));
    });

    test('un-ignore brings it back', () async {
      final ig = IgnoredApps();
      await ig.ignore('com.spam', 90);
      expect(IgnoredApps.hides(await ig.all(), 'com.spam', 90), true);
      await ig.unignore('com.spam');
      expect(IgnoredApps.hides(await ig.all(), 'com.spam', 90), false);
    });

    test('uninstall opens the system dialog and re-checks apps when the user comes back', () async {
      final e = engine();
      await e.checkApps();
      await e.uninstall('com.spam');
      expect(native.calls.where((c) => c.method == 'uninstallApp').single.arguments, {'package': 'com.spam'});
      native.apps = [app('com.meh', notif: 9)];
      results = [res('com.meh', 30, 'SUSPICIOUS', ['9 notifications/hour'])];
      await e.onResume();
      expect(e.riskyApps.value.map((a) => a.packageName), ['com.meh']);
      expect(e.status.value['apps'], 'OK');
      final before = sent.length;
      await e.onResume(); // nothing pending: no extra check
      expect(sent.length, before);
    });

    test('server failure shows ERROR', () async {
      final e = ProtectionEngine(api: apiWith((r) async => jsonResponse(500, {'message': 'x'})), settings: SettingsStore());
      await e.checkApps();
      expect(e.status.value['apps'], 'ERROR');
    });
  });

  group('6.1 opt-in labels', () {
    test('OFF by default: Ignore and Uninstall send nothing', () async {
      final e = engine();
      await e.checkApps();
      await e.ignoreApp(e.riskyApps.value.first);
      native.installed = {};
      await e.uninstall('com.self');
      await e.onResume();
      expect(labels, isEmpty);
    });

    test('opted in: Ignore = BENIGN with only the score signals', () async {
      await SettingsStore().setShareAppLabels(true);
      final e = engine();
      await e.checkApps();
      await e.ignoreApp(e.riskyApps.value.first); // com.spam, score 90
      expect(labels, hasLength(1));
      expect(labels.single['label'], 'BENIGN');
      expect(labels.single['scoreShown'], 90);
      final app = labels.single['app'] as Map<String, dynamic>;
      expect(app['packageName'], 'com.spam');
      expect(app['notifPerHour'], 30);
      expect(app.keys.toSet(), ApiClient.labelFields.toSet().intersection(app.keys.toSet()));
      expect(app.containsKey('label'), false, reason: 'the app name is not sent with a label');
    });

    test('opted in: a real uninstall = ABUSIVE, pressing Cancel sends nothing', () async {
      await SettingsStore().setShareAppLabels(true);
      final e = engine();
      await e.checkApps();
      native.installed = {'com.self'}; // user pressed Cancel
      await e.uninstall('com.self');
      await e.onResume();
      expect(labels, isEmpty);
      native.installed = {}; // now really removed
      await e.uninstall('com.self');
      await e.onResume();
      expect(labels.single['label'], 'ABUSIVE');
      expect(labels.single['scoreShown'], 45);
      expect((labels.single['app'] as Map)['selfLaunchCount'], 4);
    });

    test('a failing label upload never breaks Ignore', () async {
      await SettingsStore().setShareAppLabels(true);
      final e = ProtectionEngine(
          api: apiWith((r) async => r.url.path.endsWith('/label') ? http.Response('x', 500) : jsonResponse(200, {'results': results})),
          settings: SettingsStore(), ignored: IgnoredApps());
      await e.checkApps();
      await e.ignoreApp(e.riskyApps.value.first);
      expect(e.riskyApps.value.map((a) => a.packageName), isNot(contains('com.spam')));
    });
  });

  group('screen', () {
    Future<ProtectionEngine> pump(WidgetTester t) async {
      final e = engine();
      await t.runAsync(() => e.checkApps());
      await t.pumpWidget(MaterialApp(home: RiskyAppsScreen(engine: e)));
      await t.pump();
      return e;
    }

    testWidgets('lists every flagged app with its reasons, highest first', (t) async {
      await pump(t);
      expect(find.text('L-com.spam'), findsOneWidget);
      expect(find.text('Can draw over other apps'), findsOneWidget);
      expect(find.text('Opened itself 4 times in 24 h'), findsOneWidget);
      expect(find.text('NEW'), findsOneWidget);
      final spamY = t.getTopLeft(find.byKey(const Key('risky-com.spam'))).dy;
      final mehY = t.getTopLeft(find.byKey(const Key('risky-com.meh'))).dy;
      expect(spamY < mehY, true);
    });

    testWidgets('Ignore removes the row', (t) async {
      await pump(t);
      await t.runAsync(() async {
        await t.tap(find.descendant(of: find.byKey(const Key('risky-com.spam')), matching: find.text('Ignore')));
        await Future<void>.delayed(const Duration(milliseconds: 50));
      });
      await t.pump();
      expect(find.text('L-com.spam'), findsNothing);
    });

    testWidgets('Uninstall asks Android for exactly that package', (t) async {
      await pump(t);
      await t.tap(find.descendant(of: find.byKey(const Key('risky-com.self')), matching: find.text('Uninstall')));
      await t.pump();
      expect(native.calls.where((c) => c.method == 'uninstallApp').single.arguments, {'package': 'com.self'});
    });

    testWidgets('opt-in switch is OFF by default and saves the choice', (t) async {
      await pump(t);
      await t.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 20)));
      await t.pump();
      await t.scrollUntilVisible(find.byKey(const Key('share-app-labels')), 200);
      expect(t.widget<SwitchListTile>(find.byKey(const Key('share-app-labels'))).value, false);
      await t.runAsync(() async {
        await t.tap(find.byKey(const Key('share-app-labels')));
        await Future<void>.delayed(const Duration(milliseconds: 50));
      });
      await t.pump();
      expect(t.widget<SwitchListTile>(find.byKey(const Key('share-app-labels'))).value, true);
      expect(await t.runAsync(() => SettingsStore().shareAppLabels()), true);
    });

    testWidgets('empty state', (t) async {
      results = [];
      await pump(t);
      expect(find.text('No risky apps found'), findsOneWidget);
    });

    testWidgets('error state', (t) async {
      final e = ProtectionEngine(api: apiWith((r) async => jsonResponse(500, {'message': 'x'})), settings: SettingsStore());
      await t.runAsync(() => e.checkApps());
      await t.pumpWidget(MaterialApp(home: RiskyAppsScreen(engine: e)));
      expect(find.text('Could not check your apps'), findsOneWidget);
    });
  });
}
