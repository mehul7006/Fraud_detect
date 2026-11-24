import 'dart:math' as math;

import 'package:flutter/material.dart';

import 'theme.dart';

/// Rounded square with the feature's gradient and a white icon - the visual anchor of every tile.
class FeatureIcon extends StatelessWidget {
  final IconData icon;
  final Feature feature;
  final double size;
  const FeatureIcon(this.icon, this.feature, {super.key, this.size = 44});
  @override
  Widget build(BuildContext context) {
    final f = GroupScope.maybeOf(context) ?? feature;
    return Container(
      width: size, height: size,
      decoration: BoxDecoration(
        gradient: f.gradient,
        borderRadius: BorderRadius.circular(size * 0.32),
        boxShadow: [BoxShadow(color: f.from.withValues(alpha: 0.30), blurRadius: 12, offset: const Offset(0, 5))],
      ),
      child: Icon(icon, color: Colors.white, size: size * 0.52),
    );
  }
}

/// Tells tiles they sit inside a [GroupCard]: no own card, the group's colours.
class GroupScope extends InheritedWidget {
  final Feature feature;
  const GroupScope({super.key, required this.feature, required super.child});
  static Feature? maybeOf(BuildContext c) => c.dependOnInheritedWidgetOfExactType<GroupScope>()?.feature;
  @override
  bool updateShouldNotify(GroupScope o) => o.feature != feature;
}

/// A section as ONE rounded card: gradient header (icon, title, one line) + its rows separated by thin lines.
class GroupCard extends StatelessWidget {
  final IconData icon;
  final Feature feature;
  final String title;
  final String? subtitle;
  final List<Widget> children;
  const GroupCard({super.key, required this.icon, required this.feature, required this.title, this.subtitle, required this.children});

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    final rows = <Widget>[];
    for (var i = 0; i < children.length; i++) {
      if (i > 0) rows.add(Divider(height: 1, thickness: 1, indent: 76, endIndent: 16, color: t.colorScheme.outlineVariant));
      rows.add(children[i]);
    }
    return Container(
      margin: const EdgeInsets.only(top: 18),
      decoration: BoxDecoration(
        color: t.colorScheme.surface,
        borderRadius: BorderRadius.circular(26),
        border: Border.all(color: t.colorScheme.outlineVariant),
        boxShadow: [BoxShadow(color: feature.from.withValues(alpha: 0.10), blurRadius: 24, offset: const Offset(0, 10))],
      ),
      clipBehavior: Clip.antiAlias,
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Container(
          padding: const EdgeInsets.fromLTRB(16, 14, 16, 14),
          decoration: BoxDecoration(gradient: feature.gradient),
          child: Row(children: [
            Container(
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(color: Colors.white.withValues(alpha: 0.18), borderRadius: BorderRadius.circular(12)),
              child: Icon(icon, color: Colors.white, size: 22),
            ),
            const SizedBox(width: 12),
            Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Semantics(header: true, child: Text(title, style: t.textTheme.titleMedium?.copyWith(color: Colors.white, fontWeight: FontWeight.w700))),
              if (subtitle != null) Text(subtitle!, style: t.textTheme.bodySmall?.copyWith(color: Colors.white)),
            ])),
          ]),
        ),
        GroupScope(feature: feature, child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: rows)),
      ]),
    );
  }
}

/// Small coloured dot + text colour for a status line.
class StatusDot extends StatelessWidget {
  final Tone tone;
  const StatusDot(this.tone, {super.key});
  @override
  Widget build(BuildContext context) => Container(
        width: 8, height: 8, margin: const EdgeInsets.only(right: 6, top: 1),
        decoration: BoxDecoration(color: tone.fg(context), shape: BoxShape.circle),
      );
}

/// A home-screen tile: gradient icon, bold title, coloured status line, chevron / refresh.
/// Built on Card + ListTile (subtitle stays a plain Text so tests and TalkBack read the status as-is).
class FeatureTile extends StatelessWidget {
  final IconData icon;
  final Feature feature;
  final String title;
  final String? status;
  final Widget? statusWidget; // when the status needs more than one line of text (buttons etc.)
  final Tone tone;
  final VoidCallback? onTap;
  final VoidCallback? onLongPress;
  final IconData? trailingIcon;
  final Key? tileKey;
  const FeatureTile({super.key, required this.icon, required this.feature, required this.title, this.status,
      this.statusWidget, this.tone = Tone.neutral, this.onTap, this.onLongPress, this.trailingIcon, this.tileKey});

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    final grouped = GroupScope.maybeOf(context) != null;
    final tile = ListTile(
      key: tileKey,
      minVerticalPadding: 14,
      shape: grouped ? const RoundedRectangleBorder() : null,
      leading: FeatureIcon(icon, feature, size: grouped ? 42 : 44),
      title: Text(title),
      subtitle: statusWidget ??
          (status == null ? null : Text(status!, style: t.textTheme.bodyMedium?.copyWith(color: tone.fg(context), fontWeight: FontWeight.w500))),
      trailing: trailingIcon == null ? null : Icon(trailingIcon, color: t.colorScheme.onSurfaceVariant),
      onTap: onTap,
      onLongPress: onLongPress,
    );
    // inside a GroupCard: a Material row (ink + tests still find a ListTile; the group draws the card)
    return grouped ? Material(type: MaterialType.transparency, child: tile) : Card(child: tile);
  }
}

/// Section heading with a short accent bar.
class SectionTitle extends StatelessWidget {
  final String text;
  final String? subtitle;
  const SectionTitle(this.text, {super.key, this.subtitle});
  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(4, 22, 4, 8),
      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Container(width: 4, height: 20, margin: const EdgeInsets.only(right: 10, top: 2),
            decoration: BoxDecoration(gradient: AppColors.brandGradient, borderRadius: BorderRadius.circular(4))),
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(text, style: t.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
          if (subtitle != null) Text(subtitle!, style: t.textTheme.bodySmall?.copyWith(color: t.colorScheme.onSurfaceVariant)),
        ])),
      ]),
    );
  }
}

/// Circular progress ring: how many protections are on.
class ProtectionRing extends StatelessWidget {
  final int on, total;
  final double size;
  const ProtectionRing({super.key, required this.on, required this.total, this.size = 92});
  @override
  Widget build(BuildContext context) {
    final f = total == 0 ? 0.0 : on / total;
    return Semantics(
      label: '$on / $total',
      child: SizedBox(
        width: size, height: size,
        child: CustomPaint(
          painter: _RingPainter(f),
          // a graphic: its number shrinks to fit the ring at any text size (TalkBack reads the label above)
          child: Padding(padding: const EdgeInsets.all(18), child: FittedBox(child: Column(mainAxisSize: MainAxisSize.min, children: [
            Text('$on/$total', style: const TextStyle(color: Colors.white, fontSize: 22, fontWeight: FontWeight.w700, height: 1.1)),
            Icon(f >= 1 ? Icons.verified_user : Icons.shield_outlined, color: Colors.white, size: 18),
          ]))),
        ),
      ),
    );
  }
}

class _RingPainter extends CustomPainter {
  final double f;
  _RingPainter(this.f);
  @override
  void paint(Canvas c, Size s) {
    final r = Rect.fromLTWH(6, 6, s.width - 12, s.height - 12);
    c.drawArc(r, 0, math.pi * 2, false, Paint()
      ..color = Colors.white.withValues(alpha: 0.18)..style = PaintingStyle.stroke..strokeWidth = 9);
    c.drawArc(r, -math.pi / 2, math.pi * 2 * f.clamp(0.0, 1.0), false, Paint()
      ..shader = const SweepGradient(colors: [Color(0xFF7CF5C8), Color(0xFF34D399), Color(0xFFA7F3D0)]).createShader(r)
      ..style = PaintingStyle.stroke..strokeWidth = 9..strokeCap = StrokeCap.round);
  }
  @override
  bool shouldRepaint(_RingPainter o) => o.f != f;
}

/// Big gradient header with the protection summary. [actions] are white icon buttons (top right).
class HeroHeader extends StatelessWidget {
  final String title, headline, detail;
  final int on, total;
  final bool alert;
  final List<Widget> actions;
  const HeroHeader({super.key, required this.title, required this.headline, required this.detail,
      required this.on, required this.total, this.alert = false, this.actions = const []});

  @override
  Widget build(BuildContext context) {
    final top = MediaQuery.paddingOf(context).top;
    final t = Theme.of(context).textTheme;
    return Container(
      decoration: BoxDecoration(
        gradient: alert ? AppColors.dangerGradient : AppColors.brandGradient,
        borderRadius: const BorderRadius.vertical(bottom: Radius.circular(32)),
      ),
      child: Stack(children: [
        // soft light blobs for depth
        Positioned(right: -40, top: -30, child: _blob(180, 0.10)),
        Positioned(left: -60, bottom: -70, child: _blob(200, 0.07)),
        Padding(
          padding: EdgeInsets.fromLTRB(20, top + 8, 8, 26),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              const Icon(Icons.shield_moon, color: Colors.white, size: 26),
              const SizedBox(width: 8),
              Expanded(child: Text(title, style: t.titleLarge?.copyWith(color: Colors.white, fontWeight: FontWeight.w700))),
              ...actions,
            ]),
            const SizedBox(height: 18),
            Row(children: [
              ProtectionRing(on: on, total: total),
              const SizedBox(width: 18),
              Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(headline, style: t.headlineSmall?.copyWith(color: Colors.white, height: 1.2)),
                const SizedBox(height: 6),
                Text(detail, style: t.bodyMedium?.copyWith(color: Colors.white)),
              ])),
              const SizedBox(width: 12),
            ]),
          ]),
        ),
      ]),
    );
  }

  Widget _blob(double s, double a) => IgnorePointer(
        child: Container(width: s, height: s,
            decoration: BoxDecoration(shape: BoxShape.circle, color: Colors.white.withValues(alpha: a))));
}

/// Quick action: a full-gradient tile with a white icon bubble and white label (home screen shortcuts).
class QuickAction extends StatelessWidget {
  final IconData icon;
  final Feature feature;
  final String label;
  final VoidCallback onTap;
  final Key? actionKey;
  const QuickAction({super.key, required this.icon, required this.feature, required this.label, required this.onTap, this.actionKey});
  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    return Container(
      key: actionKey,
      decoration: BoxDecoration(
        gradient: feature.gradient,
        borderRadius: BorderRadius.circular(24),
        boxShadow: [BoxShadow(color: feature.from.withValues(alpha: 0.35), blurRadius: 18, offset: const Offset(0, 8))],
      ),
      child: Material(
        type: MaterialType.transparency,
        child: InkWell(
          borderRadius: BorderRadius.circular(24),
          onTap: onTap,
          child: Stack(children: [
            Positioned(right: -18, top: -18, child: IgnorePointer(child: Container(width: 70, height: 70,
                decoration: BoxDecoration(shape: BoxShape.circle, color: Colors.white.withValues(alpha: 0.12))))),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 16, 12, 16),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, mainAxisSize: MainAxisSize.min, children: [
                Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(color: Colors.white.withValues(alpha: 0.20), borderRadius: BorderRadius.circular(14)),
                  child: Icon(icon, color: Colors.white, size: 24),
                ),
                const SizedBox(height: 14),
                Text(label, maxLines: 2, overflow: TextOverflow.ellipsis,
                    style: t.textTheme.titleSmall?.copyWith(color: Colors.white, fontWeight: FontWeight.w700)),
              ]),
            ),
          ]),
        ),
      ),
    );
  }
}

/// Banner card for something the user should do (permission, offline ...). Card + TextButton underneath.
class NoticeCard extends StatelessWidget {
  final IconData icon;
  final String title, body;
  final String? action;
  final VoidCallback? onAction;
  final Tone tone;
  final Key? cardKey;
  const NoticeCard({super.key, required this.icon, required this.title, required this.body, this.action, this.onAction,
      this.tone = Tone.warn, this.cardKey});
  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    return Card(
      key: cardKey,
      color: tone.bg(context),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20), side: BorderSide(color: tone.fg(context).withValues(alpha: 0.25))),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 14, 8, 6),
        child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(color: tone.fg(context).withValues(alpha: 0.12), borderRadius: BorderRadius.circular(12)),
            child: Icon(icon, color: tone.fg(context), size: 22),
          ),
          const SizedBox(width: 12),
          Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(title, style: t.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700, color: t.colorScheme.onSurface)),
            const SizedBox(height: 4),
            Text(body, style: t.textTheme.bodyMedium?.copyWith(color: t.colorScheme.onSurface)),
            if (action != null)
              Align(alignment: Alignment.centerRight,
                  child: TextButton(onPressed: onAction, style: TextButton.styleFrom(foregroundColor: tone.fg(context)), child: Text(action!))),
            if (action == null) const SizedBox(height: 10),
          ])),
        ]),
      ),
    );
  }
}

/// Coloured pill label (SAFE / RISK / ON ...).
class Pill extends StatelessWidget {
  final String text;
  final Tone tone;
  final IconData? icon;
  const Pill(this.text, {super.key, this.tone = Tone.neutral, this.icon});
  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
        decoration: BoxDecoration(color: tone.bg(context), borderRadius: BorderRadius.circular(999)),
        child: Row(mainAxisSize: MainAxisSize.min, children: [
          if (icon != null) ...[Icon(icon, size: 14, color: tone.fg(context)), const SizedBox(width: 4)],
          // long labels (host names, Hindi, 200 % text) wrap inside the pill instead of running off the screen
          Flexible(child: Text(text, style: Theme.of(context).textTheme.labelMedium?.copyWith(color: tone.fg(context), fontWeight: FontWeight.w600))),
        ]),
      );
}

/// Screen header used by inner screens: gradient strip with icon + title + one-line explanation.
class ScreenBanner extends StatelessWidget {
  final IconData icon;
  final Feature feature;
  final String title;
  final String? subtitle;
  const ScreenBanner({super.key, required this.icon, required this.feature, required this.title, this.subtitle});
  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(gradient: feature.deepGradient, borderRadius: BorderRadius.circular(24),
          boxShadow: [BoxShadow(color: feature.from.withValues(alpha: 0.30), blurRadius: 18, offset: const Offset(0, 8))]),
      child: Row(children: [
        Container(padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(color: Colors.white.withValues(alpha: 0.2), borderRadius: BorderRadius.circular(16)),
            child: Icon(icon, color: Colors.white, size: 28)),
        const SizedBox(width: 14),
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Semantics(header: true, child: Text(title, style: t.titleLarge?.copyWith(color: Colors.white))),
          if (subtitle != null) ...[
            const SizedBox(height: 4),
            Text(subtitle!, style: t.bodyMedium?.copyWith(color: Colors.white)),
          ],
        ])),
      ]),
    );
  }
}
