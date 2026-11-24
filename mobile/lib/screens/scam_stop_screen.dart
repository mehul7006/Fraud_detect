import 'package:flutter/material.dart';

import '../core/native_bridge.dart';
import '../core/safety.dart';
import '../core/settings_store.dart';
import '../l10n/l10n.dart';
import 'recovery_kit_screen.dart';

/// 15.3: full-screen STOP warning (live scam in progress). Plain words, big buttons, read aloud in the app's language.
/// 15.8: if a guardian is set, one tap calls or messages them (the user taps Send - nothing goes automatically).
class ScamStopScreen extends StatefulWidget {
  final bool critical;
  final bool speakOnOpen;
  final SettingsStore? settings;
  const ScamStopScreen({super.key, this.critical = true, this.speakOnOpen = true, this.settings});

  @override
  State<ScamStopScreen> createState() => _ScamStopScreenState();
}

class _ScamStopScreenState extends State<ScamStopScreen> {
  ({String name, String phone})? _guardian;
  bool _noVoice = false;

  @override
  void initState() {
    super.initState();
    _loadGuardian();
    if (widget.speakOnOpen) WidgetsBinding.instance.addPostFrameCallback((_) => _speak());
  }

  Future<void> _loadGuardian() async {
    try {
      final g = await (widget.settings ?? SettingsStore()).emergencyContact().timeout(const Duration(seconds: 2));
      if (mounted) setState(() => _guardian = g);
    } catch (_) {/* a failed read never blocks the warning */}
  }

  String _spoken(BuildContext c) {
    final l = c.l10n;
    return [widget.critical ? l.safeStopHeadline : l.safeStopWarnHeadline, l.safeStopStep1, l.safeStopStep2, l.safeStopStep3, l.safeStopStep4].join(' ');
  }

  Future<void> _speak() async {
    if (!mounted) return;
    final lang = Localizations.localeOf(context).languageCode == 'hi' ? 'hi' : 'en';
    bool ok;
    try {
      ok = await SafetyNative.speak(_spoken(context), lang);
    } catch (_) {
      ok = false;
    }
    if (mounted && !ok) setState(() => _noVoice = true);
  }

  @override
  void dispose() {
    SafetyNative.stopSpeaking().catchError((_) {});
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    final red = Colors.red.shade800;
    final g = _guardian;
    Widget step(String t) => Padding(
          padding: const EdgeInsets.only(bottom: 10),
          child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Icon(Icons.do_not_disturb_on, color: red, size: 26),
            const SizedBox(width: 10),
            Expanded(child: Text(t, style: const TextStyle(fontSize: 19, fontWeight: FontWeight.w600))),
          ]),
        );
    return PopScope(
      canPop: true,
      child: Scaffold(
        backgroundColor: Colors.red.shade50,
        body: SafeArea(
          child: ListView(padding: const EdgeInsets.all(20), children: [
            Icon(Icons.pan_tool, color: red, size: 72, semanticLabel: l.safeStopTitle),
            Text(l.safeStopTitle, key: const Key('stop-title'), textAlign: TextAlign.center,
                style: TextStyle(fontSize: 44, fontWeight: FontWeight.w900, color: red)),
            const SizedBox(height: 4),
            Text(widget.critical ? l.safeStopHeadline : l.safeStopWarnHeadline, textAlign: TextAlign.center,
                style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold, color: red)),
            const SizedBox(height: 12),
            Text(l.safeStopBody, style: const TextStyle(fontSize: 17)),
            const SizedBox(height: 16),
            step(l.safeStopStep1),
            step(l.safeStopStep2),
            step(l.safeStopStep3),
            step(l.safeStopStep4),
            if (_noVoice) Text(l.safeNoVoice, key: const Key('stop-novoice'), style: const TextStyle(fontStyle: FontStyle.italic)),
            const SizedBox(height: 12),
            FilledButton.icon(
              key: const Key('stop-1930'),
              style: FilledButton.styleFrom(backgroundColor: red, minimumSize: const Size.fromHeight(56)),
              onPressed: () => SafetyNative.dial('1930'),
              icon: const Icon(Icons.call), label: Text(l.safeCall1930, style: const TextStyle(fontSize: 18)),
            ),
            if (g != null) ...[
              const SizedBox(height: 8),
              FilledButton.tonalIcon(
                key: const Key('stop-call-guardian'),
                style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(52)),
                onPressed: () => SafetyNative.dial(g.phone),
                icon: const Icon(Icons.family_restroom), label: Text(l.safeCallGuardian(g.name)),
              ),
              const SizedBox(height: 8),
              OutlinedButton.icon(
                key: const Key('stop-tell-guardian'),
                onPressed: () => NativeBridge.composeSms(g.phone, l.safeGuardianSms),
                icon: const Icon(Icons.sms_outlined), label: Text(l.safeTellGuardian(g.name)),
              ),
            ],
            const SizedBox(height: 8),
            OutlinedButton.icon(key: const Key('stop-speak'), onPressed: _speak, icon: const Icon(Icons.volume_up), label: Text(l.safeStopSpeak)),
            OutlinedButton.icon(
              key: const Key('stop-recovery'),
              onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const RecoveryKitScreen())),
              icon: const Icon(Icons.healing), label: Text(l.safeOpenRecovery),
            ),
            TextButton(key: const Key('stop-close'), onPressed: () => Navigator.pop(context), child: Text(l.safeImSafe)),
          ]),
        ),
      ),
    );
  }
}
