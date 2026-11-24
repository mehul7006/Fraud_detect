import 'dart:async';

import 'package:flutter/material.dart';

import '../core/native_bridge.dart';
import '../core/safety.dart';
import '../l10n/l10n.dart';

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
    final golden = _start != null && DateTime.now().difference(_start!).inMinutes < 60;
    return Scaffold(
      appBar: AppBar(title: Text(l.safeRecTitle)),
      body: ListView(padding: const EdgeInsets.all(16), children: [
        Text(l.safeRecIntro),
        const SizedBox(height: 12),
        if (_start == null)
          FilledButton.icon(
            key: const Key('rec-start'),
            onPressed: () async { final s = await RecoveryState.start(); setState(() { _start = s; _done = {}; }); },
            icon: const Icon(Icons.timer), label: Text(l.safeRecStart))
        else
          Card(
            color: golden ? Colors.orange.shade50 : Colors.red.shade50,
            child: ListTile(
              key: const Key('rec-timer'),
              leading: const Icon(Icons.timer),
              title: Text(l.safeRecElapsed(_elapsed()), style: const TextStyle(fontWeight: FontWeight.bold)),
              trailing: TextButton(onPressed: () async { await RecoveryState.reset(); setState(() { _start = null; _done = {}; }); }, child: Text(l.safeRecReset)),
            ),
          ),
        const SizedBox(height: 8),
        FilledButton.icon(
          key: const Key('rec-1930'),
          style: FilledButton.styleFrom(backgroundColor: Colors.red.shade800, minimumSize: const Size.fromHeight(52)),
          onPressed: () => SafetyNative.dial('1930'),
          icon: const Icon(Icons.call), label: Text(l.safeRec1930)),
        const SizedBox(height: 8),
        OutlinedButton.icon(
          key: const Key('rec-portal'),
          onPressed: () => NativeBridge.openUrl(portal),
          icon: const Icon(Icons.open_in_new), label: Text(l.safeRecPortal)),
        const SizedBox(height: 16),
        Text(l.safeRecChecklist, style: Theme.of(context).textTheme.titleMedium),
        for (final s in steps)
          CheckboxListTile(
            key: Key('rec-step-$s'),
            contentPadding: EdgeInsets.zero,
            value: _done.contains(s),
            title: Text(_label(context, s)),
            onChanged: (v) async { await RecoveryState.setDone(s, v == true); setState(() => v == true ? _done.add(s) : _done.remove(s)); },
          ),
        const SizedBox(height: 8),
        Text(l.safeRecBlockTitle, style: Theme.of(context).textTheme.titleMedium),
        Text(l.safeRecBlockSub, style: Theme.of(context).textTheme.bodySmall),
        for (final e in _banks.entries)
          ExpansionTile(
            key: Key('rec-bank-${e.key}'),
            tilePadding: EdgeInsets.zero,
            title: Text(e.key),
            children: [
              for (final n in e.value)
                ListTile(
                  dense: true,
                  title: Text(n.number),
                  subtitle: Text(n.purpose),
                  trailing: const Icon(Icons.call),
                  onTap: () => SafetyNative.dial(n.number.replaceAll(RegExp(r'[^0-9+]'), '')),
                ),
            ],
          ),
        const SizedBox(height: 16),
        Text(l.safeRecEvidence, style: Theme.of(context).textTheme.titleMedium),
        Text(l.safeRecEvidenceSub, style: Theme.of(context).textTheme.bodySmall),
        for (final (key, c, label, lines) in [('ev-who', _who, l.safeRecEvWho, 1), ('ev-money', _money, l.safeRecEvMoney, 2),
          ('ev-apps', _apps, l.safeRecEvApps, 1), ('ev-notes', _notes, l.safeRecEvNotes, 4)])
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: TextField(key: Key(key), controller: c, minLines: lines, maxLines: lines + 2,
                decoration: InputDecoration(labelText: label, border: const OutlineInputBorder())),
          ),
        const SizedBox(height: 8),
        Text(l.safeRecScreens, style: Theme.of(context).textTheme.bodySmall),
        const SizedBox(height: 8),
        FilledButton.tonalIcon(
          key: const Key('rec-share'),
          onPressed: () => SafetyNative.shareText(_evidence(context), subject: 'FraudShield evidence'),
          icon: const Icon(Icons.share), label: Text(l.safeRecShare)),
        const SizedBox(height: 24),
      ]),
    );
  }
}
