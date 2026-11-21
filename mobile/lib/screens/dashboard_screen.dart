import 'dart:async';
import 'package:flutter/material.dart';
import '../core/api_client.dart';
import '../core/native_bridge.dart';
import '../core/protection_engine.dart';
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
  bool _notifAccess = false, _filesAccess = false, _phoneAccess = false, _notifPermission = true;
  bool _dialogOpen = false;
  final _queue = <Alert>[];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _sub = engine.alerts.stream.listen(_enqueue);
    _refreshPerms();
    if (widget.autoStart) engine.start();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _sub?.cancel();
    engine.stop();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState s) {
    if (s == AppLifecycleState.resumed) {
      _refreshPerms();
      if (widget.autoStart) engine.runFast();
    }
  }

  Future<void> _refreshPerms() async {
    final n = await NativeBridge.hasNotificationAccess();
    final f = await NativeBridge.hasAllFilesAccess();
    final p = await NativeBridge.hasPhonePermission();
    final np = await NativeBridge.hasNotificationPermission();
    if (mounted) setState(() { _notifAccess = n; _filesAccess = f; _phoneAccess = p; _notifPermission = np; });
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
    final critical = a.severity == 'CRITICAL';
    await showDialog<void>(
      context: context,
      barrierDismissible: !critical,
      builder: (c) => AlertDialog(
        backgroundColor: critical ? Colors.red.shade50 : null,
        icon: a.severity == 'INFO'
            ? const Icon(Icons.info_outline, color: Colors.blueGrey, size: 40)
            : Icon(critical ? Icons.gpp_bad : Icons.warning_amber, color: critical ? Colors.red : Colors.orange, size: 40),
        title: Text(a.title),
        content: SingleChildScrollView(child: Text(a.body)),
        actions: [
          for (final act in a.actions)
            FilledButton(onPressed: () async { Navigator.pop(c); await act.run(); }, child: Text(act.label)),
          if (a.title.toLowerCase().contains('whatsapp') || a.title.toLowerCase().contains('telegram'))
            TextButton(onPressed: () { Navigator.pop(c); _openWizard(a.title.toLowerCase().contains('telegram') ? 'telegram' : 'whatsapp'); },
                child: const Text('Secure account')),
          TextButton(onPressed: () => Navigator.pop(c), child: Text(critical ? 'I understand' : 'Ignore')),
        ],
      ),
    );
    _dialogOpen = false;
    _drain();
  }

  void _openWizard(String app) => Navigator.push(context, MaterialPageRoute(builder: (_) => TakeoverWizardScreen(app: app)));
  void _open(Widget w) => Navigator.push(context, MaterialPageRoute(builder: (_) => w));

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: const Text('FraudShield'), actions: [
          IconButton(icon: const Icon(Icons.history), tooltip: 'Threat history', onPressed: () => _open(ThreatsScreen(api: widget.api))),
          IconButton(icon: const Icon(Icons.tune), tooltip: 'Link settings', onPressed: () => _open(SettingsScreen(engine: engine))),
          IconButton(icon: const Icon(Icons.logout), tooltip: 'Log out', onPressed: () async { await (widget.api ?? ApiClient.instance).logout(); widget.onLogout(); }),
        ]),
        body: ValueListenableBuilder<Map<String, String>>(
          valueListenable: engine.status,
          builder: (c, st, _) => ListView(padding: const EdgeInsets.all(16), children: [
            if (!_notifPermission)
              _permCard('Allow notifications', 'Android 13+ blocks all alerts until you allow notifications. Without it FraudShield cannot warn you.',
                  'Allow', () => NativeBridge.requestNotificationPermission()),
            if (!_notifAccess)
              _permCard('Enable link & ad monitoring', 'Grant Notification Access so FraudShield can scan links sent via WhatsApp, Telegram, SMS and count ad spam. Message text is never uploaded.',
                  'Grant access', () => NativeBridge.openNotificationAccessSettings()),
            if (!_filesAccess)
              _permCard('Enable file protection', 'Grant All-files access to scan downloaded APK/PDF files. Only hash and file name are sent.',
                  'Grant access', () => NativeBridge.openAllFilesAccessSettings()),
            if (!_phoneAccess)
              _permCard('Phone permission', 'Needed to check call forwarding with your carrier.', 'Allow', () => NativeBridge.requestPhonePermission()),
            const SizedBox(height: 8),
            _forwardingTile(st['forwarding']),
            _tile(Icons.phonelink_lock, 'Clone / remote access', st['clone'], onTap: engine.checkDevice),
            _tile(Icons.ads_click, 'Risky / ad-spam apps', st['apps'], onTap: engine.checkApps),
            _tile(Icons.link, 'Link protection', _notifAccess ? 'ON' : 'OFF'),
            _tile(Icons.insert_drive_file, 'File protection', _filesAccess ? 'ON' : 'OFF'),
            const Divider(height: 32),
            Text('Account security', style: Theme.of(context).textTheme.titleMedium),
            ListTile(leading: const Icon(Icons.chat), title: const Text('Secure WhatsApp'), subtitle: const Text('Log out all other devices'), onTap: () => _openWizard('whatsapp')),
            ListTile(leading: const Icon(Icons.send), title: const Text('Secure Telegram'), subtitle: const Text('Terminate other sessions'), onTap: () => _openWizard('telegram')),
            ListTile(leading: const Icon(Icons.sms_failed), title: const Text('SMS forwarding'), subtitle: const Text('Cannot be read by apps - see how to check'),
                onTap: () => _open(const SmsForwardingScreen())),
            ListTile(leading: const Icon(Icons.manage_search), title: const Text('Apps that can read my SMS'), subtitle: const Text('Find OTP-stealing apps'),
                onTap: () => _open(const SmsAuditScreen())),
          ]),
        ),
      );

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
    final v = s ?? 'checking...';
    final canFallback = v == 'UNKNOWN' || v == 'UNAVAILABLE';
    final subtitle = switch (v) {
      'UNAVAILABLE' => "UNAVAILABLE - can't query your carrier from the app",
      'UNKNOWN' => _unknownText(),
      _ => v,
    };
    return Card(
      child: ListTile(
        leading: Icon(Icons.phone_forwarded, color: v == 'ACTIVE' ? Colors.red : (canFallback ? Colors.orange : Colors.green)),
        title: const Text('Call forwarding'),
        subtitle: Text(subtitle),
        onTap: engine.checkCallForwarding,
        trailing: canFallback
            ? TextButton(onPressed: engine.dialForwardingCheck, child: const Text('Check manually'))
            : const Icon(Icons.refresh),
      ),
    );
  }

  String _unknownText() {
    final said = engine.unrecognisedAnswer.value?.trim();
    if (said == null || said.isEmpty) return 'UNKNOWN - carrier answer not understood';
    final shown = said.length > 140 ? '${said.substring(0, 140)}...' : said;
    return 'UNKNOWN - carrier answer not understood\nCarrier said: "$shown"';
  }

  Widget _tile(IconData i, String t, String? s, {VoidCallback? onTap}) {
    final v = s ?? 'checking...';
    final bad = v.startsWith('RISK') || v == 'ACTIVE' || v.contains('risky') || v == 'OFF' && t.contains('protection');
    return Card(
      child: ListTile(
        leading: Icon(i, color: bad ? Colors.red : Colors.green),
        title: Text(t), subtitle: Text(v), onTap: onTap,
        trailing: onTap == null ? null : const Icon(Icons.refresh),
      ),
    );
  }
}
