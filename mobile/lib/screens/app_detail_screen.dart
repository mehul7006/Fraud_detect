import 'package:flutter/material.dart';

import '../core/native_bridge.dart';
import '../core/protection_engine.dart';
import '../l10n/l10n.dart';
import 'why_panel.dart';
import '../ui/components.dart';
import '../ui/theme.dart';

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
    final t = Theme.of(context);
    Widget section(String title, List<Widget> children, {String? sub}) => Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          SectionTitle(title, subtitle: sub),
          Card(child: Padding(padding: const EdgeInsets.fromLTRB(16, 12, 16, 14),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: children))),
        ]);
    return Scaffold(
      appBar: AppBar(title: Text(a.label)),
      body: ListView(padding: const EdgeInsets.fromLTRB(16, 4, 16, 32), children: [
        Container(
          padding: const EdgeInsets.all(20),
          decoration: BoxDecoration(gradient: bad ? AppColors.dangerGradient : Feature.apps.deepGradient, borderRadius: BorderRadius.circular(24)),
          child: Row(children: [
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(color: Colors.white.withValues(alpha: 0.18), borderRadius: BorderRadius.circular(16)),
              child: Icon(bad ? Icons.gpp_bad_rounded : Icons.warning_amber_rounded, color: Colors.white, size: 32),
            ),
            const SizedBox(width: 14),
            Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(a.impersonatedBrand != null ? l.appFakeTitle(a.impersonatedBrand!) : a.label,
                  key: const Key('app-title'), style: t.textTheme.titleLarge?.copyWith(color: Colors.white)),
              Text(a.packageName, style: t.textTheme.bodySmall?.copyWith(color: Colors.white)),
            ])),
            const SizedBox(width: 8),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              decoration: BoxDecoration(color: Colors.white.withValues(alpha: 0.18), borderRadius: BorderRadius.circular(14)),
              child: Text('${a.score}', style: t.textTheme.titleLarge?.copyWith(color: Colors.white)),
            ),
          ]),
        ),
        if (WhyPanel.from(a.explanation) case final why?) why,
        if (a.reasons.isNotEmpty) Card(child: Padding(padding: const EdgeInsets.fromLTRB(16, 8, 16, 14), child: Column(children: [
          for (final r in a.reasons) Padding(padding: const EdgeInsets.only(top: 8), child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Padding(padding: const EdgeInsets.only(top: 2), child: Icon(Icons.error_outline_rounded, size: 20, color: (bad ? Tone.danger : Tone.warn).fg(context))),
            const SizedBox(width: 10),
            Expanded(child: Text(r)),
          ])),
        ]))),
        section(l.appDetailAccess, [
          if (access.isEmpty) Text(l.appDetailNothing),
          for (final x in access) ListTile(dense: true, contentPadding: EdgeInsets.zero,
              leading: Icon(Icons.lock_open_rounded, size: 20, color: Tone.warn.fg(context)), title: Text(x)),
        ]),
        section(l.appDetailTrackers, [
          if (_trackers == null) const LinearProgressIndicator()
          else if (_trackers!.isEmpty) Row(children: [Icon(Icons.check_circle_rounded, color: Tone.safe.fg(context)), const SizedBox(width: 8), Expanded(child: Text(l.appDetailNoTrackers))])
          else Wrap(spacing: 6, runSpacing: 6, children: [
            for (final tr in _trackers!) Chip(key: Key('tracker-${tr['name']}'), avatar: Icon(Icons.visibility_rounded, size: 16, color: Tone.danger.fg(context)),
                label: Text('${tr['name']}'), visualDensity: VisualDensity.compact),
          ]),
        ]),
        section(l.appDetailTalksTo, sub: l.appDetailTalksToBody, [
          if (net == null) const LinearProgressIndicator()
          else if (domains.isEmpty) Text(l.appDetailNoNetwork)
          else ...[
            if (((net['malware'] as num?) ?? 0) > 0)
              Padding(padding: const EdgeInsets.only(bottom: 8), child: Pill(l.appDetailMalware, tone: Tone.danger, icon: Icons.dangerous_rounded)),
            Text(l.appDetailAdServers(((net['ads'] as num?) ?? 0).toInt() + ((net['trackers'] as num?) ?? 0).toInt())),
            for (final d in domains.take(15))
              ListTile(dense: true, contentPadding: EdgeInsets.zero,
                  leading: Icon(d['category'] == 'malware' ? Icons.dangerous_rounded : d['category'] == null ? Icons.language_rounded : Icons.campaign_rounded, size: 18,
                      color: d['category'] == 'malware' ? Tone.danger.fg(context) : null),
                  title: Text('${d['domain']}'), trailing: d['category'] == null ? null : Pill('${d['category']}', tone: d['category'] == 'malware' ? Tone.danger : Tone.warn)),
          ],
        ]),
        const SizedBox(height: 16),
        if (_blocked) Container(
          margin: const EdgeInsets.only(bottom: 12),
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(color: Tone.info.bg(context), borderRadius: BorderRadius.circular(18)),
          child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Icon(Icons.wifi_off_rounded, color: Tone.info.fg(context)), const SizedBox(width: 10),
            Expanded(child: Text(l.appBlockedNote, style: t.textTheme.bodyMedium?.copyWith(color: t.colorScheme.onSurface))),
          ]),
        ),
        FilledButton.icon(key: const Key('app-block'), onPressed: _toggleBlock, icon: Icon(_blocked ? Icons.wifi_rounded : Icons.wifi_off_rounded),
            label: Text(_blocked ? l.appUnblockInternet : l.appBlockInternet)),
        const SizedBox(height: 10),
        Row(children: [
          Expanded(child: OutlinedButton.icon(key: const Key('app-revoke'), onPressed: () => NativeBridge.openAppPermissions(a.packageName),
              icon: const Icon(Icons.admin_panel_settings_outlined), label: Text(l.appRevoke))),
          const SizedBox(width: 10),
          Expanded(child: OutlinedButton.icon(key: const Key('app-uninstall'),
              style: OutlinedButton.styleFrom(foregroundColor: Tone.danger.fg(context)),
              onPressed: () => widget.engine.uninstall(a.packageName),
              icon: const Icon(Icons.delete_outline_rounded), label: Text(l.uninstall))),
        ]),
        const SizedBox(height: 4),
        TextButton(key: const Key('app-ignore'), onPressed: () async {
          await widget.engine.ignoreApp(a);
          await NativeBridge.appIgnore(a.packageName);
          if (context.mounted) {
            ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(l.appIgnored)));
            Navigator.pop(context);
          }
        }, child: Text(l.ignore)),
      ]),
    );
  }
}
