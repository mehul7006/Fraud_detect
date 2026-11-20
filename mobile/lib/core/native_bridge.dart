import 'package:flutter/services.dart';

/// Thin wrapper over the Kotlin MethodChannel (see android-native/kotlin/MainActivity.kt).
class NativeBridge {
  static const _ch = MethodChannel('com.fraudshield.app/native');

  static Future<Map<String, dynamic>> deviceInfo() async =>
      Map<String, dynamic>.from(await _ch.invokeMethod('getDeviceInfo'));

  static Future<bool> hasNotificationAccess() async => (await _ch.invokeMethod<bool>('hasNotificationAccess')) ?? false;
  static Future<void> openNotificationAccessSettings() => _ch.invokeMethod('openNotificationAccessSettings');
  static Future<bool> hasAllFilesAccess() async => (await _ch.invokeMethod<bool>('hasAllFilesAccess')) ?? false;
  static Future<void> openAllFilesAccessSettings() => _ch.invokeMethod('openAllFilesAccessSettings');
  static Future<void> requestPhonePermission() => _ch.invokeMethod('requestPhonePermission');

  /// Returns carrier text, or NO_PERMISSION / UNSUPPORTED / ERROR:n
  static Future<String> ussd(String code) async => (await _ch.invokeMethod<String>('checkCallForwarding', {'code': code})) ?? 'ERROR';
  static Future<String> disableCallForwarding() async => (await _ch.invokeMethod<String>('disableCallForwarding')) ?? 'ERROR';
  static Future<void> dial(String code) => _ch.invokeMethod('dialCode', {'code': code});

  static Future<List<Map<String, dynamic>>> auditApps() async {
    final l = await _ch.invokeMethod<List<dynamic>>('auditApps') ?? [];
    return l.map((e) => Map<String, dynamic>.from(e as Map)).toList();
  }

  static Future<List<Map<String, String>>> takePendingEvents() async {
    final l = await _ch.invokeMethod<List<dynamic>>('takePendingEvents') ?? [];
    return l.map((e) => Map<String, String>.from(e as Map)).toList();
  }

  static Future<List<Map<String, dynamic>>> scanNewFiles() async {
    final l = await _ch.invokeMethod<List<dynamic>>('scanNewFiles') ?? [];
    return l.map((e) => Map<String, dynamic>.from(e as Map)).toList();
  }

  static Future<bool> deleteFile(String path) async => (await _ch.invokeMethod<bool>('deleteFile', {'path': path})) ?? false;
  static Future<void> uninstallApp(String pkg) => _ch.invokeMethod('uninstallApp', {'package': pkg});
  static Future<bool> openApp(String pkg) async => (await _ch.invokeMethod<bool>('openApp', {'package': pkg})) ?? false;
  static Future<void> showAlert(String title, String body, {int id = 1}) =>
      _ch.invokeMethod('showAlert', {'title': title, 'body': body, 'id': id});
}
