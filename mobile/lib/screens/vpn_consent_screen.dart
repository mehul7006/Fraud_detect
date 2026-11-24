import 'package:flutter/material.dart';

import '../l10n/l10n.dart';
import '../ui/components.dart';
import '../ui/theme.dart';

/// 12.1 / 12.11: plain-language disclosure shown BEFORE Android's VPN prompt: what is seen, what is never seen,
/// what leaves the phone. Returns true when the user chose to turn protection on.
class VpnConsentScreen extends StatelessWidget {
  const VpnConsentScreen({super.key});

  Widget _point(BuildContext c, IconData icon, Feature f, String title, String body) => Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            FeatureIcon(icon, f, size: 40),
            const SizedBox(width: 14),
            Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(title, style: Theme.of(c).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700)),
              const SizedBox(height: 4),
              Text(body),
            ])),
          ]),
        ),
      );

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    final t = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(l.vpnTitle)),
      body: SafeArea(
        child: ListView(padding: const EdgeInsets.fromLTRB(16, 4, 16, 24), children: [
          Container(
            padding: const EdgeInsets.all(22),
            decoration: BoxDecoration(gradient: Feature.web.deepGradient, borderRadius: BorderRadius.circular(28),
                boxShadow: [BoxShadow(color: Feature.web.from.withValues(alpha: 0.3), blurRadius: 20, offset: const Offset(0, 8))]),
            child: Column(children: [
              Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(color: Colors.white.withValues(alpha: 0.16), shape: BoxShape.circle),
                child: const Icon(Icons.vpn_lock_rounded, size: 48, color: Colors.white),
              ),
              const SizedBox(height: 14),
              Text(l.vpnConsentTitle, key: const Key('vpn-consent-title'), textAlign: TextAlign.center,
                  style: t.textTheme.headlineSmall?.copyWith(color: Colors.white)),
              const SizedBox(height: 10),
              Text(l.vpnConsentIntro, textAlign: TextAlign.center, style: t.textTheme.bodyMedium?.copyWith(color: Colors.white)),
            ]),
          ),
          const SizedBox(height: 14),
          _point(context, Icons.visibility_rounded, Feature.web, l.vpnSeesTitle, l.vpnSees),
          _point(context, Icons.visibility_off_rounded, Feature.account, l.vpnNotSeesTitle, l.vpnNotSees),
          _point(context, Icons.cloud_upload_rounded, Feature.apps, l.vpnSentTitle, l.vpnSent),
          Container(
            margin: const EdgeInsets.only(top: 6),
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(color: Tone.info.bg(context), borderRadius: BorderRadius.circular(20)),
            child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Icon(Icons.info_outline_rounded, color: Tone.info.fg(context)),
              const SizedBox(width: 12),
              Expanded(child: Text(l.vpnConsentNote, style: t.textTheme.bodyMedium?.copyWith(color: t.colorScheme.onSurface))),
            ]),
          ),
          const SizedBox(height: 20),
          FilledButton.icon(
            key: const Key('vpn-accept'),
            style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(56)),
            icon: const Icon(Icons.shield_rounded),
            label: Text(l.vpnTurnOn),
            onPressed: () => Navigator.pop(context, true),
          ),
          TextButton(
            key: const Key('vpn-decline'),
            style: TextButton.styleFrom(minimumSize: const Size.fromHeight(48)),
            onPressed: () => Navigator.pop(context, false),
            child: Text(l.vpnNotNow),
          ),
        ]),
      ),
    );
  }
}
