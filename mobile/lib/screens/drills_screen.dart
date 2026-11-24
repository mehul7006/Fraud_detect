import 'package:flutter/material.dart';

import '../core/drills.dart';
import '../core/safety.dart';
import '../l10n/l10n.dart';

/// 15.10: five short scam drills (EN / HI), score per drill, best score saved on this phone only.
class DrillsScreen extends StatefulWidget {
  const DrillsScreen({super.key});
  @override
  State<DrillsScreen> createState() => _DrillsScreenState();
}

class _DrillsScreenState extends State<DrillsScreen> {
  final Map<String, int?> _best = {};

  @override
  void initState() { super.initState(); _load(); }

  Future<void> _load() async {
    for (final d in drills) { _best[d.id] = await DrillStore.best(d.id); }
    if (mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    final hi = Localizations.localeOf(context).languageCode == 'hi';
    return Scaffold(
      appBar: AppBar(title: Text(l.safeDrillsTitle)),
      body: ListView(padding: const EdgeInsets.all(16), children: [
        Text(l.safeDrillsIntro),
        const SizedBox(height: 8),
        for (final d in drills)
          Card(
            child: ListTile(
              key: Key('drill-${d.id}'),
              leading: const Icon(Icons.school_outlined),
              title: Text(hi ? d.titleHi : d.titleEn),
              subtitle: Text(_best[d.id] == null ? l.safeDrillNotPlayed : l.safeDrillBest(_best[d.id]!)),
              trailing: const Icon(Icons.chevron_right),
              onTap: () async {
                await Navigator.push(context, MaterialPageRoute(builder: (_) => DrillPlayScreen(drill: d)));
                _load();
              },
            ),
          ),
      ]),
    );
  }
}

class DrillPlayScreen extends StatefulWidget {
  final Drill drill;
  const DrillPlayScreen({super.key, required this.drill});
  @override
  State<DrillPlayScreen> createState() => _DrillPlayScreenState();
}

class _DrillPlayScreenState extends State<DrillPlayScreen> {
  int _step = 0;
  int? _picked;
  final List<int> _answers = [];
  int? _score;

  Future<void> _next() async {
    _answers.add(_picked!);
    if (_step + 1 < widget.drill.steps.length) {
      setState(() { _step++; _picked = null; });
      return;
    }
    final s = drillScore(widget.drill, _answers);
    await DrillStore.save(widget.drill.id, s);
    if (mounted) setState(() => _score = s);
  }

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    final hi = Localizations.localeOf(context).languageCode == 'hi';
    final d = widget.drill;
    if (_score != null) {
      return Scaffold(
        appBar: AppBar(title: Text(hi ? d.titleHi : d.titleEn)),
        body: Center(child: Column(mainAxisSize: MainAxisSize.min, children: [
          Icon(_score == 100 ? Icons.emoji_events : Icons.school, size: 72, color: _score == 100 ? Colors.amber.shade700 : Colors.blueGrey),
          Text(l.safeDrillResult(_score!), key: const Key('drill-score'), style: Theme.of(context).textTheme.headlineSmall),
          const SizedBox(height: 16),
          FilledButton(onPressed: () => Navigator.pop(context), child: Text(l.safeDrillFinish)),
        ])),
      );
    }
    final step = d.steps[_step];
    return Scaffold(
      appBar: AppBar(title: Text('${hi ? d.titleHi : d.titleEn}  ${_step + 1}/${d.steps.length}')),
      body: ListView(padding: const EdgeInsets.all(16), children: [
        Card(color: Colors.blueGrey.shade50, child: Padding(padding: const EdgeInsets.all(16),
            child: Text(hi ? step.hi : step.en, style: const TextStyle(fontSize: 17)))),
        const SizedBox(height: 8),
        for (var i = 0; i < step.choices.length; i++) _choice(context, step.choices[i], i, hi),
        if (_picked != null)
          FilledButton(key: const Key('drill-next'), onPressed: _next,
              child: Text(_step + 1 < d.steps.length ? l.safeDrillNext : l.safeDrillFinish)),
      ]),
    );
  }

  Widget _choice(BuildContext context, DrillChoice c, int i, bool hi) {
    final l = context.l10n;
    final picked = _picked == i;
    final reveal = _picked != null && (picked || c.safe);
    return Card(
      color: !reveal ? null : (c.safe ? Colors.green.shade50 : Colors.red.shade50),
      child: ListTile(
        key: Key('drill-choice-$i'),
        title: Text(hi ? c.hi : c.en),
        subtitle: reveal ? Text('${c.safe ? l.safeDrillSafe : l.safeDrillRisky}: ${hi ? c.whyHi : c.whyEn}') : null,
        leading: Icon(!reveal ? Icons.radio_button_unchecked : (c.safe ? Icons.check_circle : Icons.cancel),
            color: !reveal ? null : (c.safe ? Colors.green.shade800 : Colors.red.shade800)),
        onTap: _picked == null ? () => setState(() => _picked = i) : null,
      ),
    );
  }
}
