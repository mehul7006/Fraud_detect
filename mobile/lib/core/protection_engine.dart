import 'dart:async';
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'api_client.dart';
import 'carrier_classifier.dart';
import 'ignored_apps.dart';
import 'native_bridge.dart';
import 'push.dart';
import '../l10n/l10n.dart';
import 'settings_store.dart';
import 'url_extractor.dart';

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

/// One flagged app as shown on the risky-apps screen (6.8).
class RiskyApp {
  final String packageName, label, verdict;
  final int score;
  final List<String> reasons;
  final bool isNew;
  const RiskyApp(this.packageName, this.label, this.score, this.verdict, this.reasons, {this.isNew = false});
}

/// Orchestrates all checks. Runs while the app process is alive; the Kotlin listener keeps collecting
/// events in the background, so nothing is lost when the app is closed.
class ProtectionEngine {
  final ApiClient api;
  /// 7.3: server push (token sent with the device check-in; alerts while the app is open shown in-app).
  final PushClient push;
  StreamSubscription<PushAlert>? _pushSub;
  final alerts = StreamController<Alert>.broadcast();
  final status = ValueNotifier<Map<String, String>>({});
  Timer? _fast, _slow;

  CarrierClassifier _carriers = CarrierClassifier.builtIn();
  String? _carrierVersion;

  /// First carrier answer the rules could not classify (shown on the tile so the user - and we - can see the wording).
  /// Kept on the phone only; it may contain a forwarding number, so it is never sent anywhere.
  final unrecognisedAnswer = ValueNotifier<String?>(null);

  final SettingsStore settings;
  final IgnoredApps ignored;

  /// Last app check result: every SUSPICIOUS / MALICIOUS app not ignored, highest score first (6.8).
  final riskyApps = ValueNotifier<List<RiskyApp>>([]);
  String? _deviceId;
  bool _recheckAppsOnResume = false;
  /// Signals of each app at the last check, so a later Uninstall / Ignore can be labelled (6.1, opt-in).
  final _lastAudit = <String, Map<String, dynamic>>{};
  /// Apps the user tried to uninstall: package -> score shown. Labelled ABUSIVE once they are really gone.
  final _uninstallAsked = <String, int>{};
  final _alertedApps = <String>{};

  /// Link scan bookkeeping (4.7): same link is not scanned twice within 10 minutes, at most 3 scans at once.
  static const dedupeWindow = Duration(minutes: 10);
  static const maxParallelScans = 3;
  final _recentLinks = <String, DateTime>{};
  int _running = 0;
  final _waiting = <Completer<void>>[];

  ProtectionEngine({ApiClient? api, SettingsStore? settings, IgnoredApps? ignored, PushClient? push})
      : api = api ?? ApiClient.instance,
        push = push ?? NoPushClient(),
        settings = settings ?? SettingsStore(),
        ignored = ignored ?? IgnoredApps();

  /// Called when the app comes back to the front, e.g. after the system uninstall dialog (6.7).
  Future<void> onResume() async {
    if (_recheckAppsOnResume) {
      _recheckAppsOnResume = false;
      final asked = Map<String, int>.from(_uninstallAsked);
      _uninstallAsked.clear();
      await checkApps();
      // 6.1: only a real uninstall counts as "abusive" - pressing Cancel in the system dialog does not
      for (final e in asked.entries) {
        if (!await NativeBridge.isInstalled(e.key)) await _label(e.key, 'ABUSIVE', e.value);
      }
    }
  }

  Future<void> uninstall(String pkg) async {
    _recheckAppsOnResume = true;
    final shown = riskyApps.value.where((x) => x.packageName == pkg);
    _uninstallAsked[pkg] = shown.isEmpty ? 0 : shown.first.score;
    await NativeBridge.uninstallApp(pkg);
  }

  Future<void> ignoreApp(RiskyApp a) async {
    await ignored.ignore(a.packageName, a.score);
    riskyApps.value = riskyApps.value.where((x) => x.packageName != a.packageName).toList();
    _set('apps', _appsStatus(riskyApps.value));
    await _label(a.packageName, 'BENIGN', a.score);
  }

  /// 6.1: sends nothing unless the user switched "Help improve risky-app detection" on. Never blocks the UI flow.
  Future<void> _label(String pkg, String label, int score) async {
    try {
      final app = _lastAudit[pkg];
      if (app == null || !await settings.shareAppLabels()) return;
      await api.labelApp(app, label, score);
    } catch (e) {
      debugPrint('app label not sent: $e');
    }
  }

  static String _appsStatus(List<RiskyApp> l) {
    final bad = l.where((x) => x.verdict == 'MALICIOUS').length;
    return bad == 0 ? 'OK' : '$bad risky';
  }

  void start() {
    _pushSub ??= push.foreground.listen(onPush);
    runFast();
    runSlow();
    _fast = Timer.periodic(const Duration(seconds: 15), (_) => runFast()); // 15 s + 3 s settle keeps a new file under the 20 s goal (5.4)
    _slow = Timer.periodic(const Duration(hours: 1), (_) => runSlow());
  }

  void stop() {
    _fast?.cancel();
    _slow?.cancel();
    _pushSub?.cancel();
    _pushSub = null;
  }

  /// 7.3: a server push while the app is on screen becomes a normal in-app alert (critical: blocking popup).
  void onPush(PushAlert p) => alerts.add(Alert(p.title, p.body, p.type == 'ACCOUNT_TAKEOVER' ? 'CRITICAL' : 'HIGH', []));

  void _set(String k, String v) => status.value = {...status.value, k: v};

  // ---------------- fast loop: links, OTP/new device events, files ----------------
  Future<void> runFast() async {
    try {
      await syncBackgroundSession();
      await flushOfflineQueue();
      final shared = await NativeBridge.takeSharedText();
      if (shared != null && shared.trim().isNotEmpty) await scanShared(shared);
      final events = await NativeBridge.takePendingEvents();
      // links are scanned in parallel (max 3); one failing event must not drop the others
      await Future.wait(events.map((e) async {
        try {
          switch (e['kind']) {
            case 'LINK':
              await handleLink(e['url']!, e['pkg']);
            case 'LOGIN_CODE':
              await _handleLoginCode(e['app']!);
            case 'NEW_DEVICE':
              await _handleNewDevice(e['app']!);
          }
        } catch (err) {
          debugPrint('event ${e['kind']} failed: $err');
        }
      }));
      if (await NativeBridge.hasAllFilesAccess()) {
        for (final f in await NativeBridge.scanNewFiles()) {
          await _handleFile(f);
        }
      }
    } catch (e) {
      debugPrint('fast loop error: $e');
    }
  }

  String? _bgSig;
  bool _bgSessionSent = false;

  /// 7.11 + 8.2: the native background scanner gets its OWN session (it renews itself while the app is closed) once
  /// per app start, after that only settings changes are sent. Logged out -> its copy is cleared.
  Future<void> syncBackgroundSession() async {
    try {
      if (!api.hasToken) {
        if (_bgSig != '') await NativeBridge.clearBackgroundSession();
        _bgSig = '';
        _bgSessionSent = false;
        return;
      }
      final disabled = (await settings.disabledPackages()).toList()..sort();
      final trusted = await settings.trustedDomains();
      final sig = '${api.baseUrl}|${disabled.join(',')}|${trusted.join(',')}';
      if (_bgSessionSent && sig == _bgSig) return;
      String? token, refresh;
      if (!_bgSessionSent) {
        final s = await api.backgroundSession();
        token = s['token'] as String?;
        refresh = s['refreshToken'] as String?;
      }
      await NativeBridge.setBackgroundSession(api.baseUrl, token, refresh, disabled, trusted);
      _bgSessionSent = true;
      _bgSig = sig;
    } catch (e) {
      debugPrint('background session not updated: $e');
    }
  }

  Future<void> _slot() async {
    if (_running >= maxParallelScans) {
      final c = Completer<void>();
      _waiting.add(c);
      await c.future;
    } else {
      _running++;
    }
  }

  void _release() {
    if (_waiting.isNotEmpty) {
      _waiting.removeAt(0).complete(); // hand the slot to the next waiter
    } else {
      _running--;
    }
  }

  /// True when [host] equals a trusted domain or is a sub-domain of one.
  static bool isTrusted(String host, Iterable<String> trusted) => trusted.any((d) => host == d || host.endsWith('.$d'));

  /// Scans one link taken from a notification. Honours per-app toggles and the trusted list (4.9),
  /// skips repeats (4.7) and parks the link in the offline queue when the server cannot be reached (4.10).
  Future<void> handleLink(String url, String? pkg, {bool fromQueue = false}) async {
    final host = UrlExtractor.hostOf(url);
    if ((pkg != null && (await settings.disabledPackages()).contains(pkg)) ||
        (host != null && isTrusted(host, await settings.trustedDomains()))) {
      if (fromQueue) await settings.removeQueued(url); // user turned it off meanwhile
      return;
    }
    final now = DateTime.now();
    _recentLinks.removeWhere((_, t) => now.difference(t) > dedupeWindow);
    if (!fromQueue && _recentLinks.containsKey(url)) return;
    _recentLinks[url] = now;

    Map<String, dynamic> r;
    await _slot();
    try {
      r = await api.scanUrl(url, pkg);
    } on ApiException catch (e) {
      _recentLinks.remove(url);
      if (e.status == 401 || e.status >= 500) await settings.enqueueLink(url, pkg); // retry later
      return;
    } catch (_) {
      _recentLinks.remove(url);
      await settings.enqueueLink(url, pkg); // offline / timeout
      return;
    } finally {
      _release();
    }
    if (fromQueue) await settings.removeQueued(url);
    await _showVerdict(url, r);
  }

  Future<void> _showVerdict(String url, Map<String, dynamic> r, {bool showSafe = false}) async {
    final verdict = r['verdict'] as String;
    final reasons = (r['reasons'] as List).join(', ');
    final t = currentL10n();
    if (verdict == 'SAFE') {
      if (showSafe) alerts.add(Alert(t.alertNoDanger, t.alertNoDangerBody(url), 'INFO', []));
      return;
    }
    final bad = verdict == 'MALICIOUS';
    await NativeBridge.showAlert(bad ? t.notifDangerous : t.alertSuspiciousLink, '$url\n$reasons', id: url.hashCode);
    alerts.add(Alert(bad ? t.alertDangerous : t.alertSuspiciousLink, '$url\n\n$reasons',
        bad ? 'CRITICAL' : 'MEDIUM', []));
  }

  /// Links that were captured while offline are scanned once the server answers again (4.10).
  Future<void> flushOfflineQueue() async {
    for (final e in await settings.queuedLinks()) {
      final before = (await settings.queuedLinks()).length;
      await handleLink(e['url']!, e['pkg'], fromQueue: true);
      if ((await settings.queuedLinks()).length >= before) return; // still failing: stop, try next round
    }
  }

  /// Share -> FraudShield (4.8): scans every link in the shared text and always shows an answer.
  /// Returns how many links were checked (0 = no link in the text).
  Future<int> scanShared(String text) async {
    final urls = UrlExtractor.extract(text);
    for (final u in urls.take(5)) {
      try {
        await _showVerdict(u, await api.scanUrl(u, 'share'), showSafe: true);
      } catch (_) {
        alerts.add(Alert(currentL10n().alertCheckFailed, currentL10n().alertCheckFailedBody(u), 'INFO', []));
      }
    }
    if (urls.isEmpty) alerts.add(Alert(currentL10n().alertNoLink, currentL10n().alertNoLinkBody, 'INFO', []));
    return urls.length;
  }

  Future<void> _handleFile(Map<String, dynamic> f) async {
    final r = await api.scanFile(f);
    final verdict = r['verdict'] as String;
    if (verdict == 'SAFE') return;
    final reasons = (r['reasons'] as List).join(', ');
    final path = f['path'] as String;
    final t = currentL10n();
    await NativeBridge.showAlert(t.notifSuspiciousFile, '${f['fileName']}\n$reasons', id: path.hashCode);
    alerts.add(Alert(verdict == 'MALICIOUS' ? t.alertMaliciousFile : t.alertSuspiciousFile, '${f['fileName']}\n\n$reasons',
        verdict == 'MALICIOUS' ? 'CRITICAL' : 'MEDIUM', [
      AlertAction(t.actionDeleteFile, () async => _confirmDelete(path, f['fileName'] as String)),
    ]));
  }

  /// 5.7: deleting is a second, explicit step. The native side still refuses anything outside the scan folders.
  void _confirmDelete(String path, String name) {
    final t = currentL10n();
    alerts.add(Alert(t.alertDeleteQ(name), t.alertDeleteQBody, 'HIGH', [
      AlertAction(t.actionYesDelete, () async {
        final ok = await NativeBridge.deleteFile(path);
        alerts.add(ok
            ? Alert(t.alertDeleted, t.alertDeletedBody(name), 'INFO', [])
            : Alert(t.alertDeleteFailed, t.alertDeleteFailedBody, 'INFO', []));
      }),
    ]));
  }

  Future<void> _handleLoginCode(String app) async {
    final name = app == 'telegram' ? 'Telegram' : 'WhatsApp';
    final t = currentL10n();
    alerts.add(Alert(t.alertLoginCode(name), t.alertLoginCodeBody(name), 'HIGH', [
      AlertAction(t.actionNotMe, () async {
        await api.report(app == 'telegram' ? 'TELEGRAM_TAKEOVER' : 'WHATSAPP_TAKEOVER', 'CRITICAL',
            '$name takeover attempt', detail: 'Unrequested login code', sourceApp: app);
      }),
    ]));
  }

  Future<void> _handleNewDevice(String app) async {
    final name = app == 'telegram' ? 'Telegram' : 'WhatsApp';
    final t = currentL10n();
    await NativeBridge.showAlert(t.notifNewDevice(name), t.notifNewDeviceBody, id: 77);
    await api.report(app == 'telegram' ? 'TELEGRAM_TAKEOVER' : 'WHATSAPP_TAKEOVER', 'CRITICAL',
        '$name new device / session notice', sourceApp: app);
    alerts.add(Alert(t.alertNewDevice(name), t.alertNewDeviceBody, 'CRITICAL', []));
  }

  // ---------------- slow loop: device, forwarding, apps ----------------
  Future<void> runSlow() async {
    await checkDevice();
    await checkCallForwarding();
    await checkApps();
  }

  Future<void> checkDevice() async {
    try {
      final info = Map<String, dynamic>.from(await NativeBridge.deviceInfo());
      _deviceId = info['deviceId'] as String?;
      final pushToken = await push.token(); // 7.3: lets the server push alerts it detects itself
      if (pushToken != null) info['pushToken'] = pushToken;
      final r = await api.heartbeat(info);
      final score = r['cloneRiskScore'] as int;
      _set('clone', score >= 50 ? 'RISK $score' : 'OK');
      if (score >= 50) {
        alerts.add(Alert(currentL10n().alertClone, (r['reasons'] as List).join('\n'),
            score >= 75 ? 'CRITICAL' : 'HIGH', []));
      }
    } catch (e) {
      _set('clone', 'ERROR');
    }
  }

  // ---------------- call forwarding (roadmap 3.3 - 3.6) ----------------

  /// Carrier MMI codes that query each forwarding condition. `##002#` (disable all) is used to turn it off.
  /// The English condition names below are also what the backend report uses; this shows them in the app language.
  static String conditionLabel(String english, AppLocalizations t) => switch (english) {
        'all calls' => t.fwdAll,
        'when busy' => t.fwdBusy,
        'when not answered' => t.fwdNoAnswer,
        'when unreachable' => t.fwdUnreachable,
        _ => english,
      };

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
      final indicator = await _forwardingIndicator();
      Future<void> indicatorSaysOn(String reply) async {
        _set('forwarding', 'ACTIVE');
        await _forwardingFound(forwardingCodes['*#21#']!, '$reply\n${currentL10n().fwdIconNote}');
      }

      for (final e in forwardingCodes.entries) {
        final resp = await NativeBridge.ussd(e.key);
        debugPrint('forwarding ${e.key} -> ${_cannotQuery(resp) ? resp : '"$resp"'}');
        if (_cannotQuery(resp)) {
          // permission/API missing: the other codes would fail the same way. Android's own flag still counts.
          if (indicator == true) return await indicatorSaysOn('(the carrier could not be asked)');
          _set('forwarding', 'UNAVAILABLE');
          return;
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
      // A bare "Call forwarding" reply (service class 0) reads the same for on and off; Android's flag decides.
      if (indicator == true) return await indicatorSaysOn(unrecognisedAnswer.value?.trim() ?? 'no clear answer');
      _set('forwarding', sawUnknown ? 'UNKNOWN' : 'OFF');
    } catch (e) {
      _set('forwarding', 'UNAVAILABLE');
    }
  }

  static Future<bool?> _forwardingIndicator() async {
    try {
      return await NativeBridge.forwardingIndicator();
    } catch (e) {
      return null; // older app build / no telephony: carrier text alone decides
    }
  }

  /// 3.4 evidence: asks all four codes (does not stop at the first ACTIVE) and returns rows in the shape of
  /// test/fixtures/ussd_responses.json. Phone numbers are masked; nothing is sent anywhere, the user copies it.
  Future<List<Map<String, String>>> captureCarrierReplies() async {
    await refreshCarrierRules();
    final rows = <Map<String, String>>[];
    final indicator = await _forwardingIndicator();
    for (final code in forwardingCodes.keys) {
      final resp = await NativeBridge.ussd(code);
      rows.add({
        'code': code,
        'text': maskPhoneNumbers(resp),
        'classifiedAs': _carriers.classify(resp).name.toUpperCase(),
        'androidForwardingIcon': '$indicator',
      });
    }
    return rows;
  }

  /// Every digit of a phone-number-like run (7+ digits, may contain spaces, dashes, a leading +) becomes 9.
  /// Length and format stay the same so the carrier rules still match the masked text.
  static String maskPhoneNumbers(String s) =>
      s.replaceAllMapped(RegExp(r'\+?\d[\d\s-]{5,}\d'), (m) {
        final digits = m[0]!.replaceAll(RegExp(r'\D'), '').length;
        return digits >= 7 ? m[0]!.replaceAll(RegExp(r'\d'), '9') : m[0]!;
      });

  Future<void> _forwardingFound(String condition, String carrierText) async {
    try {
      await api.report('CALL_FORWARDING', 'CRITICAL', 'Call forwarding is ON ($condition)');
    } catch (e) {
      debugPrint('could not report forwarding: $e');
    }
    final t = currentL10n();
    final cond = conditionLabel(condition, t);
    await NativeBridge.showAlert(t.notifFwdOn, t.notifFwdOnBody(cond), id: 21);
    alerts.add(Alert(t.notifFwdOn, t.alertFwdOnBody(cond, carrierText), 'CRITICAL', [
      AlertAction(t.actionFwdOff, disableCallForwarding),
    ]));
  }

  /// One tap: clears all forwarding with ##002#, then checks again so the tile shows the real result.
  Future<void> disableCallForwarding() async {
    await NativeBridge.disableCallForwarding();
    await checkCallForwarding();
  }

  /// Fallback when the app cannot query the carrier: opens the dialer pre-filled with the check code.
  Future<void> dialForwardingCheck() => NativeBridge.dial('*#21#');

  static const _auditFields = ['packageName', 'notifPerHour', 'hasOverlayPermission', 'hasSmsPermission',
      'hasAccessibility', 'hasNotificationListener', 'hasFullScreenIntent', 'isSystem', 'selfLaunchCount', 'installer'];

  Future<void> checkApps() async {
    try {
      final apps = await NativeBridge.auditApps();
      _lastAudit
        ..clear()
        ..addAll({for (final a in apps) a['packageName'] as String: a});
      // 6.1 research (debug builds only): one log line per installed app with the signals the score uses, read by
      // mobile/tool/adabuse/collect.sh to build the real labelled test set. Never in release builds.
      if (kDebugMode) {
        for (final a in apps.where((a) => a['isSystem'] != true)) {
          debugPrint('app audit ${jsonEncode({for (final k in _auditFields) k: a[k]})}');
        }
      }
      // only send non-system apps that have at least one risky trait, keeps payload small
      final risky = apps.where((a) =>
          a['isSystem'] != true &&
          ((a['notifPerHour'] as int) >= 4 ||
              a['hasOverlayPermission'] == true ||
              a['hasSmsPermission'] == true ||
              a['hasAccessibility'] == true ||
              a['hasNotificationListener'] == true ||
              ((a['selfLaunchCount'] as int?) ?? 0) > 0)).take(300).toList();
      _deviceId ??= (await NativeBridge.deviceInfo())['deviceId'] as String?;
      final r = await api.scanApps(risky, deviceId: _deviceId);
      final labels = {for (final a in apps) a['packageName'] as String: a['label'] as String};
      final ignoredNow = await ignored.all();
      final list = <RiskyApp>[
        for (final x in (r['results'] as List).cast<Map>())
          if (x['verdict'] != 'SAFE' && !IgnoredApps.hides(ignoredNow, x['packageName'] as String, x['adAbuseScore'] as int))
            RiskyApp(x['packageName'] as String, labels[x['packageName']] ?? x['packageName'] as String,
                x['adAbuseScore'] as int, x['verdict'] as String, (x['reasons'] as List).cast<String>(),
                isNew: x['newSinceLastScan'] == true),
      ]..sort((a, b) => b.score.compareTo(a.score));
      riskyApps.value = list;
      _set('apps', _appsStatus(list));
      // one popup per app (new install or first time seen this session), not every hour
      for (final a in list.where((x) => x.verdict == 'MALICIOUS')) {
        if (!a.isNew && _alertedApps.contains(a.packageName)) continue;
        _alertedApps.add(a.packageName);
        final t = currentL10n();
        alerts.add(Alert(a.isNew ? t.alertNewRiskyApp(a.label) : t.alertRiskyApp(a.label), a.reasons.join('\n'), 'HIGH', [
          AlertAction(t.uninstall, () => uninstall(a.packageName)),
          AlertAction(t.actionIgnoreApp, () => ignoreApp(a)),
        ]));
      }
    } catch (e) {
      _set('apps', 'ERROR');
    }
  }
}
