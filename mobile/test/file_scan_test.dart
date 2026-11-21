import 'package:flutter_test/flutter_test.dart';
import 'package:fraudshield/core/api_client.dart';
import 'package:fraudshield/core/protection_engine.dart';
import 'package:fraudshield/core/settings_store.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'helpers.dart';

/// Roadmap 5.4 (new file reported), 5.6 (flags sent), 5.7 (delete needs confirmation).
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late FakeNative native;
  late List<String> bodies;
  var verdict = 'MALICIOUS';

  ApiClient api() => apiWith((req) async {
        if (req.url.path.endsWith('/scan/file')) {
          bodies.add(req.body);
          return jsonResponse(200, {'verdict': verdict, 'score': 100, 'reasons': ['PDF can launch a program'], 'action': 'DELETE'});
        }
        return jsonResponse(204, {});
      });

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    native = FakeNative()..install();
    native.filesAccess = true;
    bodies = [];
    verdict = 'MALICIOUS';
  });
  tearDown(() => native.remove());

  Map<String, dynamic> file() => {
        'path': '/storage/emulated/0/Download/bill.pdf', 'fileName': 'bill.pdf', 'sizeBytes': 9000,
        'sha256': 'a' * 64, 'headerHex': '25504446', 'sourceApp': 'downloads', 'flags': ['PDF_LAUNCH'],
      };

  Future<List<Alert>> run(ProtectionEngine e) async {
    final got = <Alert>[];
    final sub = e.alerts.stream.listen(got.add);
    await e.runFast();
    await Future<void>.delayed(Duration.zero);
    await sub.cancel();
    return got;
  }

  test('a new file is scanned, flags are sent and the path is not', () async {
    native.newFiles = [file()];
    final e = ProtectionEngine(api: api(), settings: SettingsStore());
    final a = await run(e);
    expect(bodies, hasLength(1));
    expect(bodies.single, contains('"flags":["PDF_LAUNCH"]'));
    expect(bodies.single, isNot(contains('/storage/')));
    expect(a.single.severity, 'CRITICAL');
    expect(a.single.actions.map((x) => x.label), ['Delete file']);
  });

  test('SAFE file gives no alert', () async {
    verdict = 'SAFE';
    native.newFiles = [file()];
    final e = ProtectionEngine(api: api(), settings: SettingsStore());
    expect(await run(e), isEmpty);
  });

  test('without All-files access nothing is scanned', () async {
    native.filesAccess = false;
    native.newFiles = [file()];
    final e = ProtectionEngine(api: api(), settings: SettingsStore());
    await run(e);
    expect(bodies, isEmpty);
    expect(native.count('scanNewFiles'), 0);
  });

  test('Delete file only asks; the file is removed after the second confirmation', () async {
    native.newFiles = [file()];
    final e = ProtectionEngine(api: api(), settings: SettingsStore());
    final first = await run(e);
    final confirm = <Alert>[];
    final sub = e.alerts.stream.listen(confirm.add);
    await first.single.actions.single.run();
    await Future<void>.delayed(Duration.zero);
    expect(native.count('deleteFile'), 0, reason: 'first tap must not delete');
    expect(confirm.single.title, 'Delete "bill.pdf"?');
    await confirm.single.actions.single.run();
    await Future<void>.delayed(Duration.zero);
    await sub.cancel();
    expect(native.count('deleteFile'), 1);
    expect(confirm.last.title, 'File deleted');
  });
}
