import 'package:flutter/material.dart';
import '../core/native_bridge.dart';
import '../l10n/l10n.dart';
import '../ui/components.dart';
import '../ui/screen_kit.dart';
import '../ui/theme.dart';

typedef AppsLoader = Future<List<Map<String, dynamic>>> Function();

/// 3.7: apps that can read your SMS or your notifications. Scammers' "SMS forwarder" / OTP-stealer apps
/// need exactly these permissions. System apps are listed separately because they are normally trusted.
class SmsAuditScreen extends StatefulWidget {
  final AppsLoader? loader; // injectable for tests
  final Future<void> Function(String package)? onUninstall;
  const SmsAuditScreen({super.key, this.loader, this.onUninstall});

  @override
  State<SmsAuditScreen> createState() => _SmsAuditScreenState();
}

class _SmsAuditScreenState extends State<SmsAuditScreen> {
  late final Future<List<Map<String, dynamic>>> _f = (widget.loader ?? NativeBridge.auditApps)();

  bool _canReadMessages(Map<String, dynamic> a) => a['hasSmsPermission'] == true || a['hasNotificationListener'] == true;

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: Text(context.l10n.smsAuditTitle)),
        body: FutureBuilder<List<Map<String, dynamic>>>(
          future: _f,
          builder: (c, s) {
            if (s.hasError) return Center(child: Text(context.l10n.smsAuditError));
            if (!s.hasData) return const Center(child: CircularProgressIndicator());
            final all = s.data!.where(_canReadMessages).toList()
              ..sort((a, b) => (a['label'] as String).toLowerCase().compareTo((b['label'] as String).toLowerCase()));
            final user = all.where((a) => a['isSystem'] != true).toList();
            final system = all.where((a) => a['isSystem'] == true).toList();
            return ListView(padding: const EdgeInsets.fromLTRB(16, 4, 16, 24), children: [
              IntroBanner(icon: Icons.manage_search_rounded, feature: Feature.message, text: context.l10n.smsAuditIntro),
              SectionTitle(context.l10n.smsAuditUser('${user.length}')),
              if (user.isEmpty)
                Card(
                  color: Tone.safe.bg(context),
                  child: ListTile(leading: Icon(Icons.check_circle_rounded, color: Tone.safe.fg(context)),
                      title: Text(context.l10n.smsAuditNoneUser)),
                )
              else
                for (final a in user) _row(a, canUninstall: true),
              SectionTitle(context.l10n.smsAuditSystem('${system.length}')),
              for (final a in system) _row(a, canUninstall: false),
            ]);
          },
        ),
      );

  Widget _row(Map<String, dynamic> a, {required bool canUninstall}) {
    final pkg = a['packageName'] as String;
    final t = Theme.of(context);
    return Card(
      child: ListTile(
        minVerticalPadding: 12,
        leading: canUninstall
            ? const FeatureIcon(Icons.sms_failed_rounded, Feature.scam, size: 40)
            : const FeatureIcon(Icons.android_rounded, Feature.history, size: 40),
        title: Text(a['label'] as String),
        // 8.12: the button sits under the text, not beside it, so very large text (or longer Hindi words) never squeezes it
        subtitle: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(pkg, style: t.textTheme.bodySmall?.copyWith(color: t.colorScheme.onSurfaceVariant)),
          const SizedBox(height: 6),
          Wrap(spacing: 6, runSpacing: 6, children: [
            if (a['hasSmsPermission'] == true) Pill(context.l10n.readsSms, tone: canUninstall ? Tone.danger : Tone.neutral, icon: Icons.sms_rounded),
            if (a['hasNotificationListener'] == true)
              Pill(context.l10n.readsNotifications, tone: canUninstall ? Tone.warn : Tone.neutral, icon: Icons.notifications_rounded),
          ]),
          if (canUninstall)
            Align(
              alignment: Alignment.centerRight,
              child: TextButton(
                style: TextButton.styleFrom(foregroundColor: Tone.danger.fg(context)),
                onPressed: () => (widget.onUninstall ?? NativeBridge.uninstallApp)(pkg), child: Text(context.l10n.uninstall)),
            ),
        ]),
      ),
    );
  }
}
