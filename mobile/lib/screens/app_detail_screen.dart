import 'package:flutter/material.dart';

import '../core/native_bridge.dart';
import '../core/protection_engine.dart';
import '../l10n/l10n.dart';
import 'why_panel.dart';

/// 13.8: one risky app - what it can access, the tracking code inside it, who it talked to in the last 7 days
/// (judged on the phone; site names never leave it), and four actions: Block internet (13.9), Remove permissions
/// (Android's page for the app), Uninstall, Ignore (remembered; stops reminders).
class AppDetailScreen extends StatefulWidget {
  final RiskyApp app;
  final ProtectionEngine engine;
  const AppDetailScreen({super.key, required this.app, required this.engine});
  @override
  State<AppDetailScreen> createState() => _AppDetailScreenState();
}

class _AppDetailScreenState extends State<AppDetailScreen> {
  List<Map<String, dynamic>>? _trackers;
  Map<String, dynamic>? _net;
  bool _blocked = false;

  /// Permissions people understand, in the order of how much they matter.
  static const _plain = {
    'android.permission.READ_SMS': ('Read your SMS (OTPs)', 'आपके SMS (OTP) पढ़ना'),
    'android.permission.RECEIVE_SMS': ('Receive your SMS', 'आपके SMS पाना'),
    'android.permission.SEND_SMS': ('Send SMS', 'SMS भेजना'),
    'android.permission.READ_CONTACTS': ('Your contacts', 'आपके संपर्क'),
    'android.permission.READ_CALL_LOG': ('Your call history', 'आपका कॉल इतिहास'),
    'android.permission.CALL_PHONE': ('Make calls', 'कॉल करना'),
    'android.permission.ACCESS_FINE_LOCATION': ('Your exact location', 'आपकी सटीक लोकेशन'),
    'android.permission.ACCESS_BACKGROUND_LOCATION': ('Location all the time', 'हर समय लोकेशन'),
    'android.permission.RECORD_AUDIO': ('Microphone', 'माइक्रोफ़ोन'),
    'android.permission.CAMERA': ('Camera', 'कैमरा'),
    'android.permission.READ_MEDIA_IMAGES': ('Your photos', 'आपकी फ़ोटो'),
    'android.permission.READ_EXTERNAL_STORAGE': ('Your files', 'आपकी फ़ाइलें'),
    'android.permission.SYSTEM_ALERT_WINDOW': ('Draw over other apps', 'दूसरे ऐप्स के ऊपर दिखना'),
    'android.permission.REQUEST_INSTALL_PACKAGES': ('Install other apps', 'दूसरे ऐप इंस्टॉल करना'),
    'android.permission.READ_PHONE_STATE': ('Phone number and SIM details', 'फ़ोन नंबर और SIM जानकारी'),
  };

  @override
  void initState() {
    super.initState();
    final pkg = widget.app.packageName;
    NativeBridge.appTrackers(pkg).then((t) { if (mounted) setState(() => _trackers = t); }).catchError((_) {});
    NativeBridge.appNetwork(pkg).then((n) { if (mounted) setState(() => _net = n); }).catchError((_) {});
    NativeBridge.appFirewallList().then((b) { if (mounted) setState(() => _blocked = b.contains(pkg)); }).catchError((_) {});
  }

  Future<void> _toggleBlock() async {
    await NativeBridge.appFirewallSet(widget.app.packageName, !_blocked);
    setState(() => _blocked = !_blocked);
  }

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    final hi = Localizations.localeOf(context).languageCode == 'hi';
    final a = widget.app;
    final granted = ((a.audit['grantedPermissions'] as List?) ?? const []).cast<String>().toSet()
      ..addAll([if (a.audit['hasOverlayPermission'] == true) 'android.permission.SYSTEM_ALERT_WINDOW']);
    final access = [for (final e in _plain.entries) if (granted.contains(e.key)) hi ? e.value.$2 : e.value.$1];
    if (a.audit['hasAccessibility'] == true) access.insert(0, hi ? 'आपकी स्क्रीन नियंत्रित करना (Accessibility)' : 'Control your screen (Accessibility)');
    final bad = a.verdict == 'MALICIOUS';
    final net = _net;
    final domains = ((net?['domains'] as List?) ?? const []).cast<Map>();
    return Scaffold(
      appBar: AppBar(title: Text(a.label)),
      body: ListView(padding: const EdgeInsets.all(16), children: [
        Row(children: [
          Icon(bad ? Icons.gpp_bad : Icons.warning_amber, color: bad ? Colors.red.shade800 : Colors.orange.shade800, size: 36),
          const SizedBox(width: 12),
          Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(a.impersonatedBrand != null ? l.appFakeTitle(a.impersonatedBrand!) : a.label,
                key: const Key('app-title'), style: Theme.of(context).textTheme.titleLarge),
            Text(a.packageName, style: Theme.of(context).textTheme.bodySmall),
          ])),
          Text('${a.score}', style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold, color: bad ? Colors.red.shade800 : Colors.orange.shade800)),
        ]),
        if (WhyPanel.from(a.explanation) case final why?) why,
        for (final r in a.reasons) Padding(padding: const EdgeInsets.only(top: 4), child: Text('• $r')),
        const Divider(height: 28),
        Text(l.appDetailAccess, style: Theme.of(context).textTheme.titleMedium),
        if (access.isEmpty) Text(l.appDetailNothing),
        for (final x in access) ListTile(dense: true, leading: const Icon(Icons.lock_open, size: 20), title: Text(x)),
        const Divider(height: 28),
        Text(l.appDetailTrackers, style: Theme.of(context).textTheme.titleMedium),
        if (_trackers == null) const LinearProgressIndicator()
        else if (_trackers!.isEmpty) Text(l.appDetailNoTrackers)
        else Wrap(spacing: 6, runSpacing: 4, children: [
          for (final t in _trackers!) Chip(key: Key('tracker-${t['name']}'), label: Text('${t['name']}'), visualDensity: VisualDensity.compact),
        ]),
        const Divider(height: 28),
        Text(l.appDetailTalksTo, style: Theme.of(context).textTheme.titleMedium),
        Text(l.appDetailTalksToBody, style: Theme.of(context).textTheme.bodySmall),
        if (net == null) const LinearProgressIndicator()
        else if (domains.isEmpty) Padding(padding: const EdgeInsets.only(top: 8), child: Text(l.appDetailNoNetwork))
        else ...[
          if (((net['malware'] as num?) ?? 0) > 0)
            Padding(padding: const EdgeInsets.only(top: 8), child: Text(l.appDetailMalware, style: TextStyle(color: Colors.red.shade800, fontWeight: FontWeight.bold))),
          Padding(padding: const EdgeInsets.only(top: 8),
              child: Text(l.appDetailAdServers(((net['ads'] as num?) ?? 0).toInt() + ((net['trackers'] as num?) ?? 0).toInt()))),
          for (final d in domains.take(15))
            ListTile(dense: true, leading: Icon(d['category'] == 'malware' ? Icons.dangerous : d['category'] == null ? Icons.language : Icons.campaign, size: 18),
                title: Text('${d['domain']}'), trailing: Text('${d['category'] ?? ''}')),
        ],
        const Divider(height: 28),
        if (_blocked) Card(color: Colors.blueGrey.shade50, child: Padding(padding: const EdgeInsets.all(12), child: Text(l.appBlockedNote))),
        Wrap(spacing: 8, runSpacing: 8, children: [
          FilledButton.icon(key: const Key('app-block'), onPressed: _toggleBlock, icon: Icon(_blocked ? Icons.wifi : Icons.wifi_off),
              label: Text(_blocked ? l.appUnblockInternet : l.appBlockInternet)),
          OutlinedButton.icon(key: const Key('app-revoke'), onPressed: () => NativeBridge.openAppPermissions(a.packageName),
              icon: const Icon(Icons.admin_panel_settings_outlined), label: Text(l.appRevoke)),
          OutlinedButton.icon(key: const Key('app-uninstall'), onPressed: () => widget.engine.uninstall(a.packageName),
              icon: const Icon(Icons.delete_outline), label: Text(l.uninstall)),
          TextButton(key: const Key('app-ignore'), onPressed: () async {
            await widget.engine.ignoreApp(a);
            await NativeBridge.appIgnore(a.packageName);
            if (context.mounted) {
              ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(l.appIgnored)));
              Navigator.pop(context);
            }
          }, child: Text(l.ignore)),
        ]),
      ]),
    );
  }
}
