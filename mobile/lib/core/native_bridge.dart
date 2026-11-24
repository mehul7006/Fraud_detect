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
  static Future<bool> hasPhonePermission() async => (await _ch.invokeMethod<bool>('hasPhonePermission')) ?? false;

  /// Android 13+ blocks all notifications until the user allows them. Without this every alert is silent.
  static Future<bool> hasNotificationPermission() async => (await _ch.invokeMethod<bool>('hasNotificationPermission')) ?? false;
  static Future<void> requestNotificationPermission() => _ch.invokeMethod('requestNotificationPermission');

  /// Returns carrier text, or NO_PERMISSION / UNSUPPORTED / ERROR:n
  static Future<String> ussd(String code) async => (await _ch.invokeMethod<String>('checkCallForwarding', {'code': code})) ?? 'ERROR';
  /// Android's "unconditional forwarding is on" flag (status-bar icon); null = unknown. See CallForwardIndicator.kt.
  static Future<bool?> forwardingIndicator() => _ch.invokeMethod<bool>('forwardingIndicator');
  /// 7.11: lets the notification listener check links while the app is closed (see BackgroundScanner.kt).
  /// [token]/[refresh] null = keep the session already stored natively, only update the settings.
  static Future<void> setBackgroundSession(String apiBase, String? token, String? refresh, List<String> disabled,
          List<String> trusted) =>
      _ch.invokeMethod('setBackgroundSession',
          {'apiBase': apiBase, 'token': token, 'refresh': refresh, 'disabled': disabled, 'trusted': trusted});
  static Future<void> clearBackgroundSession() => _ch.invokeMethod('clearBackgroundSession');
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

  /// Text the user shared into FraudShield from another app (Share -> FraudShield), or null. Read once.
  static Future<String?> takeSharedText() async => await _ch.invokeMethod<String>('takeSharedText');

  static Future<List<Map<String, dynamic>>> scanNewFiles() async {
    final l = await _ch.invokeMethod<List<dynamic>>('scanNewFiles') ?? [];
    return l.map((e) => Map<String, dynamic>.from(e as Map)).toList();
  }

  static Future<bool> deleteFile(String path) async => (await _ch.invokeMethod<bool>('deleteFile', {'path': path})) ?? false;
  /// 6.6 Usage Access (opt-in) lets FraudShield count apps that open themselves.
  static Future<bool> hasUsageAccess() async => (await _ch.invokeMethod<bool>('hasUsageAccess')) ?? false;
  static Future<void> openUsageAccessSettings() => _ch.invokeMethod('openUsageAccessSettings');

  // ---- Phase 12: on-device VPN (fake-website protection in every app)
  /// Shows Android's VPN prompt the first time; true when protection is starting.
  static Future<bool> vpnStart() async => (await _ch.invokeMethod<bool>('vpnStart')) ?? false;
  static Future<void> vpnStop() => _ch.invokeMethod('vpnStop');
  /// running, state (ON/OFF/OTHER_VPN/PRIVATE_DNS/REVOKED/NO_CONSENT/NOT_ALLOWED), privateDns, counters, options.
  static Future<Map<String, dynamic>> vpnStatus() async =>
      Map<String, dynamic>.from((await _ch.invokeMethod<Map>('vpnStatus')) ?? const {});
  static Future<void> vpnSetOptions({bool? localOnly, bool? blockDoh, String? upstream}) => _ch.invokeMethod('vpnSetOptions',
      {'localOnly': localOnly, 'blockDoh': blockDoh, 'upstream': upstream}..removeWhere((_, v) => v == null));
  static Future<List<Map<String, dynamic>>> vpnTopApps() async =>
      ((await _ch.invokeMethod<List>('vpnTopApps')) ?? const []).map((e) => Map<String, dynamic>.from(e as Map)).toList();
  static Future<bool> vpnSyncBlocklist() async => (await _ch.invokeMethod<bool>('vpnSyncBlocklist')) ?? false;
  static Future<void> openVpnSettings() => _ch.invokeMethod('openVpnSettings');

  // ---- Phase 18: ad / tracker / malware blocking inside the VPN (settings + stats stay on the phone)
  static Future<Map<String, dynamic>> _blockMap(String m, [Map<String, dynamic>? a]) async =>
      Map<String, dynamic>.from((await _ch.invokeMethod<Map>(m, a)) ?? const {});
  static Future<Map<String, dynamic>> blockSettings() => _blockMap('blockSettings');
  static Future<Map<String, dynamic>> blockSetCategory(String category, bool on) => _blockMap('blockSetCategory', {'category': category, 'on': on});
  static Future<Map<String, dynamic>> blockPause(int minutes) => _blockMap('blockPause', {'minutes': minutes});
  static Future<Map<String, dynamic>> blockAllowApp(String package, bool allow) => _blockMap('blockAllowApp', {'package': package, 'allow': allow});
  static Future<Map<String, dynamic>> blockAllowSite(String domain, bool allow) => _blockMap('blockAllowSite', {'domain': domain, 'allow': allow});
  static Future<Map<String, dynamic>> blockAppBroke(String package) => _blockMap('blockAppBroke', {'package': package});
  static Future<Map<String, dynamic>> blockStats(int days) => _blockMap('blockStats', {'days': days});
  static Future<bool> blockSync() async => (await _ch.invokeMethod<bool>('blockSync')) ?? false;
  static Future<void> openPrivateDnsSettings() => _ch.invokeMethod('openPrivateDnsSettings');
  static Future<void> openAutostartSettings() => _ch.invokeMethod('openAutostartSettings');

  // ---- Phase 14: on-device scam-message model (text never leaves the phone)
  static Future<Map<String, dynamic>> classifyMessage(String text) async =>
      Map<String, dynamic>.from((await _ch.invokeMethod<Map>('classifyMessage', {'text': text})) ?? const {});

  // ---- Phase 13 app protection
  static Future<List<Map<String, dynamic>>> appTrackers(String pkg) async =>
      ((await _ch.invokeMethod<List>('appTrackers', {'package': pkg})) ?? const []).map((e) => Map<String, dynamic>.from(e as Map)).toList();
  /// Last 7 days: {ads, trackers, malware, total, codes, domains:[{domain,count,category}]} - computed on the phone.
  static Future<Map<String, dynamic>> appNetwork(String pkg) async =>
      Map<String, dynamic>.from((await _ch.invokeMethod<Map>('appNetwork', {'package': pkg})) ?? const {});
  static Future<void> appFirewallSet(String pkg, bool block) => _ch.invokeMethod('appFirewallSet', {'package': pkg, 'block': block});
  static Future<List<String>> appFirewallList() async => ((await _ch.invokeMethod<List>('appFirewallList')) ?? const []).cast<String>();
  static Future<void> appIgnore(String pkg, {bool ignore = true}) => _ch.invokeMethod('appIgnore', {'package': pkg, 'ignore': ignore});
  static Future<void> openAppPermissions(String pkg) => _ch.invokeMethod('openAppPermissions', {'package': pkg});
  static Future<bool> isInstalled(String pkg) async => (await _ch.invokeMethod<bool>('isInstalled', {'package': pkg})) ?? false;

  /// 9.4: opens an https page (privacy policy) in the browser. Only https is allowed.
  static Future<bool> openUrl(String url) async => (await _ch.invokeMethod<bool>('openUrl', {'url': url})) ?? false;
  /// 7.4: opens the user's SMS app with [number] and [body] filled in; the user taps Send (no SMS permission).
  static Future<bool> composeSms(String number, String body) async =>
      (await _ch.invokeMethod<bool>('composeSms', {'number': number, 'body': body})) ?? false;
  /// 7.10: Android 14+ only grants full-screen alerts to some apps; true on older Android.
  // ---- all permissions in one place (PermissionsChannel.kt)
  static Future<Map<String, Map<String, dynamic>>> permStatus() async {
    final r = await _ch.invokeMethod<Map>('permStatus') ?? const {};
    return {for (final e in r.entries) e.key as String: Map<String, dynamic>.from(e.value as Map)};
  }
  static Future<String?> permOpen(String id) => _ch.invokeMethod<String>('permOpen', {'id': id});

  static Future<bool> canUseFullScreenAlerts() async => (await _ch.invokeMethod<bool>('canUseFullScreenAlerts')) ?? true;
  static Future<void> openFullScreenAlertSettings() => _ch.invokeMethod('openFullScreenAlertSettings');
  static Future<void> uninstallApp(String pkg) => _ch.invokeMethod('uninstallApp', {'package': pkg});
  static Future<bool> openApp(String pkg) async => (await _ch.invokeMethod<bool>('openApp', {'package': pkg})) ?? false;
  static Future<void> showAlert(String title, String body, {int id = 1}) =>
      _ch.invokeMethod('showAlert', {'title': title, 'body': body, 'id': id});
}
