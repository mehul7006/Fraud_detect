import 'package:flutter/material.dart';

import '../core/api_client.dart';
import '../l10n/l10n.dart';
import '../ui/components.dart';
import '../ui/theme.dart';
import 'states.dart';
import 'why_panel.dart';

/// Threat history: a summary of what is still open, filters (all / open / resolved), threats grouped by day,
/// one card per threat coloured by its kind, with severity, time, source app, the "why" and a clear Resolve.
class ThreatsScreen extends StatefulWidget {
  final ApiClient? api; // injectable for tests
  const ThreatsScreen({super.key, this.api});
  @override
  State<ThreatsScreen> createState() => _ThreatsScreenState();
}

enum _Filter { all, open, resolved }

class _ThreatsScreenState extends State<ThreatsScreen> {
  ApiClient get _api => widget.api ?? ApiClient.instance;
  late Future<List<dynamic>> _f = _api.threats();
  _Filter _filter = _Filter.all;

  Future<void> _resolve(int id) async {
    try {
      await _api.resolve(id);
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(friendlyError(e, context.l10n))));
      return;
    }
    if (mounted) setState(() { _f = _api.threats(); });
  }

  static Tone _tone(String? s) => switch (s) { 'CRITICAL' || 'HIGH' => Tone.danger, 'MEDIUM' => Tone.warn, _ => Tone.info };

  static (IconData, Feature) _kind(String? type) => switch (type) {
        'MALICIOUS_LINK' => (Icons.link_off_rounded, Feature.web),
        'MALICIOUS_FILE' => (Icons.insert_drive_file_rounded, Feature.files),
        'AD_ABUSE' => (Icons.ads_click_rounded, Feature.apps),
        'CALL_FORWARDING' => (Icons.phone_forwarded_rounded, Feature.call),
        'SMS_FORWARDING' || 'UNEXPECTED_OTP' => (Icons.sms_failed_rounded, Feature.message),
        'CLONE_RISK' => (Icons.phonelink_lock_rounded, Feature.device),
        'WHATSAPP_TAKEOVER' || 'TELEGRAM_TAKEOVER' || 'ACCOUNT_TAKEOVER' => (Icons.lock_person_rounded, Feature.account),
        'LIVE_SCAM' => (Icons.front_hand_rounded, Feature.scam),
        _ => (Icons.warning_rounded, Feature.history),
      };

  DateTime? _when(dynamic t) => DateTime.tryParse('${t['createdAt'] ?? ''}')?.toLocal();

  String _dayLabel(BuildContext c, DateTime? d) {
    if (d == null) return '';
    final hi = Localizations.localeOf(c).languageCode == 'hi';
    final now = DateTime.now();
    final day = DateTime(d.year, d.month, d.day);
    final today = DateTime(now.year, now.month, now.day);
    final diff = today.difference(day).inDays;
    if (diff == 0) return hi ? 'आज' : 'Today';
    if (diff == 1) return hi ? 'कल' : 'Yesterday';
    return '${d.day} ${(hi ? _monthsHi : _monthsEn)[d.month - 1]} ${d.year}';
  }

  // own formatting: no locale data to load, so a date can never break the screen
  static const _monthsEn = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  static const _monthsHi = ['जन', 'फ़र', 'मार्च', 'अप्रैल', 'मई', 'जून', 'जुलाई', 'अग', 'सित', 'अक्टू', 'नव', 'दिस'];

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: Text(context.l10n.threatHistory)), // also shown while loading / empty / offline
        body: FutureBuilder<List<dynamic>>(
          future: _f,
          builder: (c, s) {
            if (s.hasError) return ErrorState(error: s.error!, onRetry: () => setState(() { _f = _api.threats(); }));
            if (!s.hasData) return const Center(child: CircularProgressIndicator());
            if (s.data!.isEmpty) return EmptyState(icon: Icons.verified_user, text: context.l10n.noThreats);
            final all = s.data!;
            final open = all.where((t) => t['status'] == 'OPEN').length;
            final shown = all.where((t) => switch (_filter) {
                  _Filter.all => true,
                  _Filter.open => t['status'] == 'OPEN',
                  _Filter.resolved => t['status'] != 'OPEN',
                }).toList()
              ..sort((a, b) => (_when(b) ?? DateTime(0)).compareTo(_when(a) ?? DateTime(0)));
            // group by day (newest first)
            final groups = <String, List<dynamic>>{};
            for (final t in shown) { groups.putIfAbsent(_dayLabel(context, _when(t)), () => []).add(t); }
            return RefreshIndicator(
              onRefresh: () {
                setState(() { _f = _api.threats(); });   // block body: setState must not return a Future
                return _f;                               // keeps the pull-to-refresh spinner until loaded
              },
              child: ListView(padding: const EdgeInsets.fromLTRB(16, 4, 16, 32), children: [
                _hero(context, open, all.length),
                const SizedBox(height: 14),
                _filters(context, all.length, open),
                for (final g in groups.entries) ...[
                  SectionTitle(g.key),
                  for (final t in g.value) _ThreatCard(threat: t, tone: _tone(t['severity'] as String?), kind: _kind(t['type'] as String?),
                      time: _when(t), onResolve: () => _resolve(t['id'] as int)),
                ],
              ]),
            );
          },
        ),
      );

  Widget _hero(BuildContext context, int open, int total) {
    final t = Theme.of(context).textTheme;
    final hi = Localizations.localeOf(context).languageCode == 'hi';
    final clear = open == 0;
    return Container(
      key: const Key('threat-summary'),
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        gradient: clear ? Feature.account.gradient : AppColors.dangerGradient,
        borderRadius: BorderRadius.circular(26),
        boxShadow: [BoxShadow(color: (clear ? Feature.account.from : AppColors.danger).withValues(alpha: 0.28), blurRadius: 20, offset: const Offset(0, 10))],
      ),
      child: Row(children: [
        Container(
          width: 64, height: 64,
          decoration: BoxDecoration(color: Colors.white.withValues(alpha: 0.18), borderRadius: BorderRadius.circular(20)),
          child: Icon(clear ? Icons.verified_user_rounded : Icons.gpp_maybe_rounded, color: Colors.white, size: 34),
        ),
        const SizedBox(width: 16),
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(clear ? (hi ? 'सब सुरक्षित' : 'All clear') : (hi ? '$open पर ध्यान दें' : '$open need your attention'),
              style: t.titleLarge?.copyWith(color: Colors.white)),
          const SizedBox(height: 4),
          Text(hi ? 'कुल $total खतरे पकड़े गए, ${total - open} सुलझ गए' : '$total threats caught, ${total - open} resolved',
              style: t.bodyMedium?.copyWith(color: Colors.white)),
        ])),
      ]),
    );
  }

  Widget _filters(BuildContext context, int total, int open) {
    final hi = Localizations.localeOf(context).languageCode == 'hi';
    Widget chip(_Filter f, String label) => ChoiceChip(
          key: Key('threat-filter-${f.name}'),
          showCheckmark: false,
          label: Text(label),
          selected: _filter == f,
          onSelected: (_) => setState(() => _filter = f),
        );
    return Wrap(spacing: 8, runSpacing: 8, children: [
      chip(_Filter.all, hi ? 'सभी ($total)' : 'All ($total)'),
      chip(_Filter.open, hi ? 'खुले ($open)' : 'Open ($open)'),
      chip(_Filter.resolved, hi ? 'सुलझे (${total - open})' : 'Resolved (${total - open})'),
    ]);
  }
}

String _clock(DateTime t, bool hi) {
  final h = t.hour % 12 == 0 ? 12 : t.hour % 12;
  final m = t.minute.toString().padLeft(2, '0');
  return hi ? '$h:$m ${t.hour < 12 ? 'सुबह' : 'शाम'}' : '$h:$m ${t.hour < 12 ? 'AM' : 'PM'}';
}

class _ThreatCard extends StatelessWidget {
  final dynamic threat;
  final Tone tone;
  final (IconData, Feature) kind;
  final DateTime? time;
  final VoidCallback onResolve;
  const _ThreatCard({required this.threat, required this.tone, required this.kind, required this.time, required this.onResolve});

  @override
  Widget build(BuildContext context) {
    final th = Theme.of(context);
    final l = context.l10n;
    final hi = Localizations.localeOf(context).languageCode == 'hi';
    final isOpen = threat['status'] == 'OPEN';
    final sev = '${threat['severity'] ?? ''}';
    final detail = '${threat['detail'] ?? ''}'.trim();
    final app = threat['sourceApp'] as String?;
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: IntrinsicHeight(
        child: Row(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          // severity stripe on the left edge
          Container(width: 6, decoration: BoxDecoration(gradient: isOpen ? LinearGradient(colors: [tone.fg(context), tone.fg(context).withValues(alpha: 0.6)],
              begin: Alignment.topCenter, end: Alignment.bottomCenter) : null, color: isOpen ? null : th.colorScheme.outlineVariant)),
          Expanded(child: Padding(
            padding: const EdgeInsets.fromLTRB(14, 14, 14, 12),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                FeatureIcon(kind.$1, kind.$2, size: 42),
                const SizedBox(width: 12),
                Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text('${threat['title']}', style: th.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
                  const SizedBox(height: 6),
                  Wrap(spacing: 6, runSpacing: 6, crossAxisAlignment: WrapCrossAlignment.center, children: [
                    if (sev.isNotEmpty) Pill(sev, tone: tone),
                    if (time != null)
                      Pill(_clock(time!, hi), tone: Tone.neutral, icon: Icons.schedule_rounded),
                    if (app != null && app.isNotEmpty) Pill(app, tone: Tone.neutral, icon: Icons.apps_rounded),
                  ]),
                ])),
              ]),
              if (detail.isNotEmpty) ...[
                const SizedBox(height: 10),
                Text(detail, style: th.textTheme.bodyMedium?.copyWith(color: th.colorScheme.onSurfaceVariant)),
              ],
              if (WhyPanel.from({'en': threat['explanationEn'], 'hi': threat['explanationHi']}) case final why?)
                Padding(padding: const EdgeInsets.only(top: 8), child: why),
              const SizedBox(height: 8),
              Align(
                alignment: Alignment.centerRight,
                child: isOpen
                    ? FilledButton.icon(
                        onPressed: onResolve,
                        icon: const Icon(Icons.task_alt_rounded, size: 20),
                        label: Text(l.resolve),
                        style: FilledButton.styleFrom(minimumSize: const Size(48, 44)),
                      )
                    : Pill(hi ? 'सुलझ गया' : 'Resolved', tone: Tone.safe, icon: Icons.check),
              ),
            ]),
          )),
        ]),
      ),
    );
  }
}
