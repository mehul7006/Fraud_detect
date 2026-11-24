import 'package:flutter/material.dart';

import '../l10n/l10n.dart';

/// 12.1 / 12.11: plain-language disclosure shown BEFORE Android's VPN prompt: what is seen, what is never seen,
/// what leaves the phone. Returns true when the user chose to turn protection on.
class VpnConsentScreen extends StatelessWidget {
  const VpnConsentScreen({super.key});

  Widget _point(BuildContext c, IconData icon, Color color, String title, String body) => Padding(
        padding: const EdgeInsets.only(bottom: 16),
        child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Icon(icon, color: color, size: 28),
          const SizedBox(width: 12),
          Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(title, style: Theme.of(c).textTheme.titleMedium),
            const SizedBox(height: 4),
            Text(body),
          ])),
        ]),
      );

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    return Scaffold(
      appBar: AppBar(title: Text(l.vpnTitle)),
      body: SafeArea(
        child: ListView(padding: const EdgeInsets.all(20), children: [
          Icon(Icons.vpn_lock, size: 64, color: Colors.green.shade800),
          const SizedBox(height: 12),
          Text(l.vpnConsentTitle, key: const Key('vpn-consent-title'), textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.headlineSmall),
          const SizedBox(height: 12),
          Text(l.vpnConsentIntro),
          const SizedBox(height: 20),
          _point(context, Icons.visibility_outlined, Colors.blueGrey.shade700, l.vpnSeesTitle, l.vpnSees),
          _point(context, Icons.visibility_off_outlined, Colors.green.shade800, l.vpnNotSeesTitle, l.vpnNotSees),
          _point(context, Icons.upload_outlined, Colors.orange.shade900, l.vpnSentTitle, l.vpnSent),
          Card(color: Colors.amber.shade50, child: Padding(padding: const EdgeInsets.all(12), child: Text(l.vpnConsentNote))),
          const SizedBox(height: 20),
          FilledButton.icon(
            key: const Key('vpn-accept'),
            style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(52)),
            icon: const Icon(Icons.shield),
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
