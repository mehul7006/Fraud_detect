import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';

import '../core/flavour.dart';
import '../core/native_bridge.dart';
import '../l10n/l10n.dart';
import 'blocking_screen.dart';
import 'vpn_consent_screen.dart';
import '../ui/components.dart';
import '../ui/theme.dart';

/// 12.7 / 12.8 / 12.10: the protection screen - on/off, what is wrong and how to fix it (another VPN, Private DNS,
/// switched off in settings), counters, recent blocks, privacy options, and the per-app view.
class VpnStateScreen extends StatefulWidget {
  const VpnStateScreen({super.key});
  @override
  State<VpnStateScreen> createState() => _VpnStateScreenState();
}

class _VpnStateScreenState extends State<VpnStateScreen> with WidgetsBindingObserver {
  Map<String, dynamic> _s = const {};
  Timer? _poll;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _refresh();
    _poll = Timer.periodic(const Duration(seconds: 3), (_) => _refresh());
  }

  @override
  void dispose() {
    _poll?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState s) { if (s == AppLifecycleState.resumed) _refresh(); } // back from settings

  Future<void> _refresh() async {
    try {
      final s = await NativeBridge.vpnStatus();
      if (mounted) setState(() => _s = s);
    } catch (_) {}
  }

  Future<void> _turnOn() async {
    if (_s['prepared'] != true) {
      final ok = await Navigator.push<bool>(context, MaterialPageRoute(builder: (_) => const VpnConsentScreen()));
      if (ok != true) return;
    }
    await NativeBridge.vpnStart();
    await Future<void>.delayed(const Duration(milliseconds: 600));
    await _refresh();
  }

  Future<void> _set({bool? localOnly, bool? blockDoh, String? upstream}) async {
    await NativeBridge.vpnSetOptions(localOnly: localOnly, blockDoh: blockDoh, upstream: upstream);
    await _refresh();
  }

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    final state = (_s['state'] as String?) ?? 'OFF';
    final on = _s['running'] == true;
    final recent = (() {
      try { return (jsonDecode((_s['recentBlocks'] as String?) ?? '[]') as List).cast<Map>(); } catch (_) { return <Map>[]; }
    })();
    final (title, body, fix) = switch (state) {
      'ON' => (l.vpnStateOn, null, null),
      'OTHER_VPN' => (l.vpnStateOtherVpn, l.vpnStateOtherVpnBody, (l.vpnOpenVpnSettings, NativeBridge.openVpnSettings)),
      'PRIVATE_DNS' => (l.vpnStatePrivateDns, l.vpnStatePrivateDnsBody('${_s['privateDns'] ?? ''}'), (l.vpnOpenDnsSettings, NativeBridge.openPrivateDnsSettings)),
      'REVOKED' || 'NOT_ALLOWED' => (l.vpnStateRevoked, l.vpnStateRevokedBody, (l.vpnOpenVpnSettings, NativeBridge.openVpnSettings)),
      'NO_CONSENT' => (l.vpnStateOff, l.vpnStateNoConsent, null),
      _ => (l.vpnStateOff, null, null),
    };
    final good = state == 'ON';
    final t = Theme.of(context);
    Widget stat(IconData icon, Feature f, String text) => Card(
          child: ListTile(leading: FeatureIcon(icon, f, size: 38), title: Text(text, style: t.textTheme.bodyLarge?.copyWith(fontWeight: FontWeight.w600))),
        );
    return Scaffold(
      appBar: AppBar(title: Text(l.vpnTitle)),
      body: RefreshIndicator(
        onRefresh: _refresh,
        child: ListView(padding: const EdgeInsets.fromLTRB(16, 4, 16, 32), children: [
          Card(
            key: Key('vpn-state-$state'),
            child: Container(
              decoration: BoxDecoration(gradient: good ? Feature.web.deepGradient : (state == 'OFF' || state == 'NO_CONSENT') ? Feature.history.deepGradient : Feature.apps.deepGradient),
              padding: const EdgeInsets.all(20),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Row(children: [
                  Container(
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(color: Colors.white.withValues(alpha: 0.18), borderRadius: BorderRadius.circular(16)),
                    child: Icon(good ? Icons.vpn_lock_rounded : Icons.warning_amber_rounded, color: Colors.white, size: 32),
                  ),
                  const SizedBox(width: 14),
                  Expanded(child: Text(title, style: t.textTheme.titleLarge?.copyWith(color: Colors.white))),
                ]),
                if (body != null) ...[const SizedBox(height: 10), Text(body, style: t.textTheme.bodyMedium?.copyWith(color: Colors.white))],
                const SizedBox(height: 16),
                Wrap(spacing: 10, runSpacing: 10, children: [
                  if (on)
                    OutlinedButton(key: const Key('vpn-off'),
                        style: OutlinedButton.styleFrom(foregroundColor: Colors.white, side: const BorderSide(color: Colors.white, width: 1.4)),
                        onPressed: () async { await NativeBridge.vpnStop(); await _refresh(); }, child: Text(l.vpnTurnOff))
                  else
                    FilledButton(key: const Key('vpn-on'),
                        style: FilledButton.styleFrom(backgroundColor: Colors.white, foregroundColor: AppColors.indigo),
                        onPressed: _turnOn, child: Text(l.vpnTurnOn)),
                  if (fix != null) OutlinedButton(key: const Key('vpn-fix'),
                      style: OutlinedButton.styleFrom(foregroundColor: Colors.white, side: const BorderSide(color: Colors.white, width: 1.4)),
                      onPressed: fix.$2, child: Text(fix.$1)),
                ]),
              ]),
            ),
          ),
          // 12.9: on Xiaomi / Oppo / Vivo ... the maker's Autostart switch blocks the restart after reboot / update
          if (on && _s['autostartMaker'] != null)
            Card(
              key: const Key('vpn-keep-on'),
              color: Tone.info.bg(context),
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Row(children: [
                    Icon(Icons.restart_alt_rounded, color: Tone.info.fg(context)),
                    const SizedBox(width: 10),
                    Expanded(child: Text(l.vpnKeepOnTitle, style: t.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700))),
                  ]),
                  const SizedBox(height: 6),
                  Text(l.vpnKeepOnBody),
                  const SizedBox(height: 10),
                  Wrap(spacing: 8, runSpacing: 8, children: [
                    OutlinedButton(key: const Key('vpn-always-on'), onPressed: NativeBridge.openVpnSettings, child: Text(l.vpnAlwaysOn)),
                    OutlinedButton(key: const Key('vpn-autostart'), onPressed: NativeBridge.openAutostartSettings, child: Text(l.vpnAutostart)),
                  ]),
                ]),
              ),
            ),
          const SizedBox(height: 6),
          if (on) ...[
            stat(Icons.travel_explore_rounded, Feature.web, l.vpnStats((_s['queries'] as num?)?.toInt() ?? 0)),
            stat(Icons.block_rounded, Feature.scam, l.vpnBlockedCount((_s['blocked'] as num?)?.toInt() ?? 0)),
          ],
          stat(Icons.list_alt_rounded, Feature.device, l.vpnListSize((_s['blocklistSize'] as num?)?.toInt() ?? 0)),
          SectionTitle(l.vpnRecent),
          Card(
            child: Column(children: [
              if (recent.isEmpty)
                ListTile(leading: Icon(Icons.check_circle_rounded, color: Tone.safe.fg(context)), title: Text(l.vpnNoneBlocked)),
              for (final b in recent.take(10))
                ListTile(
                  dense: true,
                  leading: Icon(Icons.gpp_bad_rounded, color: Tone.danger.fg(context)),
                  title: Text('${b['domain']}'),
                  subtitle: Text([if ('${b['brand']}'.isNotEmpty) '≠ ${b['brand']}', if ('${b['app']}'.isNotEmpty) '${b['app']}'].join(' · ')),
                ),
            ]),
          ),
          SectionTitle(l.ui1VpnOptions),
          Card(
            child: Column(children: [
              SwitchListTile(
                key: const Key('vpn-local-only'),
                secondary: const FeatureIcon(Icons.phonelink_lock_rounded, Feature.device, size: 38),
                title: Text(l.vpnLocalOnly), subtitle: Text(l.vpnLocalOnlyBody),
                value: _s['localOnly'] == true, onChanged: (v) => _set(localOnly: v),
              ),
              SwitchListTile(
                key: const Key('vpn-block-doh'),
                secondary: const FeatureIcon(Icons.dns_rounded, Feature.web, size: 38),
                title: Text(l.vpnBlockDoh), subtitle: Text(l.vpnBlockDohBody),
                value: _s['blockDoh'] != false, onChanged: (v) => _set(blockDoh: v),
              ),
              ListTile(
                leading: const FeatureIcon(Icons.router_rounded, Feature.files, size: 38),
                title: Text(l.vpnUpstream),
                // below the label, full width: beside it the long Hindi names do not fit
                subtitle: DropdownButton<String>(
                  key: const Key('vpn-upstream'),
                  isExpanded: true,
                  underline: const SizedBox.shrink(),
                  value: (_s['upstream'] as String?) ?? 'system',
                  items: [
                    DropdownMenuItem(value: 'system', child: Text(l.vpnUpstreamSystem)),
                    const DropdownMenuItem(value: 'cloudflare', child: Text('Cloudflare 1.1.1.1')),
                    const DropdownMenuItem(value: 'google', child: Text('Google 8.8.8.8')),
                    const DropdownMenuItem(value: 'quad9', child: Text('Quad9 9.9.9.9')),
                  ],
                  onChanged: (v) => _set(upstream: v),
                ),
              ),
            ]),
          ),
          const SizedBox(height: 8),
          FeatureTile(
            tileKey: const Key('vpn-top-apps'),
            icon: Icons.apps_rounded, feature: Feature.apps,
            title: l.vpnTopApps,
            trailingIcon: Icons.chevron_right_rounded,
            onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const TopAppsScreen())),
          ),
          FeatureTile( // 18.3: phone-wide blocking (Play build: trackers + malware; website build: + ads)
            tileKey: const Key('vpn-blocking'),
            icon: Icons.shield_rounded, feature: Feature.scam,
            title: kAdBlockingInBuild ? l.blockTitleWeb : l.blockTitlePlay,
            status: l.blockVpnCount(((_s['adBlocked'] as num?) ?? 0).toInt()),
            trailingIcon: Icons.chevron_right_rounded,
            onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const BlockingScreen())),
          ),
        ]),
      ),
    );
  }
}

/// 12.8: per-app view - which sites each app looked up, and its data use (Android's counters).
class TopAppsScreen extends StatelessWidget {
  const TopAppsScreen({super.key});

  static String _bytes(num? b) {
    if (b == null) return '–';
    if (b < 1024 * 1024) return '${(b / 1024).toStringAsFixed(0)} KB';
    if (b < 1024 * 1024 * 1024) return '${(b / 1024 / 1024).toStringAsFixed(1)} MB';
    return '${(b / 1024 / 1024 / 1024).toStringAsFixed(2)} GB';
  }

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    return Scaffold(
      appBar: AppBar(), // the banner below carries the title
      body: FutureBuilder<List<Map<String, dynamic>>>(
        future: NativeBridge.vpnTopApps(),
        builder: (c, s) {
          if (!s.hasData) return const Center(child: CircularProgressIndicator());
          final apps = s.data!;
          return ListView(padding: const EdgeInsets.fromLTRB(16, 4, 16, 24), children: [
            ScreenBanner(icon: Icons.apps_rounded, feature: Feature.apps, title: l.vpnTopApps, subtitle: l.vpnTopAppsBody),
            if (apps.isEmpty) Card(child: ListTile(leading: const Icon(Icons.hourglass_empty_rounded), title: Text(l.vpnNoTraffic))),
            for (final a in apps)
              Card(child: ExpansionTile(
                key: Key('top-app-${a['package'] ?? a['uid']}'),
                shape: const RoundedRectangleBorder(side: BorderSide.none),
                leading: const FeatureIcon(Icons.android_rounded, Feature.apps, size: 38),
                title: Text('${a['app']}'),
                subtitle: Text('${l.vpnSitesCount((a['domains'] as num?)?.toInt() ?? 0)} · ↓ ${_bytes(a['bytesRx'] as num?)} ↑ ${_bytes(a['bytesTx'] as num?)}'),
                children: [
                  for (final d in ((a['topDomains'] as List?) ?? const []))
                    ListTile(dense: true, leading: const Icon(Icons.language_rounded, size: 18), title: Text('$d')),
                ],
              )),
          ]);
        },
      ),
    );
  }
}
