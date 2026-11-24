import 'package:flutter/material.dart';

import '../core/flavour.dart';
import '../core/native_bridge.dart';
import '../l10n/l10n.dart';

/// 18.3 / 18.4: phone-wide blocking settings - category switches (the Play build has NO ads switch), pause,
/// apps and sites that are never blocked, "this app broke". Everything is stored and applied on the phone.
class BlockingScreen extends StatefulWidget {
  /// Tests only: pretend to be the other build.
  final bool? adsInBuild;
  const BlockingScreen({super.key, this.adsInBuild});
  @override
  State<BlockingScreen> createState() => _BlockingScreenState();
}

class _BlockingScreenState extends State<BlockingScreen> {
  Map<String, dynamic>? _s;
  final _site = TextEditingController();
  bool _busy = false;

  bool get _ads => (widget.adsInBuild ?? kAdBlockingInBuild) && _s?['adsAvailable'] == true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async => _apply(await NativeBridge.blockSettings());
  void _apply(Map<String, dynamic> s) { if (mounted) setState(() => _s = s); }

  List<String> _list(String k) => ((_s?[k] as List?) ?? const []).map((e) => '$e').toList();

  Future<void> _update() async {
    setState(() => _busy = true);
    final ok = await NativeBridge.blockSync();
    await _load();
    if (!mounted) return;
    setState(() => _busy = false);
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(ok ? context.l10n.blockUpdated : context.l10n.blockUpdateFailed)));
  }

  Future<void> _pickApp() async {
    final l = context.l10n;
    final apps = await NativeBridge.vpnTopApps();
    if (!mounted) return;
    final allowed = _list('allowedApps').toSet();
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (c) => SafeArea(
        child: ListView(shrinkWrap: true, padding: const EdgeInsets.all(8), children: [
          ListTile(title: Text(l.blockPickApp, style: Theme.of(c).textTheme.titleMedium), subtitle: Text(l.blockPickAppBody)),
          for (final a in apps.where((a) => a['package'] != null && !allowed.contains(a['package'])))
            ListTile(
              key: Key('pick-${a['package']}'),
              leading: const Icon(Icons.android),
              title: Text('${a['app']}'),
              subtitle: Wrap(spacing: 8, children: [
                TextButton(key: Key('allow-${a['package']}'), onPressed: () async {
                  Navigator.pop(c);
                  _apply(await NativeBridge.blockAllowApp('${a['package']}', true));
                }, child: Text(l.blockAllow)),
                TextButton(key: Key('broke-${a['package']}'), onPressed: () async {
                  Navigator.pop(c);
                  _apply(await NativeBridge.blockAppBroke('${a['package']}'));
                  if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(l.blockAppBrokeDone('${a['app']}'))));
                }, child: Text(l.blockAppBroke)),
              ]),
            ),
        ]),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    final s = _s;
    final cats = _list('categories').toSet();
    final paused = ((s?['pausedUntil'] as num?) ?? 0).toInt();
    final size = ((s?['listSize'] as num?) ?? 0).toInt();
    Widget sw(String cat, String title, String sub, IconData icon) => SwitchListTile(
          key: Key('block-cat-$cat'),
          secondary: Icon(icon),
          title: Text(title),
          subtitle: Text(sub),
          value: cats.contains(cat),
          onChanged: (v) async => _apply(await NativeBridge.blockSetCategory(cat, v)),
        );
    return Scaffold(
      appBar: AppBar(title: Text(_ads ? l.blockTitleWeb : l.blockTitlePlay)),
      body: s == null
          ? const Center(child: CircularProgressIndicator())
          : ListView(padding: const EdgeInsets.symmetric(vertical: 8), children: [
              Padding(padding: const EdgeInsets.fromLTRB(16, 8, 16, 4), child: Text(_ads ? l.blockIntroWeb : l.blockIntroPlay)),
              Padding(padding: const EdgeInsets.fromLTRB(16, 0, 16, 8), child: Text(l.blockNeedsVpn, style: Theme.of(context).textTheme.bodySmall)),
              ListTile(
                key: const Key('block-list'),
                leading: const Icon(Icons.list_alt),
                title: Text(size > 0 ? l.blockList(size) : l.blockListNone),
                trailing: _busy ? const SizedBox(width: 24, height: 24, child: CircularProgressIndicator(strokeWidth: 2))
                    : TextButton(key: const Key('block-update'), onPressed: _update, child: Text(l.blockUpdate)),
              ),
              const Divider(),
              if (_ads) sw('ads', l.blockCatAds, l.blockCatAdsSub, Icons.ad_units_outlined),
              sw('trackers', l.blockCatTrackers, l.blockCatTrackersSub, Icons.visibility_off_outlined),
              sw('malware', l.blockCatMalware, l.blockCatMalwareSub, Icons.gpp_bad_outlined),
              if (!_ads) Padding(padding: const EdgeInsets.fromLTRB(16, 4, 16, 8),
                  child: Text(l.blockAdsNotInPlay, key: const Key('block-no-ads'), style: Theme.of(context).textTheme.bodySmall)),
              const Divider(),
              if (paused > DateTime.now().millisecondsSinceEpoch)
                ListTile(
                  key: const Key('block-paused'),
                  leading: const Icon(Icons.pause_circle_outline),
                  title: Text(l.blockPausedUntil(TimeOfDay.fromDateTime(DateTime.fromMillisecondsSinceEpoch(paused)).format(context))),
                  trailing: TextButton(key: const Key('block-resume'), onPressed: () async => _apply(await NativeBridge.blockPause(0)), child: Text(l.blockResume)),
                )
              else
                ListTile(
                  key: const Key('block-pause'),
                  leading: const Icon(Icons.pause_circle_outline),
                  title: Text(l.blockPause5),
                  onTap: () async => _apply(await NativeBridge.blockPause(5)),
                ),
              ListTile(
                key: const Key('block-stats'),
                leading: const Icon(Icons.bar_chart),
                title: Text(l.blockStats),
                trailing: const Icon(Icons.chevron_right),
                onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const BlockingStatsScreen())),
              ),
              const Divider(),
              ListTile(title: Text(l.blockAllowedApps, style: const TextStyle(fontWeight: FontWeight.w600))),
              if (_list('allowedApps').isEmpty) ListTile(dense: true, title: Text(l.blockAllowedAppsNone)),
              for (final p in _list('allowedApps'))
                ListTile(
                  key: Key('allowed-app-$p'),
                  dense: true,
                  leading: const Icon(Icons.android),
                  title: Text(p),
                  trailing: IconButton(tooltip: l.blockRemove, icon: const Icon(Icons.close),
                      onPressed: () async => _apply(await NativeBridge.blockAllowApp(p, false))),
                ),
              ListTile(key: const Key('block-allow-app'), leading: const Icon(Icons.add), title: Text(l.blockAllowApp), onTap: _pickApp),
              const Divider(),
              ListTile(title: Text(l.blockAllowedSites, style: const TextStyle(fontWeight: FontWeight.w600))),
              for (final d in _list('allowedSites'))
                ListTile(
                  key: Key('allowed-site-$d'),
                  dense: true,
                  leading: const Icon(Icons.language),
                  title: Text(d),
                  trailing: IconButton(tooltip: l.blockRemove, icon: const Icon(Icons.close),
                      onPressed: () async => _apply(await NativeBridge.blockAllowSite(d, false))),
                ),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: Row(children: [
                  Expanded(child: TextField(key: const Key('block-site-input'), controller: _site,
                      decoration: InputDecoration(labelText: l.blockAddSite, hintText: l.blockSiteHint))),
                  const SizedBox(width: 8),
                  FilledButton(key: const Key('block-site-add'), onPressed: () async {
                    final d = _site.text.trim();
                    if (d.isEmpty) return;
                    _site.clear();
                    _apply(await NativeBridge.blockAllowSite(d, true));
                  }, child: Text(l.blockAdd)),
                ]),
              ),
              const SizedBox(height: 24),
            ]),
    );
  }
}

/// 18.5: what was blocked - per type, per app, top sites, estimated data saved. Counted on the phone only.
class BlockingStatsScreen extends StatefulWidget {
  const BlockingStatsScreen({super.key});
  @override
  State<BlockingStatsScreen> createState() => _BlockingStatsScreenState();
}

class _BlockingStatsScreenState extends State<BlockingStatsScreen> {
  int _days = 7;

  static String size(num bytes) {
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(0)} KB';
    if (bytes < 1024 * 1024 * 1024) return '${(bytes / 1024 / 1024).toStringAsFixed(1)} MB';
    return '${(bytes / 1024 / 1024 / 1024).toStringAsFixed(2)} GB';
  }

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    final catName = {'ads': l.blockCatAds, 'trackers': l.blockCatTrackers, 'malware': l.blockCatMalware};
    return Scaffold(
      appBar: AppBar(title: Text(l.blockStats)),
      body: FutureBuilder<Map<String, dynamic>>(
        key: ValueKey(_days),
        future: NativeBridge.blockStats(_days),
        builder: (c, snap) {
          if (!snap.hasData) return const Center(child: CircularProgressIndicator());
          final s = snap.data!;
          final total = ((s['total'] as num?) ?? 0).toInt();
          final byCat = Map<String, dynamic>.from((s['byCategory'] as Map?) ?? const {});
          final apps = ((s['byApp'] as List?) ?? const []).map((e) => Map<String, dynamic>.from(e as Map)).toList();
          final top = ((s['topDomains'] as List?) ?? const []).map((e) => Map<String, dynamic>.from(e as Map)).toList();
          return ListView(padding: const EdgeInsets.all(8), children: [
            Wrap(spacing: 8, children: [
              for (final d in const [1, 7, 30])
                ChoiceChip(key: Key('days-$d'), label: Text(l.blockStatsDays(d)), selected: _days == d, onSelected: (_) => setState(() => _days = d)),
            ]),
            ListTile(
              key: const Key('stats-total'),
              leading: const Icon(Icons.block),
              title: Text(l.blockStatsTotal(total), style: Theme.of(context).textTheme.titleMedium),
              subtitle: Text(l.blockStatsSaved(size((s['estimatedBytesSaved'] as num?) ?? 0))),
            ),
            Padding(padding: const EdgeInsets.symmetric(horizontal: 16), child: Text(l.blockStatsLocal, style: Theme.of(context).textTheme.bodySmall)),
            if (total == 0) Padding(padding: const EdgeInsets.all(16), child: Text(l.blockStatsEmpty)),
            if (total > 0) ...[
              ListTile(title: Text(l.blockStatsByCat, style: const TextStyle(fontWeight: FontWeight.w600))),
              for (final e in byCat.entries.where((e) => (e.value as num) > 0))
                ListTile(key: Key('stats-cat-${e.key}'), dense: true, title: Text(catName[e.key] ?? e.key), trailing: Text('${e.value}')),
              ListTile(title: Text(l.blockStatsByApp, style: const TextStyle(fontWeight: FontWeight.w600))),
              for (final a in apps.take(20))
                ListTile(key: Key('stats-app-${a['package'] ?? a['uid']}'), dense: true, leading: const Icon(Icons.android),
                    title: Text('${a['label']}'), trailing: Text('${a['count']}')),
              ListTile(title: Text(l.blockStatsTop, style: const TextStyle(fontWeight: FontWeight.w600))),
              for (final d in top)
                ListTile(dense: true, leading: const Icon(Icons.language, size: 18), title: Text('${d['domain']}'),
                    subtitle: Text(catName['${d['category']}'] ?? ''), trailing: Text('${d['count']}')),
            ],
          ]);
        },
      ),
    );
  }
}
