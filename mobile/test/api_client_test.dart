import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:fraudshield/core/api_client.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

http.Response _json(int status, Object body) =>
    http.Response(jsonEncode(body), status, headers: {'content-type': 'application/json'});

ApiClient _client(MockClientHandler h, {TokenStore? store}) =>
    ApiClient(client: MockClient(h), store: store ?? MemoryTokenStore(), baseUrl: 'http://test/api/v1');

void main() {
  group('2.2 auth + token storage', () {
    test('login stores token and later requests send it as Bearer', () async {
      final store = MemoryTokenStore();
      final seen = <http.Request>[];
      final api = _client((r) async {
        seen.add(r);
        if (r.url.path.endsWith('/auth/login')) return _json(200, {'token': 'abc.def.ghi'});
        return _json(200, []);
      }, store: store);

      await api.login('a@b.com', 'password123');
      expect(await store.read(), 'abc.def.ghi');
      expect(api.hasToken, true);

      await api.threats();
      expect(seen.first.headers.containsKey('Authorization'), false, reason: 'login must not send a token');
      expect(seen.last.headers['Authorization'], 'Bearer abc.def.ghi');
      expect(seen.first.url.toString(), 'http://test/api/v1/auth/login');
    });

    test('session survives restart: loadToken reads the stored token', () async {
      final store = MemoryTokenStore()..write('saved-token');
      final api = _client((r) async => _json(200, []), store: store);
      expect(await api.loadToken(), true);
      expect(api.hasToken, true);
    });

    test('loadToken is false when nothing stored', () async {
      expect(await _client((r) async => _json(200, [])).loadToken(), false);
    });

    test('logout clears memory and storage, and stops sending the header', () async {
      final store = MemoryTokenStore();
      http.Request? last;
      final api = _client((r) async {
        last = r;
        return r.url.path.endsWith('/login') ? _json(200, {'token': 't'}) : _json(200, []);
      }, store: store);
      await api.login('a@b.com', 'password123');
      await api.logout();
      expect(await store.read(), isNull);
      await api.threats();
      expect(last!.headers.containsKey('Authorization'), false);
    });

    test('register posts email and password as JSON', () async {
      Map<String, dynamic>? body;
      final api = _client((r) async {
        body = jsonDecode(r.body) as Map<String, dynamic>;
        return _json(200, {'token': 't'});
      });
      await api.register('x@y.com', 'password123');
      expect(body, {'email': 'x@y.com', 'password': 'password123'});
    });

    test('missing token in login response is an error, nothing stored', () async {
      final store = MemoryTokenStore();
      final api = _client((r) async => _json(200, {}), store: store);
      await expectLater(api.login('a@b.com', 'password123'), throwsA(isA<ApiException>()));
      expect(await store.read(), isNull);
    });
  });

  group('8.9 https only in release', () {
    test('release refuses http, allows https', () {
      expect(() => checkBaseUrl('http://10.0.2.2:8080/api/v1', release: true), throwsStateError);
      expect(checkBaseUrl('https://api.fraudshield.app/api/v1', release: true), 'https://api.fraudshield.app/api/v1');
    });
    test('debug may use the local emulator backend over http', () {
      expect(checkBaseUrl('http://10.0.2.2:8080/api/v1', release: false), 'http://10.0.2.2:8080/api/v1');
    });
  });

  group('8.2 refresh tokens', () {
    ApiClient withServer(Map<String, int> hits, {bool refreshOk = true, TokenStore? store}) => _client((r) async {
          final p = r.url.path;
          hits[p] = (hits[p] ?? 0) + 1;
          if (p.endsWith('/auth/login')) return _json(200, {'token': 'old', 'refreshToken': 'r1', 'expiresIn': 900});
          if (p.endsWith('/auth/refresh')) {
            expect(jsonDecode(r.body), {'refreshToken': 'r1'});
            return refreshOk ? _json(200, {'token': 'new', 'refreshToken': 'r2', 'expiresIn': 900})
                : _json(401, {'status': 401, 'message': 'Refresh token already used'});
          }
          if (p.endsWith('/auth/logout')) return http.Response('', 204);
          // the 15-minute access token "ran out": only the renewed one works
          return r.headers['Authorization'] == 'Bearer new' ? _json(200, []) : _json(401, {'status': 401, 'message': 'expired'});
        }, store: store);

    test('an expired access token is renewed once, quietly, and the call succeeds', () async {
      final hits = <String, int>{};
      final store = MemoryTokenStore();
      var expired = 0;
      final api = withServer(hits, store: store)..onSessionExpired = () => expired++;
      await api.login('a@b.com', 'password123');
      expect(await api.threats(), isEmpty);
      expect(hits['/api/v1/auth/refresh'], 1);
      expect(expired, 0);
      expect(jsonDecode((await store.read())!), {'a': 'new', 'r': 'r2'}, reason: 'rotated pair saved');
    });

    test('several calls failing at once share ONE refresh (a second one would look like token theft)', () async {
      final hits = <String, int>{};
      final api = withServer(hits);
      await api.login('a@b.com', 'password123');
      await Future.wait([api.threats(), api.threats(), api.threats()]);
      expect(hits['/api/v1/auth/refresh'], 1);
    });

    test('refresh refused -> session expired, back to login', () async {
      final hits = <String, int>{};
      var expired = 0;
      final api = withServer(hits, refreshOk: false)..onSessionExpired = () => expired++;
      await api.login('a@b.com', 'password123');
      await expectLater(api.threats(), throwsA(isA<ApiException>()));
      expect(expired, 1);
      expect(api.hasToken, isFalse);
    });

    test('offline while renewing does NOT log the user out', () async {
      var expired = 0;
      final api = _client((r) async {
        if (r.url.path.endsWith('/auth/login')) return _json(200, {'token': 'old', 'refreshToken': 'r1'});
        if (r.url.path.endsWith('/auth/refresh')) throw http.ClientException('no network');
        return _json(401, {'status': 401});
      })..onSessionExpired = () => expired++;
      await api.login('a@b.com', 'password123');
      await expectLater(api.threats(), throwsA(isA<http.ClientException>()));
      expect(expired, 0);
      expect(api.hasToken, isTrue);
    });

    test('logout tells the server, then forgets both tokens', () async {
      final hits = <String, int>{};
      final store = MemoryTokenStore();
      final api = withServer(hits, store: store);
      await api.login('a@b.com', 'password123');
      await api.logout();
      expect(hits['/api/v1/auth/logout'], 1);
      expect(await store.read(), isNull);
    });

    test('a token saved by an older app version (plain string) still loads', () async {
      final api = _client((r) async => _json(200, []), store: MemoryTokenStore()..write('legacy.jwt'));
      expect(await api.loadToken(), isTrue);
      expect(api.token, 'legacy.jwt');
    });
  });

  group('error mapping (matches backend ErrorBody)', () {
    test('401 / 409 / 400 become ApiException with status and message', () async {
      for (final c in [
        (401, 'Invalid credentials'),
        (409, 'Email already used'),
        (400, 'email must not be blank'),
      ]) {
        final api = _client((r) async => _json(c.$1, {
              'timestamp': 't', 'status': c.$1, 'error': 'x', 'message': c.$2, 'path': '/p', 'requestId': 'req-123456'
            }));
        try {
          await api.login('a@b.com', 'password123');
          fail('should throw');
        } on ApiException catch (e) {
          expect(e.status, c.$1);
          expect(e.message, c.$2);
          expect(e.requestId, 'req-123456');
        }
      }
    });

    test('non-JSON error body (proxy/gateway) does not crash parsing', () async {
      final api = _client((r) async => http.Response('<html>Bad gateway</html>', 502, reasonPhrase: 'Bad Gateway'));
      try {
        await api.threats();
        fail('should throw');
      } on ApiException catch (e) {
        expect(e.status, 502);
        expect(e.message, 'Bad Gateway');
      }
    });
  });

  group('scanning calls', () {
    test('scanUrl posts url and sourceApp, returns verdict', () async {
      Map<String, dynamic>? body;
      final api = _client((r) async {
        body = jsonDecode(r.body) as Map<String, dynamic>;
        expect(r.url.path, '/api/v1/scan/url');
        return _json(200, {'verdict': 'MALICIOUS', 'score': 85, 'reasons': ['x']});
      });
      final res = await api.scanUrl('http://evil.xyz', 'com.whatsapp');
      expect(body, {'url': 'http://evil.xyz', 'sourceApp': 'com.whatsapp'});
      expect(res['verdict'], 'MALICIOUS');
    });

    test('scanFile sends only hash, name, size, source and header - never the path', () async {
      Map<String, dynamic>? body;
      final api = _client((r) async {
        body = jsonDecode(r.body) as Map<String, dynamic>;
        return _json(200, {'verdict': 'SAFE', 'score': 0, 'reasons': [], 'action': 'NONE'});
      });
      await api.scanFile({
        'path': '/storage/emulated/0/Download/secret.pdf',
        'sha256': 'a' * 64, 'fileName': 'secret.pdf', 'sizeBytes': 10, 'sourceApp': 'downloads', 'headerHex': '25504446'
      });
      expect(body!.containsKey('path'), false, reason: 'local path must not leave the phone');
      expect(body!.keys.toSet(), {'sha256', 'fileName', 'sizeBytes', 'sourceApp', 'headerHex', 'flags'});
      expect(body!['flags'], isEmpty);
    });

    test('scanFile sends only known flag names, at most 10 (5.6)', () async {
      Map<String, dynamic>? body;
      final api = _client((r) async {
        body = jsonDecode(r.body) as Map<String, dynamic>;
        return _json(200, {'verdict': 'SAFE', 'score': 0, 'reasons': [], 'action': 'NONE'});
      });
      await api.scanFile({
        'sha256': 'a' * 64, 'fileName': 'x.pdf', 'sizeBytes': 10, 'sourceApp': 'downloads', 'headerHex': '25504446',
        'flags': ['PDF_JS', 'PDF_LAUNCH', 'secret text from the file', 42, 'OFFICE_MACRO'],
      });
      expect(body!['flags'], ['PDF_JS', 'PDF_LAUNCH', 'OFFICE_MACRO']);
    });

    test('threats with status adds the query', () async {
      Uri? url;
      final api = _client((r) async {
        url = r.url;
        return _json(200, []);
      });
      await api.threats(status: 'OPEN');
      expect(url!.query, 'status=OPEN');
    });

    test('resolve posts to /threats/{id}/resolve and accepts empty 204', () async {
      http.Request? req;
      final api = _client((r) async {
        req = r;
        return http.Response('', 204);
      });
      await api.resolve(7);
      expect(req!.method, 'POST');
      expect(req!.url.path, '/api/v1/threats/7/resolve');
    });
  });
}
