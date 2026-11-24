import 'package:flutter/material.dart';

import '../l10n/l10n.dart';

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
    return Card(
      key: const Key('why-panel'),
      color: Colors.blueGrey.shade50,
      margin: const EdgeInsets.symmetric(vertical: 8),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Wrap(crossAxisAlignment: WrapCrossAlignment.center, spacing: 8, runSpacing: 4, children: [
            Text(l.whyRisky, style: Theme.of(context).textTheme.titleSmall),
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
          const SizedBox(height: 8),
          Text(widget.explanation[lang] as String, key: const Key('why-text')),
        ]),
      ),
    );
  }
}
