import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fraudshield/screens/sms_audit_screen.dart';
import 'package:fraudshield/screens/sms_forwarding_screen.dart';
import 'helpers.dart';

Map<String, dynamic> app(String pkg, String label,
        {bool sms = false, bool listener = false, bool overlay = false, bool system = false}) =>
    {
      'packageName': pkg, 'label': label, 'notifPerHour': 0, 'hasOverlayPermission': overlay, 'hasSmsPermission': sms,
      'hasAccessibility': false, 'hasNotificationListener': listener, 'hasFullScreenIntent': false, 'isSystem': system,
    };

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('3.7 SMS-stealer audit', () {
    testWidgets('a user-installed app with READ/RECEIVE_SMS appears, flagged, with Uninstall', (t) async {
      await t.pumpWidget(MaterialApp(
        home: SmsAuditScreen(loader: () async => [app('com.evil.smsforward', 'Free SMS Forwarder', sms: true)]),
      ));
      await t.pumpAndSettle();
      expect(find.text('Installed by you (1)'), findsOneWidget);
      expect(find.text('Free SMS Forwarder'), findsOneWidget);
      expect(find.text('com.evil.smsforward'), findsOneWidget);
      expect(find.text('Reads SMS'), findsOneWidget);
      expect(find.text('Uninstall'), findsOneWidget);
    });

    testWidgets('notification-listener apps are listed too', (t) async {
      await t.pumpWidget(MaterialApp(home: SmsAuditScreen(loader: () async => [app('com.spy', 'Notif Spy', listener: true)])));
      await t.pumpAndSettle();
      expect(find.text('Reads notifications'), findsOneWidget);
    });

    testWidgets('apps without SMS/notification access are NOT listed', (t) async {
      await t.pumpWidget(MaterialApp(
        home: SmsAuditScreen(loader: () async => [app('com.game', 'Game', overlay: true), app('com.calc', 'Calculator')]),
      ));
      await t.pumpAndSettle();
      expect(find.text('Game'), findsNothing);
      expect(find.text('Calculator'), findsNothing);
      expect(find.text('No user-installed app can read your SMS'), findsOneWidget);
    });

    testWidgets('system apps are separate and cannot be uninstalled from here', (t) async {
      await t.pumpWidget(MaterialApp(
        home: SmsAuditScreen(loader: () async => [app('com.google.messages', 'Messages', sms: true, system: true)]),
      ));
      await t.pumpAndSettle();
      expect(find.text('System apps - usually safe (1)'), findsOneWidget);
      expect(find.text('Messages'), findsOneWidget);
      expect(find.text('Uninstall'), findsNothing);
    });

    testWidgets('Uninstall asks Android to uninstall exactly that package', (t) async {
      String? removed;
      await t.pumpWidget(MaterialApp(
        home: SmsAuditScreen(loader: () async => [app('com.evil.smsforward', 'Free SMS Forwarder', sms: true)], onUninstall: (p) async => removed = p),
      ));
      await t.pumpAndSettle();
      await t.tap(find.text('Uninstall'));
      await t.pump();
      expect(removed, 'com.evil.smsforward');
    });

    testWidgets('lists are sorted by name, case-insensitively', (t) async {
      await t.pumpWidget(MaterialApp(
        home: SmsAuditScreen(loader: () async => [app('p.z', 'zeta', sms: true), app('p.a', 'Alpha', sms: true), app('p.b', 'beta', sms: true)]),
      ));
      await t.pumpAndSettle();
      final names = ['Alpha', 'beta', 'zeta'];
      final ys = names.map((n) => t.getTopLeft(find.text(n)).dy).toList();
      expect(ys, orderedEquals([...ys]..sort()));
    });

    testWidgets('a failure reading apps shows a message, not a crash', (t) async {
      await t.pumpWidget(MaterialApp(home: SmsAuditScreen(loader: () async => throw Exception('denied'))));
      await t.pumpAndSettle();
      expect(find.text('Could not read installed apps'), findsOneWidget);
    });

    testWidgets('default loader reads from the native side', (t) async {
      final native = FakeNative()..apps = [app('com.evil', 'Evil', sms: true)];
      native.install();
      addTearDown(native.remove);
      await t.pumpWidget(const MaterialApp(home: SmsAuditScreen()));
      await t.pumpAndSettle();
      expect(find.text('Evil'), findsOneWidget);
      expect(native.count('auditApps'), 1);
    });
  });

  group('3.8 SMS forwarding guidance', () {
    testWidgets('shows the three checks and the honest limitation', (t) async {
      await t.pumpWidget(const MaterialApp(home: SmsForwardingScreen()));
      expect(find.textContaining('cannot see whether your carrier forwards'), findsOneWidget);
      expect(find.text('Remove apps that can read your SMS'), findsOneWidget);
      expect(find.text('Ask your carrier'), findsOneWidget);
      expect(find.text('Watch for a SIM swap'), findsOneWidget);
      expect(find.textContaining('customer care'), findsOneWidget);
    });

    testWidgets('the first step opens the SMS audit screen', (t) async {
      final native = FakeNative();
      native.install();
      addTearDown(native.remove);
      await t.pumpWidget(const MaterialApp(home: SmsForwardingScreen()));
      await t.tap(find.text('Show apps that read my SMS'));
      await t.pumpAndSettle();
      expect(find.text('Apps that can read your SMS'), findsOneWidget);
    });
  });
}
