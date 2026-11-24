import 'dart:async';

import 'package:flutter/material.dart';

import '../core/native_bridge.dart';
import '../core/safety.dart';
import '../l10n/l10n.dart';
import '../ui/components.dart';
import '../ui/screen_kit.dart';
import '../ui/theme.dart';

/// 15.6: after a scam. Golden-hour timer, one-tap 1930 + cybercrime.gov.in, bank block numbers (from the verified
/// directory - each read on the bank's own website), checklist, evidence pack that stays on the phone until shared.
class RecoveryKitScreen extends StatefulWidget {
  final AssetBundle? bundle;
  const RecoveryKitScreen({super.key, this.bundle});
  @override
  State<RecoveryKitScreen> createState() => _RecoveryKitScreenState();
}

class _RecoveryKitScreenState extends State<RecoveryKitScreen> {
  static const portal = 'https://cybercrime.gov.in/';
  static const steps = ['call1930', 'bank', 'passwords', 'remote', 'portal', 'keep'];
  DateTime? _start;
  Set<String> _done = {};
  Map<String, List<CareEntry>> _banks = {};
  Timer? _tick;
  final _who = TextEditingController(), _money = TextEditingController(), _apps = TextEditingController(), _notes = TextEditingController();

  @override
  void initState() {
    super.initState();
    _load();
    _tick = Timer.periodic(const Duration(seconds: 30), (_) { if (mounted && _start != null) setState(() {}); });
  }

  Future<void> _load() async {
    final s = await RecoveryState.startedAt();
    final d = await RecoveryState.done();
    Map<String, List<CareEntry>> b = {};
    try {
      b = CareDirectory.blockNumbers(await CareDirectory.load(widget.bundle));
    } catch (_) {}
    if (mounted) setState(() { _start = s; _done = d; _banks = b; });
  }

  @override
  void dispose() {
    _tick?.cancel();
    for (final c in [_who, _money, _apps, _notes]) { c.dispose(); }
    super.dispose();
  }

  String _elapsed() {
    final d = DateTime.now().difference(_start!);
    final h = d.inHours, m = d.inMinutes % 60;
    return h > 0 ? '$h h $m min' : '$m min';
  }

  String _label(BuildContext c, String step) {
    final l = c.l10n;
    return switch (step) {
      'call1930' => l.safeRecStepCall1930, 'bank' => l.safeRecStepBank, 'passwords' => l.safeRecStepPasswords,
      'remote' => l.safeRecStepRemote, 'portal' => l.safeRecStepPortal, _ => l.safeRecStepKeep,
    };
  }

  String _evidence(BuildContext c) {
    final l = c.l10n;
    final b = StringBuffer('FraudShield evidence\n');
    if (_start != null) b.writeln('Scam reported at: ${_start!.toLocal()}');
    b.writeln('Written: ${DateTime.now().toLocal()}');
    for (final (k, v) in [(l.safeRecEvWho, _who.text), (l.safeRecEvMoney, _money.text), (l.safeRecEvApps, _apps.text), (l.safeRecEvNotes, _notes.text)]) {
      if (v.trim().isNotEmpty) b.writeln('\n$k:\n${v.trim()}');
    }
    return b.toString();
  }

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    final t = Theme.of(context);
    final golden = _start != null && DateTime.now().difference(_start!).inMinutes < 60;
    final timerTone = golden ? Tone.warn : Tone.danger;
    return Scaffold(
      appBar: AppBar(title: Text(l.safeRecTitle)),
      body: ListView(padding: const EdgeInsets.fromLTRB(16, 4, 16, 32), children: [
        IntroBanner(icon: Icons.healing_rounded, feature: Feature.scam, text: l.safeRecIntro),
        if (_start == null)
          FilledButton.icon(
            key: const Key('rec-start'),
            onPressed: () async { final s = await RecoveryState.start(); setState(() { _start = s; _done = {}; }); },
            icon: const Icon(Icons.timer_rounded), label: Text(l.safeRecStart))
        else
          Card(
            color: timerTone.bg(context),
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20), side: BorderSide(color: timerTone.fg(context).withValues(alpha: 0.3))),
            child: ListTile(
              key: const Key('rec-timer'),
              leading: Icon(Icons.timer_rounded, color: timerTone.fg(context), size: 30),
              title: Text(l.safeRecElapsed(_elapsed()), style: TextStyle(fontWeight: FontWeight.w700, color: timerTone.fg(context))),
              trailing: TextButton(onPressed: () async { await RecoveryState.reset(); setState(() { _start = null; _done = {}; }); }, child: Text(l.safeRecReset)),
            ),
          ),
        const SizedBox(height: 12),
        FilledButton.icon(
          key: const Key('rec-1930'),
          style: FilledButton.styleFrom(backgroundColor: Tone.danger.fg(context),
              foregroundColor: t.brightness == Brightness.dark ? AppColors.ink : Colors.white, minimumSize: const Size.fromHeight(56)),
          onPressed: () => SafetyNative.dial('1930'),
          icon: const Icon(Icons.call_rounded), label: Text(l.safeRec1930)),
        const SizedBox(height: 10),
        OutlinedButton.icon(
          key: const Key('rec-portal'),
          onPressed: () => NativeBridge.openUrl(portal),
          icon: const Icon(Icons.open_in_new_rounded), label: Text(l.safeRecPortal)),

        SectionTitle(l.safeRecChecklist),
        Card(
          child: Column(children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 14, 16, 4),
              child: Row(children: [
                Expanded(child: ClipRRect(
                  borderRadius: BorderRadius.circular(8),
                  child: LinearProgressIndicator(value: _done.length / steps.length, minHeight: 8),
                )),
                const SizedBox(width: 12),
                Pill('${_done.length}/${steps.length}', tone: _done.length == steps.length ? Tone.safe : Tone.info),
              ]),
            ),
            for (final s in steps)
              CheckboxListTile(
                key: Key('rec-step-$s'),
                value: _done.contains(s),
                title: Text(_label(context, s)),
                onChanged: (v) async { await RecoveryState.setDone(s, v == true); setState(() => v == true ? _done.add(s) : _done.remove(s)); },
              ),
          ]),
        ),

        SectionTitle(l.safeRecBlockTitle, subtitle: l.safeRecBlockSub),
        Card(
          child: Column(children: [
            for (final e in _banks.entries)
              ExpansionTile(
                key: Key('rec-bank-${e.key}'),
                leading: const FeatureIcon(Icons.account_balance_rounded, Feature.web, size: 38),
                title: Text(e.key),
                shape: const Border(),
                children: [
                  for (final n in e.value)
                    ListTile(
                      title: Text(n.number),
                      subtitle: Text(n.purpose),
                      trailing: Icon(Icons.call_rounded, color: Tone.safe.fg(context)),
                      onTap: () => SafetyNative.dial(n.number.replaceAll(RegExp(r'[^0-9+]'), '')),
                    ),
                ],
              ),
          ]),
        ),

        SectionTitle(l.safeRecEvidence, subtitle: l.safeRecEvidenceSub),
        Card(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
            child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              for (final (key, c, label, lines) in [('ev-who', _who, l.safeRecEvWho, 1), ('ev-money', _money, l.safeRecEvMoney, 2),
                ('ev-apps', _apps, l.safeRecEvApps, 1), ('ev-notes', _notes, l.safeRecEvNotes, 4)])
                Padding(
                  padding: const EdgeInsets.only(top: 10),
                  child: TextField(key: Key(key), controller: c, minLines: lines, maxLines: lines + 2,
                      decoration: InputDecoration(labelText: label)),
                ),
              const SizedBox(height: 10),
              Text(l.safeRecScreens, style: t.textTheme.bodySmall?.copyWith(color: t.colorScheme.onSurfaceVariant)),
              const SizedBox(height: 12),
              FilledButton.tonalIcon(
                key: const Key('rec-share'),
                onPressed: () => SafetyNative.shareText(_evidence(context), subject: 'FraudShield evidence'),
                icon: const Icon(Icons.share_rounded), label: Text(l.safeRecShare)),
            ]),
          ),
        ),
      ]),
    );
  }
}
