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

  bool notifAccess = false, filesAccess = false, phone = false, notifPermission = true;
  List<Map<String, dynamic>> apps = [];
  List<Map<String, String>> pendingEvents = [];
  List<Map<String, dynamic>> newFiles = [];
  Map<String, dynamic> deviceInfo = {
    'deviceId': 'dev1', 'model': 'Test', 'osVersion': 'Android 13', 'simHash': 's', 'fingerprint': 'f',
    'rooted': false, 'emulator': true, 'playProtectOn': true, 'remoteAppsInstalled': <String>[],
  };

  /// Carrier answer for a USSD code. Return 'NO_PERMISSION' / 'UNSUPPORTED' / 'ERROR:n' to simulate failures.
  String Function(String code) ussd = (_) => 'Call forwarding is not active';
  Object? Function()? ussdThrows;

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
        case 'takePendingEvents':
          final e = pendingEvents;
          pendingEvents = [];
          return e;
        case 'scanNewFiles':
          return newFiles;
        case 'auditApps':
          return apps;
        case 'checkCallForwarding':
          if (ussdThrows != null) throw PlatformException(code: 'NATIVE_ERROR', message: 'boom');
          return ussd((call.arguments as Map)['code'] as String);
        case 'disableCallForwarding':
          onDisableForwarding?.call();
          return 'OK';
        default:
          return true; // openXSettings, requestXPermission, dialCode, showAlert, uninstallApp, openApp, deleteFile
      }
    });
  }

  void remove() =>
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(channel, null);
}
