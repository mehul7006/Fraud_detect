import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';

import '../core/flavour.dart';
import '../core/native_bridge.dart';
import '../l10n/l10n.dart';
import 'blocking_screen.dart';
import 'vpn_consent_screen.dart';

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
    return Scaffold(
      appBar: AppBar(title: Text(l.vpnTitle)),
      body: RefreshIndicator(
        onRefresh: _refresh,
        child: ListView(padding: const EdgeInsets.all(16), children: [
          Card(
            key: Key('vpn-state-$state'),
            color: good ? Colors.green.shade50 : Colors.orange.shade50,
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Row(children: [
                  Icon(good ? Icons.vpn_lock : Icons.warning_amber, color: good ? Colors.green.shade800 : Colors.orange.shade900, size: 32),
                  const SizedBox(width: 12),
                  Expanded(child: Text(title, style: Theme.of(context).textTheme.titleLarge)),
                ]),
                if (body != null) ...[const SizedBox(height: 8), Text(body)],
                const SizedBox(height: 12),
                Wrap(spacing: 8, runSpacing: 8, children: [
                  if (on)
                    OutlinedButton(key: const Key('vpn-off'), onPressed: () async { await NativeBridge.vpnStop(); await _refresh(); }, child: Text(l.vpnTurnOff))
                  else
                    FilledButton(key: const Key('vpn-on'), onPressed: _turnOn, child: Text(l.vpnTurnOn)),
                  if (fix != null) OutlinedButton(key: const Key('vpn-fix'), onPressed: fix.$2, child: Text(fix.$1)),
                ]),
              ]),
            ),
          ),
          // 12.9: on Xiaomi / Oppo / Vivo ... the maker's Autostart switch blocks the restart after reboot / update
          if (on && _s['autostartMaker'] != null)
            Card(
              key: const Key('vpn-keep-on'),
              color: Colors.blue.shade50,
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(l.vpnKeepOnTitle, style: Theme.of(context).textTheme.titleMedium),
                  const SizedBox(height: 4),
                  Text(l.vpnKeepOnBody),
                  const SizedBox(height: 8),
                  Wrap(spacing: 8, runSpacing: 8, children: [
                    OutlinedButton(key: const Key('vpn-always-on'), onPressed: NativeBridge.openVpnSettings, child: Text(l.vpnAlwaysOn)),
                    OutlinedButton(key: const Key('vpn-autostart'), onPressed: NativeBridge.openAutostartSettings, child: Text(l.vpnAutostart)),
                  ]),
                ]),
              ),
            ),
          if (on) ...[
            ListTile(leading: const Icon(Icons.travel_explore), title: Text(l.vpnStats((_s['queries'] as num?)?.toInt() ?? 0))),
            ListTile(leading: Icon(Icons.block, color: Colors.red.shade800), title: Text(l.vpnBlockedCount((_s['blocked'] as num?)?.toInt() ?? 0))),
          ],
          ListTile(leading: const Icon(Icons.list_alt), title: Text(l.vpnListSize((_s['blocklistSize'] as num?)?.toInt() ?? 0))),
          const Divider(),
          Text(l.vpnRecent, style: Theme.of(context).textTheme.titleMedium),
          if (recent.isEmpty) Padding(padding: const EdgeInsets.all(8), child: Text(l.vpnNoneBlocked)),
          for (final b in recent.take(10))
            ListTile(
              dense: true,
              leading: Icon(Icons.gpp_bad, color: Colors.red.shade800),
              title: Text('${b['domain']}'),
              subtitle: Text([if ('${b['brand']}'.isNotEmpty) '≠ ${b['brand']}', if ('${b['app']}'.isNotEmpty) '${b['app']}'].join(' · ')),
            ),
          const Divider(),
          SwitchListTile(
            key: const Key('vpn-local-only'),
            title: Text(l.vpnLocalOnly), subtitle: Text(l.vpnLocalOnlyBody),
            value: _s['localOnly'] == true, onChanged: (v) => _set(localOnly: v),
          ),
          SwitchListTile(
            key: const Key('vpn-block-doh'),
            title: Text(l.vpnBlockDoh), subtitle: Text(l.vpnBlockDohBody),
            value: _s['blockDoh'] != false, onChanged: (v) => _set(blockDoh: v),
          ),
          ListTile(
            title: Text(l.vpnUpstream),
            // below the label, full width: beside it the long Hindi names do not fit
            subtitle: DropdownButton<String>(
              key: const Key('vpn-upstream'),
              isExpanded: true,
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
          const Divider(),
          ListTile(
            key: const Key('vpn-top-apps'),
            leading: const Icon(Icons.apps),
            title: Text(l.vpnTopApps),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const TopAppsScreen())),
          ),
          ListTile( // 18.3: phone-wide blocking (Play build: trackers + malware; website build: + ads)
            key: const Key('vpn-blocking'),
            leading: const Icon(Icons.shield_outlined),
            title: Text(kAdBlockingInBuild ? l.blockTitleWeb : l.blockTitlePlay),
            subtitle: Text(l.blockVpnCount(((_s['adBlocked'] as num?) ?? 0).toInt())),
            trailing: const Icon(Icons.chevron_right),
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
      appBar: AppBar(title: Text(l.vpnTopApps)),
      body: FutureBuilder<List<Map<String, dynamic>>>(
        future: NativeBridge.vpnTopApps(),
        builder: (c, s) {
          if (!s.hasData) return const Center(child: CircularProgressIndicator());
          final apps = s.data!;
          return ListView(padding: const EdgeInsets.all(8), children: [
            Padding(padding: const EdgeInsets.all(8), child: Text(l.vpnTopAppsBody)),
            if (apps.isEmpty) Padding(padding: const EdgeInsets.all(16), child: Text(l.vpnNoTraffic)),
            for (final a in apps)
              ExpansionTile(
                key: Key('top-app-${a['package'] ?? a['uid']}'),
                leading: const Icon(Icons.android),
                title: Text('${a['app']}'),
                subtitle: Text('${l.vpnSitesCount((a['domains'] as num?)?.toInt() ?? 0)} · ↓ ${_bytes(a['bytesRx'] as num?)} ↑ ${_bytes(a['bytesTx'] as num?)}'),
                children: [
                  for (final d in ((a['topDomains'] as List?) ?? const []))
                    ListTile(dense: true, leading: const Icon(Icons.language, size: 18), title: Text('$d')),
                ],
              ),
          ]);
        },
      ),
    );
  }
}
