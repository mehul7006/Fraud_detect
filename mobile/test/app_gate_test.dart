import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fraudshield/core/api_client.dart';
import 'package:fraudshield/core/protection_engine.dart';
import 'package:fraudshield/main.dart';
import 'helpers.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late FakeNative native;

  setUp(() => native = FakeNative()..install());
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

  testWidgets('2.4 app uses Material 3 with the brand colour', (t) async {
    await pumpApp(t, apiWith((r) async => jsonResponse(200, {})));
    final app = t.widget<MaterialApp>(find.byType(MaterialApp));
    expect(app.theme!.useMaterial3, true);
    expect(app.title, 'FraudShield');
    expect(app.debugShowCheckedModeBanner, false);
  });
}
