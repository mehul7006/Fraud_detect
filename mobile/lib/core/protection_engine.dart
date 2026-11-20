import 'dart:async';
import 'package:flutter/foundation.dart';
import 'api_client.dart';
import 'native_bridge.dart';

/// A threat the UI must show as a blocking popup.
class Alert {
  final String title;
  final String body;
  final String severity; // CRITICAL / HIGH / MEDIUM
  final List<AlertAction> actions;
  Alert(this.title, this.body, this.severity, this.actions);
}

class AlertAction {
  final String label;
  final Future<void> Function() run;
  AlertAction(this.label, this.run);
}

/// Orchestrates all checks. Runs while the app process is alive; the Kotlin listener keeps collecting
/// events in the background, so nothing is lost when the app is closed.
class ProtectionEngine {
  final api = ApiClient.instance;
  final alerts = StreamController<Alert>.broadcast();
  final status = ValueNotifier<Map<String, String>>({});
  Timer? _fast, _slow;

  void start() {
    runFast();
    runSlow();
    _fast = Timer.periodic(const Duration(seconds: 20), (_) => runFast());
    _slow = Timer.periodic(const Duration(hours: 1), (_) => runSlow());
  }

  void stop() {
    _fast?.cancel();
    _slow?.cancel();
  }

  void _set(String k, String v) => status.value = {...status.value, k: v};

  // ---------------- fast loop: links, OTP/new device events, files ----------------
  Future<void> runFast() async {
    try {
      for (final e in await NativeBridge.takePendingEvents()) {
        switch (e['kind']) {
          case 'LINK':
            await _handleLink(e['url']!, e['pkg']);
          case 'LOGIN_CODE':
            await _handleLoginCode(e['app']!);
          case 'NEW_DEVICE':
            await _handleNewDevice(e['app']!);
        }
      }
      if (await NativeBridge.hasAllFilesAccess()) {
        for (final f in await NativeBridge.scanNewFiles()) {
          await _handleFile(f);
        }
      }
    } catch (e) {
      debugPrint('fast loop error: $e');
    }
  }

  Future<void> _handleLink(String url, String? pkg) async {
    final r = await api.scanUrl(url, pkg);
    final verdict = r['verdict'] as String;
    if (verdict == 'SAFE') return;
    final reasons = (r['reasons'] as List).join(', ');
    final bad = verdict == 'MALICIOUS';
    await NativeBridge.showAlert(bad ? 'Dangerous link!' : 'Suspicious link', '$url\n$reasons', id: url.hashCode);
    alerts.add(Alert(bad ? 'DANGEROUS LINK - do not open' : 'Suspicious link', '$url\n\n$reasons',
        bad ? 'CRITICAL' : 'MEDIUM', []));
  }

  Future<void> _handleFile(Map<String, dynamic> f) async {
    final r = await api.scanFile(f);
    final verdict = r['verdict'] as String;
    if (verdict == 'SAFE') return;
    final reasons = (r['reasons'] as List).join(', ');
    final path = f['path'] as String;
    await NativeBridge.showAlert('Suspicious file received', '${f['fileName']}\n$reasons', id: path.hashCode);
    alerts.add(Alert(verdict == 'MALICIOUS' ? 'MALICIOUS FILE' : 'Suspicious file', '${f['fileName']}\n\n$reasons',
        verdict == 'MALICIOUS' ? 'CRITICAL' : 'MEDIUM', [
      AlertAction('Delete file', () async {
        await NativeBridge.deleteFile(path);
      }),
    ]));
  }

  Future<void> _handleLoginCode(String app) async {
    final name = app == 'telegram' ? 'Telegram' : 'WhatsApp';
    alerts.add(Alert('$name login code received',
        'A $name login code just arrived. If YOU did not just log in, someone is trying to take over your account. NEVER share this code.',
        'HIGH', [
      AlertAction('I did NOT request it', () async {
        await api.report(app == 'telegram' ? 'TELEGRAM_TAKEOVER' : 'WHATSAPP_TAKEOVER', 'CRITICAL',
            '$name takeover attempt', detail: 'Unrequested login code', sourceApp: app);
      }),
    ]));
  }

  Future<void> _handleNewDevice(String app) async {
    final name = app == 'telegram' ? 'Telegram' : 'WhatsApp';
    await NativeBridge.showAlert('$name: new device activity', 'Open FraudShield to secure your account', id: 77);
    await api.report(app == 'telegram' ? 'TELEGRAM_TAKEOVER' : 'WHATSAPP_TAKEOVER', 'CRITICAL',
        '$name new device / session notice', sourceApp: app);
    alerts.add(Alert('$name may be accessed from another device',
        'A new-device or linked-device notice appeared. Log out all other devices now.', 'CRITICAL', []));
  }

  // ---------------- slow loop: device, forwarding, apps ----------------
  Future<void> runSlow() async {
    await checkDevice();
    await checkCallForwarding();
    await checkApps();
  }

  Future<void> checkDevice() async {
    try {
      final info = await NativeBridge.deviceInfo();
      final r = await api.heartbeat(info);
      final score = r['cloneRiskScore'] as int;
      _set('clone', score >= 50 ? 'RISK $score' : 'OK');
      if (score >= 50) {
        alerts.add(Alert('Possible clone / remote access', (r['reasons'] as List).join('\n'),
            score >= 75 ? 'CRITICAL' : 'HIGH', []));
      }
    } catch (e) {
      _set('clone', 'ERROR');
    }
  }

  /// Parses carrier answer to *#21#. Carrier formats vary; we flag any answer containing a phone number
  /// that isn't "not forwarded/inactive".
  static bool looksForwarded(String resp) {
    final low = resp.toLowerCase();
    if (low.contains('not forwarded') || low.contains('inactive') || low.contains('deactivated') ||
        low.contains('not active') || low.contains('disabled') || low.contains('not registered')) {
      return false;
    }
    return RegExp(r'\+?\d{8,}').hasMatch(resp) || low.contains('forwarded to') || low.contains('active');
  }

  Future<void> checkCallForwarding() async {
    try {
      final resp = await NativeBridge.ussd('*#21#');
      if (resp == 'NO_PERMISSION' || resp == 'UNSUPPORTED' || resp.startsWith('ERROR')) {
        _set('forwarding', 'UNKNOWN');
        return;
      }
      final forwarded = looksForwarded(resp);
      _set('forwarding', forwarded ? 'ACTIVE' : 'OFF');
      if (forwarded) {
        await api.report('CALL_FORWARDING', 'CRITICAL', 'Call forwarding is ON');
        await NativeBridge.showAlert('Call forwarding is ON', 'Someone may be receiving your calls/OTPs.', id: 21);
        alerts.add(Alert('Call forwarding is ON', 'Carrier says:\n$resp\n\nScammers use this to steal OTP calls.', 'CRITICAL', [
          AlertAction('Turn OFF forwarding', () async {
            await NativeBridge.disableCallForwarding();
            await checkCallForwarding();
          }),
        ]));
      }
    } catch (e) {
      _set('forwarding', 'UNKNOWN');
    }
  }

  Future<void> checkApps() async {
    try {
      final apps = await NativeBridge.auditApps();
      // only send non-system apps that have at least one risky trait, keeps payload small
      final risky = apps.where((a) =>
          a['isSystem'] != true &&
          ((a['notifPerHour'] as int) >= 4 ||
              a['hasOverlayPermission'] == true ||
              a['hasSmsPermission'] == true ||
              a['hasAccessibility'] == true ||
              a['hasNotificationListener'] == true)).take(300).toList();
      final r = await api.scanApps(risky);
      final labels = {for (final a in apps) a['packageName'] as String: a['label'] as String};
      final bad = (r['results'] as List).where((x) => x['verdict'] == 'MALICIOUS').toList();
      _set('apps', bad.isEmpty ? 'OK' : '${bad.length} risky');
      for (final x in bad) {
        final pkg = x['packageName'] as String;
        alerts.add(Alert('Risky app: ${labels[pkg] ?? pkg}', (x['reasons'] as List).join('\n'), 'HIGH', [
          AlertAction('Uninstall', () => NativeBridge.uninstallApp(pkg)),
        ]));
      }
    } catch (e) {
      _set('apps', 'ERROR');
    }
  }
}
