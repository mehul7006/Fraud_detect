import 'package:flutter/material.dart';
import '../core/native_bridge.dart';
import '../l10n/l10n.dart';

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
            return ListView(children: [
              Padding(padding: const EdgeInsets.all(16), child: Text(context.l10n.smsAuditIntro)),
              _section(context.l10n.smsAuditUser('${user.length}')),
              if (user.isEmpty)
                ListTile(leading: const Icon(Icons.check_circle, color: Colors.green), title: Text(context.l10n.smsAuditNoneUser))
              else
                for (final a in user) _row(a, canUninstall: true),
              _section(context.l10n.smsAuditSystem('${system.length}')),
              for (final a in system) _row(a, canUninstall: false),
            ]);
          },
        ),
      );

  Widget _section(String t) => Padding(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 4),
        child: Text(t, style: Theme.of(context).textTheme.titleSmall),
      );

  Widget _row(Map<String, dynamic> a, {required bool canUninstall}) {
    final pkg = a['packageName'] as String;
    return ListTile(
      leading: Icon(Icons.sms_failed, color: canUninstall ? Colors.red : Colors.grey),
      title: Text(a['label'] as String),
      // 8.12: the button sits under the text, not beside it, so very large text (or longer Hindi words) never squeezes it
      subtitle: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Wrap(spacing: 6, children: [
          Text(pkg, style: const TextStyle(fontSize: 12)),
          if (a['hasSmsPermission'] == true) Chip(label: Text(context.l10n.readsSms), visualDensity: VisualDensity.compact),
          if (a['hasNotificationListener'] == true) Chip(label: Text(context.l10n.readsNotifications), visualDensity: VisualDensity.compact),
        ]),
        if (canUninstall)
          Align(
            alignment: Alignment.centerRight,
            child: TextButton(onPressed: () => (widget.onUninstall ?? NativeBridge.uninstallApp)(pkg), child: Text(context.l10n.uninstall)),
          ),
      ]),
    );
  }
}
