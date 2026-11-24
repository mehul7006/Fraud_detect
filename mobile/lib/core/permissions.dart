import 'package:flutter/material.dart';

import '../l10n/app_localizations.dart';
import '../ui/theme.dart';
import 'native_bridge.dart';

/// One permission FraudShield uses: what it is for, whether it is on, and how it is switched on.
class PermItem {
  final String id;
  final bool granted, required, unknown;
  final String kind; // dialog | page | screen
  const PermItem(this.id, {required this.granted, required this.required, required this.kind, this.unknown = false});

  IconData get icon => switch (id) {
        'notifications' => Icons.notifications_active_rounded,
        'fullScreen' => Icons.fullscreen_rounded,
        'notificationAccess' => Icons.mark_chat_unread_rounded,
        'allFiles' => Icons.folder_open_rounded,
        'phone' => Icons.phone_in_talk_rounded,
        'vpn' => Icons.vpn_lock_rounded,
        'callScreening' => Icons.phone_locked_rounded,
        'contacts' => Icons.contacts_rounded,
        'callLog' => Icons.history_rounded,
        'usage' => Icons.query_stats_rounded,
        'battery' => Icons.battery_charging_full_rounded,
        'autostart' => Icons.restart_alt_rounded,
        _ => Icons.verified_user_rounded,
      };

  Feature get feature => switch (id) {
        'notifications' || 'fullScreen' => Feature.scam,
        'notificationAccess' => Feature.message,
        'allFiles' => Feature.files,
        'phone' || 'callScreening' || 'contacts' || 'callLog' => Feature.call,
        'vpn' => Feature.web,
        'usage' => Feature.apps,
        _ => Feature.device,
      };

  String title(AppLocalizations l) => switch (id) {
        'notifications' => l.permNameNotifications, 'fullScreen' => l.permNameFullScreen,
        'notificationAccess' => l.permNameNotifAccess, 'allFiles' => l.permNameFiles, 'phone' => l.permNamePhone,
        'vpn' => l.permNameVpn, 'callScreening' => l.permNameCallScreening, 'contacts' => l.permNameContacts,
        'callLog' => l.permNameCallLog, 'usage' => l.permNameUsage, 'battery' => l.permNameBattery,
        'autostart' => l.permNameAutostart, _ => id,
      };

  String why(AppLocalizations l) => switch (id) {
        'notifications' => l.permWhyNotifications, 'fullScreen' => l.permWhyFullScreen,
        'notificationAccess' => l.permWhyNotifAccess, 'allFiles' => l.permWhyFiles, 'phone' => l.permWhyPhone,
        'vpn' => l.permWhyVpn, 'callScreening' => l.permWhyCallScreening, 'contacts' => l.permWhyContacts,
        'callLog' => l.permWhyCallLog, 'usage' => l.permWhyUsage, 'battery' => l.permWhyBattery,
        'autostart' => l.permWhyAutostart, _ => '',
      };
}

class Permissions {
  /// Every permission, in the order they matter most. Unknown ones (phone-maker autostart) are listed but never counted.
  static Future<List<PermItem>> load() async {
    final raw = await NativeBridge.permStatus();
    return [
      for (final e in raw.entries)
        PermItem(e.key,
            granted: e.value['granted'] == true, required: e.value['required'] == true,
            kind: (e.value['kind'] ?? 'page') as String, unknown: e.value['unknown'] == true),
    ];
  }

  static ({int allowed, int total, int pending}) count(List<PermItem> items) {
    final counted = items.where((p) => !p.unknown).toList();
    final allowed = counted.where((p) => p.granted).length;
    return (allowed: allowed, total: counted.length, pending: counted.length - allowed);
  }
}
