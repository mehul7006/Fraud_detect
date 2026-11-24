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
import 'permissions_screen.dart';
import '../core/permissions.dart';
import 'drills_screen.dart';
import 'recovery_kit_screen.dart';
import '../ui/components.dart';
import '../ui/theme.dart';
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
  bool _notifAccess = false, _filesAccess = false, _vpnOn = false;
  bool _dialogOpen = false;
  List<PermItem>? _perms; // all permissions (summary card)
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
    bool vpn = false;
    try { vpn = (await NativeBridge.vpnStatus())['running'] == true; } catch (_) {} // 12.x
    List<PermItem>? perms;
    try { perms = await Permissions.load(); } catch (_) {}
    if (mounted) setState(() { _notifAccess = n; _filesAccess = f; _vpnOn = vpn; _perms = perms; });
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
  Future<void> _open(Widget w) => Navigator.push(context, MaterialPageRoute(builder: (_) => w));

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    return Scaffold(
      body: AnnotatedRegion<SystemUiOverlayStyle>(
        value: SystemUiOverlayStyle.light, // white status-bar icons on the gradient hero
        child: Stack(children: [ValueListenableBuilder<Map<String, String>>(
          valueListenable: engine.status,
          builder: (c, st, _) {
            // the six shields the user can see at a glance (hero ring)
            final checks = <bool>[
              st['forwarding'] == 'OFF',
              _vpnOn,
              _notifAccess,
              _filesAccess,
              st['clone'] == 'OK',
              st['apps'] == 'OK',
            ];
            final on = checks.where((x) => x).length;
            final alert = st['forwarding'] == 'ACTIVE' || (st['clone'] ?? '').startsWith('RISK');
            final white = IconButton.styleFrom(foregroundColor: Colors.white, minimumSize: const Size(48, 48));
            final dark = Theme.of(context).brightness == Brightness.dark;
            return DecoratedBox(
              decoration: BoxDecoration(gradient: dark ? AppColors.canvasGradientDark : AppColors.canvasGradient),
              child: ListView(padding: EdgeInsets.zero, children: [
              HeroHeader(
                title: l.appTitle,
                on: on, total: checks.length, alert: alert,
                headline: on == checks.length ? l.homeProtected : l.homeNeedsAttention(checks.length - on),
                detail: on == checks.length ? l.homeProtectedDetail : l.homeAttentionDetail,
                actions: [
                  IconButton(style: white, icon: const Icon(Icons.history), tooltip: l.threatHistory, onPressed: () => _open(ThreatsScreen(api: widget.api))),
                  IconButton(style: white, icon: const Icon(Icons.tune), tooltip: l.linkSettings, onPressed: () => _open(SettingsScreen(engine: engine))),
                  IconButton(style: white, icon: const Icon(Icons.logout), tooltip: l.logOut, onPressed: () async { await (widget.api ?? ApiClient.instance).logout(); await NativeBridge.clearBackgroundSession().catchError((_) {}); widget.onLogout(); }),
                ],
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
                child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                  // every permission in one place: count + one tap to the permissions page
                  if (_perms != null)
                    PermissionSummaryCard(
                      allowed: Permissions.count(_perms!).allowed, total: Permissions.count(_perms!).total,
                      onTap: () async { await _open(const PermissionsScreen()); _refreshPerms(); },
                    ),
                  if (st.values.contains('ERROR'))
                    NoticeCard(cardKey: const Key('offline-banner'), icon: Icons.cloud_off_rounded, tone: Tone.info,
                        title: l.offlineTitle, body: l.offlineBody),


                  SectionTitle(l.homeQuick),
                  Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Expanded(child: QuickAction(icon: Icons.mark_chat_read_rounded, feature: Feature.message, label: l.msgDashTile,
                        onTap: () => _open(CheckMessageScreen(api: widget.api)))),
                    const SizedBox(width: 12),
                    Expanded(child: QuickAction(actionKey: const Key('safe-tile'), icon: Icons.health_and_safety_rounded, feature: Feature.scam,
                        label: l.safeTileTitle, onTap: () => _open(SafetyScreen(api: widget.api)))),
                  ]),
                  const SizedBox(height: 12),
                  Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Expanded(child: QuickAction(icon: Icons.healing_rounded, feature: Feature.account, label: l.homeScammed,
                        onTap: () => _open(const RecoveryKitScreen()))),
                    const SizedBox(width: 12),
                    Expanded(child: QuickAction(icon: Icons.school_rounded, feature: Feature.device, label: l.safeDrillsTitle,
                        onTap: () => _open(const DrillsScreen()))),
                  ]),

                  // ---- group 1: the shields that run all the time
                  GroupCard(icon: Icons.shield_rounded, feature: Feature.web, title: l.homeStatus, subtitle: l.homeStatusSub, children: [
                    _forwardingTile(st['forwarding']),
                    FeatureTile(
                      icon: _vpnOn ? Icons.vpn_lock_rounded : Icons.vpn_lock_outlined, feature: Feature.web,
                      title: l.vpnTitle, status: _vpnOn ? l.vpnTileOn : l.vpnTileOff, tone: _vpnOn ? Tone.safe : Tone.danger,
                      trailingIcon: Icons.chevron_right_rounded,
                      onTap: () async { await Navigator.push(context, MaterialPageRoute(builder: (_) => const VpnStateScreen())); _refreshPerms(); },
                    ),
                    _tile(Icons.link_rounded, Feature.web, l.linkProtection, _notifAccess ? l.statusOn : l.statusOff, bad: !_notifAccess),
                    _tile(Icons.insert_drive_file_rounded, Feature.files, l.fileProtection, _filesAccess ? l.statusOn : l.statusOff, bad: !_filesAccess),
                  ]),

                  // ---- group 2: the phone itself
                  GroupCard(icon: Icons.smartphone_rounded, feature: Feature.apps, title: l.homeGroupPhone, subtitle: l.homeGroupPhoneSub, children: [
                    FeatureTile(
                      icon: Icons.ads_click_rounded, feature: Feature.apps, title: l.riskyApps, status: _appsText(st['apps']),
                      tone: (st['apps'] ?? '').contains('risky') ? Tone.danger : st['apps'] == 'OK' ? Tone.safe : Tone.neutral,
                      trailingIcon: Icons.chevron_right_rounded,
                      onTap: () => _open(RiskyAppsScreen(engine: engine)),
                    ),
                    _tile(Icons.phonelink_lock_rounded, Feature.device, l.cloneRemote, _cloneText(st['clone']), onTap: engine.checkDevice,
                        bad: (st['clone'] ?? '').startsWith('RISK')),
                    const LeftoverTile(), // 17.x
                  ]),

                  // ---- group 3: calls and SMS
                  GroupCard(icon: Icons.call_rounded, feature: Feature.call, title: l.homeGroupCalls, subtitle: l.homeGroupCallsSub, children: [
                    CallGuardTile(api: widget.api), // 16.x
                    FeatureTile(icon: Icons.forward_to_inbox_rounded, feature: Feature.message, title: l.smsForwarding, status: l.smsForwardingSub,
                        trailingIcon: Icons.chevron_right_rounded, onTap: () => _open(const SmsForwardingScreen())),
                    FeatureTile(icon: Icons.manage_search_rounded, feature: Feature.history, title: l.smsReaders, status: l.smsReadersSub,
                        trailingIcon: Icons.chevron_right_rounded, onTap: () => _open(const SmsAuditScreen())),
                  ]),

                  // ---- group 4: accounts
                  GroupCard(icon: Icons.lock_person_rounded, feature: Feature.account, title: l.accountSecurity, subtitle: l.homeAccountSub, children: [
                    FeatureTile(icon: Icons.chat_rounded, feature: Feature.account, title: l.secureWhatsApp, status: l.secureWhatsAppSub,
                        trailingIcon: Icons.chevron_right_rounded, onTap: () => _openWizard('whatsapp')),
                    FeatureTile(icon: Icons.send_rounded, feature: Feature.web, title: l.secureTelegram, status: l.secureTelegramSub,
                        trailingIcon: Icons.chevron_right_rounded, onTap: () => _openWizard('telegram')),
                  ]),
                ]),
              ),
            ]));
          },
        ),
          // brand strip behind the status bar: scrolled cards never show through it (white icons stay readable)
          Positioned(top: 0, left: 0, right: 0, height: MediaQuery.paddingOf(context).top,
              child: const IgnorePointer(child: DecoratedBox(decoration: BoxDecoration(gradient: AppColors.brandGradient)))),
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
    final tone = v == 'ACTIVE' ? Tone.danger : v == 'OFF' ? Tone.safe : (canFallback ? Tone.warn : Tone.neutral);
    final t = Theme.of(context);
    final style = t.textTheme.bodyMedium?.copyWith(color: tone.fg(context), fontWeight: FontWeight.w500);
    return FeatureTile(
      icon: Icons.phone_forwarded_rounded, feature: Feature.call,
      title: l.callForwarding, tone: tone,
      // 8.12: the fallback button goes under the text (not in `trailing`) so large text / Hindi never squeezes it
      statusWidget: canFallback
          ? Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(subtitle, style: style),
              Align(alignment: Alignment.centerRight,
                  child: TextButton(onPressed: engine.dialForwardingCheck, child: Text(l.checkManually))),
            ])
          : Text(subtitle, style: style),
      onTap: engine.checkCallForwarding,
      onLongPress: _captureReplies,
      trailingIcon: canFallback ? null : Icons.refresh_rounded,
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

  Widget _tile(IconData i, Feature f, String t, String? s, {VoidCallback? onTap, bool bad = false}) {
    final v = s == 'ERROR' ? context.l10n.couldntCheck : s ?? context.l10n.checking;
    final known = s != null && s != 'ERROR';
    return FeatureTile(
      icon: i, feature: f, title: t, status: v,
      tone: !known ? Tone.neutral : (bad ? Tone.danger : Tone.safe),
      onTap: onTap, trailingIcon: onTap == null ? null : Icons.refresh_rounded,
    );
  }
}
