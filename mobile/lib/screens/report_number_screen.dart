import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../core/api_client.dart';
import '../core/call_guard.dart';
import '../core/native_bridge.dart';
import '../l10n/l10n.dart';
import '../ui/components.dart';
import '../ui/screen_kit.dart';
import '../ui/theme.dart';

/// 16.5 / 16.12: report a number to FraudShield (crowd list) and to the government (DoT Sanchar Saathi "Chakshu":
/// copy the number, open the portal). 1600 / 140 numbers are official service series and cannot be reported (TRAI).
class ReportNumberScreen extends StatefulWidget {
  final ApiClient? api;
  final String? number;
  const ReportNumberScreen({super.key, this.api, this.number});
  @override
  State<ReportNumberScreen> createState() => _ReportNumberScreenState();
}

class _ReportNumberScreenState extends State<ReportNumberScreen> {
  late final _num = TextEditingController(text: widget.number ?? '');
  String _cat = 'SCAM';
  bool _busy = false;
  String? _msg;
  bool _ok = false;

  @override
  void dispose() {
    _num.dispose();
    super.dispose();
  }

  Future<void> _send() async {
    final l = context.l10n;
    final n = _num.text.trim();
    setState(() { _busy = true; _msg = null; });
    try {
      final c = await CallGuardNative.check(n);
      if (c['canonical'] == null) {
        _done(l.callAllowInvalid, false);
        return;
      }
      if (c['protected'] == true) {
        _done(l.callReportProtected, false);
        return;
      }
      await (widget.api ?? ApiClient.instance).reportNumber(c['canonical'] as String, _cat);
      _done(l.callReportDone, true);
    } on ApiException catch (e) {
      _done(e.status == 422 ? l.callReportProtected : e.status == 429 ? l.callReportLimit
          : e.status == 400 ? l.callAllowInvalid : l.callReportFailed, false);
    } catch (_) {
      _done(l.callReportFailed, false);
    }
  }

  void _done(String m, bool ok) {
    if (mounted) setState(() { _busy = false; _msg = m; _ok = ok; });
  }

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    final t = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(l.callReportTitle)),
      body: ListView(padding: const EdgeInsets.fromLTRB(16, 4, 16, 24), children: [
        IntroBanner(icon: Icons.flag_rounded, feature: Feature.scam, text: l.callReportIntro),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              TextField(
                key: const Key('report-number'),
                controller: _num,
                keyboardType: TextInputType.phone,
                decoration: InputDecoration(labelText: l.callReportNumber, prefixIcon: const Icon(Icons.phone_rounded)),
              ),
              const SizedBox(height: 14),
              Text(l.callReportCategory, style: t.textTheme.titleSmall),
              const SizedBox(height: 6),
              Wrap(spacing: 8, runSpacing: 8, children: [
                for (final (k, label) in [('SCAM', l.callCatScam), ('SPAM', l.callCatSpam), ('TELEMARKETING', l.callCatTele)])
                  ChoiceChip(key: Key('report-cat-$k'), label: Text(label), selected: _cat == k, onSelected: (_) => setState(() => _cat = k)),
              ]),
              const SizedBox(height: 16),
              FilledButton.icon(
                key: const Key('report-send'),
                onPressed: _busy || _ok ? null : _send,
                icon: const Icon(Icons.flag_rounded),
                label: Text(l.callReportSend),
              ),
              if (_busy) const Padding(padding: EdgeInsets.only(top: 10), child: LinearProgressIndicator()),
              if (_msg != null)
                Container(
                  margin: const EdgeInsets.only(top: 12),
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(color: (_ok ? Tone.safe : Tone.danger).bg(context), borderRadius: BorderRadius.circular(14)),
                  child: Row(children: [
                    Icon(_ok ? Icons.check_circle_rounded : Icons.error_outline_rounded, color: (_ok ? Tone.safe : Tone.danger).fg(context)),
                    const SizedBox(width: 8),
                    Expanded(child: Text(_msg!, key: const Key('report-result'),
                        style: TextStyle(color: (_ok ? Tone.safe : Tone.danger).fg(context), fontWeight: FontWeight.w600))),
                  ]),
                ),
            ]),
          ),
        ),
        SectionTitle(l.callGovTitle),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                const FeatureIcon(Icons.account_balance_rounded, Feature.web, size: 40),
                const SizedBox(width: 12),
                Expanded(child: Text(l.callGovBody)),
              ]),
              const SizedBox(height: 12),
              Wrap(spacing: 8, runSpacing: 8, children: [
                OutlinedButton.icon(
                  key: const Key('gov-copy'),
                  icon: const Icon(Icons.copy_rounded),
                  label: Text(l.callGovCopy),
                  onPressed: () async {
                    await Clipboard.setData(ClipboardData(text: _num.text.trim()));
                    if (context.mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(l.callGovCopied)));
                  },
                ),
                FilledButton.tonalIcon(
                  key: const Key('gov-open'),
                  icon: const Icon(Icons.open_in_new_rounded),
                  label: Text(l.callGovOpen),
                  onPressed: () async {
                    // copy first (the portal form needs the number pasted), but never let the clipboard hold up opening it
                    unawaited(Clipboard.setData(ClipboardData(text: _num.text.trim())));
                    await NativeBridge.openUrl(kChakshuUrl);
                  },
                ),
              ]),
              const SizedBox(height: 12),
              Text(l.callGovMoney, style: TextStyle(fontWeight: FontWeight.w600, color: Tone.warn.fg(context))),
            ]),
          ),
        ),
      ]),
    );
  }
}
