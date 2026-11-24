import 'package:flutter/material.dart';

import 'components.dart';
import 'theme.dart';

/// Coloured intro strip for inner screens: deep feature gradient, glass icon, explanation text (the AppBar keeps the
/// title, so no text is duplicated). White text only on the deep gradient (contrast >= 5:1).
class IntroBanner extends StatelessWidget {
  final IconData icon;
  final Feature feature;
  final String text;
  final Widget? footer;
  const IntroBanner({super.key, required this.icon, required this.feature, required this.text, this.footer});
  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        gradient: feature.deepGradient,
        borderRadius: BorderRadius.circular(24),
        boxShadow: [BoxShadow(color: feature.from.withValues(alpha: 0.28), blurRadius: 18, offset: const Offset(0, 8))],
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(color: Colors.white.withValues(alpha: 0.18), borderRadius: BorderRadius.circular(16)),
            child: Icon(icon, color: Colors.white, size: 28),
          ),
          const SizedBox(width: 14),
          Expanded(child: Text(text, style: t.bodyLarge?.copyWith(color: Colors.white))),
        ]),
        if (footer != null) ...[const SizedBox(height: 12), footer!],
      ]),
    );
  }
}

/// Server / phone verdict -> tone.
Tone toneOfVerdict(String? v) => switch (v) {
      'MALICIOUS' || 'FAKE' || 'DANGEROUS' => Tone.danger,
      'SUSPICIOUS' || 'UNKNOWN' => Tone.warn,
      null => Tone.neutral,
      _ => Tone.safe,
    };

IconData iconOfTone(Tone t) => switch (t) {
      Tone.danger => Icons.gpp_bad_rounded,
      Tone.warn => Icons.warning_amber_rounded,
      Tone.safe => Icons.verified_user_rounded,
      _ => Icons.info_outline_rounded,
    };

/// Result card: tinted header with icon + headline, then the details.
class VerdictCard extends StatelessWidget {
  final Tone tone;
  final String headline;
  final List<Widget> children;
  const VerdictCard({super.key, required this.tone, required this.headline, this.children = const []});
  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    return Card(
      margin: const EdgeInsets.only(top: 16),
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Container(
          color: tone.bg(context),
          padding: const EdgeInsets.fromLTRB(16, 14, 16, 14),
          child: Row(children: [
            Icon(iconOfTone(tone), color: tone.fg(context), size: 30),
            const SizedBox(width: 10),
            Expanded(child: Text(headline, style: t.textTheme.titleLarge?.copyWith(color: tone.fg(context)))),
          ]),
        ),
        if (children.isNotEmpty)
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 16),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: children),
          ),
      ]),
    );
  }
}

/// Numbered step: gradient number badge, title, optional body and an optional action below.
class StepRow extends StatelessWidget {
  final int n;
  final Feature feature;
  final String title;
  final String? body;
  final Widget? action;
  final bool last;
  const StepRow({super.key, required this.n, required this.feature, required this.title, this.body, this.action, this.last = false});
  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    return Padding(
      padding: EdgeInsets.only(bottom: last ? 0 : 16),
      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Container(
          width: 34, height: 34, alignment: Alignment.center,
          decoration: BoxDecoration(gradient: feature.deepGradient, shape: BoxShape.circle),
          child: Text('$n', style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w700)),
        ),
        const SizedBox(width: 14),
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Padding(padding: const EdgeInsets.only(top: 5), child: Text(title, style: t.textTheme.titleSmall)),
          if (body != null) ...[
            const SizedBox(height: 3),
            Text(body!, style: t.textTheme.bodyMedium?.copyWith(color: t.colorScheme.onSurfaceVariant)),
          ],
          if (action != null) ...[const SizedBox(height: 10), action!],
        ])),
      ]),
    );
  }
}

/// Bullet line with a small coloured dot.
class Bullet extends StatelessWidget {
  final String text;
  final Tone tone;
  const Bullet(this.text, {super.key, this.tone = Tone.neutral});
  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(top: 6),
        child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Padding(padding: const EdgeInsets.only(top: 7), child: StatusDot(tone)),
          Expanded(child: Text(text)),
        ]),
      );
}
