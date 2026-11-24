import 'dart:convert';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fraudshield/core/api_client.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

http.Response jsonResponse(int status, Object body) =>
    http.Response(jsonEncode(body), status, headers: {'content-type': 'application/json'});

ApiClient apiWith(MockClientHandler h, {TokenStore? store}) =>
    ApiClient(client: MockClient(h), store: store ?? MemoryTokenStore(), baseUrl: 'http://test/api/v1');

/// Stands in for the Kotlin side (MainActivity). Records every call so tests can assert what the app asked Android to do.
class FakeNative {
  static const channel = MethodChannel('com.fraudshield.app/native');

  bool notifAccess = false, filesAccess = false, phone = false, notifPermission = true, usageAccess = true, fullScreen = true;
  /// 12.x VPN: what vpnStatus returns; vpnStart flips running (as Android does after the prompt).
  Map<String, dynamic> vpn = {'running': false, 'prepared': false, 'state': 'OFF', 'blockDoh': true, 'localOnly': false,
    'upstream': 'system', 'blocklistSize': 0, 'queries': 0, 'blocked': 0, 'recentBlocks': '[]'};
  List<Map<String, dynamic>> topApps = [];
  /// 18.x blocking: what blockSettings returns; the set/allow calls change it like AdBlocker.kt does.
  Map<String, dynamic> block = {'flavour': 'play', 'adsAvailable': false, 'categories': ['trackers', 'malware'], 'pausedUntil': 0,
    'allowedApps': <String>[], 'allowedSites': <String>[], 'brokeReports': '[]', 'listVersion': 0, 'listSize': 0};
  Map<String, dynamic> blockStats = {'total': 0, 'byCategory': {}, 'byApp': [], 'topDomains': [], 'estimatedBytesSaved': 0};
  bool blockSyncOk = true;
  Map<String, dynamic> blockCall(String m, Map<String, dynamic> a) {
    List<String> l(String k) => List<String>.from(block[k] as List);
    switch (m) {
      case 'blockSetCategory':
        final c = l('categories')..remove(a['category']);
        if (a['on'] == true && (a['category'] != 'ads' || block['adsAvailable'] == true)) c.add(a['category'] as String);
        block = {...block, 'categories': c};
      case 'blockPause':
        final min = (a['minutes'] as int?) ?? 0;
        block = {...block, 'pausedUntil': min <= 0 ? 0 : DateTime.now().millisecondsSinceEpoch + min * 60000};
      case 'blockAllowApp' || 'blockAppBroke':
        final p = l('allowedApps')..remove(a['package']);
        if (m == 'blockAppBroke' || a['allow'] == true) p.add(a['package'] as String);
        block = {...block, 'allowedApps': p};
      case 'blockAllowSite':
        final s = l('allowedSites')..remove(a['domain']);
        if (a['allow'] == true) s.add(a['domain'] as String);
        block = {...block, 'allowedSites': s};
    }
    return block;
  }
  /// 14.x on-device model stand-in: text -> {scam, category, categoryProbability, ms}
  Map<String, dynamic> Function(String) messageModel = (_) => {'scam': 0.02, 'category': 'BANK_KYC_BLOCK', 'categoryProbability': 0.1, 'ms': 1.0};
  /// 16.x call guard (CallGuardBridge.kt stand-in)
  Map<String, dynamic> callGuard = {'supported': true, 'sdk': 35, 'roleHeld': false, 'mode': 2, 'allow': <String>[],
    'block': <String>[], 'scamListSize': 0, 'scamListVersion': 0, 'rulesVersion': '2026-11-24.1',
    'contactsPermission': false, 'callLogPermission': false, 'counts': <String, int>{}};
  List<Map<String, dynamic>> callLog = [];
  bool callOpenLog = false;
  /// canonical form like CallNumbers.canonical for the numbers tests use: 10 digits -> +91, '+' kept, else null
  static String? canonicalNumber(String s) {
    final d = s.replaceAll(RegExp(r'[\s\-.()]'), '');
    if (RegExp(r'^\+\d{8,15}$').hasMatch(d)) return d;
    if (RegExp(r'^\d{10}$').hasMatch(d)) return '+91$d';
    if (RegExp(r'^0\d{10}$').hasMatch(d)) return '+91${d.substring(1)}';
    if (RegExp(r'^91\d{10}$').hasMatch(d)) return '+$d';
    return null;
  }
  /// 13.x app detail
  List<Map<String, dynamic>> appTrackers = [];
  Map<String, dynamic> appNetwork = {};
  Set<String> firewall = {};
  Set<String> installed = {};
  List<Map<String, dynamic>> apps = [];
  List<Map<String, String>> pendingEvents = [];
  /// 15.x live scam guard
  Map<String, dynamic> guardStatus = {'enabled': false, 'otpGuard': true, 'phoneState': false, 'callLog': false,
    'contacts': false, 'usageAccess': false, 'notificationAccess': true};
  bool speakOk = true;
  String? sharedText;
  List<Map<String, dynamic>> newFiles = [];
  Map<String, dynamic> deviceInfo = {
    'deviceId': 'dev1', 'model': 'Test', 'osVersion': 'Android 13', 'simHash': 's', 'fingerprint': 'f',
    'rooted': false, 'emulator': true, 'playProtectOn': true, 'remoteAppsInstalled': <String>[],
  };

  /// Carrier answer for a USSD code. Return 'NO_PERMISSION' / 'UNSUPPORTED' / 'ERROR:n' to simulate failures.
  String Function(String code) ussd = (_) => 'Call forwarding is not active';
  Object? Function()? ussdThrows;

  /// Android's unconditional-forwarding flag (CallForwardIndicator.kt); null = unknown.
  bool? forwardingIndicator;

  /// Called when the app runs ##002#.
  void Function()? onDisableForwarding;

  final calls = <MethodCall>[];

  int count(String method) => calls.where((c) => c.method == method).length;
  List<String> ussdCodes() =>
      calls.where((c) => c.method == 'checkCallForwarding').map((c) => (c.arguments as Map)['code'] as String).toList();

  void install() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(channel, (call) async {
      calls.add(call);
      switch (call.method) {
        case 'hasNotificationAccess':
          return notifAccess;
        case 'hasAllFilesAccess':
          return filesAccess;
        case 'hasPhonePermission':
          return phone;
        case 'hasNotificationPermission':
          return notifPermission;
        case 'getDeviceInfo':
          return deviceInfo;
        case 'takeSharedText':
          final t = sharedText;
          sharedText = null;
          return t;
        case 'takePendingEvents':
          final e = pendingEvents;
          pendingEvents = [];
          return e;
        case 'scanNewFiles':
          return newFiles;
        case 'vpnStatus':
          return vpn;
        case 'vpnStart':
          vpn = {...vpn, 'running': true, 'prepared': true, 'state': 'ON'};
          return true;
        case 'vpnStop':
          vpn = {...vpn, 'running': false, 'state': 'OFF'};
          return true;
        case 'vpnSetOptions':
          final a = Map<String, dynamic>.from(call.arguments as Map);
          vpn = {...vpn, if (a.containsKey('localOnly')) 'localOnly': a['localOnly'], if (a.containsKey('blockDoh')) 'blockDoh': a['blockDoh'],
            if (a.containsKey('upstream')) 'upstream': a['upstream']};
          return true;
        case 'vpnTopApps':
          return topApps;
        case 'classifyMessage':
          return messageModel((call.arguments as Map)['text'] as String);
        case 'callGuardStatus':
          return callGuard;
        case 'callGuardRequestRole':
          callGuard = {...callGuard, 'roleHeld': true};
          return true;
        case 'callGuardSetMode':
          callGuard = {...callGuard, 'mode': (call.arguments as Map)['mode']};
          return true;
        case 'callGuardAllow':
        case 'callGuardNotSpam':
          final a = call.arguments as Map;
          final c = canonicalNumber(a['number'] as String);
          if (c == null) return null;
          final allow = {...(callGuard['allow'] as List).cast<String>()};
          if (a['allow'] == false) { allow.remove(c); } else { allow.add(c); }
          callGuard = {...callGuard, 'allow': allow.toList()};
          if (call.method == 'callGuardNotSpam') {
            callLog = [for (final r in callLog) r['number'] == c ? {...r, 'undone': true} : r];
          }
          return c;
        case 'callGuardBlock':
          return canonicalNumber((call.arguments as Map)['number'] as String);
        case 'callGuardLog':
          return callLog;
        case 'callGuardSync':
          return {'rules': true, 'numbers': true};
        case 'callGuardCheck':
          final n = (call.arguments as Map)['number'] as String;
          final c = canonicalNumber(n);
          final prot = c != null && (c.startsWith('+911600') || c.startsWith('+91140'));
          return {'canonical': c, 'protected': prot, 'rule': prot ? 'IN_1600_SERVICE' : null, 'label': null};
        case 'callGuardTakeOpenLog':
          final o = callOpenLog;
          callOpenLog = false;
          return o;
        case 'appTrackers':
          return appTrackers;
        case 'appNetwork':
          return appNetwork;
        case 'appFirewallList':
          return firewall.toList();
        case 'appFirewallSet':
          final m = call.arguments as Map;
          if (m['block'] == true) { firewall.add(m['package'] as String); } else { firewall.remove(m['package']); }
          return true;
        case 'canUseFullScreenAlerts':
          return fullScreen;
        case 'hasUsageAccess':
          return usageAccess;
        case 'isInstalled':
          return installed.contains((call.arguments as Map)['package']);
        case 'auditApps':
          return apps;
        case 'checkCallForwarding':
          if (ussdThrows != null) throw PlatformException(code: 'NATIVE_ERROR', message: 'boom');
          return ussd((call.arguments as Map)['code'] as String);
        case 'forwardingIndicator':
          return forwardingIndicator;
        case 'disableCallForwarding':
          onDisableForwarding?.call();
          return 'OK';
        case 'blockSettings' || 'blockSetCategory' || 'blockPause' || 'blockAllowApp' || 'blockAllowSite' || 'blockAppBroke':
          return blockCall(call.method, Map<String, dynamic>.from((call.arguments as Map?) ?? const {}));
        case 'blockStats':
          return blockStats;
        case 'scamGuardStatus': // 15.2
          return guardStatus;
        case 'scamGuardSet':
          guardStatus = {...guardStatus, 'enabled': (call.arguments as Map)['enabled']};
          return true;
        case 'scamGuardOtp':
          guardStatus = {...guardStatus, 'otpGuard': (call.arguments as Map)['enabled']};
          return true;
        case 'speak': // 15.3
          return speakOk;
        case 'scamGuardSimulate':
          return false;
        case 'blockSync':
          return blockSyncOk;
        default:
          return true; // openXSettings, requestXPermission, dialCode, showAlert, uninstallApp, openApp, deleteFile
      }
    });
  }

  void remove() =>
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(channel, null);
}
