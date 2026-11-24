import 'package:flutter/material.dart';
import '../core/native_bridge.dart';
import '../l10n/l10n.dart';

/// WhatsApp/Telegram do not let other apps log out sessions. This wizard opens the app and walks the user
/// through the exact steps. Be honest: we cannot do it automatically.
class TakeoverWizardScreen extends StatelessWidget {
  final String app; // 'whatsapp' | 'telegram'
  const TakeoverWizardScreen({super.key, required this.app});

  bool get _wa => app == 'whatsapp';

  List<String> _steps(AppLocalizations l) => _wa
      ? [l.waStep1, l.waStep2, l.waStep3, l.waStep4, l.waStep5, l.waStep6]
      : [l.tgStep1, l.tgStep2, l.tgStep3, l.tgStep4, l.tgStep5, l.tgStep6];

  @override
  Widget build(BuildContext context) {
    final pkg = _wa ? 'com.whatsapp' : 'org.telegram.messenger';
    final l = context.l10n;
    final name = _wa ? 'WhatsApp' : 'Telegram';
    final steps = _steps(l);
    return Scaffold(
      appBar: AppBar(title: Text(l.wizTitle(name))),
      body: ListView(padding: const EdgeInsets.all(16), children: [
        Card(
          color: const Color(0xFFFFF3CD),
          child: Padding(padding: const EdgeInsets.all(12), child: Text(l.wizWarning)),
        ),
        const SizedBox(height: 12),
        for (var i = 0; i < steps.length; i++)
          ListTile(leading: CircleAvatar(child: Text('${i + 1}')), title: Text(steps[i])),
        const SizedBox(height: 12),
        FilledButton.icon(
          icon: const Icon(Icons.open_in_new),
          label: Text(l.wizOpenNow(name)),
          onPressed: () async {
            final ok = await NativeBridge.openApp(pkg);
            if (!ok && context.mounted) {
              ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(l.wizNotInstalled), duration: const Duration(seconds: 6)));
            }
          },
        ),
      ]),
    );
  }
}
