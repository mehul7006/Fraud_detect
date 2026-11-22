import 'package:flutter_test/flutter_test.dart';
import 'package:fraudshield/core/api_client.dart';
import 'package:fraudshield/core/protection_engine.dart';
import 'package:fraudshield/core/settings_store.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'helpers.dart';

/// 7.11 + 8.2: the native background scanner gets its OWN session (from /auth/background-session) plus the link
/// settings, and loses them on logout.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late FakeNative native;
  late int bgSessions;

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    native = FakeNative()..install();
    bgSessions = 0;
  });
  tearDown(() => native.remove());

  Future<ApiClient> loggedIn() async {
    final store = MemoryTokenStore();
    await store.write('{"a":"app-access","r":"app-refresh"}');
    final api = apiWith((r) async {
      if (r.url.path.endsWith('/auth/background-session')) {
        bgSessions++;
        expect(r.headers['Authorization'], 'Bearer app-access');
        return jsonResponse(200, {'token': 'bg-access-$bgSessions', 'refreshToken': 'bg-refresh-$bgSessions', 'expiresIn': 900});
      }
      return jsonResponse(200, {});
    }, store: store);
    await api.loadToken();
    return api;
  }

  List<Map> sessions() =>
      native.calls.where((c) => c.method == 'setBackgroundSession').map((c) => c.arguments as Map).toList();

  test('logged in: a separate background session + API address are handed over once', () async {
    final e = ProtectionEngine(api: await loggedIn());
    await e.syncBackgroundSession();
    await e.syncBackgroundSession(); // nothing changed -> not sent again
    expect(sessions(), hasLength(1));
    expect(sessions().single['token'], 'bg-access-1', reason: 'never the app\'s own token');
    expect(sessions().single['refresh'], 'bg-refresh-1');
    expect(sessions().single['apiBase'], 'http://test/api/v1');
    expect(bgSessions, 1);
  });

  test('a new trusted site is passed on without asking for another session', () async {
    final settings = SettingsStore();
    final e = ProtectionEngine(api: await loggedIn(), settings: settings);
    await e.syncBackgroundSession();
    await settings.addTrustedDomain('sbi.co.in');
    await e.syncBackgroundSession();
    expect(sessions(), hasLength(2));
    expect(sessions().last['trusted'], contains('sbi.co.in'));
    expect(sessions().last['token'], isNull, reason: 'keep the stored native session');
    expect(bgSessions, 1);
  });

  test('logged out: the background copy is cleared', () async {
    final api = await loggedIn();
    final e = ProtectionEngine(api: api);
    await e.syncBackgroundSession();
    await api.logout();
    await e.syncBackgroundSession();
    expect(native.count('clearBackgroundSession'), 1);
  });

  test('the fast loop keeps it in sync', () async {
    final e = ProtectionEngine(api: await loggedIn());
    await e.runFast();
    expect(sessions(), hasLength(1));
  });
}
