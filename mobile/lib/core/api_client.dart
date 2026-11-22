import 'dart:convert';
import 'package:flutter/foundation.dart' show kReleaseMode;
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:http/http.dart' as http;
import 'carrier_classifier.dart';

/// Android emulator -> host machine is 10.0.2.2. Change for real device / production (must be https).
const String kBaseUrl = String.fromEnvironment('API_BASE', defaultValue: 'http://10.0.2.2:8080/api/v1');

/// 8.9: refuses a non-https API address in release builds (Dart's HTTP client ignores Android's cleartext rules,
/// so it is enforced here). Debug builds may use http://10.0.2.2 for the local emulator backend.
String checkBaseUrl(String base, {required bool release}) {
  if (release && !base.startsWith('https://')) {
    throw StateError('Release builds must use an https API address, got "$base" (set --dart-define API_BASE=https://...)');
  }
  return base;
}

/// Error returned by the backend. Mirrors the backend ErrorBody: {timestamp,status,error,message,path,requestId}.
class ApiException implements Exception {
  final int status;
  final String message;
  final String? requestId;
  ApiException(this.status, this.message, {this.requestId});
  @override
  String toString() => 'ApiException($status): $message';
}

/// Where the JWT is kept. Real app = Android Keystore backed; tests use [MemoryTokenStore].
abstract class TokenStore {
  Future<String?> read();
  Future<void> write(String token);
  Future<void> clear();
}

class SecureTokenStore implements TokenStore {
  final _s = const FlutterSecureStorage();
  @override
  Future<String?> read() => _s.read(key: 'jwt');
  @override
  Future<void> write(String token) => _s.write(key: 'jwt', value: token);
  @override
  Future<void> clear() => _s.delete(key: 'jwt');
}

class MemoryTokenStore implements TokenStore {
  String? _t;
  @override
  Future<String?> read() async => _t;
  @override
  Future<void> write(String token) async => _t = token;
  @override
  Future<void> clear() async => _t = null;
}

class ApiClient {
  static final ApiClient instance = ApiClient();

  final http.Client _http;
  final TokenStore _store;
  final String _base;
  final Duration _timeout;
  String? _token;
  String? _refresh; // 8.2: single-use refresh token (rotated on every refresh)
  Future<bool>? _refreshing; // one refresh at a time, shared by all requests that got a 401

  ApiClient({http.Client? client, TokenStore? store, String? baseUrl, Duration timeout = const Duration(seconds: 15)})
      : _http = client ?? http.Client(),
        _store = store ?? SecureTokenStore(),
        _base = checkBaseUrl(baseUrl ?? kBaseUrl, release: kReleaseMode),
        _timeout = timeout;

  bool get hasToken => _token != null;

  /// Called once when the server rejects our token (401 on an authenticated call): the account was deleted, the
  /// token expired or was revoked. The token is already cleared; the app shows the login screen.
  void Function()? onSessionExpired;

  /// 7.11: handed to the native background scanner (stored there encrypted with the Android Keystore).
  String? get token => _token;
  String get baseUrl => _base;

  /// Stored as JSON {"a": access, "r": refresh}. A plain string (saved by older versions) is an access token only.
  Future<bool> loadToken() async {
    final v = await _store.read();
    _token = null;
    _refresh = null;
    if (v != null && v.startsWith('{')) {
      try {
        final j = jsonDecode(v) as Map<String, dynamic>;
        _token = j['a'] as String?;
        _refresh = j['r'] as String?;
      } catch (_) {}
    } else {
      _token = v;
    }
    return _token != null;
  }

  Future<void> _save() => _refresh == null ? _store.write(_token!) : _store.write(jsonEncode({'a': _token, 'r': _refresh}));

  /// 8.2: tells the server too (all tokens of this account stop working), then forgets them here.
  Future<void> logout() async {
    if (_token != null) {
      try {
        await _http.post(Uri.parse('$_base/auth/logout'), headers: _headers(true)).timeout(const Duration(seconds: 5));
      } catch (_) {} // offline: the local logout still happens
    }
    _token = null;
    _refresh = null;
    await _store.clear();
  }

  /// 8.2 / 7.11: an independent session (access + refresh token) for the native background scanner.
  Future<Map<String, dynamic>> backgroundSession() => _post('/auth/background-session', {});

  Future<void> login(String email, String password) => _auth('login', email, password);
  Future<void> register(String email, String password) => _auth('register', email, password);

  Future<void> _auth(String path, String email, String password) async {
    final r = await _post('/auth/$path', {'email': email, 'password': password}, auth: false);
    final t = r['token'];
    if (t is! String || t.isEmpty) throw ApiException(500, 'Server returned no token');
    _token = t;
    _refresh = r['refreshToken'] as String?;
    await _save();
  }

  /// Swaps the refresh token for a new pair. true = renewed; false = the server refused (session really over).
  /// Network errors are thrown, so being offline never logs anybody out.
  Future<bool> _renew() {
    final rt = _refresh;
    if (rt == null) return Future.value(false);
    return _refreshing ??= () async {
      try {
        final res = await _http
            .post(Uri.parse('$_base/auth/refresh'), headers: {'Content-Type': 'application/json'},
                body: jsonEncode({'refreshToken': rt}))
            .timeout(_timeout);
        if (res.statusCode != 200) return false;
        final j = jsonDecode(res.body) as Map<String, dynamic>;
        _token = j['token'] as String;
        _refresh = j['refreshToken'] as String?;
        await _save();
        return true;
      } finally {
        _refreshing = null;
      }
    }();
  }

  /// Sends [call]; on 401 renews the access token once and sends it again (the 15-minute token simply ran out).
  Future<http.Response> _send(Future<http.Response> Function() call, {required bool authed}) async {
    final res = await call().timeout(_timeout);
    if (res.statusCode != 401 || !authed || _token == null || _refresh == null) return res;
    if (!await _renew()) return res; // refused: _decode turns this 401 into "session expired"
    return call().timeout(_timeout);
  }

  // ---- scanning ----
  Future<Map<String, dynamic>> scanUrl(String url, String? sourceApp) =>
      _post('/scan/url', {'url': url, 'sourceApp': sourceApp});

  Future<Map<String, dynamic>> scanFile(Map<String, dynamic> f) => _post('/scan/file', {
        'sha256': f['sha256'],
        'fileName': f['fileName'],
        'sizeBytes': f['sizeBytes'],
        'sourceApp': f['sourceApp'],
        'headerHex': f['headerHex'],
        // flag NAMES only (PDF_JS, PDF_LAUNCH, PDF_OPENACTION, OFFICE_MACRO) - never file content
        'flags': _flagNames(f['flags']),
      });

  static const _knownFlags = {'PDF_JS', 'PDF_LAUNCH', 'PDF_OPENACTION', 'OFFICE_MACRO'};
  static List<String> _flagNames(Object? raw) =>
      raw is List ? raw.whereType<String>().where(_knownFlags.contains).take(10).toList() : <String>[];

  Future<Map<String, dynamic>> scanApps(List<Map<String, dynamic>> apps, {String? deviceId}) =>
      _post('/scan/apps', {'apps': apps, if (deviceId != null) 'deviceId': deviceId});

  /// 6.1 (opt-in): only the score signals of one app and the user's decision. Label, certificate and installer
  /// are not sent with it.
  static const labelFields = ['packageName', 'notifPerHour', 'hasOverlayPermission', 'hasSmsPermission', 'hasAccessibility',
    'hasNotificationListener', 'hasFullScreenIntent', 'selfLaunchCount', 'isSystem'];
  Future<void> labelApp(Map<String, dynamic> app, String label, int scoreShown) => _post('/scan/apps/label', {
        'app': {for (final k in labelFields) if (app.containsKey(k)) k: app[k]},
        'label': label,
        'scoreShown': scoreShown,
      });

  Future<Map<String, dynamic>> heartbeat(Map<String, dynamic> device) => _post('/devices/heartbeat', device);

  Future<void> report(String type, String severity, String title, {String? detail, String? sourceApp}) =>
      _post('/threats', {'type': type, 'severity': severity, 'title': title, 'detail': detail, 'sourceApp': sourceApp});

  Future<List<dynamic>> threats({String? status}) async {
    final r = await _get('/threats${status == null ? '' : '?status=$status'}');
    return r as List<dynamic>;
  }

  Future<void> resolve(int id) => _post('/threats/$id/resolve', {});

  /// 9.4 / Play policy: in-app account deletion. Removes every record of the user on the server (9.2), then forgets
  /// the session here and tells the app (shows login).
  Future<void> deleteAccount() async {
    final res = await _send(() => _http.delete(Uri.parse('$_base/me'), headers: _headers(true)), authed: true);
    if (res.statusCode != 204 && res.statusCode != 200) _decode(res, authed: true); // throws ApiException
    _token = null;
    _refresh = null;
    await _store.clear();
    onSessionExpired?.call();
  }

  /// Carrier answer patterns (backend 3.2). Pass the last [version] to get null back when nothing changed (HTTP 304).
  Future<CarrierConfig?> carrierConfig({String? version}) async {
    final res = await _send(() {
      final h = _headers(true);
      if (version != null) h['If-None-Match'] = '"$version"';
      return _http.get(Uri.parse('$_base/config/carriers'), headers: h);
    }, authed: true);
    if (res.statusCode == 304) return null;
    final d = _decode(res, authed: true);
    return CarrierConfig.fromJson(Map<String, dynamic>.from(d as Map));
  }

  // ---- http helpers ----
  Map<String, String> _headers(bool auth) => {
        'Content-Type': 'application/json',
        if (auth && _token != null) 'Authorization': 'Bearer $_token',
      };

  Future<dynamic> _get(String path) async {
    final res = await _send(() => _http.get(Uri.parse('$_base$path'), headers: _headers(true)), authed: true);
    return _decode(res, authed: true);
  }

  Future<Map<String, dynamic>> _post(String path, Map<String, dynamic> body, {bool auth = true}) async {
    final res = await _send(() => _http.post(Uri.parse('$_base$path'), headers: _headers(auth), body: jsonEncode(body)),
        authed: auth);
    final d = _decode(res, authed: auth);
    return d is Map<String, dynamic> ? d : <String, dynamic>{};
  }

  dynamic _decode(http.Response res, {bool authed = false}) {
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return res.body.isEmpty ? <String, dynamic>{} : jsonDecode(res.body);
    }
    if (res.statusCode == 401 && authed && _token != null) {
      _token = null;
      _refresh = null;
      _store.clear(); // fire and forget: the in-memory token is already gone
      onSessionExpired?.call();
    }
    // backend error shape; fall back gracefully for non-JSON bodies (proxies, gateways)
    var message = res.reasonPhrase ?? 'Request failed';
    String? requestId;
    try {
      final j = jsonDecode(res.body);
      if (j is Map) {
        message = (j['message'] ?? message).toString();
        requestId = j['requestId']?.toString();
      }
    } catch (_) {}
    throw ApiException(res.statusCode, message, requestId: requestId);
  }
}
