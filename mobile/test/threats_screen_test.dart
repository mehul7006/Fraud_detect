import 'dart:convert';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fraudshield/core/api_client.dart';
import 'package:fraudshield/screens/threats_screen.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

ApiClient _api(MockClientHandler h) =>
    ApiClient(client: MockClient(h), store: MemoryTokenStore(), baseUrl: 'http://test/api/v1');

Map<String, dynamic> _threat(int id, String title, String sev, String status) => {
      'id': id, 'type': 'MALICIOUS_LINK', 'severity': sev, 'title': title,
      'detail': 'detail $id', 'sourceApp': null, 'status': status, 'createdAt': '2026-11-20T10:00:00Z'
    };

void main() {
  testWidgets('2.7 shows threats from the backend', (t) async {
    final api = _api((r) async => http.Response(
        jsonEncode([_threat(2, 'Dangerous link blocked', 'CRITICAL', 'OPEN'), _threat(1, 'Old one', 'LOW', 'RESOLVED')]), 200));
    await t.pumpWidget(MaterialApp(home: ThreatsScreen(api: api)));
    await t.pumpAndSettle();
    expect(find.text('Dangerous link blocked'), findsOneWidget);
    expect(find.text('Old one'), findsOneWidget);
    expect(find.text('Resolve'), findsOneWidget, reason: 'only OPEN threats have a Resolve button');
    expect(find.byIcon(Icons.check), findsOneWidget, reason: 'resolved threat shows a check');
  });

  testWidgets('2.7 empty list shows the safe message', (t) async {
    await t.pumpWidget(MaterialApp(home: ThreatsScreen(api: _api((r) async => http.Response('[]', 200)))));
    await t.pumpAndSettle();
    expect(find.text('No threats found. You are safe.'), findsOneWidget);
  });

  testWidgets('2.7 / 8.10 offline: plain message + Try again, which reloads', (t) async {
    var down = true;
    await t.pumpWidget(MaterialApp(home: ThreatsScreen(api: _api((r) async {
      if (down) throw const SocketException('down');
      return http.Response('[]', 200, headers: {'content-type': 'application/json'});
    }))));
    await t.pumpAndSettle();
    expect(find.textContaining('No internet connection'), findsOneWidget);
    down = false;
    await t.tap(find.text('Try again'));
    await t.pumpAndSettle();
    expect(find.text('No threats found. You are safe.'), findsOneWidget);
  });

  testWidgets('2.7 Resolve posts to the backend and reloads the list', (t) async {
    var resolved = false;
    var resolveCalls = 0;
    final api = _api((r) async {
      if (r.method == 'POST' && r.url.path == '/api/v1/threats/5/resolve') {
        resolveCalls++;
        resolved = true;
        return http.Response('', 204);
      }
      return http.Response(jsonEncode([_threat(5, 'Call forwarding is ON', 'CRITICAL', resolved ? 'RESOLVED' : 'OPEN')]), 200);
    });
    await t.pumpWidget(MaterialApp(home: ThreatsScreen(api: api)));
    await t.pumpAndSettle();
    await t.tap(find.text('Resolve'));
    await t.pumpAndSettle();
    expect(resolveCalls, 1);
    expect(find.text('Resolve'), findsNothing);
    expect(find.byIcon(Icons.check), findsOneWidget);
  });
}
