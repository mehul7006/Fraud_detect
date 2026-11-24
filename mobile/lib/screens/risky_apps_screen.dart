import 'package:flutter/material.dart';
import '../core/protection_engine.dart';
import '../l10n/l10n.dart';
import 'app_detail_screen.dart';
import '../ui/components.dart';
import '../ui/theme.dart';

/// 6.8: every flagged app with its reasons, highest risk first. Uninstall / Ignore per app (6.7).
class RiskyAppsScreen extends StatefulWidget {
  final ProtectionEngine engine;
  const RiskyAppsScreen({super.key, required this.engine});
  @override
  State<RiskyAppsScreen> createState() => _RiskyAppsScreenState();
}

class _RiskyAppsScreenState extends State<RiskyAppsScreen> {
  bool _loading = false;
  bool _share = false;

  @override
  void initState() {
    super.initState();
    widget.engine.settings.shareAppLabels().then((v) { if (mounted) setState(() => _share = v); });
  }

  Future<void> _setShare(bool on) async {
    await widget.engine.settings.setShareAppLabels(on);
    if (mounted) setState(() => _share = on);
  }

  Future<void> _refresh() async {
    setState(() => _loading = true);
    await widget.engine.checkApps();
    if (mounted) setState(() => _loading = false);
  }

  @override
  Widget build(BuildContext context) {
    final status = widget.engine.status;
    final l = context.l10n;
    return Scaffold(
      appBar: AppBar(), // the banner below carries the title
      body: ValueListenableBuilder<List<RiskyApp>>(
        valueListenable: widget.engine.riskyApps,
        builder: (c, apps, _) {
          final failed = status.value['apps'] == 'ERROR';
          return RefreshIndicator(
            onRefresh: _refresh,
            child: ListView(padding: const EdgeInsets.fromLTRB(16, 4, 16, 32), children: [
              ScreenBanner(icon: Icons.ads_click_rounded, feature: Feature.apps, title: l.riskyTitle),
              if (_loading) const Padding(padding: EdgeInsets.only(bottom: 8), child: LinearProgressIndicator(minHeight: 6, borderRadius: BorderRadius.all(Radius.circular(6)))),
              if (failed)
                _Info(Icons.cloud_off_rounded, l.riskyFailedTitle, l.riskyFailedBody, Tone.info)
              else if (status.value['apps'] == null)
                _Info(Icons.hourglass_top_rounded, l.riskyCheckingTitle, l.riskyCheckingBody, Tone.neutral)
              else if (apps.isEmpty)
                _Info(Icons.verified_user_rounded, l.riskyNoneTitle, l.riskyNoneBody, Tone.safe),
              for (final a in apps) _AppCard(app: a, engine: widget.engine),
              const SizedBox(height: 12),
              // 6.1: opt-in, off by default
              Card(child: SwitchListTile(
                key: const Key('share-app-labels'),
                value: _share,
                onChanged: _setShare,
                secondary: const FeatureIcon(Icons.share_rounded, Feature.device, size: 40),
                title: Text(l.riskyShareTitle),
                subtitle: Text(l.riskyShareBody),
              )),
            ]),
          );
        },
      ),
    );
  }
}

class _Info extends StatelessWidget {
  final IconData icon;
  final String title, body;
  final Tone tone;
  const _Info(this.icon, this.title, this.body, this.tone);
  @override
  Widget build(BuildContext context) => Card(
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 32, horizontal: 20),
          child: Column(children: [
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(color: tone.bg(context), shape: BoxShape.circle),
              child: Icon(icon, size: 40, color: tone.fg(context)),
            ),
            const SizedBox(height: 14),
            Text(title, textAlign: TextAlign.center, style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 6),
            Text(body, textAlign: TextAlign.center),
          ]),
        ),
      );
}

class _AppCard extends StatelessWidget {
  final RiskyApp app;
  final ProtectionEngine engine;
  const _AppCard({required this.app, required this.engine});

  @override
  Widget build(BuildContext context) {
    final bad = app.verdict == 'MALICIOUS';
    return Card(
      key: Key('risky-${app.packageName}'),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        // 13.8: details - what it can access, who it talks to, block internet / permissions / uninstall / ignore
        onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => AppDetailScreen(app: app, engine: engine))),
        child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            FeatureIcon(bad ? Icons.gpp_bad_rounded : Icons.warning_amber_rounded, bad ? Feature.scam : Feature.apps),
            const SizedBox(width: 12),
            Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(app.label, style: Theme.of(context).textTheme.titleMedium),
              Text(app.packageName, style: Theme.of(context).textTheme.bodySmall?.copyWith(color: Theme.of(context).colorScheme.onSurfaceVariant)),
            ])),
            if (app.isNew) Padding(padding: const EdgeInsets.only(right: 6), child: Pill(context.l10n.newBadge, tone: Tone.info)),
            Pill('${app.score}', tone: bad ? Tone.danger : Tone.warn),
          ]),
          const SizedBox(height: 10),
          Wrap(spacing: 6, runSpacing: 6, children: [for (final r in app.reasons) Pill(r, tone: bad ? Tone.danger : Tone.warn, icon: Icons.error_outline_rounded)]),
          const SizedBox(height: 6),
          Row(mainAxisAlignment: MainAxisAlignment.end, children: [
            TextButton(onPressed: () => engine.ignoreApp(app), child: Text(context.l10n.ignore)),
            const SizedBox(width: 8),
            FilledButton(onPressed: () => engine.uninstall(app.packageName), child: Text(context.l10n.uninstall)),
          ]),
        ]),
      )),
    );
  }
}
