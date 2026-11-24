import 'package:flutter/material.dart';

import '../l10n/l10n.dart';
import '../ui/theme.dart';

/// 11.10: "Why is this risky?" - the server's plain explanation, switchable between English and Hindi
/// (starts in the app's language). [explanation] = {en, hi, source?}.
class WhyPanel extends StatefulWidget {
  final Map<String, dynamic> explanation;
  const WhyPanel({super.key, required this.explanation});

  /// Null when there is nothing to show (old threats, SAFE verdicts).
  static WhyPanel? from(Object? e) {
    if (e is! Map) return null;
    final en = e['en'], hi = e['hi'];
    if (en is! String || en.isEmpty) return null;
    return WhyPanel(explanation: {'en': en, 'hi': hi is String && hi.isNotEmpty ? hi : en});
  }

  @override
  State<WhyPanel> createState() => _WhyPanelState();
}

class _WhyPanelState extends State<WhyPanel> {
  String? _lang;

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    final lang = _lang ?? (Localizations.localeOf(context).languageCode == 'hi' ? 'hi' : 'en');
    final t = Theme.of(context);
    return Card(
      key: const Key('why-panel'),
      color: Tone.info.bg(context),
      margin: const EdgeInsets.symmetric(vertical: 8),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20),
          side: BorderSide(color: Tone.info.fg(context).withValues(alpha: 0.25))),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Wrap(crossAxisAlignment: WrapCrossAlignment.center, spacing: 8, runSpacing: 4, children: [
            Icon(Icons.psychology_alt_rounded, color: Tone.info.fg(context), size: 22),
            Text(l.whyRisky, style: t.textTheme.titleSmall?.copyWith(color: Tone.info.fg(context), fontWeight: FontWeight.w700)),
            // chips, not a SegmentedButton: dialogs measure content intrinsically, which SegmentedButton can't do
            for (final (code, name) in const [('en', 'English'), ('hi', 'हिंदी')])
              ChoiceChip(
                key: Key('why-$code'),
                label: Text(name),
                selected: lang == code,
                showCheckmark: false,
                onSelected: (_) => setState(() => _lang = code),
              ),
          ]),
          const SizedBox(height: 10),
          Text(widget.explanation[lang] as String, key: const Key('why-text'),
              style: t.textTheme.bodyMedium?.copyWith(color: t.colorScheme.onSurface)),
        ]),
      ),
    );
  }
}
