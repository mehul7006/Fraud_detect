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

/// Phase 15 hub: live scam guard, check before you pay (UPI / QR), customer-care number, recovery kit, guardian, drills.
class SafetyScreen extends StatelessWidget {
  final ApiClient? api;
  const SafetyScreen({super.key, this.api});

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    Widget tile(Key k, IconData i, String t, String s, Widget Function() page) => Card(
          child: ListTile(key: k, leading: Icon(i, color: Colors.indigo), title: Text(t), subtitle: Text(s),
              trailing: const Icon(Icons.chevron_right),
              onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => page()))),
        );
    return Scaffold(
      appBar: AppBar(title: Text(l.safeHubTitle)),
      body: ListView(padding: const EdgeInsets.all(12), children: [
        tile(const Key('safe-guard'), Icons.shield_moon_outlined, l.safeGuardTitle, l.safeGuardSub, () => const ScamGuardScreen()),
        tile(const Key('safe-upi'), Icons.qr_code_2, l.safeUpiTitle, l.safeUpiSub, () => UpiCheckScreen(api: api)),
        tile(const Key('safe-care'), Icons.support_agent, l.safeCareTitle, l.safeCareSub, () => CareNumberScreen(api: api)),
        tile(const Key('safe-recovery'), Icons.healing, l.safeRecoveryTitle, l.safeRecoverySub, () => const RecoveryKitScreen()),
        tile(const Key('safe-guardian'), Icons.family_restroom, l.safeGuardianTitle, l.safeGuardianSub, () => const GuardianScreen()),
        tile(const Key('safe-drills'), Icons.school_outlined, l.safeDrillsTitle, l.safeDrillsSub, () => const DrillsScreen()),
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
          contentPadding: EdgeInsets.zero,
          leading: Icon(ok ? Icons.check_circle : Icons.radio_button_unchecked, color: ok ? Colors.green : Colors.orange),
          title: Text(label),
          trailing: ok ? Text(l.safeAllowed) : TextButton(onPressed: grant, child: Text(l.safeAllow)),
        );
    return Scaffold(
      appBar: AppBar(title: Text(l.safeGuardTitle)),
      body: ListView(padding: const EdgeInsets.all(16), children: [
        Text(l.safeGuardIntro),
        SwitchListTile(
          key: const Key('guard-switch'),
          contentPadding: EdgeInsets.zero,
          title: Text(l.safeGuardSwitch, style: const TextStyle(fontWeight: FontWeight.bold)),
          value: _b('enabled'),
          onChanged: (v) async {
            if (v && !_b('phoneState')) await SafetyNative.requestPermissions();
            await SafetyNative.setGuard(v);
            _refresh();
          },
        ),
        SwitchListTile(
          key: const Key('otp-switch'),
          contentPadding: EdgeInsets.zero,
          title: Text(l.safeOtpSwitch),
          subtitle: Text(l.safeOtpSwitchSub),
          value: _s['otpGuard'] != false,
          onChanged: (v) async { await SafetyNative.setOtpGuard(v); _refresh(); },
        ),
        const Divider(),
        Text(l.safePermsTitle, style: Theme.of(context).textTheme.titleMedium),
        perm('phone', l.safePermPhone, _b('phoneState'), SafetyNative.requestPermissions),
        perm('calllog', l.safePermCallLog, _b('callLog'), SafetyNative.requestPermissions),
        perm('contacts', l.safePermContacts, _b('contacts'), SafetyNative.requestPermissions),
        perm('usage', l.safePermUsage, _b('usageAccess'), NativeBridge.openUsageAccessSettings),
        perm('notif', l.safePermNotif, _b('notificationAccess'), NativeBridge.openNotificationAccessSettings),
        if (kDebugMode) ...[
          const Divider(),
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

Color _verdictColor(String? v) => switch (v) {
      'MALICIOUS' || 'FAKE' => Colors.red.shade800,
      'SUSPICIOUS' || 'UNKNOWN' => Colors.orange.shade900,
      _ => Colors.green.shade800,
    };

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
      body: ListView(padding: const EdgeInsets.all(16), children: [
        TextField(key: const Key('upi-input'), controller: _in, minLines: 1, maxLines: 4,
            decoration: InputDecoration(hintText: l.safeUpiHint, border: const OutlineInputBorder())),
        Padding(padding: const EdgeInsets.symmetric(vertical: 6), child: Text(l.safeUpiQrNote, style: Theme.of(context).textTheme.bodySmall)),
        FilledButton.icon(key: const Key('upi-check'), onPressed: _busy ? null : _check, icon: const Icon(Icons.search), label: Text(l.safeCheck)),
        if (_busy) const Padding(padding: EdgeInsets.only(top: 12), child: LinearProgressIndicator()),
        if (_error != null) Padding(padding: const EdgeInsets.only(top: 12), child: Text(_error!)),
        if (r != null)
          Card(
            key: Key('upi-verdict-$v'),
            margin: const EdgeInsets.only(top: 16),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(v == 'MALICIOUS' ? l.safeVerdictBad : v == 'SUSPICIOUS' ? l.safeVerdictSusp : l.safeVerdictSafe,
                    style: Theme.of(context).textTheme.titleLarge?.copyWith(color: _verdictColor(v), fontWeight: FontWeight.bold)),
                if (payee != null) Text(l.safeUpiPayee(payee)),
                for (final reason in _reasons(context, r)) Padding(padding: const EdgeInsets.only(top: 6), child: Text('• $reason')),
                if (v != 'SAFE' && payee != null) ...[
                  const SizedBox(height: 8),
                  _reported
                      ? Text(l.safeReported, key: const Key('upi-reported'))
                      : OutlinedButton.icon(key: const Key('upi-report'), onPressed: _report, icon: const Icon(Icons.flag_outlined), label: Text(l.safeReport)),
                ],
              ]),
            ),
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
      body: ListView(padding: const EdgeInsets.all(16), children: [
        TextField(key: const Key('care-number'), controller: _num, keyboardType: TextInputType.phone,
            decoration: InputDecoration(labelText: l.safeCareHint, border: const OutlineInputBorder())),
        const SizedBox(height: 8),
        TextField(key: const Key('care-brand'), controller: _brand,
            decoration: InputDecoration(labelText: l.safeCareBrand, border: const OutlineInputBorder())),
        const SizedBox(height: 8),
        FilledButton.icon(key: const Key('care-check'), onPressed: _busy ? null : _check, icon: const Icon(Icons.search), label: Text(l.safeCheck)),
        if (_busy) const Padding(padding: EdgeInsets.only(top: 12), child: LinearProgressIndicator()),
        if (_error != null) Padding(padding: const EdgeInsets.only(top: 12), child: Text(_error!)),
        if (r != null)
          Card(
            key: Key('care-verdict-$v'),
            margin: const EdgeInsets.only(top: 16),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(switch (v) { 'OFFICIAL' => l.safeCareOfficial, 'FAKE' => l.safeCareFake, 'SUSPICIOUS' => l.safeCareSusp, _ => l.safeCareUnknown },
                    style: Theme.of(context).textTheme.titleLarge?.copyWith(color: _verdictColor(v), fontWeight: FontWeight.bold)),
                if (r['brand'] != null) Text('${r['brand']}'),
                for (final reason in _reasons(context, r)) Padding(padding: const EdgeInsets.only(top: 6), child: Text('• $reason')),
                if (official.isNotEmpty && v != 'OFFICIAL') ...[
                  const SizedBox(height: 8),
                  Text(l.safeCareReal, style: const TextStyle(fontWeight: FontWeight.w600)),
                  for (final o in official.whereType<Map>())
                    ListTile(dense: true, contentPadding: EdgeInsets.zero, title: Text('${o['number']}'), subtitle: Text('${o['purpose'] ?? ''}'),
                        trailing: const Icon(Icons.call), onTap: () => SafetyNative.dial('${o['number']}'.replaceAll(RegExp(r'[^0-9+]'), ''))),
                ],
              ]),
            ),
          ),
      ]),
    );
  }
}
