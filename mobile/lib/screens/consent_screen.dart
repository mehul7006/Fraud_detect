import 'package:flutter/material.dart';
import '../core/native_bridge.dart';
import '../l10n/l10n.dart';

/// 9.4: shown once after login (again whenever [kConsentVersion] goes up), before any permission is requested.
/// Mirrors docs/PRIVACY-POLICY.md in short. Google Play: prominent disclosure for notification / file access.
const int kConsentVersion = 1;
const String kPrivacyUrl = String.fromEnvironment('PRIVACY_URL', defaultValue: 'https://fraudshield.app/privacy');

class ConsentScreen extends StatelessWidget {
  final VoidCallback onAccept;
  final VoidCallback onDecline;
  const ConsentScreen({super.key, required this.onAccept, required this.onDecline});

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    Widget block(IconData i, String t, String b) => ListTile(
          leading: Icon(i, color: const Color(0xFF0B6E4F)),
          title: Text(t, style: const TextStyle(fontWeight: FontWeight.bold)),
          subtitle: Text(b),
        );
    return Scaffold(
      appBar: AppBar(title: Text(l.consentTitle), automaticallyImplyLeading: false),
      body: SafeArea(
        child: ListView(padding: const EdgeInsets.all(16), children: [
          Text(l.consentIntro, style: Theme.of(context).textTheme.bodyLarge),
          const SizedBox(height: 8),
          block(Icons.cloud_upload_outlined, l.consentChecks, l.consentChecksBody),
          block(Icons.lock_outline, l.consentNever, l.consentNeverBody),
          block(Icons.groups_outlined, l.consentShared, l.consentSharedBody),
          block(Icons.tune, l.consentDelete, l.consentDeleteBody),
          TextButton.icon(
            key: const Key('privacy-policy-link'),
            icon: const Icon(Icons.open_in_new),
            label: Text(l.readPrivacyPolicy),
            onPressed: () => NativeBridge.openUrl(kPrivacyUrl),
          ),
          const SizedBox(height: 8),
          FilledButton(key: const Key('consent-accept'), onPressed: onAccept, child: Text(l.agreeContinue)),
          TextButton(key: const Key('consent-decline'), onPressed: onDecline, child: Text(l.logOut)),
        ]),
      ),
    );
  }
}
