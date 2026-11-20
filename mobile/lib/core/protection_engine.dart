import 'dart:async';
import 'package:flutter/foundation.dart';
import 'api_client.dart';
import 'carrier_classifier.dart';
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
  final ApiClient api;
  final alerts = StreamController<Alert>.broadcast();
  final status = ValueNotifier<Map<String, String>>({});
  Timer? _fast, _slow;

  CarrierClassifier _carriers = CarrierClassifier.builtIn();
  String? _carrierVersion;

  /// First carrier answer the rules could not classify (shown on the tile so the user - and we - can see the wording).
  /// Kept on the phone only; it may contain a forwarding number, so it is never sent anywhere.
  final unrecognisedAnswer = ValueNotifier<String?>(null);

  ProtectionEngine({ApiClient? api}) : api = api ?? ApiClient.instance;

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

  // ---------------- call forwarding (roadmap 3.3 - 3.6) ----------------

  /// Carrier MMI codes that query each forwarding condition. `##002#` (disable all) is used to turn it off.
  static const Map<String, String> forwardingCodes = {
    '*#21#': 'all calls',
    '*#67#': 'when busy',
    '*#61#': 'when not answered',
    '*#62#': 'when unreachable',
  };

  /// Kept for existing callers/tests: true when the built-in rules read the answer as "forwarding is ON".
  static bool looksForwarded(String resp) => CarrierClassifier.builtIn().classify(resp) == CarrierState.active;

  /// Fetches the latest answer patterns from the server (ETag); keeps the built-in/previous rules when offline.
  Future<void> refreshCarrierRules() async {
    try {
      final cfg = await api.carrierConfig(version: _carrierVersion);
      if (cfg != null && cfg.rules.isNotEmpty) {
        _carriers = CarrierClassifier(cfg.rules);
        _carrierVersion = cfg.version;
      }
    } catch (e) {
      debugPrint('carrier rules not refreshed (using ${_carrierVersion ?? 'built-in'}): $e');
    }
  }

  static bool _cannotQuery(String resp) => resp == 'NO_PERMISSION' || resp == 'UNSUPPORTED' || resp.startsWith('ERROR');

  /// Sets status 'forwarding' to ACTIVE | OFF | UNKNOWN (answer not understood) | UNAVAILABLE (cannot query the carrier).
  /// UNKNOWN / UNAVAILABLE let the user check by dialing the code themselves (see [dialForwardingCheck]).
  Future<void> checkCallForwarding() async {
    try {
      await refreshCarrierRules();
      var sawUnknown = false;
      unrecognisedAnswer.value = null;
      for (final e in forwardingCodes.entries) {
        final resp = await NativeBridge.ussd(e.key);
        debugPrint('forwarding ${e.key} -> ${_cannotQuery(resp) ? resp : '"$resp"'}');
        if (_cannotQuery(resp)) {
          _set('forwarding', 'UNAVAILABLE');
          return; // permission/API missing: the other codes would fail the same way
        }
        final state = _carriers.classify(resp);
        if (state == CarrierState.active) {
          _set('forwarding', 'ACTIVE');
          await _forwardingFound(e.value, resp);
          return;
        }
        if (state == CarrierState.unknown) {
          sawUnknown = true;
          unrecognisedAnswer.value ??= resp;
        }
      }
      _set('forwarding', sawUnknown ? 'UNKNOWN' : 'OFF');
    } catch (e) {
      _set('forwarding', 'UNAVAILABLE');
    }
  }

  Future<void> _forwardingFound(String condition, String carrierText) async {
    try {
      await api.report('CALL_FORWARDING', 'CRITICAL', 'Call forwarding is ON ($condition)');
    } catch (e) {
      debugPrint('could not report forwarding: $e');
    }
    await NativeBridge.showAlert('Call forwarding is ON', 'Forwarding $condition is active. Scammers use this to steal OTP calls.', id: 21);
    alerts.add(Alert('Call forwarding is ON',
        'Forwarding $condition is active.\n\nCarrier says:\n$carrierText\n\nScammers use this to steal OTP calls.', 'CRITICAL', [
      AlertAction('Turn OFF forwarding', disableCallForwarding),
    ]));
  }

  /// One tap: clears all forwarding with ##002#, then checks again so the tile shows the real result.
  Future<void> disableCallForwarding() async {
    await NativeBridge.disableCallForwarding();
    await checkCallForwarding();
  }

  /// Fallback when the app cannot query the carrier: opens the dialer pre-filled with the check code.
  Future<void> dialForwardingCheck() => NativeBridge.dial('*#21#');

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
