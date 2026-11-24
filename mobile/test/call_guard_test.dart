import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fraudshield/core/api_client.dart';
import 'package:fraudshield/core/call_guard.dart';
import 'package:fraudshield/screens/blocked_calls_screen.dart';
import 'package:fraudshield/screens/call_guard_settings_screen.dart';
import 'package:fraudshield/screens/report_number_screen.dart';
import 'helpers.dart';

/// 16.6 role flow, 16.8 modes + mode-4 warning, allow list, 16.10 log + undo, 16.12 report + Chakshu, dashboard tile.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late FakeNative native;
  late List<_Req> reqs;

  setUp(() {
    native = FakeNative()..install();
    reqs = [];
  });
  tearDown(() => native.remove());

  ApiClient api({int status = 200}) => apiWith((r) async {
        reqs.add(_Req(r.url.path, r.body));
        if (r.url.path.endsWith('/numbers/report')) {
          return jsonResponse(status, status == 200 ? {'status': 'REPORTED', 'counted': true, 'reports': 1} : {'message': 'x'});
        }
        return jsonResponse(200, {});
      });

  Future<void> pump(WidgetTester t, Widget w) async {
    t.view.physicalSize = const Size(1080, 6000);
    t.view.devicePixelRatio = 2.5;
    addTearDown(t.view.reset);
    await t.pumpWidget(MaterialApp(home: w));
    await t.pumpAndSettle();
  }

  int? lastMode() {
    final l = native.calls.where((c) => c.method == 'callGuardSetMode').toList();
    return l.isEmpty ? null : (l.last.arguments as Map)['mode'] as int;
  }

  testWidgets('16.6: role not held -> Allow asks Android for the call-screening role', (t) async {
    await pump(t, CallGuardSettingsScreen(api: api()));
    expect(find.byKey(const Key('call-role-card')), findsOneWidget);
    await t.tap(find.byKey(const Key('call-role-allow')));
    await t.pumpAndSettle();
    expect(native.count('callGuardRequestRole'), 1);
    expect(find.byKey(const Key('call-role-on')), findsOneWidget);
    expect(find.byKey(const Key('call-role-card')), findsNothing);
  });

  testWidgets('16.6: Android below 10 gets a clear message, no modes, reporting still works', (t) async {
    native.callGuard = {...native.callGuard, 'supported': false, 'sdk': 28};
    await pump(t, CallGuardSettingsScreen(api: api()));
    expect(find.byKey(const Key('call-unsupported')), findsOneWidget);
    expect(find.byKey(const Key('call-mode-2')), findsNothing);
    expect(find.byKey(const Key('call-open-report')), findsOneWidget);
  });

  testWidgets('16.8: each mode is set; mode 4 only after the warning is confirmed', (t) async {
    native.callGuard = {...native.callGuard, 'roleHeld': true};
    await pump(t, CallGuardSettingsScreen(api: api()));
    await t.tap(find.byKey(const Key('call-mode-1')));
    await t.pumpAndSettle();
    expect(lastMode(), 1);
    await t.tap(find.byKey(const Key('call-mode-3')));
    await t.pumpAndSettle();
    expect(lastMode(), 3);
    // mode 4: warning first; Cancel keeps mode 3
    await t.tap(find.byKey(const Key('call-mode-4')));
    await t.pumpAndSettle();
    expect(find.byKey(const Key('call-mode4-warning')), findsOneWidget);
    expect(find.textContaining('Emergency numbers and official 1600 bank calls always ring'), findsOneWidget);
    await t.tap(find.byKey(const Key('call-mode4-cancel')));
    await t.pumpAndSettle();
    expect(lastMode(), 3);
    expect(native.callGuard['mode'], 3);
    await t.tap(find.byKey(const Key('call-mode-4')));
    await t.pumpAndSettle();
    await t.tap(find.byKey(const Key('call-mode4-confirm')));
    await t.pumpAndSettle();
    expect(lastMode(), 4);
    await t.tap(find.byKey(const Key('call-mode-2')));
    await t.pumpAndSettle();
    expect(lastMode(), 2);
  });

  testWidgets('16.8: allow-list manager adds, rejects junk, removes', (t) async {
    native.callGuard = {...native.callGuard, 'roleHeld': true};
    await pump(t, CallGuardSettingsScreen(api: api()));
    await t.enterText(find.byKey(const Key('call-allow-input')), '98765 43210');
    await t.tap(find.byKey(const Key('call-allow-add')));
    await t.pumpAndSettle();
    expect(find.text('+919876543210'), findsOneWidget);
    await t.enterText(find.byKey(const Key('call-allow-input')), '12ab');
    await t.tap(find.byKey(const Key('call-allow-add')));
    await t.pumpAndSettle();
    expect(find.text('Not a valid phone number'), findsOneWidget);
    await t.tap(find.byTooltip('Remove'));
    await t.pumpAndSettle();
    expect(find.text('+919876543210'), findsNothing);
    expect(reqs, isEmpty, reason: 'allow list never leaves the phone');
  });

  testWidgets('16.10: rejected call listed; Not spam moves the number to the allow list, nothing sent', (t) async {
    native.callLog = [
      {'id': 1, 'at': DateTime(2026, 11, 24, 10, 5).millisecondsSinceEpoch, 'number': '+919811122233', 'action': 'REJECT',
        'kind': 'SCAM_LIST', 'label': 'Reported as fraud by many people', 'rule': null, 'category': 'SCAM', 'undone': false},
      {'id': 2, 'at': DateTime(2026, 11, 23, 9).millisecondsSinceEpoch, 'number': '+911600300123', 'action': 'ALLOW',
        'kind': 'PROTECTED_SERIES', 'label': 'Official service call (1600 number)', 'rule': 'IN_1600_SERVICE', 'category': null, 'undone': false},
    ];
    await pump(t, BlockedCallsScreen(api: api()));
    expect(find.text('Reported as fraud by many people'), findsOneWidget);
    expect(find.textContaining('Rejected'), findsOneWidget);
    expect(find.byKey(const Key('call-notspam-2')), findsNothing, reason: '1600 numbers have no actions');
    await t.tap(find.byKey(const Key('call-notspam-1')));
    await t.pumpAndSettle();
    expect((native.callGuard['allow'] as List), contains('+919811122233'));
    expect(find.textContaining('Allowed by you'), findsOneWidget);
    expect(find.byKey(const Key('call-notspam-1')), findsNothing);
    expect(reqs, isEmpty, reason: 'no number leaves the phone except on Report');
  });

  testWidgets('16.10: empty log', (t) async {
    await pump(t, BlockedCallsScreen(api: api()));
    expect(find.byKey(const Key('calls-empty')), findsOneWidget);
  });

  testWidgets('16.5 / 16.12: report sends canonical number + category; Chakshu copies and opens the verified URL', (t) async {
    final a = api();
    await pump(t, ReportNumberScreen(api: a, number: '098111 22233'));
    await t.tap(find.byKey(const Key('report-cat-SPAM')));
    await t.pumpAndSettle();
    await t.tap(find.byKey(const Key('report-send')));
    await t.pumpAndSettle();
    expect(reqs.single.path, '/api/v1/numbers/report');
    expect(jsonDecode(reqs.single.body), {'number': '+919811122233', 'category': 'SPAM'});
    expect(find.text('Reported - thank you'), findsOneWidget);
    await t.tap(find.byKey(const Key('gov-open')));
    await t.pumpAndSettle();
    final open = native.calls.lastWhere((c) => c.method == 'openUrl');
    expect((open.arguments as Map)['url'], kChakshuUrl);
    expect(kChakshuUrl, 'https://sancharsaathi.gov.in/sfc/');
  });

  testWidgets('16.5: 1600 numbers are refused on the phone; server 422 / 429 shown plainly', (t) async {
    await pump(t, ReportNumberScreen(api: api(), number: '1600300123'));
    await t.tap(find.byKey(const Key('report-send')));
    await t.pumpAndSettle();
    expect(find.textContaining('official 1600 / 140 service number'), findsOneWidget);
    expect(reqs, isEmpty);

    await pump(t, ReportNumberScreen(key: UniqueKey(), api: api(status: 429), number: '9811122233'));
    await t.tap(find.byKey(const Key('report-send')));
    await t.pumpAndSettle();
    expect(find.textContaining('reported many numbers today'), findsOneWidget);
  });

  testWidgets('tile: off -> on text; a tapped call notification opens the log', (t) async {
    await pump(t, Scaffold(body: CallGuardTile(api: api())));
    expect(find.text('Off - tap to block scam calls'), findsOneWidget);
    native.callGuard = {...native.callGuard, 'roleHeld': true, 'counts': {'REJECT': 3, 'SILENCE': 1}};
    native.callOpenLog = true;
    await pump(t, Scaffold(body: CallGuardTile(key: UniqueKey(), api: api())));
    expect(find.byType(BlockedCallsScreen), findsOneWidget);
    await t.pageBack();
    await t.pumpAndSettle();
    expect(find.text('On - 4 calls stopped in 30 days'), findsOneWidget);
  });
}

class _Req {
  final String path, body;
  _Req(this.path, this.body);
}
