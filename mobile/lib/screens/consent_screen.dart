import 'package:flutter/material.dart';
import '../core/native_bridge.dart';
import '../l10n/l10n.dart';
import '../ui/components.dart';
import '../ui/theme.dart';

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
    final t = Theme.of(context);
    Widget block(IconData i, Feature f, String title, String b) => Card(
          child: ListTile(
            minVerticalPadding: 14,
            leading: FeatureIcon(i, f),
            title: Text(title),
            subtitle: Text(b),
          ),
        );
    return Scaffold(
      appBar: AppBar(title: Text(l.consentTitle), automaticallyImplyLeading: false),
      body: SafeArea(
        child: ListView(padding: const EdgeInsets.fromLTRB(16, 4, 16, 24), children: [
          Container(
            padding: const EdgeInsets.all(18),
            decoration: BoxDecoration(gradient: AppColors.brandGradient, borderRadius: BorderRadius.circular(24)),
            child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(color: Colors.white.withValues(alpha: 0.18), borderRadius: BorderRadius.circular(16)),
                child: const Icon(Icons.privacy_tip_rounded, color: Colors.white, size: 28),
              ),
              const SizedBox(width: 14),
              Expanded(child: Text(l.consentIntro, style: t.textTheme.bodyLarge?.copyWith(color: Colors.white))),
            ]),
          ),
          const SizedBox(height: 10),
          block(Icons.cloud_upload_rounded, Feature.web, l.consentChecks, l.consentChecksBody),
          block(Icons.lock_rounded, Feature.account, l.consentNever, l.consentNeverBody),
          block(Icons.groups_rounded, Feature.device, l.consentShared, l.consentSharedBody),
          block(Icons.tune_rounded, Feature.apps, l.consentDelete, l.consentDeleteBody),
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton.icon(
              key: const Key('privacy-policy-link'),
              icon: const Icon(Icons.open_in_new_rounded),
              label: Text(l.readPrivacyPolicy),
              onPressed: () => NativeBridge.openUrl(kPrivacyUrl),
            ),
          ),
          const SizedBox(height: 12),
          FilledButton(key: const Key('consent-accept'), onPressed: onAccept, child: Text(l.agreeContinue)),
          const SizedBox(height: 4),
          TextButton(key: const Key('consent-decline'), onPressed: onDecline, child: Text(l.logOut)),
        ]),
      ),
    );
  }
}
