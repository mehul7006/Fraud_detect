import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';

/// User choices that must survive a restart (4.9) and the links waiting for the network (4.10).
/// Stored on the phone only. The offline queue holds link + source app, nothing else.
class SettingsStore {
  static const _disabled = 'disabled_packages';
  static const _trusted = 'trusted_domains';
  static const _queue = 'offline_link_queue';
  static const _shareLabels = 'share_app_labels';
  static const _language = 'app_language'; // 8.11: 'system' | 'en' | 'hi'
  static const _consent = 'consent_version'; // 9.4: privacy notice version the user accepted
  static const _contact = 'emergency_contact'; // 7.4: [name, number], on this phone only
  static const maxQueue = 100;

  Future<SharedPreferences> get _p => SharedPreferences.getInstance();

  Future<String> language() async => (await _p).getString(_language) ?? 'system';
  Future<int> consentVersion() async => (await _p).getInt(_consent) ?? 0;

  /// 7.4: null = none. Indian mobile number normalised to 10 digits (an optional +91 / 0 prefix is dropped).
  Future<({String name, String phone})?> emergencyContact() async {
    final l = (await _p).getStringList(_contact);
    return l == null || l.length != 2 ? null : (name: l[0], phone: l[1]);
  }

  /// Returns false (and saves nothing) when the name is empty or the number is not a valid mobile number.
  Future<bool> setEmergencyContact(String name, String phone) async {
    final n = name.trim();
    final p = normalisePhone(phone);
    if (n.isEmpty || n.length > 40 || p == null) return false;
    await (await _p).setStringList(_contact, [n, p]);
    return true;
  }

  Future<void> clearEmergencyContact() async => (await _p).remove(_contact);

  static String? normalisePhone(String s) {
    var d = s.replaceAll(RegExp(r'[\s()-]'), '');
    if (d.startsWith('+91')) d = d.substring(3);
    if (d.startsWith('0') && d.length == 11) d = d.substring(1);
    return RegExp(r'^[6-9]\d{9}$').hasMatch(d) ? d : null;
  }
  Future<void> setConsentVersion(int v) async => (await _p).setInt(_consent, v);
  Future<void> setLanguage(String code) async => (await _p).setString(_language, code);

  Future<Set<String>> disabledPackages() async => ((await _p).getStringList(_disabled) ?? []).toSet();
  Future<void> setPackageMonitored(String pkg, bool monitored) async {
    final s = await disabledPackages();
    monitored ? s.remove(pkg) : s.add(pkg);
    await (await _p).setStringList(_disabled, s.toList());
  }

  /// 6.1 opt-in, OFF by default: send the app's risk signals + my Uninstall / Ignore decision to improve detection.
  Future<bool> shareAppLabels() async => (await _p).getBool(_shareLabels) ?? false;
  Future<void> setShareAppLabels(bool on) async => (await _p).setBool(_shareLabels, on);

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
