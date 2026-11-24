import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fraudshield/core/leftovers.dart';
import 'package:fraudshield/l10n/app_localizations.dart';
import 'package:fraudshield/screens/leftover_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// 17.3 review screen (safe defaults, EN/HI), 17.4 nothing removed without the confirm, 17.5/17.6 overview,
/// 17.7 settings check shown, dashboard tile + notification tap.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const ch = MethodChannel('com.fraudshield.app/native');
  late List<MethodCall> calls;
  late bool access;
  String? openPkg;

  const root = '/storage/emulated/0';
  final pkgItems = [
    {'path': '$root/Download/telegram.apk', 'rel': 'Download/telegram.apk', 'isDir': false, 'bytes': 80 << 20, 'files': 1, 'group': 'INSTALLER', 'ticked': true},
    {'path': '$root/Telegram/.cache', 'rel': 'Telegram/.cache', 'isDir': true, 'bytes': 30 << 20, 'files': 120, 'group': 'CACHE', 'ticked': true},
    {'path': '$root/Pictures/Telegram', 'rel': 'Pictures/Telegram', 'isDir': true, 'bytes': 200 << 20, 'files': 340, 'group': 'MEDIA', 'ticked': false},
    {'path': '$root/Documents/Telegram/bill.pdf', 'rel': 'Documents/Telegram/bill.pdf', 'isDir': false, 'bytes': 1 << 20, 'files': 1, 'group': 'DOCUMENT', 'ticked': false},
  ];

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    calls = [];
    access = true;
    openPkg = null;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(ch, (c) async {
      calls.add(c);
      switch (c.method) {
        case 'hasAllFilesAccess':
          return access;
        case 'leftoverForPackage':
          return {'package': 'org.telegram.messenger', 'label': 'Telegram', 'items': pkgItems, 'settings': [
            {'kind': 'ACCESSIBILITY', 'state': 'CLEAR', 'detail': ''},
            {'kind': 'NOTIFICATION_ACCESS', 'state': 'LEFTOVER', 'detail': ''},
            {'kind': 'ALWAYS_ON_VPN', 'state': 'UNKNOWN', 'detail': ''},
          ]};
        case 'leftoverPending':
          return [{'package': 'org.telegram.messenger', 'label': 'Telegram', 'at': 1, 'files': 462, 'bytes': 311 << 20}];
        case 'leftoverScan':
          return {'indexed': 10, 'orphans': [
            {'path': '$root/SHAREit', 'rel': 'SHAREit', 'isDir': true, 'bytes': 5 << 20, 'files': 9, 'group': 'CACHE', 'ticked': true, 'note': 'SHAREit'},
          ], 'apks': [
            {'path': '$root/Download/wa.apk', 'rel': 'Download/wa.apk', 'bytes': 50 << 20, 'package': 'com.whatsapp', 'label': 'WhatsApp', 'version': '2.24', 'status': 'SAME_INSTALLED', 'ticked': true},
            {'path': '$root/Download/game.apk', 'rel': 'Download/game.apk', 'bytes': 90 << 20, 'package': 'com.game', 'label': 'Game', 'version': '1.0', 'status': 'NOT_INSTALLED', 'ticked': false},
            {'path': '$root/Download/kyc.apk', 'rel': 'Download/kyc.apk', 'bytes': 3 << 20, 'package': 'com.fake.kyc', 'label': 'SBI KYC', 'version': '1.0', 'status': 'FLAGGED', 'ticked': true},
          ]};
        case 'leftoverDelete':
          return {'trashed': 0, 'deleted': 2, 'refused': {}, 'cancelled': false};
        case 'leftoverTakeOpen':
          final p = openPkg;
          openPkg = null;
          return p;
        default:
          return true;
      }
    });
  });
  tearDown(() => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(ch, null));

  Future<void> pump(WidgetTester t, Widget w, {Locale? locale}) async {
    t.view.physicalSize = const Size(1080, 6000);
    t.view.devicePixelRatio = 2.5;
    addTearDown(t.view.reset);
    await t.pumpWidget(MaterialApp(
      locale: locale,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: w,
    ));
    await t.pumpAndSettle();
  }

  bool ticked(WidgetTester t, String path) => t.widget<CheckboxListTile>(find.byKey(Key('left-item-$path'))).value == true;
  Iterable<MethodCall> deletes() => calls.where((c) => c.method == 'leftoverDelete');

  testWidgets('safe defaults: installers + cache ticked, photos and documents not; settings check shown', (t) async {
    await pump(t, const LeftoverScreen(package: 'org.telegram.messenger'));
    expect(find.text('Leftovers of Telegram'), findsOneWidget);
    expect(ticked(t, '$root/Download/telegram.apk'), isTrue);
    expect(ticked(t, '$root/Telegram/.cache'), isTrue);
    expect(ticked(t, '$root/Pictures/Telegram'), isFalse, reason: 'photos are never pre-ticked');
    expect(ticked(t, '$root/Documents/Telegram/bill.pdf'), isFalse, reason: 'documents are never pre-ticked');
    expect(find.text('Photos, videos and audio'), findsOneWidget);
    expect(find.textContaining('Folder, 340 files, 200.0 MB'), findsOneWidget);
    expect(find.byKey(const Key('left-settings')), findsOneWidget);
    expect(find.text('Still points to the removed app - open Settings and switch it off'), findsOneWidget);
    expect(find.text('Android does not let apps check this'), findsOneWidget, reason: 'unknown is never shown as clear');
    expect(find.text('Remove selected (2, 110.0 MB)'), findsOneWidget);
    expect(deletes(), isEmpty, reason: 'opening the screen removes nothing');
  });

  testWidgets('nothing is removed without the confirm; then exactly the ticked items', (t) async {
    await pump(t, const LeftoverScreen(package: 'org.telegram.messenger'));
    await t.tap(find.byKey(const Key('left-item-$root/Telegram/.cache'))); // untick the cache
    await t.pumpAndSettle();
    await t.tap(find.byKey(const Key('left-remove')));
    await t.pumpAndSettle();
    expect(find.textContaining('restore them for 30 days'), findsOneWidget);
    await t.tap(find.byKey(const Key('left-confirm-no')));
    await t.pumpAndSettle();
    expect(deletes(), isEmpty, reason: 'Cancel = nothing removed');
    await t.tap(find.byKey(const Key('left-remove')));
    await t.pumpAndSettle();
    await t.tap(find.byKey(const Key('left-confirm-yes')));
    await t.pumpAndSettle();
    final d = deletes().single.arguments as Map;
    expect(d['paths'], ['$root/Download/telegram.apk']);
    expect(d['package'], 'org.telegram.messenger');
    expect(find.text('Moved 0 to trash, deleted 2.'), findsOneWidget);
  });

  testWidgets('overview: removed apps, orphan folders, old installers classified; flagged list passed', (t) async {
    await FlaggedFiles.add('$root/Download/kyc.apk');
    await pump(t, const LeftoverScreen());
    expect(find.byKey(const Key('left-removed-org.telegram.messenger')), findsOneWidget);
    expect(find.text('462 files, 311.0 MB'), findsOneWidget);
    expect(find.text('Folders of apps that are no longer installed'), findsOneWidget);
    expect(find.textContaining('From SHAREit'), findsOneWidget);
    expect(find.textContaining('The same version is installed - not needed'), findsOneWidget);
    expect(find.textContaining('This app is not installed'), findsOneWidget);
    expect(find.textContaining('Flagged by file protection'), findsOneWidget);
    expect(ticked(t, '$root/Download/game.apk'), isFalse);
    expect(ticked(t, '$root/Download/kyc.apk'), isTrue);
    final scan = calls.firstWhere((c) => c.method == 'leftoverScan').arguments as Map;
    expect(scan['flagged'], ['$root/Download/kyc.apk']);
  });

  testWidgets('without file access: asks for it and scans nothing', (t) async {
    access = false;
    await pump(t, const LeftoverScreen());
    expect(find.byKey(const Key('left-need-access')), findsOneWidget);
    expect(calls.where((c) => c.method == 'leftoverScan'), isEmpty);
  });

  testWidgets('Hindi', (t) async {
    await pump(t, const LeftoverScreen(package: 'org.telegram.messenger'), locale: const Locale('hi'));
    expect(find.text('Telegram की बची हुई फ़ाइलें'), findsOneWidget);
    expect(find.text('फ़ोटो, वीडियो और ऑडियो'), findsOneWidget);
    expect(find.text('सेटिंग्स में कुछ बचा तो नहीं?'), findsOneWidget);
  });

  testWidgets('dashboard tile shows the count; a tapped notification opens that app', (t) async {
    await pump(t, const Scaffold(body: LeftoverTile()));
    expect(find.text('1 removed app(s) left files - tap to review'), findsOneWidget);
    openPkg = 'org.telegram.messenger';
    t.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
    t.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await t.pumpAndSettle();
    expect(find.text('Leftovers of Telegram'), findsOneWidget);
  });
}
