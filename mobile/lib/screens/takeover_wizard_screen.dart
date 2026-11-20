import 'package:flutter/material.dart';
import '../core/native_bridge.dart';

/// WhatsApp/Telegram do not let other apps log out sessions. This wizard opens the app and walks the user
/// through the exact steps. Be honest: we cannot do it automatically.
class TakeoverWizardScreen extends StatelessWidget {
  final String app; // 'whatsapp' | 'telegram'
  const TakeoverWizardScreen({super.key, required this.app});

  bool get _wa => app == 'whatsapp';

  List<String> get _steps => _wa
      ? [
          'Open WhatsApp > tap the 3 dots > Linked devices.',
          'Tap every device you do not recognise > Log out. (Or: Log out from all devices.)',
          'Settings > Account > Two-step verification > Turn on and set a PIN + recovery email.',
          'Settings > Account > Passkeys / Email address: add an email so only you can recover the account.',
          'Never share the 6-digit code received by SMS. WhatsApp support never asks for it.',
          'If you are locked out: Uninstall > reinstall > register your number again. This logs the attacker out.',
        ]
      : [
          'Open Telegram > Settings > Devices.',
          'Tap "Terminate all other sessions".',
          'Settings > Privacy and Security > Two-Step Verification > set a password + recovery email.',
          'Settings > Privacy and Security > Active Websites: disconnect unknown ones.',
          'Settings > Privacy and Security > Passcode Lock: enable.',
          'Never share Telegram login codes.',
        ];

  @override
  Widget build(BuildContext context) {
    final pkg = _wa ? 'com.whatsapp' : 'org.telegram.messenger';
    return Scaffold(
      appBar: AppBar(title: Text('Secure ${_wa ? 'WhatsApp' : 'Telegram'}')),
      body: ListView(padding: const EdgeInsets.all(16), children: [
        const Card(
          color: Color(0xFFFFF3CD),
          child: Padding(
            padding: EdgeInsets.all(12),
            child: Text('Apps cannot log out other devices for you - only you can do it inside the app. Follow these steps now.'),
          ),
        ),
        const SizedBox(height: 12),
        for (var i = 0; i < _steps.length; i++)
          ListTile(leading: CircleAvatar(child: Text('${i + 1}')), title: Text(_steps[i])),
        const SizedBox(height: 12),
        FilledButton.icon(
          icon: const Icon(Icons.open_in_new),
          label: Text('Open ${_wa ? 'WhatsApp' : 'Telegram'} now'),
          onPressed: () async {
            final ok = await NativeBridge.openApp(pkg);
            if (!ok && context.mounted) {
              ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('App not installed')));
            }
          },
        ),
      ]),
    );
  }
}
