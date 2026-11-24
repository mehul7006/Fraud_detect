import 'package:flutter/material.dart';

import '../core/native_bridge.dart';
import '../core/settings_store.dart';
import '../l10n/l10n.dart';
import '../ui/screen_kit.dart';
import '../ui/theme.dart';

/// 15.8 (extends 7.4): one family member, saved on this phone only (same storage as the 7.4 emergency contact).
/// The STOP screen and every CRITICAL alert offer one tap to call / message them; the user always taps Send, nothing
/// goes automatically and no message content is ever included. Removing the guardian switches it off at once.
class GuardianScreen extends StatefulWidget {
  final SettingsStore? settings;
  const GuardianScreen({super.key, this.settings});
  @override
  State<GuardianScreen> createState() => _GuardianScreenState();
}

class _GuardianScreenState extends State<GuardianScreen> {
  late final SettingsStore _store = widget.settings ?? SettingsStore();
  final _name = TextEditingController(), _phone = TextEditingController();
  ({String name, String phone})? _g;
  String? _error;

  @override
  void initState() {
    super.initState();
    _store.emergencyContact().then((g) { if (mounted) setState(() => _g = g); });
  }

  @override
  void dispose() { _name.dispose(); _phone.dispose(); super.dispose(); }

  Future<void> _save() async {
    final ok = await _store.setEmergencyContact(_name.text, _phone.text);
    if (!mounted) return;
    if (!ok) { setState(() => _error = context.l10n.safeGuardianInvalid); return; }
    final g = await _store.emergencyContact();
    setState(() { _g = g; _error = null; _name.clear(); _phone.clear(); });
  }

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    final g = _g;
    return Scaffold(
      appBar: AppBar(title: Text(l.safeGuardianTitle)),
      body: ListView(padding: const EdgeInsets.fromLTRB(16, 4, 16, 24), children: [
        IntroBanner(icon: Icons.family_restroom_rounded, feature: Feature.message, text: l.safeGuardianIntro),
        if (g != null) ...[
          Card(
            child: ListTile(
              key: const Key('guardian-current'),
              minVerticalPadding: 14,
              leading: CircleAvatar(
                radius: 24, backgroundColor: Tone.safe.bg(context),
                child: Text(g.name.isEmpty ? '?' : g.name.characters.first.toUpperCase(),
                    style: TextStyle(color: Tone.safe.fg(context), fontWeight: FontWeight.w700, fontSize: 20)),
              ),
              title: Text(l.safeGuardianSaved(g.name)),
              subtitle: Text(g.phone),
              trailing: Icon(Icons.verified_rounded, color: Tone.safe.fg(context)),
            ),
          ),
          const SizedBox(height: 12),
          OutlinedButton.icon(
            key: const Key('guardian-test'),
            onPressed: () => NativeBridge.composeSms(g.phone, l.safeGuardianTestText),
            icon: const Icon(Icons.sms_rounded), label: Text(l.safeGuardianTest)),
          const SizedBox(height: 6),
          TextButton.icon(
            key: const Key('guardian-remove'),
            style: TextButton.styleFrom(foregroundColor: Tone.danger.fg(context)),
            onPressed: () async { await _store.clearEmergencyContact(); setState(() => _g = null); },
            icon: const Icon(Icons.delete_outline_rounded), label: Text(l.safeGuardianRemove)),
        ] else
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                TextField(key: const Key('guardian-name'), controller: _name,
                    decoration: InputDecoration(labelText: l.safeGuardianName, prefixIcon: const Icon(Icons.person_outline_rounded))),
                const SizedBox(height: 12),
                TextField(key: const Key('guardian-phone'), controller: _phone, keyboardType: TextInputType.phone,
                    decoration: InputDecoration(labelText: l.safeGuardianNumber, prefixIcon: const Icon(Icons.phone_rounded), errorText: _error)),
                const SizedBox(height: 16),
                FilledButton(key: const Key('guardian-save'), onPressed: _save, child: Text(l.safeGuardianSave)),
              ]),
            ),
          ),
      ]),
    );
  }
}
