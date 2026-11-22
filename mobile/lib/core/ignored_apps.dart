import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';

/// Apps the user chose to ignore (6.7). Kept on the phone only: package name -> risk score at the time of ignoring.
/// An ignored app comes back as soon as its risk score goes UP (it gained a new risky ability).
class IgnoredApps {
  static const _key = 'ignored_apps';

  Future<Map<String, int>> all() async {
    final raw = (await SharedPreferences.getInstance()).getString(_key);
    if (raw == null) return {};
    return (jsonDecode(raw) as Map).map((k, v) => MapEntry(k as String, (v as num).toInt()));
  }

  Future<void> _save(Map<String, int> m) async => (await SharedPreferences.getInstance()).setString(_key, jsonEncode(m));

  Future<void> ignore(String pkg, int score) async => _save({...await all(), pkg: score});

  Future<void> unignore(String pkg) async => _save((await all())..remove(pkg));

  /// True when [pkg] was ignored and its risk did not increase since.
  static bool hides(Map<String, int> ignored, String pkg, int score) {
    final s = ignored[pkg];
    return s != null && score <= s;
  }
}
