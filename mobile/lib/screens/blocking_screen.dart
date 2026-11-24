import 'package:flutter/material.dart';

import '../core/flavour.dart';
import '../core/native_bridge.dart';
import '../l10n/l10n.dart';
import '../ui/components.dart';
import '../ui/theme.dart';

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
    final t = Theme.of(context);
    Widget sw(String cat, String title, String sub, IconData icon, Feature f) => SwitchListTile(
          key: Key('block-cat-$cat'),
          secondary: FeatureIcon(icon, f, size: 40),
          title: Text(title),
          subtitle: Text(sub),
          value: cats.contains(cat),
          onChanged: (v) async => _apply(await NativeBridge.blockSetCategory(cat, v)),
        );
    Widget small(String text, {Key? key}) => Padding(padding: const EdgeInsets.fromLTRB(16, 4, 16, 12),
        child: Text(text, key: key, style: t.textTheme.bodySmall?.copyWith(color: t.colorScheme.onSurfaceVariant)));
    return Scaffold(
      appBar: AppBar(), // the banner below carries the title
      body: s == null
          ? const Center(child: CircularProgressIndicator())
          : ListView(padding: const EdgeInsets.fromLTRB(16, 4, 16, 32), children: [
              ScreenBanner(icon: Icons.shield_rounded, feature: Feature.web,
                  title: _ads ? l.blockTitleWeb : l.blockTitlePlay, subtitle: _ads ? l.blockIntroWeb : l.blockIntroPlay),
              Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(color: Tone.info.bg(context), borderRadius: BorderRadius.circular(18)),
                child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Icon(Icons.info_outline_rounded, color: Tone.info.fg(context), size: 20),
                  const SizedBox(width: 10),
                  Expanded(child: Text(l.blockNeedsVpn, style: t.textTheme.bodyMedium?.copyWith(color: t.colorScheme.onSurface))),
                ]),
              ),
              const SizedBox(height: 8),
              Card(child: ListTile(
                key: const Key('block-list'),
                leading: const FeatureIcon(Icons.list_alt_rounded, Feature.device, size: 40),
                title: Text(size > 0 ? l.blockList(size) : l.blockListNone),
                trailing: _busy ? const SizedBox(width: 24, height: 24, child: CircularProgressIndicator(strokeWidth: 2))
                    : TextButton(key: const Key('block-update'), onPressed: _update, child: Text(l.blockUpdate)),
              )),
              Card(child: Column(children: [
                if (_ads) sw('ads', l.blockCatAds, l.blockCatAdsSub, Icons.ad_units_rounded, Feature.apps),
                sw('trackers', l.blockCatTrackers, l.blockCatTrackersSub, Icons.visibility_off_rounded, Feature.message),
                sw('malware', l.blockCatMalware, l.blockCatMalwareSub, Icons.gpp_bad_rounded, Feature.scam),
                if (!_ads) small(l.blockAdsNotInPlay, key: const Key('block-no-ads')),
              ])),
              Card(child: Column(children: [
                if (paused > DateTime.now().millisecondsSinceEpoch)
                  ListTile(
                    key: const Key('block-paused'),
                    leading: const FeatureIcon(Icons.pause_rounded, Feature.cleaner, size: 40),
                    title: Text(l.blockPausedUntil(TimeOfDay.fromDateTime(DateTime.fromMillisecondsSinceEpoch(paused)).format(context))),
                    trailing: TextButton(key: const Key('block-resume'), onPressed: () async => _apply(await NativeBridge.blockPause(0)), child: Text(l.blockResume)),
                  )
                else
                  ListTile(
                    key: const Key('block-pause'),
                    leading: const FeatureIcon(Icons.pause_rounded, Feature.cleaner, size: 40),
                    title: Text(l.blockPause5),
                    onTap: () async => _apply(await NativeBridge.blockPause(5)),
                  ),
                ListTile(
                  key: const Key('block-stats'),
                  leading: const FeatureIcon(Icons.bar_chart_rounded, Feature.web, size: 40),
                  title: Text(l.blockStats),
                  trailing: const Icon(Icons.chevron_right_rounded),
                  onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const BlockingStatsScreen())),
                ),
              ])),
              SectionTitle(l.blockAllowedApps),
              Card(child: Column(children: [
                if (_list('allowedApps').isEmpty) ListTile(dense: true, title: Text(l.blockAllowedAppsNone)),
                for (final p in _list('allowedApps'))
                  ListTile(
                    key: Key('allowed-app-$p'),
                    dense: true,
                    leading: const Icon(Icons.android_rounded),
                    title: Text(p),
                    trailing: IconButton(tooltip: l.blockRemove, icon: const Icon(Icons.close),
                        onPressed: () async => _apply(await NativeBridge.blockAllowApp(p, false))),
                  ),
                ListTile(key: const Key('block-allow-app'), leading: Icon(Icons.add_circle_rounded, color: t.colorScheme.primary),
                    title: Text(l.blockAllowApp, style: TextStyle(color: t.colorScheme.primary, fontWeight: FontWeight.w600)), onTap: _pickApp),
              ])),
              SectionTitle(l.blockAllowedSites),
              Card(child: Padding(
                padding: const EdgeInsets.only(bottom: 14),
                child: Column(children: [
                  for (final d in _list('allowedSites'))
                    ListTile(
                      key: Key('allowed-site-$d'),
                      dense: true,
                      leading: const Icon(Icons.language_rounded),
                      title: Text(d),
                      trailing: IconButton(tooltip: l.blockRemove, icon: const Icon(Icons.close),
                          onPressed: () async => _apply(await NativeBridge.blockAllowSite(d, false))),
                    ),
                  Padding(
                    padding: const EdgeInsets.fromLTRB(14, 14, 14, 0),
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
                ]),
              )),
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
          final t = Theme.of(context);
          Widget head(String text) => SectionTitle(text);
          return ListView(padding: const EdgeInsets.fromLTRB(16, 4, 16, 32), children: [
            Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(gradient: Feature.web.deepGradient, borderRadius: BorderRadius.circular(24)),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Wrap(spacing: 8, runSpacing: 8, children: [
                  for (final d in const [1, 7, 30])
                    ChoiceChip(key: Key('days-$d'), label: Text(l.blockStatsDays(d)), selected: _days == d, onSelected: (_) => setState(() => _days = d)),
                ]),
                const SizedBox(height: 8),
                ListTile(
                  key: const Key('stats-total'),
                  contentPadding: EdgeInsets.zero,
                  leading: const Icon(Icons.block_rounded, color: Colors.white, size: 32),
                  title: Text(l.blockStatsTotal(total), style: t.textTheme.titleLarge?.copyWith(color: Colors.white)),
                  subtitle: Text(l.blockStatsSaved(size((s['estimatedBytesSaved'] as num?) ?? 0)), style: t.textTheme.bodyMedium?.copyWith(color: Colors.white)),
                ),
              ]),
            ),
            Padding(padding: const EdgeInsets.fromLTRB(4, 10, 4, 0),
                child: Row(children: [
                  Icon(Icons.lock_rounded, size: 16, color: t.colorScheme.onSurfaceVariant), const SizedBox(width: 6),
                  Expanded(child: Text(l.blockStatsLocal, style: t.textTheme.bodySmall?.copyWith(color: t.colorScheme.onSurfaceVariant))),
                ])),
            if (total == 0) Card(margin: const EdgeInsets.only(top: 16), child: ListTile(leading: Icon(Icons.check_circle_rounded, color: Tone.safe.fg(context)), title: Text(l.blockStatsEmpty))),
            if (total > 0) ...[
              head(l.blockStatsByCat),
              Card(child: Column(children: [
                for (final e in byCat.entries.where((e) => (e.value as num) > 0))
                  ListTile(key: Key('stats-cat-${e.key}'), dense: true, title: Text(catName[e.key] ?? e.key),
                      trailing: Pill('${e.value}', tone: e.key == 'malware' ? Tone.danger : e.key == 'ads' ? Tone.warn : Tone.info)),
              ])),
              head(l.blockStatsByApp),
              Card(child: Column(children: [
                for (final a in apps.take(20))
                  ListTile(key: Key('stats-app-${a['package'] ?? a['uid']}'), dense: true, leading: const Icon(Icons.android_rounded),
                      title: Text('${a['label']}'), trailing: Pill('${a['count']}')),
              ])),
              head(l.blockStatsTop),
              Card(child: Column(children: [
                for (final d in top)
                  ListTile(dense: true, leading: const Icon(Icons.language_rounded, size: 18), title: Text('${d['domain']}'),
                      subtitle: Text(catName['${d['category']}'] ?? ''), trailing: Pill('${d['count']}')),
              ])),
            ],
          ]);
        },
      ),
    );
  }
}
