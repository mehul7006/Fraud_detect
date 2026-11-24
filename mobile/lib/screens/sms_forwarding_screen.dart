import 'package:flutter/material.dart';
import 'sms_audit_screen.dart';
import '../l10n/l10n.dart';
import '../ui/screen_kit.dart';
import '../ui/theme.dart';

/// 3.8: Android gives apps no way to read SMS forwarding set at the carrier, so we explain how to check it.
class SmsForwardingScreen extends StatelessWidget {
  const SmsForwardingScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    return Scaffold(
        appBar: AppBar(title: Text(l.smsFwdTitle)),
        body: ListView(padding: const EdgeInsets.fromLTRB(16, 4, 16, 24), children: [
          IntroBanner(icon: Icons.forward_to_inbox_rounded, feature: Feature.message, text: l.smsFwdIntro),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(children: [
                StepRow(n: 1, feature: Feature.message, title: l.smsFwd1Title, body: l.smsFwd1Body,
                    action: FilledButton.tonal(
                      onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const SmsAuditScreen())),
                      child: Text(l.smsFwd1Button),
                    )),
                StepRow(n: 2, feature: Feature.message, title: l.smsFwd2Title, body: l.smsFwd2Body),
                StepRow(n: 3, feature: Feature.message, title: l.smsFwd3Title, body: l.smsFwd3Body, last: true),
              ]),
            ),
          ),
        ]),
      );
  }
}
