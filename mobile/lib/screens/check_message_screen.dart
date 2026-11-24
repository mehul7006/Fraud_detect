import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../core/api_client.dart';
import '../core/message_check.dart';
import '../core/message_rules.dart';
import '../l10n/l10n.dart';
import 'why_panel.dart';

/// 14.4 / 14.7 / 14.8: "Check this message". Paste (or share) a message; the phone judges it on its own
/// (text stays on the phone). "Check with cloud AI" is a separate, opt-in step with a clear consent.
class CheckMessageScreen extends StatefulWidget {
  final ApiClient? api;
  final String? initialText;
  const CheckMessageScreen({super.key, this.api, this.initialText});
  @override
  State<CheckMessageScreen> createState() => _CheckMessageScreenState();
}

class _CheckMessageScreenState extends State<CheckMessageScreen> {
  late final _text = TextEditingController(text: widget.initialText ?? '');
  MessageVerdict? _v;
  bool _busy = false;
  late final _check = MessageCheck(widget.api ?? ApiClient.instance);

  @override
  void initState() {
    super.initState();
    if ((widget.initialText ?? '').trim().isNotEmpty) WidgetsBinding.instance.addPostFrameCallback((_) => _run());
  }

  Future<void> _run() async {
    if (_text.text.trim().isEmpty) return;
    setState(() { _busy = true; _v = null; });
    final v = await _check.local(_text.text.trim());
    if (mounted) setState(() { _v = v; _busy = false; });
  }

  Future<void> _cloud() async {
    final ok = await CloudConsentDialog.ask(context);
    if (!ok || !mounted || _v == null) return;
    setState(() => _busy = true);
    try {
      final v = await _check.withCloud(_text.text.trim(), _v!);
      if (mounted) setState(() => _v = v);
    } catch (_) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(context.l10n.msgCloudFailed)));
    }
    if (mounted) setState(() => _busy = false);
  }

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    return Scaffold(
      appBar: AppBar(title: Text(l.msgTitle)),
      body: ListView(padding: const EdgeInsets.all(16), children: [
        Text(l.msgIntro),
        const SizedBox(height: 12),
        TextField(
          key: const Key('msg-input'),
          controller: _text,
          minLines: 4, maxLines: 10, maxLength: 2000,
          decoration: InputDecoration(border: const OutlineInputBorder(), hintText: l.msgHint),
        ),
        FilledButton.icon(key: const Key('msg-check'), onPressed: _busy ? null : _run, icon: const Icon(Icons.search), label: Text(l.msgCheck)),
        if (_busy) const Padding(padding: EdgeInsets.only(top: 12), child: LinearProgressIndicator()),
        if (_v != null) MessageVerdictView(verdict: _v!, onCloud: _v!.cloud ? null : _cloud),
      ]),
    );
  }
}

/// 14.8: the verdict - what kind of scam, why (each rule in plain words), what the links are, what to do.
class MessageVerdictView extends StatelessWidget {
  final MessageVerdict verdict;
  final VoidCallback? onCloud;
  const MessageVerdictView({super.key, required this.verdict, this.onCloud});

  static String categoryName(BuildContext c, String? cat) {
    final l = c.l10n;
    return switch (cat) {
      'BANK_KYC_BLOCK' => l.msgCatKyc, 'LOTTERY_PRIZE' => l.msgCatPrize, 'COURIER_PARCEL' => l.msgCatParcel,
      'ELECTRICITY_DISCONNECT' => l.msgCatPower, 'JOB_TASK' => l.msgCatJob, 'LOAN_INSTANT' => l.msgCatLoan,
      'DIGITAL_ARREST' => l.msgCatArrest, 'OTP_REQUEST' => l.msgCatOtp, 'UPI_REFUND' => l.msgCatUpi,
      'INVESTMENT_TRADING' => l.msgCatInvest, _ => l.msgCatGeneric,
    };
  }

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    final hi = Localizations.localeOf(context).languageCode == 'hi';
    final v = verdict;
    final safe = v.verdict == 'SAFE';
    final bad = v.verdict == 'MALICIOUS';
    final color = safe ? Colors.green.shade800 : bad ? Colors.red.shade800 : Colors.orange.shade900;
    return Card(
      key: Key('msg-verdict-${v.verdict}'),
      margin: const EdgeInsets.only(top: 16),
      color: safe ? Colors.green.shade50 : bad ? Colors.red.shade50 : Colors.orange.shade50,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            Icon(safe ? Icons.verified_user : Icons.gpp_bad, color: color, size: 32),
            const SizedBox(width: 10),
            Expanded(child: Text(
              safe ? l.msgSafe : (bad ? l.msgScam(categoryName(context, v.category)) : l.msgSuspicious(categoryName(context, v.category))),
              key: const Key('msg-headline'),
              style: Theme.of(context).textTheme.titleMedium?.copyWith(color: color, fontWeight: FontWeight.bold))),
          ]),
          for (final RuleHit h in v.hits.take(5))
            Padding(padding: const EdgeInsets.only(top: 6), child: Text('• ${hi ? h.hi : h.en}')),
          for (final link in v.links)
            ListTile(
              dense: true, contentPadding: EdgeInsets.zero,
              leading: Icon(link.verdict == 'MALICIOUS' ? Icons.link_off : Icons.link,
                  color: link.verdict == 'MALICIOUS' ? Colors.red.shade800 : Colors.grey.shade700),
              title: Text(link.url, maxLines: 1, overflow: TextOverflow.ellipsis),
              subtitle: Text(link.verdict == 'MALICIOUS'
                  ? (link.brand != null ? l.msgLinkFake(link.brand!) : l.msgLinkBad)
                  : link.verdict == 'SAFE' ? l.msgLinkOk : l.msgLinkUnknown),
            ),
          if (WhyPanel.from(v.cloudExplanation) case final why?) why,
          if (!safe) ...[
            const SizedBox(height: 8),
            Text(l.msgAdvice, style: const TextStyle(fontWeight: FontWeight.w600)),
          ],
          if (v.cloud) Padding(padding: const EdgeInsets.only(top: 8), child: Text(l.msgCloudDone, style: Theme.of(context).textTheme.bodySmall)),
          if (onCloud != null)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: OutlinedButton.icon(key: const Key('msg-cloud'), onPressed: onCloud, icon: const Icon(Icons.cloud_upload_outlined), label: Text(l.msgCloudButton)),
            ),
        ]),
      ),
    );
  }
}

/// 14.7: the cloud check never runs without this tap. First time: full explanation; every time: confirm.
class CloudConsentDialog {
  static const _seenKey = 'msg_cloud_explained';

  static Future<bool> ask(BuildContext context) async {
    final l = context.l10n;
    final prefs = await SharedPreferences.getInstance();
    final first = !(prefs.getBool(_seenKey) ?? false);
    if (!context.mounted) return false;
    final ok = await showDialog<bool>(
      context: context,
      builder: (c) => AlertDialog(
        icon: const Icon(Icons.cloud_upload_outlined),
        title: Text(l.msgCloudTitle),
        content: SingleChildScrollView(child: Text(first ? l.msgCloudFirst : l.msgCloudAgain, key: const Key('msg-cloud-text'))),
        actions: [
          TextButton(key: const Key('msg-cloud-no'), onPressed: () => Navigator.pop(c, false), child: Text(l.vpnNotNow)),
          FilledButton(key: const Key('msg-cloud-yes'), onPressed: () => Navigator.pop(c, true), child: Text(l.msgCloudSend)),
        ],
      ),
    );
    if (ok == true) await prefs.setBool(_seenKey, true);
    return ok == true;
  }
}
