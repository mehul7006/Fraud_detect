import 'dart:convert';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:http/http.dart' as http;
import 'carrier_classifier.dart';

/// Android emulator -> host machine is 10.0.2.2. Change for real device / production (must be https).
const String kBaseUrl = String.fromEnvironment('API_BASE', defaultValue: 'http://10.0.2.2:8080/api/v1');

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

  ApiClient({http.Client? client, TokenStore? store, String? baseUrl, Duration timeout = const Duration(seconds: 15)})
      : _http = client ?? http.Client(),
        _store = store ?? SecureTokenStore(),
        _base = baseUrl ?? kBaseUrl,
        _timeout = timeout;

  bool get hasToken => _token != null;

  Future<bool> loadToken() async {
    _token = await _store.read();
    return _token != null;
  }

  Future<void> logout() async {
    _token = null;
    await _store.clear();
  }

  Future<void> login(String email, String password) => _auth('login', email, password);
  Future<void> register(String email, String password) => _auth('register', email, password);

  Future<void> _auth(String path, String email, String password) async {
    final r = await _post('/auth/$path', {'email': email, 'password': password}, auth: false);
    final t = r['token'];
    if (t is! String || t.isEmpty) throw ApiException(500, 'Server returned no token');
    _token = t;
    await _store.write(t);
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
      });

  Future<Map<String, dynamic>> scanApps(List<Map<String, dynamic>> apps) => _post('/scan/apps', {'apps': apps});

  Future<Map<String, dynamic>> heartbeat(Map<String, dynamic> device) => _post('/devices/heartbeat', device);

  Future<void> report(String type, String severity, String title, {String? detail, String? sourceApp}) =>
      _post('/threats', {'type': type, 'severity': severity, 'title': title, 'detail': detail, 'sourceApp': sourceApp});

  Future<List<dynamic>> threats({String? status}) async {
    final r = await _get('/threats${status == null ? '' : '?status=$status'}');
    return r as List<dynamic>;
  }

  Future<void> resolve(int id) => _post('/threats/$id/resolve', {});

  /// Carrier answer patterns (backend 3.2). Pass the last [version] to get null back when nothing changed (HTTP 304).
  Future<CarrierConfig?> carrierConfig({String? version}) async {
    final headers = _headers(true);
    if (version != null) headers['If-None-Match'] = '"$version"';
    final res = await _http.get(Uri.parse('$_base/config/carriers'), headers: headers).timeout(_timeout);
    if (res.statusCode == 304) return null;
    final d = _decode(res);
    return CarrierConfig.fromJson(Map<String, dynamic>.from(d as Map));
  }

  // ---- http helpers ----
  Map<String, String> _headers(bool auth) => {
        'Content-Type': 'application/json',
        if (auth && _token != null) 'Authorization': 'Bearer $_token',
      };

  Future<dynamic> _get(String path) async {
    final res = await _http.get(Uri.parse('$_base$path'), headers: _headers(true)).timeout(_timeout);
    return _decode(res);
  }

  Future<Map<String, dynamic>> _post(String path, Map<String, dynamic> body, {bool auth = true}) async {
    final res = await _http
        .post(Uri.parse('$_base$path'), headers: _headers(auth), body: jsonEncode(body))
        .timeout(_timeout);
    final d = _decode(res);
    return d is Map<String, dynamic> ? d : <String, dynamic>{};
  }

  dynamic _decode(http.Response res) {
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return res.body.isEmpty ? <String, dynamic>{} : jsonDecode(res.body);
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
