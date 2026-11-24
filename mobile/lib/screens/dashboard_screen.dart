import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../core/api_client.dart';
import '../core/native_bridge.dart';
import '../core/protection_engine.dart';
import '../l10n/l10n.dart';
import 'call_guard_settings_screen.dart' show CallGuardTile;
import 'check_message_screen.dart';
import 'leftover_screen.dart' show LeftoverTile;
import 'fake_site_screen.dart';
import 'vpn_state_screen.dart';
import 'why_panel.dart';
import 'risky_apps_screen.dart';
import 'safety_screen.dart';
import 'scam_stop_screen.dart';
import 'settings_screen.dart';
import 'sms_audit_screen.dart';
import 'sms_forwarding_screen.dart';
import 'takeover_wizard_screen.dart';
import 'threats_screen.dart';

class DashboardScreen extends StatefulWidget {
  final VoidCallback onLogout;
  final ProtectionEngine? engine; // injectable for tests
  final ApiClient? api; // injectable for tests
  final bool autoStart; // tests turn the background timers off and drive the engine by hand
  const DashboardScreen({super.key, required this.onLogout, this.engine, this.api, this.autoStart = true});
  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> with WidgetsBindingObserver {
  late final ProtectionEngine engine = widget.engine ?? ProtectionEngine();
  StreamSubscription<Alert>? _sub;
  bool _notifAccess = false, _filesAccess = false, _phoneAccess = false, _notifPermission = true, _usageAccess = true, _fullScreen = true, _vpnOn = false;
  bool _dialogOpen = false;
  final _queue = <Alert>[];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _sub = engine.alerts.stream.listen(_enqueue);
    _refreshPerms();
    engine.sharedMessage.addListener(_openSharedMessage); // 14.4
    engine.liveScam.addListener(_openScamStop); // 15.3
    if (widget.autoStart) engine.start();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    engine.sharedMessage.removeListener(_openSharedMessage);
    engine.liveScam.removeListener(_openScamStop);
    _sub?.cancel();
    engine.stop();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState s) {
    if (s == AppLifecycleState.resumed) {
      _refreshPerms();
      if (widget.autoStart) {
        engine.runFast();
        engine.onResume();
      }
    }
  }

  Future<void> _refreshPerms() async {
    final n = await NativeBridge.hasNotificationAccess();
    final f = await NativeBridge.hasAllFilesAccess();
    final p = await NativeBridge.hasPhonePermission();
    final np = await NativeBridge.hasNotificationPermission();
    final u = await NativeBridge.hasUsageAccess();
    final fs = await NativeBridge.canUseFullScreenAlerts().catchError((_) => true); // 7.10
    bool vpn = false;
    try { vpn = (await NativeBridge.vpnStatus())['running'] == true; } catch (_) {} // 12.x
    if (mounted) setState(() { _notifAccess = n; _filesAccess = f; _phoneAccess = p; _notifPermission = np; _usageAccess = u; _fullScreen = fs; _vpnOn = vpn; });
  }

  // ---- blocking popup, one at a time ----
  void _enqueue(Alert a) {
    _queue.add(a);
    _drain();
  }

  Future<void> _drain() async {
    if (_dialogOpen || _queue.isEmpty || !mounted) return;
    _dialogOpen = true;
    final a = _queue.removeAt(0);
    if (a.fakeSite != null) { // 10.11: full-screen fake-site warning
      await Navigator.push(context, MaterialPageRoute(builder: (_) => FakeSiteScreen(site: a.fakeSite!, api: widget.api)));
      _dialogOpen = false;
      _drain();
      return;
    }
    final critical = a.severity == 'CRITICAL';
    // 7.4: reading the optional contact must never stop an alert from showing (a failed read = no contact)
    ({String name, String phone})? contact;
    if (critical) {
      try {
        contact = await engine.settings.emergencyContact().timeout(const Duration(seconds: 2));
      } catch (_) {}
    }
    final tell = contact; // final copy: usable inside the dialog builder
    if (!mounted) return;
    await showDialog<void>(
      context: context,
      barrierDismissible: !critical,
      builder: (c) => AlertDialog(
        backgroundColor: critical ? Colors.red.shade50 : null,
        icon: a.severity == 'INFO'
            ? const Icon(Icons.info_outline, color: Colors.blueGrey, size: 40)
            : Icon(critical ? Icons.gpp_bad : Icons.warning_amber, color: critical ? Colors.red : Colors.orange, size: 40),
        title: Text(a.title),
        content: SingleChildScrollView(child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start,
            children: [Text(a.body), if (WhyPanel.from(a.explanation) case final why?) why])),
        actions: [
          for (final act in a.actions)
            FilledButton(onPressed: () async { Navigator.pop(c); await act.run(); }, child: Text(act.label)),
          if (a.title.toLowerCase().contains('whatsapp') || a.title.toLowerCase().contains('telegram'))
            TextButton(onPressed: () { Navigator.pop(c); _openWizard(a.title.toLowerCase().contains('telegram') ? 'telegram' : 'whatsapp'); },
                child: Text(context.l10n.secureAccount)),
          if (tell != null)
            TextButton.icon(
              key: const Key('tell-contact'),
              icon: const Icon(Icons.sms_outlined),
              label: Text(context.l10n.tellContact(tell.name)),
              onPressed: () => NativeBridge.composeSms(tell.phone, context.l10n.contactMessage(a.title)),
            ),
          TextButton(onPressed: () => Navigator.pop(c), child: Text(critical ? context.l10n.iUnderstand : context.l10n.ignore)),
        ],
      ),
    );
    _dialogOpen = false;
    _drain();
  }

  void _openSharedMessage() {
    final text = engine.sharedMessage.value;
    if (text == null || !mounted) return;
    engine.sharedMessage.value = null;
    Navigator.push(context, MaterialPageRoute(builder: (_) => CheckMessageScreen(api: widget.api, initialText: text)));
  }

  void _openScamStop() { // 15.3: full-screen STOP, read aloud
    final level = engine.liveScam.value;
    if (level == null || !mounted) return;
    engine.liveScam.value = null;
    Navigator.push(context, MaterialPageRoute(fullscreenDialog: true,
        builder: (_) => ScamStopScreen(critical: level == 'CRITICAL', settings: engine.settings)));
  }

  void _openWizard(String app) => Navigator.push(context, MaterialPageRoute(builder: (_) => TakeoverWizardScreen(app: app)));
  void _open(Widget w) => Navigator.push(context, MaterialPageRoute(builder: (_) => w));

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    return Scaffold(
        appBar: AppBar(title: Text(l.appTitle), actions: [
          IconButton(icon: const Icon(Icons.history), tooltip: l.threatHistory, onPressed: () => _open(ThreatsScreen(api: widget.api))),
          IconButton(icon: const Icon(Icons.tune), tooltip: l.linkSettings, onPressed: () => _open(SettingsScreen(engine: engine))),
          IconButton(icon: const Icon(Icons.logout), tooltip: l.logOut, onPressed: () async { await (widget.api ?? ApiClient.instance).logout(); await NativeBridge.clearBackgroundSession().catchError((_) {}); widget.onLogout(); }),
        ]),
        body: ValueListenableBuilder<Map<String, String>>(
          valueListenable: engine.status,
          builder: (c, st, _) => ListView(padding: const EdgeInsets.all(16), children: [
            if (!_notifPermission)
              _permCard(l.permNotifTitle, l.permNotifBody, l.allow, () => NativeBridge.requestNotificationPermission()),
            if (!_fullScreen)
              _permCard(l.permFsiTitle, l.permFsiBody, l.allow, () => NativeBridge.openFullScreenAlertSettings()),
            if (!_notifAccess)
              _permCard(l.permLinkTitle, l.permLinkBody, l.grantAccess, () => NativeBridge.openNotificationAccessSettings()),
            if (!_filesAccess)
              _permCard(l.permFilesTitle, l.permFilesBody, l.grantAccess, () => NativeBridge.openAllFilesAccessSettings()),
            if (!_phoneAccess)
              _permCard(l.permPhoneTitle, l.permPhoneBody, l.allow, () => NativeBridge.requestPhonePermission()),
            if (st.values.contains('ERROR'))
              Card(
                key: const Key('offline-banner'),
                color: Colors.amber.shade50,
                child: ListTile(
                  leading: const Icon(Icons.cloud_off, color: Colors.orange),
                  title: Text(l.offlineTitle),
                  subtitle: Text(l.offlineBody),
                ),
              ),
            const SizedBox(height: 8),
            _forwardingTile(st['forwarding']),
            _tile(_vpnOn ? Icons.vpn_lock : Icons.vpn_lock_outlined, l.vpnTitle, _vpnOn ? l.vpnTileOn : l.vpnTileOff,
                bad: !_vpnOn, onTap: () async { await Navigator.push(context, MaterialPageRoute(builder: (_) => const VpnStateScreen())); _refreshPerms(); }),
            _tile(Icons.sms_failed_outlined, l.msgDashTile, l.msgDashTileSub,
                onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => CheckMessageScreen(api: widget.api)))),
            Card(child: ListTile( // 15.x scam help hub
              key: const Key('safe-tile'),
              leading: const Icon(Icons.health_and_safety_outlined, color: Colors.indigo),
              title: Text(l.safeTileTitle), subtitle: Text(l.safeTileSub), trailing: const Icon(Icons.chevron_right),
              onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => SafetyScreen(api: widget.api))))),
            const LeftoverTile(), // 17.x
            CallGuardTile(api: widget.api), // 16.x
            _tile(Icons.phonelink_lock, l.cloneRemote, _cloneText(st['clone']), onTap: engine.checkDevice,
                bad: (st['clone'] ?? '').startsWith('RISK')),
            if (!_usageAccess)
              _permCard(l.permUsageTitle, l.permUsageBody, l.allow, () => NativeBridge.openUsageAccessSettings()),
            Card(
              child: ListTile(
                leading: Icon(Icons.ads_click, color: (st['apps'] ?? '').contains('risky') ? Colors.red : Colors.green),
                title: Text(l.riskyApps),
                subtitle: Text(_appsText(st['apps'])),
                trailing: const Icon(Icons.chevron_right),
                onTap: () => _open(RiskyAppsScreen(engine: engine)),
              ),
            ),
            _tile(Icons.link, l.linkProtection, _notifAccess ? l.statusOn : l.statusOff, bad: !_notifAccess),
            _tile(Icons.insert_drive_file, l.fileProtection, _filesAccess ? l.statusOn : l.statusOff, bad: !_filesAccess),
            const Divider(height: 32),
            Text(l.accountSecurity, style: Theme.of(context).textTheme.titleMedium),
            ListTile(leading: const Icon(Icons.chat), title: Text(l.secureWhatsApp), subtitle: Text(l.secureWhatsAppSub), onTap: () => _openWizard('whatsapp')),
            ListTile(leading: const Icon(Icons.send), title: Text(l.secureTelegram), subtitle: Text(l.secureTelegramSub), onTap: () => _openWizard('telegram')),
            ListTile(leading: const Icon(Icons.sms_failed), title: Text(l.smsForwarding), subtitle: Text(l.smsForwardingSub),
                onTap: () => _open(const SmsForwardingScreen())),
            ListTile(leading: const Icon(Icons.manage_search), title: Text(l.smsReaders), subtitle: Text(l.smsReadersSub),
                onTap: () => _open(const SmsAuditScreen())),
          ]),
        ),
      );
  }

  String _appsText(String? s) {
    final l = context.l10n;
    if (s == null) return l.checking;
    if (s == 'ERROR') return l.couldntCheck;
    if (s == 'OK') return l.statusOk;
    final m = RegExp(r'^(\d+) risky$').firstMatch(s);
    return m != null ? l.appsRisky(m.group(1)!) : s;
  }

  /// engine codes -> words in the user's language
  String? _cloneText(String? s) {
    if (s == null || s == 'ERROR') return s;
    final l = context.l10n;
    if (s == 'OK') return l.statusOk;
    if (s.startsWith('RISK ')) return l.statusRisk(s.substring(5));
    return s;
  }

  Widget _permCard(String t, String d, String btn, VoidCallback onTap) => Card(
        color: Colors.amber.shade50,
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(t, style: const TextStyle(fontWeight: FontWeight.bold)),
            const SizedBox(height: 4), Text(d),
            Align(alignment: Alignment.centerRight, child: TextButton(onPressed: onTap, child: Text(btn))),
          ]),
        ),
      );

  /// Call-forwarding tile. When the app cannot query the carrier (or cannot understand the answer) it offers
  /// to open the dialer with the check code so the user can look themselves (3.6).
  Widget _forwardingTile(String? s) {
    final l = context.l10n;
    final v = s ?? l.checking;
    final canFallback = v == 'UNKNOWN' || v == 'UNAVAILABLE';
    final subtitle = switch (v) {
      'UNAVAILABLE' => l.fwdUnavailable,
      'UNKNOWN' => _unknownText(),
      'ACTIVE' => l.statusActive,
      'OFF' => l.statusOff,
      _ => v,
    };
    return Card(
      child: ListTile(
        leading: Icon(Icons.phone_forwarded, color: v == 'ACTIVE' ? Colors.red : (canFallback ? Colors.orange : Colors.green)),
        title: Text(l.callForwarding),
        // 8.12: the fallback button goes under the text (not in `trailing`) so large text / Hindi never squeezes it
        subtitle: canFallback
            ? Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(subtitle),
                Align(alignment: Alignment.centerRight,
                    child: TextButton(onPressed: engine.dialForwardingCheck, child: Text(l.checkManually))),
              ])
            : Text(subtitle),
        onTap: engine.checkCallForwarding,
        onLongPress: _captureReplies,
        trailing: canFallback ? null : const Icon(Icons.refresh),
      ),
    );
  }

  /// 3.4: long-press the call forwarding tile -> the four carrier replies (numbers masked) ready to copy.
  Future<void> _captureReplies() async {
    final rows = await engine.captureCarrierReplies();
    final text = const JsonEncoder.withIndent('  ').convert(rows);
    if (!mounted) return;
    await showDialog<void>(
      context: context,
      builder: (c) => AlertDialog(
        title: Text(context.l10n.carrierReplies),
        content: SingleChildScrollView(child: SelectableText(context.l10n.carrierRepliesBody(text))),
        actions: [
          TextButton(onPressed: () { Clipboard.setData(ClipboardData(text: text)); Navigator.pop(c); }, child: Text(context.l10n.copy)),
          TextButton(onPressed: () => Navigator.pop(c), child: Text(context.l10n.close)),
        ],
      ),
    );
  }

  String _unknownText() {
    final said = engine.unrecognisedAnswer.value?.trim();
    if (said == null || said.isEmpty) return context.l10n.fwdUnknown;
    final shown = said.length > 140 ? '${said.substring(0, 140)}...' : said;
    return context.l10n.fwdCarrierSaid(shown);
  }

  Widget _tile(IconData i, String t, String? s, {VoidCallback? onTap, bool bad = false}) {
    final v = s == 'ERROR' ? context.l10n.couldntCheck : s ?? context.l10n.checking;
    return Card(
      child: ListTile(
        leading: Icon(i, color: bad ? Colors.red : Colors.green),
        title: Text(t), subtitle: Text(v), onTap: onTap,
        trailing: onTap == null ? null : const Icon(Icons.refresh),
      ),
    );
  }
}
