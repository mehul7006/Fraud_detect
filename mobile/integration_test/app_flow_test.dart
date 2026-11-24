// End-to-end journey on a real phone / emulator, driven from inside the app (works on MIUI, which blocks taps
// injected from a PC). Uses the real native code and the real dev server (API_BASE dart-define).
//
//   flutter test integration_test/app_flow_test.dart -d <device> --dart-define=API_BASE=http://127.0.0.1:18080/api/v1
//
// It logs out, creates a fresh test account, and walks every main screen. Every step is logged as
// "E2E PASS <step>" / "E2E FAIL <step>: <reason>"; the test keeps going after a failed step and fails at the end.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fraudshield/core/api_client.dart';
import 'package:fraudshield/core/native_bridge.dart';
import 'package:fraudshield/main.dart';
import 'package:integration_test/integration_test.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('FraudShield: login to the last screen', (t) async {
    final failures = <String>[];
    final email = 'e2e-${DateTime.now().millisecondsSinceEpoch}@test.local';
    const password = 'password123';

    // pump until a finder shows (the app has timers and spinners, so pumpAndSettle would never settle)
    Future<bool> waitFor(Finder f, {int seconds = 25}) async {
      for (var i = 0; i < seconds * 5; i++) {
        await t.pump(const Duration(milliseconds: 200));
        if (f.evaluate().isNotEmpty) return true;
      }
      return false;
    }

    Future<void> tapVisible(Finder f) async {
      await t.ensureVisible(f.first);
      await t.pump(const Duration(milliseconds: 300));
      await t.tap(f.first);
      await t.pump(const Duration(milliseconds: 500));
    }

    Future<void> back() async {
      final nav = t.state<NavigatorState>(find.byType(Navigator).first);
      await nav.maybePop(); // never pops the home screen itself
      await t.pump(const Duration(milliseconds: 800));
    }

    // the hero buttons (history, settings, log out) sit at the very top of the home list
    Future<void> toTop() async {
      for (var i = 0; i < 6 && find.byIcon(Icons.logout).evaluate().isEmpty; i++) {
        await t.fling(find.byType(Scrollable).first, const Offset(0, 2500), 4000);
        await t.pump(const Duration(milliseconds: 600));
      }
    }

    Future<void> step(String name, Future<void> Function() body) async {
      try {
        await body();
        debugPrint('E2E PASS $name');
      } catch (e) {
        final msg = '$e'.split('\n').first;
        failures.add('$name: $msg');
        debugPrint('E2E FAIL $name: $msg');
        final shown = find.byType(Text).evaluate().map((e) => (e.widget as Text).data).whereType<String>()
            .where((x) => x.trim().isNotEmpty).take(14).join(' | ');
        debugPrint('E2E SCREEN $shown');
        // get back to the home screen for the next step
        for (var i = 0; i < 4 && find.text('Quick actions').evaluate().isEmpty; i++) {
          try { await back(); } catch (_) {}
        }
      }
    }

    Future<void> expectShown(Finder f, String what, {int seconds = 25}) async {
      if (!await waitFor(f, seconds: seconds)) throw StateError('$what not shown');
    }

    // ---- start from a clean, logged-out app
    final api = ApiClient.instance;
    await api.loadToken();
    await api.logout();
    await NativeBridge.clearBackgroundSession().catchError((_) {});
    await t.pumpWidget(FraudShieldApp(api: api));

    await step('1 login screen shown', () => expectShown(find.byType(TextField), 'login form'));
    await step('2 create account', () async {
      await tapVisible(find.text('New here? Register'));
      await t.enterText(find.byType(TextField).at(0), email);
      await t.enterText(find.byType(TextField).at(1), password);
      await tapVisible(find.widgetWithText(FilledButton, 'Create account'));
      // first run on this phone: the privacy notice comes first; once accepted, it is not shown again
      await expectShown(find.byWidgetPredicate((w) => w is Text && (w.data == 'Before you start' || w.data == 'Quick actions')),
          'privacy notice or home');
    });
    await step('3 privacy notice accepted (or already accepted earlier)', () async {
      if (find.text('Before you start').evaluate().isNotEmpty) {
        // the Agree button is at the end of the notice: scroll until it is built
        for (var i = 0; i < 8 && find.byKey(const Key('consent-accept')).evaluate().isEmpty; i++) {
          await t.fling(find.byType(Scrollable).first, const Offset(0, -2500), 4000);
          await t.pump(const Duration(milliseconds: 600));
        }
        await tapVisible(find.byKey(const Key('consent-accept')));
      }
      await expectShown(find.text('Quick actions'), 'home screen');
    });
    await step('4 home: protection ring + permission summary', () async {
      await expectShown(find.byKey(const Key('perm-summary')), 'permission summary card');
      final title = t.widget<Text>(find.descendant(of: find.byKey(const Key('perm-summary')), matching: find.byType(Text)).first).data;
      debugPrint('E2E INFO permissions: $title');
    });
    await step('5 permissions page (all in one place)', () async {
      await tapVisible(find.byKey(const Key('perm-summary')));
      await expectShown(find.byKey(const Key('perm-count')), 'permission count');
      debugPrint('E2E INFO ${t.widget<Text>(find.byKey(const Key('perm-count'))).data} / ${t.widget<Text>(find.byKey(const Key('perm-pending'))).data}');
      await back();
    });
    await step('6 check a scam message', () async {
      await tapVisible(find.text('Check a message'));
      await expectShown(find.byKey(const Key('msg-input')), 'message box');
      await t.enterText(find.byKey(const Key('msg-input')),
          'Dear customer your SBI account will be blocked today. Update KYC now http://sbi-kyc-update.top/login');
      await tapVisible(find.byKey(const Key('msg-check')));
      await expectShown(find.byKey(const Key('msg-headline')), 'verdict');
      if (find.byKey(const Key('msg-verdict-SAFE')).evaluate().isNotEmpty) throw StateError('scam judged SAFE');
      debugPrint('E2E INFO verdict: ${t.widget<Text>(find.byKey(const Key('msg-headline'))).data}');
      await back();
    });
    await step('7 scam help -> UPI QR check', () async {
      await tapVisible(find.byKey(const Key('safe-tile')));
      await expectShown(find.byKey(const Key('safe-upi')), 'scam help hub');
      await tapVisible(find.byKey(const Key('safe-upi')));
      await expectShown(find.byKey(const Key('upi-input')), 'UPI box');
      await t.enterText(find.byKey(const Key('upi-input')), 'upi://pay?pa=refund.desk@ybl&pn=SBI%20Refund&am=4999');
      await tapVisible(find.byKey(const Key('upi-check')));
      await expectShown(find.textContaining('Do not pay'), 'UPI verdict');
      await back();
      await back();
    });
    await step('8 recovery kit (I was scammed)', () async {
      await tapVisible(find.text('I was scammed'));
      await expectShown(find.byKey(const Key('rec-1930')), 'recovery kit');
      await back();
    });
    await step('9 scam drills', () async {
      await tapVisible(find.text('Scam drills'));
      await expectShown(find.byType(ListTile), 'drill list');
      await back();
    });
    await step('10 fake-website protection (VPN) screen', () async {
      await tapVisible(find.text('Fake-website protection'));
      await expectShown(find.byWidgetPredicate((w) => w.key == const Key('vpn-on') || w.key == const Key('vpn-off') || w.key == const Key('vpn-top-apps')), 'VPN screen');
      await back();
    });
    await step('11 risky apps', () async {
      await tapVisible(find.text('Risky / ad-spam apps'));
      await expectShown(find.byType(Scrollable), 'risky apps list');
      await back();
    });
    await step('12 leftover files', () async {
      await tapVisible(find.byKey(const Key('left-tile')));
      await expectShown(find.byType(Scrollable), 'leftover screen');
      await back();
    });
    await step('13 call guard', () async {
      await tapVisible(find.byKey(const Key('call-guard-tile')));
      // role granted -> settings with the blocked-calls log; not yet -> the "Let FraudShield screen calls - Allow" card
      await expectShown(find.byWidgetPredicate((w) => w.key == const Key('call-open-log') || w.key == const Key('call-role-card') || w.key == const Key('call-role-on')), 'call guard screen');
      await back();
    });
    await step('14 threat history', () async {
      await toTop();
      await t.tap(find.byIcon(Icons.history));
      await t.pump(const Duration(milliseconds: 500));
      await expectShown(find.text('Threat history'), 'threat history');
      await waitFor(find.byKey(const Key('threat-summary')), seconds: 8); // empty for a new account is fine
      await back();
    });
    await step('15 settings', () async {
      await toTop();
      await t.tap(find.byIcon(Icons.tune));
      await t.pump(const Duration(milliseconds: 500));
      await expectShown(find.byKey(const Key('contact-save')), 'settings');
      await back();
    });
    await step('16 log out', () async {
      await toTop();
      await t.tap(find.byIcon(Icons.logout));
      await expectShown(find.byType(TextField), 'login screen after logout');
    });
    await step('17 log back in', () async {
      await t.enterText(find.byType(TextField).at(0), email);
      await t.enterText(find.byType(TextField).at(1), password);
      await tapVisible(find.widgetWithText(FilledButton, 'Login'));
      await expectShown(find.text('Quick actions'), 'home after login');
    });

    debugPrint('E2E DONE ${17 - failures.length}/17 steps passed${failures.isEmpty ? '' : ' - failed: ${failures.join(' | ')}'}');
    expect(failures, isEmpty, reason: failures.join('\n'));
  });
}
