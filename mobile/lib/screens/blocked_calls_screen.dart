import 'package:flutter/material.dart';

import '../core/api_client.dart';
import '../core/call_guard.dart';
import '../l10n/l10n.dart';
import 'report_number_screen.dart';
import '../ui/screen_kit.dart';
import '../ui/theme.dart';

/// 16.10: screened calls of the last 30 days (local database on the phone). One-tap undo: "Not spam" puts the number
/// on the allow list so it always rings. No number leaves the phone unless the user taps Report.
class BlockedCallsScreen extends StatefulWidget {
  final ApiClient? api;
  const BlockedCallsScreen({super.key, this.api});
  @override
  State<BlockedCallsScreen> createState() => _BlockedCallsScreenState();
}

class _BlockedCallsScreenState extends State<BlockedCallsScreen> {
  List<Map<String, dynamic>>? _rows;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final r = await CallGuardNative.log();
    if (mounted) setState(() => _rows = r);
  }

  String _when(BuildContext c, int ms) {
    final d = DateTime.fromMillisecondsSinceEpoch(ms);
    final loc = MaterialLocalizations.of(c);
    return '${loc.formatShortDate(d)} ${loc.formatTimeOfDay(TimeOfDay.fromDateTime(d))}';
  }

  Future<void> _notSpam(String number) async {
    final c = await CallGuardNative.notSpam(number);
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(context.l10n.callNotSpamDone(c ?? number))));
    await _load();
  }

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    final rows = _rows;
    return Scaffold(
      appBar: AppBar(title: Text(l.callBlockedTitle)),
      body: rows == null
          ? const Center(child: CircularProgressIndicator())
          : rows.isEmpty
              ? Center(child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: Column(mainAxisSize: MainAxisSize.min, children: [
                    Container(padding: const EdgeInsets.all(22),
                        decoration: BoxDecoration(color: Tone.safe.bg(context), shape: BoxShape.circle),
                        child: Icon(Icons.phone_enabled_rounded, size: 44, color: Tone.safe.fg(context))),
                    const SizedBox(height: 16),
                    Text(l.callBlockedEmpty, key: const Key('calls-empty'), textAlign: TextAlign.center, style: Theme.of(context).textTheme.bodyLarge),
                  ]),
                ))
              : ListView(padding: const EdgeInsets.fromLTRB(16, 4, 16, 24), children: [
                  IntroBanner(icon: Icons.history_rounded, feature: Feature.call, text: l.callBlockedSub),
                  for (final r in rows) _row(context, r),
                ]),
    );
  }

  Widget _row(BuildContext context, Map<String, dynamic> r) {
    final l = context.l10n;
    final action = r['action'] as String? ?? 'ALLOW';
    final undone = r['undone'] == true;
    final number = r['number'] as String? ?? '';
    final dialable = number.startsWith('+') || RegExp(r'^\d{3,}$').hasMatch(number);
    final protected = r['kind'] == 'PROTECTED_SERIES';
    final outcome = undone
        ? l.callUndone
        : action == 'REJECT' ? l.callActionReject : action == 'SILENCE' ? l.callActionSilence : l.callActionAllow;
    final tone = undone ? Tone.safe : action == 'REJECT' ? Tone.danger : action == 'SILENCE' ? Tone.warn : Tone.info;
    final color = tone.fg(context);
    return Card(
      key: Key('call-row-${r['id']}'),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(14, 12, 8, 4),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Container(
              padding: const EdgeInsets.all(9),
              decoration: BoxDecoration(color: tone.bg(context), borderRadius: BorderRadius.circular(14)),
              child: Icon(action == 'REJECT' ? Icons.call_end_rounded : action == 'SILENCE' ? Icons.volume_off_rounded : Icons.phone_callback_rounded,
                  color: color, size: 22),
            ),
            const SizedBox(width: 12),
            Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(r['label'] as String? ?? number, style: const TextStyle(fontWeight: FontWeight.w600)),
              const SizedBox(height: 2),
              Text('$number · ${_when(context, (r['at'] as num).toInt())} · $outcome', style: TextStyle(color: color)),
            ])),
          ]),
          if (dialable && !protected)
            OverflowBar(alignment: MainAxisAlignment.end, children: [
              if (!undone)
                TextButton(key: Key('call-notspam-${r['id']}'), onPressed: () => _notSpam(number), child: Text(l.callNotSpam)),
              TextButton(
                key: Key('call-report-${r['id']}'),
                onPressed: () => Navigator.push(context,
                    MaterialPageRoute(builder: (_) => ReportNumberScreen(api: widget.api, number: number))),
                child: Text(l.callReport),
              ),
            ]),
        ]),
      ),
    );
  }
}
