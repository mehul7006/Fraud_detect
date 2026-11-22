import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:flutter_test/flutter_test.dart';
import 'package:fraudshield/core/api_client.dart';
import 'package:fraudshield/core/protection_engine.dart';
import 'package:fraudshield/main.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'helpers.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late FakeNative native;

  setUp(() {
    SharedPreferences.setMockInitialValues({'consent_version': 1}); // privacy notice already accepted (see 9.4 tests)
    native = FakeNative()..install();
  });
  tearDown(() => native.remove());

  Future<void> pumpApp(WidgetTester t, ApiClient api) async {
    t.view.physicalSize = const Size(800, 3200);
    t.view.devicePixelRatio = 1.0;
    addTearDown(t.view.reset);
    await t.pumpWidget(FraudShieldApp(api: api, engine: ProtectionEngine(api: api), autoStart: false));
    await t.pumpAndSettle();
  }

  testWidgets('2.4 cold start without a saved session shows login', (t) async {
    await pumpApp(t, apiWith((r) async => jsonResponse(200, {})));
    expect(find.text('FraudShield'), findsOneWidget);
    expect(find.text('Login'), findsOneWidget);
    expect(find.text('Call forwarding'), findsNothing);
  });

  testWidgets('2.4 cold start WITH a saved session goes straight to the dashboard', (t) async {
    final store = MemoryTokenStore();
    await store.write('saved.jwt.token');
    await pumpApp(t, apiWith((r) async => jsonResponse(200, {}), store: store));
    expect(find.text('Call forwarding'), findsOneWidget);
    expect(find.text('Login'), findsNothing);
  });

  testWidgets('2.4 logging in moves from login to the dashboard', (t) async {
    final api = apiWith((r) async => jsonResponse(200, {'token': 'tok'}));
    await pumpApp(t, api);
    await t.enterText(find.byType(TextField).at(0), 'a@b.com');
    await t.enterText(find.byType(TextField).at(1), 'password123');
    await t.tap(find.text('Login'));
    await t.pumpAndSettle();
    expect(find.text('Call forwarding'), findsOneWidget);
  });

  testWidgets('2.4 logging out returns to login and forgets the session', (t) async {
    final store = MemoryTokenStore();
    await store.write('saved.jwt.token');
    final api = apiWith((r) async => jsonResponse(200, {}), store: store);
    await pumpApp(t, api);
    await t.tap(find.byIcon(Icons.logout));
    await t.pumpAndSettle();
    expect(find.text('Login'), findsOneWidget);
    expect(await store.read(), isNull);
  });

  testWidgets('7.11 server rejects the token (account deleted) -> back to login, session forgotten', (t) async {
    final store = MemoryTokenStore();
    await store.write('token.of.deleted.account');
    final api = apiWith((r) async => jsonResponse(401, {'status': 401, 'message': 'Authentication required'}), store: store);
    await pumpApp(t, api);
    expect(find.text('Call forwarding'), findsOneWidget);
    await expectLater(api.threats(), throwsA(isA<ApiException>()));
    await t.pumpAndSettle();
    expect(find.text('Login'), findsOneWidget);
    expect(api.hasToken, isFalse);
    expect(await store.read(), isNull);
    expect(native.count('clearBackgroundSession'), 1);
  });

  testWidgets('a wrong password (401 on login) is NOT treated as an expired session', (t) async {
    var expired = 0;
    final api = apiWith((r) async => jsonResponse(401, {'status': 401, 'message': 'Wrong email or password'}));
    api.onSessionExpired = () => expired++;
    await expectLater(api.login('a@b.com', 'nope'), throwsA(isA<ApiException>()));
    expect(expired, 0);
  });

  group('9.4 privacy notice before the dashboard', () {
    testWidgets('first login: notice first; Agree -> dashboard, remembered', (t) async {
      SharedPreferences.setMockInitialValues({});
      final store = MemoryTokenStore();
      await store.write('saved.jwt.token');
      await pumpApp(t, apiWith((r) async => jsonResponse(200, {}), store: store));
      expect(find.text('Before you start'), findsOneWidget);
      expect(find.text('Call forwarding'), findsNothing, reason: 'no dashboard / permission cards before consent');
      expect(find.textContaining('never leaves your phone'), findsOneWidget);
      await t.tap(find.byKey(const Key('consent-accept')));
      await t.pumpAndSettle();
      expect(find.text('Call forwarding'), findsOneWidget);
      expect((await SharedPreferences.getInstance()).getInt('consent_version'), 1);
    });

    testWidgets('declining logs out', (t) async {
      SharedPreferences.setMockInitialValues({});
      final store = MemoryTokenStore();
      await store.write('saved.jwt.token');
      await pumpApp(t, apiWith((r) async => jsonResponse(200, {}), store: store));
      await t.tap(find.byKey(const Key('consent-decline')));
      await t.pumpAndSettle();
      expect(find.text('Login'), findsOneWidget);
      expect(await store.read(), isNull);
    });

    testWidgets('privacy policy link opens the https page', (t) async {
      SharedPreferences.setMockInitialValues({});
      final store = MemoryTokenStore();
      await store.write('saved.jwt.token');
      await pumpApp(t, apiWith((r) async => jsonResponse(200, {}), store: store));
      await t.tap(find.byKey(const Key('privacy-policy-link')));
      await t.pumpAndSettle();
      final call = native.calls.lastWhere((c) => c.method == 'openUrl');
      expect((call.arguments as Map)['url'], startsWith('https://'));
    });
  });

  testWidgets('9.4 Delete my account: confirm -> DELETE /me -> login screen, session gone', (t) async {
    final store = MemoryTokenStore();
    await store.write('saved.jwt.token');
    String? deleted;
    final api = apiWith((r) async {
      if (r.method == 'DELETE') { deleted = r.url.path; return http.Response('', 204); }
      return jsonResponse(200, {});
    }, store: store);
    await pumpApp(t, api);
    await t.tap(find.byTooltip('Link settings'));
    await t.pumpAndSettle();
    await t.tap(find.byKey(const Key('delete-account')));
    await t.pumpAndSettle();
    await t.tap(find.text('Cancel'));
    await t.pumpAndSettle();
    expect(deleted, isNull, reason: 'cancel deletes nothing');
    await t.tap(find.byKey(const Key('delete-account')));
    await t.pumpAndSettle();
    await t.tap(find.byKey(const Key('delete-account-confirm')));
    await t.pumpAndSettle();
    expect(deleted, '/api/v1/me');
    expect(find.text('Login'), findsOneWidget);
    expect(await store.read(), isNull);
    expect(native.count('clearBackgroundSession'), greaterThanOrEqualTo(1));
  });

  testWidgets('2.4 app uses Material 3 with the brand colour', (t) async {
    await pumpApp(t, apiWith((r) async => jsonResponse(200, {})));
    final app = t.widget<MaterialApp>(find.byType(MaterialApp));
    expect(app.theme!.useMaterial3, true);
    expect(app.title, 'FraudShield');
    expect(app.debugShowCheckedModeBanner, false);
  });
}
