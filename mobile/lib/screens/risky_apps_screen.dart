import 'package:flutter/material.dart';
import '../core/protection_engine.dart';
import '../l10n/l10n.dart';

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
      appBar: AppBar(title: Text(l.riskyTitle)),
      body: ValueListenableBuilder<List<RiskyApp>>(
        valueListenable: widget.engine.riskyApps,
        builder: (c, apps, _) {
          final failed = status.value['apps'] == 'ERROR';
          return RefreshIndicator(
            onRefresh: _refresh,
            child: ListView(padding: const EdgeInsets.all(12), children: [
              if (_loading) const LinearProgressIndicator(),
              if (failed)
                _Info(Icons.cloud_off, l.riskyFailedTitle, l.riskyFailedBody)
              else if (status.value['apps'] == null)
                _Info(Icons.hourglass_empty, l.riskyCheckingTitle, l.riskyCheckingBody)
              else if (apps.isEmpty)
                _Info(Icons.verified_user, l.riskyNoneTitle, l.riskyNoneBody),
              for (final a in apps) _AppCard(app: a, engine: widget.engine),
              const Divider(height: 24),
              // 6.1: opt-in, off by default
              SwitchListTile(
                key: const Key('share-app-labels'),
                value: _share,
                onChanged: _setShare,
                title: Text(l.riskyShareTitle),
                subtitle: Text(l.riskyShareBody),
              ),
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
  const _Info(this.icon, this.title, this.body);
  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 48),
        child: Column(children: [
          Icon(icon, size: 48, color: Colors.grey),
          const SizedBox(height: 12),
          Text(title, style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 4),
          Text(body, textAlign: TextAlign.center),
        ]),
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
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            Icon(bad ? Icons.gpp_bad : Icons.warning_amber, color: bad ? Colors.red : Colors.orange),
            const SizedBox(width: 8),
            Expanded(child: Text(app.label, style: Theme.of(context).textTheme.titleMedium)),
            if (app.isNew) Chip(label: Text(context.l10n.newBadge)),
            const SizedBox(width: 4),
            Text('${app.score}', style: TextStyle(fontWeight: FontWeight.bold, color: bad ? Colors.red : Colors.orange)),
          ]),
          Text(app.packageName, style: Theme.of(context).textTheme.bodySmall),
          const SizedBox(height: 8),
          Wrap(spacing: 6, runSpacing: 4, children: [for (final r in app.reasons) Chip(label: Text(r), visualDensity: VisualDensity.compact)]),
          Row(mainAxisAlignment: MainAxisAlignment.end, children: [
            TextButton(onPressed: () => engine.ignoreApp(app), child: Text(context.l10n.ignore)),
            const SizedBox(width: 8),
            FilledButton(onPressed: () => engine.uninstall(app.packageName), child: Text(context.l10n.uninstall)),
          ]),
        ]),
      ),
    );
  }
}
