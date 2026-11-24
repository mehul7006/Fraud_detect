import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import '../core/api_client.dart';
import '../core/native_bridge.dart';
import '../core/safety.dart';
import '../l10n/l10n.dart';
import 'drills_screen.dart';
import 'guardian_screen.dart';
import 'recovery_kit_screen.dart';
import 'scam_stop_screen.dart';
import '../ui/components.dart';
import '../ui/screen_kit.dart';
import '../ui/theme.dart';

/// Phase 15 hub: live scam guard, check before you pay (UPI / QR), customer-care number, recovery kit, guardian, drills.
class SafetyScreen extends StatelessWidget {
  final ApiClient? api;
  const SafetyScreen({super.key, this.api});

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    Widget tile(Key k, IconData i, Feature f, String t, String s, Widget Function() page) => FeatureTile(
          tileKey: k, icon: i, feature: f, title: t, status: s, trailingIcon: Icons.chevron_right_rounded,
          onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => page())),
        );
    return Scaffold(
      appBar: AppBar(title: Text(l.safeHubTitle)),
      body: ListView(padding: const EdgeInsets.fromLTRB(16, 4, 16, 24), children: [
        IntroBanner(icon: Icons.health_and_safety_rounded, feature: Feature.scam, text: l.safeTileSub),
        tile(const Key('safe-guard'), Icons.shield_moon_rounded, Feature.scam, l.safeGuardTitle, l.safeGuardSub, () => const ScamGuardScreen()),
        tile(const Key('safe-upi'), Icons.qr_code_2_rounded, Feature.web, l.safeUpiTitle, l.safeUpiSub, () => UpiCheckScreen(api: api)),
        tile(const Key('safe-care'), Icons.support_agent_rounded, Feature.call, l.safeCareTitle, l.safeCareSub, () => CareNumberScreen(api: api)),
        tile(const Key('safe-recovery'), Icons.healing_rounded, Feature.account, l.safeRecoveryTitle, l.safeRecoverySub, () => const RecoveryKitScreen()),
        tile(const Key('safe-guardian'), Icons.family_restroom_rounded, Feature.message, l.safeGuardianTitle, l.safeGuardianSub, () => const GuardianScreen()),
        tile(const Key('safe-drills'), Icons.school_rounded, Feature.device, l.safeDrillsTitle, l.safeDrillsSub, () => const DrillsScreen()),
      ]),
    );
  }
}

/// 15.2 opt-in + what the guard needs (each permission shown with its reason).
class ScamGuardScreen extends StatefulWidget {
  const ScamGuardScreen({super.key});
  @override
  State<ScamGuardScreen> createState() => _ScamGuardScreenState();
}

class _ScamGuardScreenState extends State<ScamGuardScreen> with WidgetsBindingObserver {
  Map<String, dynamic> _s = {};

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _refresh();
  }

  @override
  void dispose() { WidgetsBinding.instance.removeObserver(this); super.dispose(); }

  @override
  void didChangeAppLifecycleState(AppLifecycleState s) { if (s == AppLifecycleState.resumed) _refresh(); }

  Future<void> _refresh() async {
    try {
      final s = await SafetyNative.guardStatus();
      if (mounted) setState(() => _s = s);
    } catch (_) {}
  }

  bool _b(String k) => _s[k] == true;

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    Widget perm(String key, String label, bool ok, VoidCallback grant) => ListTile(
          key: Key('perm-$key'),
          leading: Icon(ok ? Icons.check_circle_rounded : Icons.radio_button_unchecked_rounded,
              color: (ok ? Tone.safe : Tone.warn).fg(context)),
          title: Text(label),
          trailing: ok ? Pill(l.safeAllowed, tone: Tone.safe) : TextButton(onPressed: grant, child: Text(l.safeAllow)),
        );
    return Scaffold(
      appBar: AppBar(title: Text(l.safeGuardTitle)),
      body: ListView(padding: const EdgeInsets.fromLTRB(16, 4, 16, 24), children: [
        IntroBanner(icon: Icons.shield_moon_rounded, feature: Feature.scam, text: l.safeGuardIntro),
        Card(child: Column(children: [
        SwitchListTile(
          key: const Key('guard-switch'),
          title: Text(l.safeGuardSwitch, style: const TextStyle(fontWeight: FontWeight.w700)),
          value: _b('enabled'),
          onChanged: (v) async {
            if (v && !_b('phoneState')) await SafetyNative.requestPermissions();
            await SafetyNative.setGuard(v);
            _refresh();
          },
        ),
        SwitchListTile(
          key: const Key('otp-switch'),
          title: Text(l.safeOtpSwitch),
          subtitle: Text(l.safeOtpSwitchSub),
          value: _s['otpGuard'] != false,
          onChanged: (v) async { await SafetyNative.setOtpGuard(v); _refresh(); },
        ),
        ])),
        SectionTitle(l.safePermsTitle),
        Card(child: Column(children: [
          perm('phone', l.safePermPhone, _b('phoneState'), SafetyNative.requestPermissions),
          perm('calllog', l.safePermCallLog, _b('callLog'), SafetyNative.requestPermissions),
          perm('contacts', l.safePermContacts, _b('contacts'), SafetyNative.requestPermissions),
          perm('usage', l.safePermUsage, _b('usageAccess'), NativeBridge.openUsageAccessSettings),
          perm('notif', l.safePermNotif, _b('notificationAccess'), NativeBridge.openNotificationAccessSettings),
        ])),
        if (kDebugMode) ...[
          const SizedBox(height: 16),
          OutlinedButton.icon(
            key: const Key('guard-test'),
            onPressed: () async {
              if (await SafetyNative.simulate()) return; // native path: alert + event -> STOP screen
              if (context.mounted) Navigator.push(context, MaterialPageRoute(builder: (_) => const ScamStopScreen(speakOnOpen: false)));
            },
            icon: const Icon(Icons.science_outlined), label: Text(l.safeTestStop)),
        ],
      ]),
    );
  }
}

List<String> _reasons(BuildContext c, Map<String, dynamic> r) {
  final hi = Localizations.localeOf(c).languageCode == 'hi';
  final list = (hi ? r['reasonsHi'] : r['reasons']) ?? r['reasons'];
  return list is List ? list.map((e) => '$e').toList() : const [];
}

/// 15.5 check before paying + 15.7 report. QR: the text of the code (Google Lens / camera "copy text") is pasted in.
class UpiCheckScreen extends StatefulWidget {
  final ApiClient? api;
  final String? initial;
  const UpiCheckScreen({super.key, this.api, this.initial});
  @override
  State<UpiCheckScreen> createState() => _UpiCheckScreenState();
}

class _UpiCheckScreenState extends State<UpiCheckScreen> {
  late final _in = TextEditingController(text: widget.initial ?? '');
  late final ApiClient _api = widget.api ?? ApiClient.instance;
  Map<String, dynamic>? _r;
  bool _busy = false, _reported = false;
  String? _error;

  @override
  void dispose() { _in.dispose(); super.dispose(); }

  Future<void> _check() async {
    final t = _in.text.trim();
    if (t.isEmpty) return;
    setState(() { _busy = true; _r = null; _error = null; _reported = false; });
    try {
      final isQr = t.toLowerCase().startsWith('upi:') || t.contains('://') || t.contains('?');
      final r = isQr ? await _api.scanUpi(qrPayload: t) : await _api.scanUpi(vpa: t);
      if (mounted) setState(() => _r = r);
    } catch (_) {
      if (mounted) setState(() => _error = context.l10n.safeCheckFailed);
    }
    if (mounted) setState(() => _busy = false);
  }

  Future<void> _report() async {
    final vpa = (_r?['payee'] as Map?)?['vpa'] as String? ?? _in.text.trim();
    try {
      await _api.reportItem('UPI', vpa);
      if (mounted) setState(() => _reported = true);
    } catch (_) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(context.l10n.safeReportFailed)));
    }
  }

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    final r = _r;
    final v = r?['verdict'] as String?;
    final payee = (r?['payee'] as Map?)?['vpa'] as String?;
    return Scaffold(
      appBar: AppBar(title: Text(l.safeUpiTitle)),
      body: ListView(padding: const EdgeInsets.fromLTRB(16, 4, 16, 24), children: [
        IntroBanner(icon: Icons.qr_code_2_rounded, feature: Feature.web, text: l.safeUpiSub),
        TextField(key: const Key('upi-input'), controller: _in, minLines: 1, maxLines: 4,
            decoration: InputDecoration(hintText: l.safeUpiHint, prefixIcon: const Icon(Icons.account_balance_wallet_outlined))),
        Padding(padding: const EdgeInsets.fromLTRB(4, 8, 4, 12),
            child: Text(l.safeUpiQrNote, style: Theme.of(context).textTheme.bodySmall?.copyWith(color: Theme.of(context).colorScheme.onSurfaceVariant))),
        FilledButton.icon(key: const Key('upi-check'), onPressed: _busy ? null : _check, icon: const Icon(Icons.search_rounded), label: Text(l.safeCheck)),
        if (_busy) const Padding(padding: EdgeInsets.only(top: 12), child: LinearProgressIndicator()),
        if (_error != null) Padding(padding: const EdgeInsets.only(top: 12), child: Text(_error!)),
        if (r != null)
          VerdictCard(
            key: Key('upi-verdict-$v'),
            tone: toneOfVerdict(v),
            headline: v == 'MALICIOUS' ? l.safeVerdictBad : v == 'SUSPICIOUS' ? l.safeVerdictSusp : l.safeVerdictSafe,
            children: [
              if (payee != null) Text(l.safeUpiPayee(payee), style: const TextStyle(fontWeight: FontWeight.w600)),
              for (final reason in _reasons(context, r)) Padding(padding: const EdgeInsets.only(top: 6), child: Text('• $reason')),
              if (v != 'SAFE' && payee != null) ...[
                const SizedBox(height: 12),
                _reported
                    ? Pill(l.safeReported, key: const Key('upi-reported'), tone: Tone.safe, icon: Icons.check_rounded)
                    : OutlinedButton.icon(key: const Key('upi-report'), onPressed: _report, icon: const Icon(Icons.flag_outlined), label: Text(l.safeReport)),
              ],
            ],
          ),
      ]),
    );
  }
}

/// 15.9: is this a brand's real customer-care number?
class CareNumberScreen extends StatefulWidget {
  final ApiClient? api;
  const CareNumberScreen({super.key, this.api});
  @override
  State<CareNumberScreen> createState() => _CareNumberScreenState();
}

class _CareNumberScreenState extends State<CareNumberScreen> {
  final _num = TextEditingController(), _brand = TextEditingController();
  late final ApiClient _api = widget.api ?? ApiClient.instance;
  Map<String, dynamic>? _r;
  bool _busy = false;
  String? _error;

  @override
  void dispose() { _num.dispose(); _brand.dispose(); super.dispose(); }

  Future<void> _check() async {
    if (_num.text.trim().isEmpty) return;
    setState(() { _busy = true; _r = null; _error = null; });
    try {
      final r = await _api.careNumber(_num.text, brand: _brand.text);
      if (mounted) setState(() => _r = r);
    } catch (_) {
      if (mounted) setState(() => _error = context.l10n.safeCheckFailed);
    }
    if (mounted) setState(() => _busy = false);
  }

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    final r = _r;
    final v = r?['verdict'] as String?;
    final official = (r?['officialNumbers'] as List?) ?? const [];
    return Scaffold(
      appBar: AppBar(title: Text(l.safeCareTitle)),
      body: ListView(padding: const EdgeInsets.fromLTRB(16, 4, 16, 24), children: [
        IntroBanner(icon: Icons.support_agent_rounded, feature: Feature.call, text: l.safeCareSub),
        TextField(key: const Key('care-number'), controller: _num, keyboardType: TextInputType.phone,
            decoration: InputDecoration(labelText: l.safeCareHint, prefixIcon: const Icon(Icons.phone_rounded))),
        const SizedBox(height: 12),
        TextField(key: const Key('care-brand'), controller: _brand,
            decoration: InputDecoration(labelText: l.safeCareBrand, prefixIcon: const Icon(Icons.storefront_rounded))),
        const SizedBox(height: 14),
        FilledButton.icon(key: const Key('care-check'), onPressed: _busy ? null : _check, icon: const Icon(Icons.search_rounded), label: Text(l.safeCheck)),
        if (_busy) const Padding(padding: EdgeInsets.only(top: 12), child: LinearProgressIndicator()),
        if (_error != null) Padding(padding: const EdgeInsets.only(top: 12), child: Text(_error!)),
        if (r != null)
          VerdictCard(
            key: Key('care-verdict-$v'),
            tone: v == 'OFFICIAL' ? Tone.safe : toneOfVerdict(v),
            headline: switch (v) { 'OFFICIAL' => l.safeCareOfficial, 'FAKE' => l.safeCareFake, 'SUSPICIOUS' => l.safeCareSusp, _ => l.safeCareUnknown },
            children: [
              if (r['brand'] != null) Text('${r['brand']}', style: const TextStyle(fontWeight: FontWeight.w600)),
              for (final reason in _reasons(context, r)) Padding(padding: const EdgeInsets.only(top: 6), child: Text('• $reason')),
              if (official.isNotEmpty && v != 'OFFICIAL') ...[
                const SizedBox(height: 12),
                Text(l.safeCareReal, style: const TextStyle(fontWeight: FontWeight.w700)),
                for (final o in official.whereType<Map>())
                  ListTile(contentPadding: EdgeInsets.zero, leading: const FeatureIcon(Icons.call_rounded, Feature.call, size: 36),
                      title: Text('${o['number']}'), subtitle: Text('${o['purpose'] ?? ''}'),
                      onTap: () => SafetyNative.dial('${o['number']}'.replaceAll(RegExp(r'[^0-9+]'), ''))),
              ],
            ],
          ),
      ]),
    );
  }
}
