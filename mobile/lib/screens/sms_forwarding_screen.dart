import 'package:flutter/material.dart';
import 'sms_audit_screen.dart';
import '../l10n/l10n.dart';

/// 3.8: Android gives apps no way to read SMS forwarding set at the carrier, so we explain how to check it.
class SmsForwardingScreen extends StatelessWidget {
  const SmsForwardingScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    return Scaffold(
        appBar: AppBar(title: Text(l.smsFwdTitle)),
        body: ListView(padding: const EdgeInsets.all(16), children: [
          Card(
            color: const Color(0xFFFFF3CD),
            child: Padding(padding: const EdgeInsets.all(12), child: Text(l.smsFwdIntro)),
          ),
          const SizedBox(height: 12),
          ListTile(leading: const CircleAvatar(child: Text('1')), title: Text(l.smsFwd1Title), subtitle: Text(l.smsFwd1Body)),
          Padding(
            padding: const EdgeInsets.only(left: 72, bottom: 8),
            child: Align(
              alignment: Alignment.centerLeft,
              child: FilledButton.tonal(
                onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const SmsAuditScreen())),
                child: Text(l.smsFwd1Button),
              ),
            ),
          ),
          ListTile(leading: const CircleAvatar(child: Text('2')), title: Text(l.smsFwd2Title), subtitle: Text(l.smsFwd2Body)),
          ListTile(leading: const CircleAvatar(child: Text('3')), title: Text(l.smsFwd3Title), subtitle: Text(l.smsFwd3Body)),
        ]),
      );
  }
}
