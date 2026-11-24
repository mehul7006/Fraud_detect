import 'package:flutter/material.dart';

import '../core/native_bridge.dart';
import '../core/permissions.dart';
import '../l10n/l10n.dart';
import '../ui/components.dart';
import '../ui/theme.dart';
import 'vpn_state_screen.dart';

/// Every permission in one place: how many are allowed, which still need the user's OK, and one tap to the exact
/// Android page (or the system dialog) where it is switched on. Re-checks itself whenever the user comes back.
class PermissionsScreen extends StatefulWidget {
  const PermissionsScreen({super.key});
  @override
  State<PermissionsScreen> createState() => _PermissionsScreenState();
}

class _PermissionsScreenState extends State<PermissionsScreen> with WidgetsBindingObserver {
  List<PermItem>? _items;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _load();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState s) {
    if (s == AppLifecycleState.resumed) _load(); // back from Settings: show the new state at once
  }

  Future<void> _load() async {
    final items = await Permissions.load();
    if (mounted) setState(() => _items = items);
  }

  Future<void> _open(PermItem p) async {
    if (p.kind == 'screen') { // VPN: its own consent screen explains it before Android's dialog
      await Navigator.push(context, MaterialPageRoute(builder: (_) => const VpnStateScreen()));
    } else {
      await NativeBridge.permOpen(p.id);
    }
    await Future<void>.delayed(const Duration(milliseconds: 400));
    _load();
  }

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    final items = _items;
    return Scaffold(
      appBar: AppBar(title: Text(l.permTitle)),
      body: items == null
          ? const Center(child: CircularProgressIndicator())
          : RefreshIndicator(
              onRefresh: _load,
              child: ListView(padding: const EdgeInsets.fromLTRB(16, 4, 16, 32), children: [
                _summary(context, Permissions.count(items)),
                Padding(
                  padding: const EdgeInsets.fromLTRB(4, 14, 4, 0),
                  child: Text(l.permIntro, style: Theme.of(context).textTheme.bodyMedium?.copyWith(color: Theme.of(context).colorScheme.onSurfaceVariant)),
                ),
                if (items.any((p) => !p.granted && !p.unknown))
                  GroupCard(icon: Icons.pending_actions_rounded, feature: Feature.cleaner, title: l.permNeedsOk, children: [
                    for (final p in items.where((p) => !p.granted && !p.unknown)) _row(context, p),
                  ]),
                if (items.any((p) => p.unknown))
                  GroupCard(icon: Icons.build_circle_rounded, feature: Feature.device, title: l.permByHand, children: [
                    for (final p in items.where((p) => p.unknown)) _row(context, p),
                  ]),
                if (items.any((p) => p.granted))
                  GroupCard(icon: Icons.verified_rounded, feature: Feature.account, title: l.permAllowed, children: [
                    for (final p in items.where((p) => p.granted)) _row(context, p),
                  ]),
              ]),
            ),
    );
  }

  Widget _summary(BuildContext context, ({int allowed, int total, int pending}) c) {
    final l = context.l10n;
    final t = Theme.of(context).textTheme;
    final done = c.pending == 0;
    return Container(
      key: const Key('perm-summary-hero'),
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        gradient: done ? Feature.account.gradient : AppColors.brandGradient,
        borderRadius: BorderRadius.circular(26),
        boxShadow: [BoxShadow(color: AppColors.violet.withValues(alpha: 0.25), blurRadius: 20, offset: const Offset(0, 10))],
      ),
      child: Row(children: [
        ProtectionRing(on: c.allowed, total: c.total, size: 84),
        const SizedBox(width: 16),
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(l.permSummary(c.allowed, c.total), key: const Key('perm-count'),
              style: t.titleLarge?.copyWith(color: Colors.white)),
          const SizedBox(height: 4),
          Text(done ? l.permAllDone : l.permPending(c.pending), key: const Key('perm-pending'),
              style: t.bodyMedium?.copyWith(color: Colors.white)),
        ])),
      ]),
    );
  }

  Widget _row(BuildContext context, PermItem p) {
    final l = context.l10n;
    final t = Theme.of(context);
    final label = p.unknown ? l.permOpen : l.permAllow;
    return FeatureTile(
      tileKey: Key('perm-${p.id}'),
      icon: p.icon, feature: p.feature,
      title: p.title(l),
      statusWidget: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(p.why(l), style: t.textTheme.bodyMedium?.copyWith(color: t.colorScheme.onSurfaceVariant)),
        const SizedBox(height: 6),
        Wrap(spacing: 8, runSpacing: 8, crossAxisAlignment: WrapCrossAlignment.center, children: [
          if (p.granted) Pill(l.permOn, tone: Tone.safe, icon: Icons.check_rounded),
          if (!p.granted && !p.required && !p.unknown) Pill(l.permOptional, tone: Tone.neutral),
          if (!p.granted)
            FilledButton.tonal(
              key: Key('perm-open-${p.id}'),
              style: FilledButton.styleFrom(minimumSize: const Size(48, 44)),
              onPressed: () => _open(p),
              child: Text(label),
            ),
        ]),
      ]),
      onTap: p.granted ? null : () => _open(p),
    );
  }
}

/// Home-screen summary: "9 of 11 allowed - 2 need your OK", tap to open the permissions page.
class PermissionSummaryCard extends StatelessWidget {
  final int allowed, total;
  final VoidCallback onTap;
  const PermissionSummaryCard({super.key, required this.allowed, required this.total, required this.onTap});
  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    final pending = total - allowed;
    return FeatureTile(
      tileKey: const Key('perm-summary'),
      icon: pending == 0 ? Icons.verified_user_rounded : Icons.admin_panel_settings_rounded,
      feature: pending == 0 ? Feature.account : Feature.cleaner,
      title: '${l.permTitle} - ${l.permSummary(allowed, total)}',
      status: pending == 0 ? l.permAllDone : l.permPending(pending),
      tone: pending == 0 ? Tone.safe : Tone.warn,
      trailingIcon: Icons.chevron_right_rounded,
      onTap: onTap,
    );
  }
}
