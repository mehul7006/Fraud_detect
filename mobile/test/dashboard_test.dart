import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fraudshield/core/protection_engine.dart';
import 'package:fraudshield/screens/dashboard_screen.dart';
import 'helpers.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late FakeNative native;
  late ProtectionEngine engine;
  var logouts = 0;

  Future<void> pumpDashboard(WidgetTester t) async {
    // a ListView only builds what fits on screen, so use a tall phone to make every tile exist
    t.view.physicalSize = const Size(800, 3200);
    t.view.devicePixelRatio = 1.0;
    addTearDown(t.view.reset);
    await t.pumpWidget(MaterialApp(
      home: DashboardScreen(engine: engine, api: apiWith((r) async => jsonResponse(200, {})), autoStart: false, onLogout: () => logouts++),
    ));
    await t.pumpAndSettle();
  }

  setUp(() {
    native = FakeNative()..install();
    engine = ProtectionEngine(api: apiWith((r) async => jsonResponse(200, {})));
    logouts = 0;
  });
  tearDown(() => native.remove());

  group('2.6 permission onboarding', () {
    testWidgets('every missing permission shows a plain-language card', (t) async {
      native.notifPermission = false;
      await pumpDashboard(t);
      expect(find.text('Allow notifications'), findsOneWidget);
      expect(find.text('Enable link & ad monitoring'), findsOneWidget);
      expect(find.text('Enable file protection'), findsOneWidget);
      expect(find.text('Phone permission'), findsOneWidget);
      expect(find.textContaining('Message text is never uploaded'), findsOneWidget, reason: 'consent text must promise no message upload');
    });

    testWidgets('each card button opens the right system screen / permission request', (t) async {
      native.notifPermission = false;
      await pumpDashboard(t);
      final cards = {
        'Allow notifications': 'requestNotificationPermission',
        'Enable link & ad monitoring': 'openNotificationAccessSettings',
        'Enable file protection': 'openAllFilesAccessSettings',
        'Phone permission': 'requestPhonePermission',
      };
      for (final e in cards.entries) {
        final card = find.ancestor(of: find.text(e.key), matching: find.byType(Card));
        await t.ensureVisible(card);
        await t.tap(find.descendant(of: card, matching: find.byType(TextButton)));
        await t.pump();
        expect(native.count(e.value), 1, reason: '${e.key} should call ${e.value}');
      }
    });

    testWidgets('cards disappear once permissions are granted and the app is resumed', (t) async {
      native.notifPermission = false;
      await pumpDashboard(t);
      expect(find.text('Phone permission'), findsOneWidget);

      native..notifAccess = true..filesAccess = true..phone = true..notifPermission = true;
      t.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
      t.binding.handleAppLifecycleStateChanged(AppLifecycleState.hidden);
      t.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
      t.binding.handleAppLifecycleStateChanged(AppLifecycleState.hidden);
      t.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
      t.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await t.pumpAndSettle();

      expect(find.text('Allow notifications'), findsNothing);
      expect(find.text('Enable link & ad monitoring'), findsNothing);
      expect(find.text('Enable file protection'), findsNothing);
      expect(find.text('Phone permission'), findsNothing);
    });

    testWidgets('notification-permission card is hidden when already allowed (pre-Android 13)', (t) async {
      native.notifPermission = true;
      await pumpDashboard(t);
      expect(find.text('Allow notifications'), findsNothing);
    });
  });

  group('2.5 status tiles', () {
    testWidgets('link/file protection tiles follow the granted permissions', (t) async {
      native..notifAccess = true..filesAccess = false;
      await pumpDashboard(t);
      ListTile tile(String title) => t.widget<ListTile>(find.ancestor(of: find.text(title), matching: find.byType(ListTile)));
      expect((tile('Link protection').subtitle as Text).data, 'ON');
      expect((tile('File protection').subtitle as Text).data, 'OFF');
    });

    testWidgets('engine status is shown on the tiles and updates live', (t) async {
      await pumpDashboard(t);
      expect(find.text('checking...'), findsWidgets);
      engine.status.value = {'forwarding': 'ACTIVE', 'clone': 'RISK 85', 'apps': '2 risky'};
      await t.pump();
      expect(find.text('ACTIVE'), findsOneWidget);
      expect(find.text('RISK 85'), findsOneWidget);
      expect(find.text('2 risky'), findsOneWidget);
    });

    testWidgets('tapping the forwarding tile re-runs the carrier check', (t) async {
      await pumpDashboard(t);
      await t.tap(find.text('Call forwarding'));
      await t.pumpAndSettle();
      expect(native.ussdCodes(), isNotEmpty);
    });

    testWidgets('3.6 UNAVAILABLE forwarding offers "Check manually" which opens the dialer with *#21#', (t) async {
      await pumpDashboard(t);
      engine.status.value = {'forwarding': 'UNAVAILABLE'};
      await t.pump();
      expect(find.textContaining("can't query your carrier"), findsOneWidget);
      await t.tap(find.text('Check manually'));
      await t.pump();
      final dial = native.calls.singleWhere((c) => c.method == 'dialCode');
      expect((dial.arguments as Map)['code'], '*#21#');
    });

    testWidgets('UNKNOWN shows what the carrier actually said', (t) async {
      await pumpDashboard(t);
      engine.unrecognisedAnswer.value = 'Voice: weird wording 123';
      engine.status.value = {'forwarding': 'UNKNOWN'};
      await t.pump();
      expect(find.textContaining('Carrier said: "Voice: weird wording 123"'), findsOneWidget);
    });

    testWidgets('a very long carrier answer is shortened on the tile', (t) async {
      await pumpDashboard(t);
      engine.unrecognisedAnswer.value = 'x' * 400;
      engine.status.value = {'forwarding': 'UNKNOWN'};
      await t.pump();
      expect(find.textContaining('${'x' * 140}..."'), findsOneWidget, reason: 'shortened to 140 characters plus an ellipsis');
      expect(find.textContaining('x' * 141), findsNothing);
    });

    testWidgets('UNKNOWN also offers the manual check; ACTIVE / OFF do not', (t) async {
      await pumpDashboard(t);
      engine.status.value = {'forwarding': 'UNKNOWN'};
      await t.pump();
      expect(find.text('Check manually'), findsOneWidget);
      engine.status.value = {'forwarding': 'OFF'};
      await t.pump();
      expect(find.text('Check manually'), findsNothing);
      engine.status.value = {'forwarding': 'ACTIVE'};
      await t.pump();
      expect(find.text('Check manually'), findsNothing);
    });

    testWidgets('logout button clears the session and calls onLogout', (t) async {
      await pumpDashboard(t);
      await t.tap(find.byIcon(Icons.logout));
      await t.pumpAndSettle();
      expect(logouts, 1);
    });
  });

  group('2.8 blocking alert popups', () {
    testWidgets('two alerts at once show one after another, in order', (t) async {
      await pumpDashboard(t);
      engine.alerts.add(Alert('First', 'body 1', 'CRITICAL', []));
      engine.alerts.add(Alert('Second', 'body 2', 'MEDIUM', []));
      await t.pumpAndSettle();
      expect(find.text('First'), findsOneWidget);
      expect(find.text('Second'), findsNothing, reason: 'second must wait for the first to be dismissed');

      await t.tap(find.text('I understand'));
      await t.pumpAndSettle();
      expect(find.text('First'), findsNothing);
      expect(find.text('Second'), findsOneWidget);

      await t.tap(find.text('Ignore'));
      await t.pumpAndSettle();
      expect(find.byType(AlertDialog), findsNothing);
    });

    testWidgets('CRITICAL cannot be dismissed by tapping outside; only by its button', (t) async {
      await pumpDashboard(t);
      engine.alerts.add(Alert('Danger', 'do not open', 'CRITICAL', []));
      await t.pumpAndSettle();
      await t.tapAt(const Offset(4, 4)); // barrier
      await t.pumpAndSettle();
      expect(find.text('Danger'), findsOneWidget);
      await t.tap(find.text('I understand'));
      await t.pumpAndSettle();
      expect(find.text('Danger'), findsNothing);
    });

    testWidgets('non-critical alert can be dismissed by tapping outside', (t) async {
      await pumpDashboard(t);
      engine.alerts.add(Alert('Heads up', 'fyi', 'MEDIUM', []));
      await t.pumpAndSettle();
      await t.tapAt(const Offset(4, 4));
      await t.pumpAndSettle();
      expect(find.text('Heads up'), findsNothing);
    });

    testWidgets('an action button closes the popup and then runs its action', (t) async {
      await pumpDashboard(t);
      var ran = 0;
      engine.alerts.add(Alert('Call forwarding is ON', 'x', 'CRITICAL', [AlertAction('Turn OFF forwarding', () async => ran++)]));
      await t.pumpAndSettle();
      await t.tap(find.text('Turn OFF forwarding'));
      await t.pumpAndSettle();
      expect(ran, 1);
      expect(find.byType(AlertDialog), findsNothing);
    });

    testWidgets('WhatsApp alert offers "Secure account" which opens the wizard', (t) async {
      await pumpDashboard(t);
      engine.alerts.add(Alert('WhatsApp may be accessed from another device', 'log out', 'CRITICAL', []));
      await t.pumpAndSettle();
      await t.tap(find.text('Secure account'));
      await t.pumpAndSettle();
      expect(find.text('Secure WhatsApp'), findsOneWidget);
    });

    testWidgets('three queued alerts are all shown eventually', (t) async {
      await pumpDashboard(t);
      for (final n in ['A', 'B', 'C']) {
        engine.alerts.add(Alert('Alert $n', 'b', 'MEDIUM', []));
      }
      final seen = <String>[];
      for (var i = 0; i < 3; i++) {
        await t.pumpAndSettle();
        final title = (t.widget<AlertDialog>(find.byType(AlertDialog)).title as Text).data!;
        seen.add(title);
        await t.tap(find.text('Ignore'));
      }
      await t.pumpAndSettle();
      expect(seen, ['Alert A', 'Alert B', 'Alert C']);
    });
  });

  group('navigation', () {
    testWidgets('SMS forwarding tile opens the guidance screen (3.8)', (t) async {
      await pumpDashboard(t);
      await t.scrollUntilVisible(find.text('SMS forwarding'), 200, scrollable: find.byType(Scrollable).first);
      await t.tap(find.text('SMS forwarding'));
      await t.pumpAndSettle();
      expect(find.text('Check SMS forwarding'), findsOneWidget);
    });

    testWidgets('"Apps that can read my SMS" tile opens the audit (3.7)', (t) async {
      await pumpDashboard(t);
      await t.scrollUntilVisible(find.text('Apps that can read my SMS'), 200, scrollable: find.byType(Scrollable).first);
      await t.tap(find.text('Apps that can read my SMS'));
      await t.pumpAndSettle();
      expect(find.text('Apps that can read your SMS'), findsOneWidget);
    });

    testWidgets('history icon opens threat history', (t) async {
      await pumpDashboard(t);
      await t.tap(find.byIcon(Icons.history));
      await t.pumpAndSettle();
      expect(find.text('Threat history'), findsOneWidget);
    });
  });
}
