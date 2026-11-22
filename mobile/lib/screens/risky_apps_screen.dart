import 'package:flutter/material.dart';
import '../core/protection_engine.dart';

/// 6.8: every flagged app with its reasons, highest risk first. Uninstall / Ignore per app (6.7).
class RiskyAppsScreen extends StatefulWidget {
  final ProtectionEngine engine;
  const RiskyAppsScreen({super.key, required this.engine});
  @override
  State<RiskyAppsScreen> createState() => _RiskyAppsScreenState();
}

class _RiskyAppsScreenState extends State<RiskyAppsScreen> {
  bool _loading = false;

  Future<void> _refresh() async {
    setState(() => _loading = true);
    await widget.engine.checkApps();
    if (mounted) setState(() => _loading = false);
  }

  @override
  Widget build(BuildContext context) {
    final status = widget.engine.status;
    return Scaffold(
      appBar: AppBar(title: const Text('Risky apps')),
      body: ValueListenableBuilder<List<RiskyApp>>(
        valueListenable: widget.engine.riskyApps,
        builder: (c, apps, _) {
          final failed = status.value['apps'] == 'ERROR';
          return RefreshIndicator(
            onRefresh: _refresh,
            child: ListView(padding: const EdgeInsets.all(12), children: [
              if (_loading) const LinearProgressIndicator(),
              if (failed)
                const _Info(Icons.cloud_off, 'Could not check your apps', 'Check your internet connection and pull down to try again.')
              else if (status.value['apps'] == null)
                const _Info(Icons.hourglass_empty, 'Checking your apps...', 'This takes a few seconds.')
              else if (apps.isEmpty)
                const _Info(Icons.verified_user, 'No risky apps found', 'Pull down to check again.'),
              for (final a in apps) _AppCard(app: a, engine: widget.engine),
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
            if (app.isNew) const Chip(label: Text('NEW')),
            const SizedBox(width: 4),
            Text('${app.score}', style: TextStyle(fontWeight: FontWeight.bold, color: bad ? Colors.red : Colors.orange)),
          ]),
          Text(app.packageName, style: Theme.of(context).textTheme.bodySmall),
          const SizedBox(height: 8),
          Wrap(spacing: 6, runSpacing: 4, children: [for (final r in app.reasons) Chip(label: Text(r), visualDensity: VisualDensity.compact)]),
          Row(mainAxisAlignment: MainAxisAlignment.end, children: [
            TextButton(onPressed: () => engine.ignoreApp(app), child: const Text('Ignore')),
            const SizedBox(width: 8),
            FilledButton(onPressed: () => engine.uninstall(app.packageName), child: const Text('Uninstall')),
          ]),
        ]),
      ),
    );
  }
}
