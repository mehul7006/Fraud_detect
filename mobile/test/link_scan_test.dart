import 'dart:io';
import 'package:flutter_test/flutter_test.dart';
import 'package:fraudshield/core/api_client.dart';
import 'package:fraudshield/core/protection_engine.dart';
import 'package:fraudshield/core/settings_store.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'helpers.dart';

/// Roadmap 4.7 (scan queue + verdicts), 4.8 (share), 4.9 (toggles, trusted list), 4.10 (offline queue).
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late FakeNative native;
  late List<String> scanned;
  late Map<String, Map<String, dynamic>> verdicts;
  var offline = false;
  var inFlight = 0, maxInFlight = 0;
  var delay = Duration.zero;

  ApiClient api() => apiWith((req) async {
        if (req.url.path.endsWith('/scan/url')) {
          if (offline) throw const SocketException('offline');
          final url = RegExp(r'"url":"([^"]+)"').firstMatch(req.body)!.group(1)!;
          scanned.add(url);
          inFlight++;
          if (inFlight > maxInFlight) maxInFlight = inFlight;
          if (delay > Duration.zero) await Future<void>.delayed(delay);
          inFlight--;
          return jsonResponse(200, verdicts[url] ?? {'verdict': 'SAFE', 'score': 0, 'reasons': []});
        }
        return jsonResponse(204, {});
      });

  ProtectionEngine engine() => ProtectionEngine(api: api(), settings: SettingsStore());

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    native = FakeNative()..install();
    scanned = [];
    verdicts = {};
    offline = false;
    inFlight = 0;
    maxInFlight = 0;
    delay = Duration.zero;
  });
  tearDown(() => native.remove());

  Future<List<Alert>> collect(ProtectionEngine e, Future<void> Function() body) async {
    final got = <Alert>[];
    final sub = e.alerts.stream.listen(got.add);
    await body();
    await Future<void>.delayed(Duration.zero);
    await sub.cancel();
    return got;
  }

  group('4.7 verdicts', () {
    test('SAFE gives no alert', () async {
      final e = engine();
      expect(await collect(e, () => e.handleLink('http://ok.test', 'wa')), isEmpty);
    });
    test('SUSPICIOUS gives a MEDIUM alert', () async {
      verdicts['http://meh.test'] = {'verdict': 'SUSPICIOUS', 'score': 50, 'reasons': ['Not using HTTPS']};
      final e = engine();
      final a = await collect(e, () => e.handleLink('http://meh.test', 'wa'));
      expect(a.single.severity, 'MEDIUM');
    });
    test('MALICIOUS gives a CRITICAL alert and a native notification', () async {
      verdicts['http://bad.test'] = {'verdict': 'MALICIOUS', 'score': 100, 'reasons': ['Domain is on blocklist']};
      final e = engine();
      final a = await collect(e, () => e.handleLink('http://bad.test', 'wa'));
      expect(a.single.severity, 'CRITICAL');
      expect(native.count('showAlert'), 1);
    });
    test('same link twice gives one API call', () async {
      final e = engine();
      await e.handleLink('http://dup.test', 'wa');
      await e.handleLink('http://dup.test', 'wa');
      expect(scanned, ['http://dup.test']);
    });
    test('at most 3 scans run at once', () async {
      delay = const Duration(milliseconds: 20);
      final e = engine();
      await Future.wait([for (var i = 0; i < 8; i++) e.handleLink('http://l$i.test', 'wa')]);
      expect(scanned.length, 8);
      expect(maxInFlight, lessThanOrEqualTo(3));
    });
    test('one failing event does not drop the others in runFast', () async {
      verdicts['http://bad2.test'] = {'verdict': 'MALICIOUS', 'score': 100, 'reasons': ['x']};
      native.pendingEvents = [
        {'kind': 'LINK', 'url': 'http://boom.test', 'pkg': 'wa'},
        {'kind': 'LOGIN_CODE'}, // missing "app" -> throws inside the handler
        {'kind': 'LINK', 'url': 'http://bad2.test', 'pkg': 'wa'},
      ];
      final e = engine();
      final a = await collect(e, e.runFast);
      expect(a.where((x) => x.severity == 'CRITICAL'), hasLength(1));
    });
  });

  group('4.8 share', () {
    test('shared link is scanned and a SAFE answer is still shown', () async {
      native.sharedText = 'Look at https://ok.test/page';
      final e = engine();
      final a = await collect(e, e.runFast);
      expect(scanned, ['https://ok.test/page']);
      expect(a.single.title, 'No known danger found');
    });
    test('shared dangerous link shows CRITICAL', () async {
      verdicts['http://bad3.test'] = {'verdict': 'MALICIOUS', 'score': 100, 'reasons': ['x']};
      final e = engine();
      final a = await collect(e, () => e.scanShared('http://bad3.test'));
      expect(a.single.severity, 'CRITICAL');
    });
    test('text without a link says so', () async {
      final e = engine();
      final a = await collect(e, () => e.scanShared('hello there'));
      expect(a.single.title, 'No link found');
      expect(scanned, isEmpty);
    });
  });

  group('4.9 toggles and trusted domains', () {
    test('disabled app: links ignored', () async {
      final e = engine();
      await e.settings.setPackageMonitored('com.spam.app', false);
      await e.handleLink('http://x.test', 'com.spam.app');
      expect(scanned, isEmpty);
      await e.handleLink('http://x.test', 'com.other');
      expect(scanned, hasLength(1));
    });
    test('trusted domain and its sub-domains are not scanned', () async {
      final e = engine();
      await e.settings.addTrustedDomain('https://www.Example.com/x');
      await e.handleLink('http://shop.example.com/a', 'wa');
      await e.handleLink('http://example.com', 'wa');
      await e.handleLink('http://notexample.com', 'wa');
      expect(scanned, ['http://notexample.com']);
    });
    test('settings survive a new store instance', () async {
      await SettingsStore().addTrustedDomain('keep.me');
      await SettingsStore().setPackageMonitored('p', false);
      expect(await SettingsStore().trustedDomains(), ['keep.me']);
      expect(await SettingsStore().disabledPackages(), {'p'});
    });
    test('normaliseDomain', () {
      expect(SettingsStore.normaliseDomain('HTTPS://www.A-b.com:8080/x'), 'a-b.com');
      expect(SettingsStore.normaliseDomain('nonsense'), isNull);
    });
  });

  group('4.10 offline queue', () {
    test('link captured offline is scanned when the network is back', () async {
      verdicts['http://late.test'] = {'verdict': 'MALICIOUS', 'score': 100, 'reasons': ['x']};
      offline = true;
      native.pendingEvents = [
        {'kind': 'LINK', 'url': 'http://late.test', 'pkg': 'wa'},
      ];
      final e = engine();
      var a = await collect(e, e.runFast);
      expect(a, isEmpty);
      expect(await e.settings.queuedLinks(), hasLength(1));
      offline = false;
      a = await collect(e, e.runFast);
      expect(a.single.severity, 'CRITICAL');
      expect(await e.settings.queuedLinks(), isEmpty);
    });
    test('queue keeps at most 100 links and ignores duplicates', () async {
      final s = SettingsStore();
      for (var i = 0; i < 120; i++) {
        await s.enqueueLink('http://q$i.test', null);
      }
      await s.enqueueLink('http://q119.test', null);
      final q = await s.queuedLinks();
      expect(q.length, 100);
      expect(q.first['url'], 'http://q20.test');
    });
    test('link disabled while queued is dropped, not stuck', () async {
      final e = engine();
      await e.settings.enqueueLink('http://gone.test', 'com.x');
      await e.settings.setPackageMonitored('com.x', false);
      await e.flushOfflineQueue();
      expect(await e.settings.queuedLinks(), isEmpty);
    });
  });
}
