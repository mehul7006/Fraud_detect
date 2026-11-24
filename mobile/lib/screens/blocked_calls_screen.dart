import 'package:flutter/material.dart';

import '../core/api_client.dart';
import '../core/call_guard.dart';
import '../l10n/l10n.dart';
import 'report_number_screen.dart';

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
              ? Center(child: Padding(padding: const EdgeInsets.all(24), child: Text(l.callBlockedEmpty, key: const Key('calls-empty'))))
              : ListView(children: [
                  Padding(padding: const EdgeInsets.fromLTRB(16, 12, 16, 4), child: Text(l.callBlockedSub)),
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
    final color = undone ? Colors.green.shade800 : action == 'REJECT' ? Colors.red.shade800 : Colors.orange.shade900;
    return Card(
      key: Key('call-row-${r['id']}'),
      margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(12, 8, 8, 4),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            Icon(action == 'REJECT' ? Icons.call_end : Icons.phone_callback, color: color),
            const SizedBox(width: 8),
            Expanded(child: Text(r['label'] as String? ?? number, style: const TextStyle(fontWeight: FontWeight.w600))),
          ]),
          Padding(
            padding: const EdgeInsets.only(left: 32, top: 2),
            child: Text('$number · ${_when(context, (r['at'] as num).toInt())} · $outcome', style: TextStyle(color: color)),
          ),
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
