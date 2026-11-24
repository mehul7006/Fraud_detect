import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../core/api_client.dart';
import '../core/message_check.dart';
import '../core/message_rules.dart';
import '../l10n/l10n.dart';
import 'why_panel.dart';
import '../ui/components.dart';
import '../ui/theme.dart';

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
      appBar: AppBar(), // the banner below carries the title
      body: ListView(padding: const EdgeInsets.fromLTRB(16, 4, 16, 32), children: [
        ScreenBanner(icon: Icons.mark_chat_read_rounded, feature: Feature.message, title: l.msgTitle, subtitle: l.msgIntro),
        const SizedBox(height: 4),
        TextField(
          key: const Key('msg-input'),
          controller: _text,
          minLines: 4, maxLines: 10, maxLength: 2000,
          decoration: InputDecoration(
            hintText: l.msgHint,
            prefixIcon: const Padding(padding: EdgeInsets.only(bottom: 64), child: Icon(Icons.sms_outlined)),
          ),
        ),
        const SizedBox(height: 4),
        FilledButton.icon(key: const Key('msg-check'), onPressed: _busy ? null : _run, icon: const Icon(Icons.search_rounded), label: Text(l.msgCheck)),
        if (_busy) const Padding(padding: EdgeInsets.only(top: 16), child: LinearProgressIndicator(minHeight: 6, borderRadius: BorderRadius.all(Radius.circular(6)))),
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
    final t = Theme.of(context);
    final hi = Localizations.localeOf(context).languageCode == 'hi';
    final v = verdict;
    final safe = v.verdict == 'SAFE';
    final bad = v.verdict == 'MALICIOUS';
    final tone = safe ? Tone.safe : bad ? Tone.danger : Tone.warn;
    final gradient = safe ? Feature.account.deepGradient : bad ? AppColors.dangerGradient : Feature.apps.deepGradient;
    return Card(
      key: Key('msg-verdict-${v.verdict}'),
      margin: const EdgeInsets.only(top: 20),
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        // gradient verdict header
        Container(
          padding: const EdgeInsets.fromLTRB(18, 20, 18, 20),
          decoration: BoxDecoration(gradient: gradient),
          child: Row(children: [
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(color: Colors.white.withValues(alpha: 0.18), borderRadius: BorderRadius.circular(16)),
              child: Icon(safe ? Icons.verified_user_rounded : Icons.gpp_bad_rounded, color: Colors.white, size: 34),
            ),
            const SizedBox(width: 14),
            Expanded(child: Text(
              safe ? l.msgSafe : (bad ? l.msgScam(categoryName(context, v.category)) : l.msgSuspicious(categoryName(context, v.category))),
              key: const Key('msg-headline'),
              style: t.textTheme.titleMedium?.copyWith(color: Colors.white, fontWeight: FontWeight.w700, height: 1.3))),
          ]),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 16),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            for (final RuleHit h in v.hits.take(5))
              Padding(
                padding: const EdgeInsets.only(top: 8),
                child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Padding(padding: const EdgeInsets.only(top: 2), child: Icon(Icons.error_outline_rounded, size: 20, color: tone.fg(context))),
                  const SizedBox(width: 10),
                  Expanded(child: Text(hi ? h.hi : h.en, style: t.textTheme.bodyMedium?.copyWith(color: t.colorScheme.onSurface))),
                ]),
              ),
            for (final link in v.links)
              Container(
                margin: const EdgeInsets.only(top: 12),
                decoration: BoxDecoration(
                  color: (link.verdict == 'MALICIOUS' ? Tone.danger : link.verdict == 'SAFE' ? Tone.safe : Tone.neutral).bg(context),
                  borderRadius: BorderRadius.circular(16)),
                child: ListTile(
                  dense: true,
                  leading: Icon(link.verdict == 'MALICIOUS' ? Icons.link_off_rounded : Icons.link_rounded,
                      color: (link.verdict == 'MALICIOUS' ? Tone.danger : link.verdict == 'SAFE' ? Tone.safe : Tone.neutral).fg(context)),
                  title: Text(link.url, maxLines: 1, overflow: TextOverflow.ellipsis),
                  subtitle: Text(link.verdict == 'MALICIOUS'
                      ? (link.brand != null ? l.msgLinkFake(link.brand!) : l.msgLinkBad)
                      : link.verdict == 'SAFE' ? l.msgLinkOk : l.msgLinkUnknown),
                ),
              ),
            if (WhyPanel.from(v.cloudExplanation) case final why?) why,
            if (!safe)
              Container(
                margin: const EdgeInsets.only(top: 14),
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(color: Tone.warn.bg(context), borderRadius: BorderRadius.circular(16)),
                child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Icon(Icons.tips_and_updates_rounded, color: Tone.warn.fg(context)),
                  const SizedBox(width: 10),
                  Expanded(child: Text(l.msgAdvice, style: t.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w600, color: t.colorScheme.onSurface))),
                ]),
              ),
            if (v.cloud)
              Padding(padding: const EdgeInsets.only(top: 12),
                  child: Pill(l.msgCloudDone, tone: Tone.info, icon: Icons.cloud_done_rounded)),
            if (onCloud != null)
              Padding(
                padding: const EdgeInsets.only(top: 14),
                child: OutlinedButton.icon(key: const Key('msg-cloud'), onPressed: onCloud, icon: const Icon(Icons.cloud_upload_outlined), label: Text(l.msgCloudButton)),
              ),
          ]),
        ),
      ]),
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
