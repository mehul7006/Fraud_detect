import 'dart:async';
import 'package:flutter/material.dart';
import '../core/api_client.dart';
import '../core/native_bridge.dart';
import '../core/protection_engine.dart';
import 'takeover_wizard_screen.dart';
import 'threats_screen.dart';

class DashboardScreen extends StatefulWidget {
  final VoidCallback onLogout;
  const DashboardScreen({super.key, required this.onLogout});
  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> with WidgetsBindingObserver {
  final engine = ProtectionEngine();
  StreamSubscription<Alert>? _sub;
  bool _notifAccess = false, _filesAccess = false;
  bool _dialogOpen = false;
  final _queue = <Alert>[];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _sub = engine.alerts.stream.listen(_enqueue);
    _refreshPerms();
    engine.start();
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
      engine.runFast();
    }
  }

  Future<void> _refreshPerms() async {
    final n = await NativeBridge.hasNotificationAccess();
    final f = await NativeBridge.hasAllFilesAccess();
    if (mounted) setState(() { _notifAccess = n; _filesAccess = f; });
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
        icon: Icon(critical ? Icons.gpp_bad : Icons.warning_amber, color: critical ? Colors.red : Colors.orange, size: 40),
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

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: const Text('FraudShield'), actions: [
          IconButton(icon: const Icon(Icons.history), onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const ThreatsScreen()))),
          IconButton(icon: const Icon(Icons.logout), onPressed: () async { await ApiClient.instance.logout(); widget.onLogout(); }),
        ]),
        body: ValueListenableBuilder<Map<String, String>>(
          valueListenable: engine.status,
          builder: (c, st, _) => ListView(padding: const EdgeInsets.all(16), children: [
            if (!_notifAccess)
              _permCard('Enable link & ad monitoring', 'Grant Notification Access so FraudShield can scan links sent via WhatsApp, Telegram, SMS and count ad spam. Message text is never uploaded.',
                  'Grant access', () async { await NativeBridge.openNotificationAccessSettings(); }),
            if (!_filesAccess)
              _permCard('Enable file protection', 'Grant All-files access to scan downloaded APK/PDF files. Only hash and file name are sent.',
                  'Grant access', () async { await NativeBridge.openAllFilesAccessSettings(); }),
            _permCard('Phone permission', 'Needed to check call forwarding with your carrier.', 'Allow', () => NativeBridge.requestPhonePermission()),
            const SizedBox(height: 8),
            _tile(Icons.phone_forwarded, 'Call forwarding', st['forwarding'], onTap: engine.checkCallForwarding),
            _tile(Icons.phonelink_lock, 'Clone / remote access', st['clone'], onTap: engine.checkDevice),
            _tile(Icons.ads_click, 'Risky / ad-spam apps', st['apps'], onTap: engine.checkApps),
            _tile(Icons.link, 'Link protection', _notifAccess ? 'ON' : 'OFF'),
            _tile(Icons.insert_drive_file, 'File protection', _filesAccess ? 'ON' : 'OFF'),
            const Divider(height: 32),
            Text('Account security', style: Theme.of(context).textTheme.titleMedium),
            ListTile(leading: const Icon(Icons.chat), title: const Text('Secure WhatsApp'), subtitle: const Text('Log out all other devices'), onTap: () => _openWizard('whatsapp')),
            ListTile(leading: const Icon(Icons.send), title: const Text('Secure Telegram'), subtitle: const Text('Terminate other sessions'), onTap: () => _openWizard('telegram')),
            ListTile(leading: const Icon(Icons.sms_failed), title: const Text('SMS forwarding'), subtitle: const Text('Cannot be read by apps - see carrier steps'),
                onTap: () => showDialog(context: context, builder: (c) => AlertDialog(
                  title: const Text('Check SMS forwarding'),
                  content: const Text('SMS forwarding is set at your carrier or by an SMS-forwarder app.\n\n1. Uninstall unknown apps with SMS permission (see "Risky apps").\n2. Call your carrier customer care and ask: "Is SMS forwarding / SMS redirect active on my number?"\n3. Check your SIM was not duplicated: if your phone suddenly loses network, call the carrier immediately.'),
                  actions: [TextButton(onPressed: () => Navigator.pop(c), child: const Text('OK'))],
                ))),
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
