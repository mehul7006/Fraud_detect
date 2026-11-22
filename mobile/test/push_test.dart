import 'dart:async';
import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:fraudshield/core/protection_engine.dart';
import 'package:fraudshield/core/push.dart';
import 'package:http/http.dart' as http;
import 'helpers.dart';

class FakePush implements PushClient {
  String? t;
  final ctrl = StreamController<PushAlert>.broadcast();
  FakePush(this.t);
  @override
  Future<String?> token() async => t;
  @override
  Stream<PushAlert> get foreground => ctrl.stream;
}

/// 7.3: push token goes to the server with the device check-in; a push while the app is open becomes an alert.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late FakeNative native;
  late List<http.Request> sent;

  setUp(() {
    native = FakeNative()..install();
    sent = [];
  });
  tearDown(() => native.remove());

  ProtectionEngine engine(PushClient p) => ProtectionEngine(
      push: p,
      api: apiWith((r) async {
        sent.add(r);
        return jsonResponse(200, {'deviceId': 'dev1', 'cloneRiskScore': 0, 'reasons': []});
      }));

  Map<String, dynamic> heartbeat() =>
      jsonDecode(sent.lastWhere((r) => r.url.path.endsWith('/devices/heartbeat')).body) as Map<String, dynamic>;

  test('the push token is sent with the device check-in', () async {
    await engine(FakePush('fcm-token-123')).checkDevice();
    expect(heartbeat()['pushToken'], 'fcm-token-123');
    expect(heartbeat()['deviceId'], 'dev1', reason: 'normal check-in data still there');
  });

  test('no push available (no Firebase / no Play services): check-in works, no token field', () async {
    await engine(NoPushClient()).checkDevice();
    expect(heartbeat().containsKey('pushToken'), isFalse);
  });

  test('a takeover push while the app is open becomes a CRITICAL in-app alert', () async {
    final p = FakePush('t');
    final e = engine(p)..start();
    final alerts = <Alert>[];
    e.alerts.stream.listen(alerts.add);
    p.ctrl.add(PushAlert('Account takeover in progress', 'Open FraudShield now to secure your account.', 'ACCOUNT_TAKEOVER'));
    await Future<void>.delayed(const Duration(milliseconds: 10));
    e.stop();
    final a = alerts.firstWhere((x) => x.title == 'Account takeover in progress');
    expect(a.severity, 'CRITICAL');
  });
}
