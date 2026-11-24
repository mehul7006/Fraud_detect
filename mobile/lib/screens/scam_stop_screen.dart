import 'package:flutter/material.dart';

import '../core/native_bridge.dart';
import '../core/safety.dart';
import '../core/settings_store.dart';
import '../l10n/l10n.dart';
import '../ui/theme.dart';
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
    final t = Theme.of(context);
    final danger = Tone.danger.fg(context);
    final g = _guardian;
    var n = 0;
    Widget step(String text) {
      n++;
      return Padding(
        padding: const EdgeInsets.only(bottom: 12),
        child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Container(
            width: 32, height: 32, alignment: Alignment.center,
            decoration: const BoxDecoration(gradient: AppColors.dangerGradient, shape: BoxShape.circle),
            child: Text('$n', style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w700)),
          ),
          const SizedBox(width: 12),
          Expanded(child: Padding(
            padding: const EdgeInsets.only(top: 3),
            child: Text(text, style: t.textTheme.titleMedium?.copyWith(fontSize: 18, fontWeight: FontWeight.w600)),
          )),
        ]),
      );
    }

    return PopScope(
      canPop: true,
      child: Scaffold(
        body: ListView(padding: EdgeInsets.zero, children: [
          // unmistakable, calm: deep red hero, huge STOP
          Container(
            padding: EdgeInsets.fromLTRB(20, MediaQuery.paddingOf(context).top + 20, 20, 28),
            decoration: const BoxDecoration(
              gradient: AppColors.dangerGradient,
              borderRadius: BorderRadius.vertical(bottom: Radius.circular(32)),
            ),
            child: Column(children: [
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(color: Colors.white.withValues(alpha: 0.16), shape: BoxShape.circle),
                child: Icon(Icons.pan_tool_rounded, color: Colors.white, size: 56, semanticLabel: l.safeStopTitle),
              ),
              const SizedBox(height: 8),
              Text(l.safeStopTitle, key: const Key('stop-title'), textAlign: TextAlign.center,
                  style: const TextStyle(fontSize: 52, fontWeight: FontWeight.w800, color: Colors.white, letterSpacing: 2, height: 1.1)),
              const SizedBox(height: 6),
              Text(widget.critical ? l.safeStopHeadline : l.safeStopWarnHeadline, textAlign: TextAlign.center,
                  style: t.textTheme.titleLarge?.copyWith(color: Colors.white)),
            ]),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
            child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text(l.safeStopBody, style: t.textTheme.bodyLarge),
                    const SizedBox(height: 16),
                    step(l.safeStopStep1),
                    step(l.safeStopStep2),
                    step(l.safeStopStep3),
                    step(l.safeStopStep4),
                    if (_noVoice)
                      Text(l.safeNoVoice, key: const Key('stop-novoice'),
                          style: t.textTheme.bodyMedium?.copyWith(fontStyle: FontStyle.italic, color: t.colorScheme.onSurfaceVariant)),
                  ]),
                ),
              ),
              const SizedBox(height: 12),
              FilledButton.icon(
                key: const Key('stop-1930'),
                style: FilledButton.styleFrom(backgroundColor: danger, foregroundColor: Theme.of(context).brightness == Brightness.dark ? AppColors.ink : Colors.white,
                    minimumSize: const Size.fromHeight(60)),
                onPressed: () => SafetyNative.dial('1930'),
                icon: const Icon(Icons.call_rounded), label: Text(l.safeCall1930, style: const TextStyle(fontSize: 18)),
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
              const SizedBox(height: 10),
              OutlinedButton.icon(key: const Key('stop-speak'), onPressed: _speak, icon: const Icon(Icons.volume_up_rounded), label: Text(l.safeStopSpeak)),
              const SizedBox(height: 10),
              OutlinedButton.icon(
                key: const Key('stop-recovery'),
                onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const RecoveryKitScreen())),
                icon: const Icon(Icons.healing_rounded), label: Text(l.safeOpenRecovery),
              ),
              const SizedBox(height: 6),
              TextButton(key: const Key('stop-close'), onPressed: () => Navigator.pop(context), child: Text(l.safeImSafe)),
            ]),
          ),
        ]),
      ),
    );
  }
}
