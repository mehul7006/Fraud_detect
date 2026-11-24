import 'package:flutter/material.dart';

import '../core/drills.dart';
import '../core/safety.dart';
import '../l10n/l10n.dart';
import '../ui/components.dart';
import '../ui/screen_kit.dart';
import '../ui/theme.dart';

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
      body: ListView(padding: const EdgeInsets.fromLTRB(16, 4, 16, 24), children: [
        IntroBanner(icon: Icons.school_rounded, feature: Feature.device, text: l.safeDrillsIntro),
        for (final (i, d) in drills.indexed)
          FeatureTile(
            tileKey: Key('drill-${d.id}'),
            icon: _icons[i % _icons.length], feature: _features[i % _features.length],
            title: hi ? d.titleHi : d.titleEn,
            status: _best[d.id] == null ? l.safeDrillNotPlayed : l.safeDrillBest(_best[d.id]!),
            tone: _best[d.id] == null ? Tone.neutral : _best[d.id]! >= 100 ? Tone.safe : Tone.warn,
            trailingIcon: Icons.play_circle_fill_rounded,
            onTap: () async {
              await Navigator.push(context, MaterialPageRoute(builder: (_) => DrillPlayScreen(drill: d)));
              _load();
            },
          ),
      ]),
    );
  }
}

const _icons = [Icons.local_police_rounded, Icons.account_balance_rounded, Icons.emoji_events_rounded, Icons.currency_rupee_rounded, Icons.work_rounded];
const _features = [Feature.scam, Feature.web, Feature.apps, Feature.call, Feature.message];

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
    final t = Theme.of(context);
    if (_score != null) {
      final perfect = _score == 100;
      return Scaffold(
        appBar: AppBar(title: Text(hi ? d.titleHi : d.titleEn)),
        body: Center(child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            Container(
              padding: const EdgeInsets.all(26),
              decoration: BoxDecoration(gradient: (perfect ? Feature.cleaner : Feature.device).gradient, shape: BoxShape.circle,
                  boxShadow: [BoxShadow(color: (perfect ? Feature.cleaner : Feature.device).from.withValues(alpha: 0.35), blurRadius: 24, offset: const Offset(0, 10))]),
              child: Icon(perfect ? Icons.emoji_events_rounded : Icons.school_rounded, size: 64, color: Colors.white),
            ),
            const SizedBox(height: 20),
            Text(l.safeDrillResult(_score!), key: const Key('drill-score'), textAlign: TextAlign.center, style: t.textTheme.headlineSmall),
            const SizedBox(height: 24),
            FilledButton(onPressed: () => Navigator.pop(context), child: Text(l.safeDrillFinish)),
          ]),
        )),
      );
    }
    final step = d.steps[_step];
    return Scaffold(
      appBar: AppBar(title: Text('${hi ? d.titleHi : d.titleEn}  ${_step + 1}/${d.steps.length}')),
      body: ListView(padding: const EdgeInsets.fromLTRB(16, 4, 16, 24), children: [
        ClipRRect(
          borderRadius: BorderRadius.circular(8),
          child: LinearProgressIndicator(value: (_step + (_picked != null ? 1 : 0)) / d.steps.length, minHeight: 8),
        ),
        const SizedBox(height: 14),
        // the scenario, like a message bubble
        Container(
          padding: const EdgeInsets.all(18),
          decoration: BoxDecoration(gradient: Feature.device.deepGradient, borderRadius: BorderRadius.circular(24)),
          child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const Icon(Icons.forum_rounded, color: Colors.white, size: 26),
            const SizedBox(width: 12),
            Expanded(child: Text(hi ? step.hi : step.en, style: t.textTheme.bodyLarge?.copyWith(color: Colors.white, fontSize: 17))),
          ]),
        ),
        const SizedBox(height: 10),
        for (var i = 0; i < step.choices.length; i++) _choice(context, step.choices[i], i, hi),
        if (_picked != null) ...[
          const SizedBox(height: 10),
          FilledButton(key: const Key('drill-next'), onPressed: _next,
              child: Text(_step + 1 < d.steps.length ? l.safeDrillNext : l.safeDrillFinish)),
        ],
      ]),
    );
  }

  Widget _choice(BuildContext context, DrillChoice c, int i, bool hi) {
    final l = context.l10n;
    final picked = _picked == i;
    final reveal = _picked != null && (picked || c.safe);
    final tone = !reveal ? Tone.neutral : (c.safe ? Tone.safe : Tone.danger);
    return Card(
      color: !reveal ? null : tone.bg(context),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20),
          side: BorderSide(color: !reveal ? Theme.of(context).colorScheme.outlineVariant : tone.fg(context).withValues(alpha: 0.4), width: reveal ? 1.6 : 1)),
      child: ListTile(
        key: Key('drill-choice-$i'),
        minVerticalPadding: 12,
        title: Text(hi ? c.hi : c.en),
        subtitle: reveal ? Text('${c.safe ? l.safeDrillSafe : l.safeDrillRisky}: ${hi ? c.whyHi : c.whyEn}',
            style: TextStyle(color: tone.fg(context))) : null,
        leading: CircleAvatar(
          radius: 18,
          backgroundColor: !reveal ? Theme.of(context).colorScheme.surfaceContainerHighest : tone.fg(context),
          child: !reveal
              ? Text(String.fromCharCode(65 + i), style: TextStyle(fontWeight: FontWeight.w700, color: Theme.of(context).colorScheme.onSurface))
              : Icon(c.safe ? Icons.check_rounded : Icons.close_rounded,
                  color: Theme.of(context).brightness == Brightness.dark ? AppColors.ink : Colors.white, size: 20),
        ),
        onTap: _picked == null ? () => setState(() => _picked = i) : null,
      ),
    );
  }
}
