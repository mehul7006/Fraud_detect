import 'package:flutter/material.dart';
import 'sms_audit_screen.dart';

/// 3.8: Android gives apps no way to read SMS forwarding set at the carrier, so we explain how to check it.
class SmsForwardingScreen extends StatelessWidget {
  const SmsForwardingScreen({super.key});

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: const Text('Check SMS forwarding')),
        body: ListView(padding: const EdgeInsets.all(16), children: [
          const Card(
            color: Color(0xFFFFF3CD),
            child: Padding(
              padding: EdgeInsets.all(12),
              child: Text('Apps cannot see whether your carrier forwards your SMS. Follow these three checks.'),
            ),
          ),
          const SizedBox(height: 12),
          const ListTile(
            leading: CircleAvatar(child: Text('1')),
            title: Text('Remove apps that can read your SMS'),
            subtitle: Text('Scam "SMS forwarder" apps copy your OTPs to another phone.'),
          ),
          Padding(
            padding: const EdgeInsets.only(left: 72, bottom: 8),
            child: Align(
              alignment: Alignment.centerLeft,
              child: FilledButton.tonal(
                onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const SmsAuditScreen())),
                child: const Text('Show apps that read my SMS'),
              ),
            ),
          ),
          const ListTile(
            leading: CircleAvatar(child: Text('2')),
            title: Text('Ask your carrier'),
            subtitle: Text('Call customer care from your own number and ask: "Is SMS forwarding or SMS redirect active on my number?" '
                'Ask them to switch it off and to tell you if the SIM was duplicated or replaced.'),
          ),
          const ListTile(
            leading: CircleAvatar(child: Text('3')),
            title: Text('Watch for a SIM swap'),
            subtitle: Text('If your phone suddenly shows "No service" for no reason, call the carrier at once from another phone. '
                'A swapped SIM receives all your SMS and calls.'),
          ),
        ]),
      );
}
