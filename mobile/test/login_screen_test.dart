import 'dart:convert';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fraudshield/core/api_client.dart';
import 'package:fraudshield/screens/login_screen.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

ApiClient _api(MockClientHandler h) =>
    ApiClient(client: MockClient(h), store: MemoryTokenStore(), baseUrl: 'http://test/api/v1');

http.Response _err(int s, String m) => http.Response(jsonEncode({'status': s, 'message': m}), s);

Future<void> _pump(WidgetTester t, ApiClient api, VoidCallback onDone) =>
    t.pumpWidget(MaterialApp(home: LoginScreen(api: api, onDone: onDone)));

Future<void> _fillAndSubmit(WidgetTester t, String buttonLabel) async {
  await t.enterText(find.byType(TextField).at(0), 'a@b.com');
  await t.enterText(find.byType(TextField).at(1), 'password123');
  await t.tap(find.text(buttonLabel));
  await t.pumpAndSettle();
}

void main() {
  testWidgets('2.3 successful login calls onDone', (t) async {
    var done = 0;
    await _pump(t, _api((r) async => http.Response(jsonEncode({'token': 'tok'}), 200)), () => done++);
    await _fillAndSubmit(t, 'Login');
    expect(done, 1);
    expect(find.textContaining('Wrong'), findsNothing);
  });

  testWidgets('2.3 wrong password shows "Wrong email or password"', (t) async {
    var done = 0;
    await _pump(t, _api((r) async => _err(401, 'Invalid credentials')), () => done++);
    await _fillAndSubmit(t, 'Login');
    expect(find.text('Wrong email or password'), findsOneWidget);
    expect(done, 0);
  });

  testWidgets('2.3 register with used email shows "Email already registered"', (t) async {
    await _pump(t, _api((r) async => _err(409, 'Email already used')), () {});
    await t.tap(find.text('New here? Register'));
    await t.pump();
    expect(find.text('Create account'), findsOneWidget);
    await _fillAndSubmit(t, 'Create account');
    expect(find.text('Email already registered'), findsOneWidget);
  });

  testWidgets('2.3 validation error (400) shows the server message', (t) async {
    await _pump(t, _api((r) async => _err(400, 'password size must be between 8 and 72')), () {});
    await _fillAndSubmit(t, 'Login');
    expect(find.text('password size must be between 8 and 72'), findsOneWidget);
  });

  testWidgets('2.3 server down shows "Cannot reach server"', (t) async {
    await _pump(t, _api((r) async => throw const SocketException('no route')), () {});
    await _fillAndSubmit(t, 'Login');
    expect(find.text('Cannot reach server'), findsOneWidget);
  });

  testWidgets('2.3 unexpected status shows generic error code', (t) async {
    await _pump(t, _api((r) async => _err(500, 'Unexpected server error')), () {});
    await _fillAndSubmit(t, 'Login');
    expect(find.text('Error 500'), findsOneWidget);
  });

  testWidgets('2.3 error clears and button re-enables after a failed attempt', (t) async {
    var fail = true;
    var done = 0;
    await _pump(t, _api((r) async => fail ? _err(401, 'x') : http.Response(jsonEncode({'token': 't'}), 200)), () => done++);
    await _fillAndSubmit(t, 'Login');
    expect(find.text('Wrong email or password'), findsOneWidget);
    fail = false;
    await t.tap(find.text('Login'));
    await t.pumpAndSettle();
    expect(find.text('Wrong email or password'), findsNothing);
    expect(done, 1);
  });
}
