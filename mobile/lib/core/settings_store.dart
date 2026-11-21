import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';

/// User choices that must survive a restart (4.9) and the links waiting for the network (4.10).
/// Stored on the phone only. The offline queue holds link + source app, nothing else.
class SettingsStore {
  static const _disabled = 'disabled_packages';
  static const _trusted = 'trusted_domains';
  static const _queue = 'offline_link_queue';
  static const maxQueue = 100;

  Future<SharedPreferences> get _p => SharedPreferences.getInstance();

  Future<Set<String>> disabledPackages() async => ((await _p).getStringList(_disabled) ?? []).toSet();
  Future<void> setPackageMonitored(String pkg, bool monitored) async {
    final s = await disabledPackages();
    monitored ? s.remove(pkg) : s.add(pkg);
    await (await _p).setStringList(_disabled, s.toList());
  }

  Future<List<String>> trustedDomains() async => (await _p).getStringList(_trusted) ?? [];
  Future<void> addTrustedDomain(String domain) async {
    final d = normaliseDomain(domain);
    if (d == null) return;
    final l = await trustedDomains();
    if (!l.contains(d)) await (await _p).setStringList(_trusted, [...l, d]);
  }

  Future<void> removeTrustedDomain(String d) async =>
      (await _p).setStringList(_trusted, (await trustedDomains()).where((x) => x != d).toList());

  /// "https://www.Example.com/x" -> "example.com"; null when it is not a domain.
  static String? normaliseDomain(String input) {
    var s = input.trim().toLowerCase().replaceFirst(RegExp(r'^[a-z][a-z0-9+.-]*://'), '');
    s = s.split(RegExp(r'[/?#:]')).first;
    if (s.startsWith('www.')) s = s.substring(4);
    return RegExp(r'^[a-z0-9]([a-z0-9.-]*[a-z0-9])?\.[a-z0-9-]{2,}$').hasMatch(s) ? s : null;
  }

  Future<List<Map<String, String?>>> queuedLinks() async {
    final raw = (await _p).getString(_queue);
    if (raw == null) return [];
    return (jsonDecode(raw) as List).map((e) => Map<String, String?>.from(e as Map)).toList();
  }

  Future<void> _saveQueue(List<Map<String, String?>> l) async => (await _p).setString(_queue, jsonEncode(l));

  Future<void> enqueueLink(String url, String? pkg) async {
    final l = await queuedLinks();
    if (l.any((e) => e['url'] == url)) return;
    l.add({'url': url, 'pkg': pkg});
    await _saveQueue(l.length > maxQueue ? l.sublist(l.length - maxQueue) : l);
  }

  Future<void> removeQueued(String url) async => _saveQueue((await queuedLinks()).where((e) => e['url'] != url).toList());
}
