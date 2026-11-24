import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Phase 17 leftover cleaner: Dart side of LeftoverBridge.kt. Everything stays on the phone.
class LeftoverItem {
  final String path, rel, group, note;
  final bool isDir;
  final int bytes, files;
  final bool tickedByDefault;
  const LeftoverItem({required this.path, required this.rel, required this.group, this.note = '', this.isDir = false,
      required this.bytes, this.files = 1, required this.tickedByDefault});

  factory LeftoverItem.fromMap(Map m) => LeftoverItem(
        path: m['path'] as String, rel: (m['rel'] ?? m['path']) as String, group: (m['group'] ?? 'OTHER') as String,
        note: (m['note'] ?? '') as String, isDir: m['isDir'] == true, bytes: (m['bytes'] as num?)?.toInt() ?? 0,
        files: (m['files'] as num?)?.toInt() ?? 1, tickedByDefault: m['ticked'] == true);
}

class OldApk {
  final String path, rel, label, status;
  final String? package, version;
  final int bytes;
  final bool tickedByDefault;
  const OldApk(this.path, this.rel, this.label, this.status, this.package, this.version, this.bytes, this.tickedByDefault);
  factory OldApk.fromMap(Map m) => OldApk(m['path'] as String, (m['rel'] ?? m['path']) as String, (m['label'] ?? '') as String,
      (m['status'] ?? 'UNREADABLE') as String, m['package'] as String?, m['version'] as String?, (m['bytes'] as num?)?.toInt() ?? 0,
      m['ticked'] == true);
}

class RemovedApp {
  final String package, label;
  final int files, bytes;
  final DateTime at;
  const RemovedApp(this.package, this.label, this.files, this.bytes, this.at);
}

class SettingsCheck {
  final String kind, state, detail; // state: CLEAR / LEFTOVER / UNKNOWN
  const SettingsCheck(this.kind, this.state, this.detail);
}

class PackageLeftovers {
  final String package, label;
  final List<LeftoverItem> items;
  final List<SettingsCheck> settings;
  const PackageLeftovers(this.package, this.label, this.items, this.settings);
}

class DeleteResult {
  final int trashed, deleted, refused;
  final bool cancelled;
  const DeleteResult(this.trashed, this.deleted, this.refused, this.cancelled);
}

class LeftoverApi {
  static const _ch = MethodChannel('com.fraudshield.app/native');

  static List<Map> _maps(Object? v) => v is List ? v.whereType<Map>().toList() : const [];

  static Future<List<RemovedApp>> pending() async {
    try {
      return _maps(await _ch.invokeMethod('leftoverPending'))
          .map((m) => RemovedApp(m['package'] as String, (m['label'] ?? m['package']) as String, (m['files'] as num?)?.toInt() ?? 0,
              (m['bytes'] as num?)?.toInt() ?? 0, DateTime.fromMillisecondsSinceEpoch((m['at'] as num?)?.toInt() ?? 0)))
          .toList();
    } catch (_) {
      return const [];
    }
  }

  static Future<PackageLeftovers> forPackage(String pkg) async {
    final v = await _ch.invokeMethod('leftoverForPackage', {'package': pkg});
    final m = v is Map ? v : const {};
    return PackageLeftovers(pkg, (m['label'] ?? pkg) as String, _maps(m['items']).map(LeftoverItem.fromMap).toList(),
        _maps(m['settings']).map((s) => SettingsCheck(s['kind'] as String, s['state'] as String, (s['detail'] ?? '') as String)).toList());
  }

  static Future<({List<LeftoverItem> orphans, List<OldApk> apks})> scan() async {
    final v = await _ch.invokeMethod('leftoverScan', {'flagged': await FlaggedFiles.all()});
    final m = v is Map ? v : const {};
    return (orphans: _maps(m['orphans']).map(LeftoverItem.fromMap).toList(), apks: _maps(m['apks']).map(OldApk.fromMap).toList());
  }

  /// Only ever called after the user's explicit confirm in LeftoverScreen.
  static Future<DeleteResult> delete(List<String> paths, {String? package}) async {
    final v = await _ch.invokeMethod('leftoverDelete', {'paths': paths, 'package': package});
    final m = v is Map ? v : const {};
    final refused = m['refused'];
    return DeleteResult((m['trashed'] as num?)?.toInt() ?? 0, (m['deleted'] as num?)?.toInt() ?? 0,
        refused is Map ? refused.length : 0, m['cancelled'] == true);
  }

  static Future<void> dismiss(String pkg) => _ch.invokeMethod('leftoverDismiss', {'package': pkg});

  /// Package from a tapped "X left N files" notification (null when none).
  static Future<String?> takeOpen() async {
    try {
      final v = await _ch.invokeMethod('leftoverTakeOpen');
      return v is String ? v : null;
    } catch (_) {
      return null;
    }
  }
}

/// 17.6: files file protection (5.x) flagged, so an old flagged installer in Download is pre-ticked for removal.
class FlaggedFiles {
  static const _key = 'flagged_files';
  static Future<void> add(String path) async {
    final p = await SharedPreferences.getInstance();
    final l = p.getStringList(_key) ?? [];
    if (!l.contains(path)) await p.setStringList(_key, [...l.length > 499 ? l.sublist(l.length - 499) : l, path]);
  }

  static Future<List<String>> all() async => (await SharedPreferences.getInstance()).getStringList(_key) ?? [];
}

String humanSize(int bytes) {
  if (bytes >= 1 << 30) return '${(bytes / (1 << 30)).toStringAsFixed(1)} GB';
  if (bytes >= 1 << 20) return '${(bytes / (1 << 20)).toStringAsFixed(1)} MB';
  if (bytes >= 1 << 10) return '${bytes ~/ 1024} KB';
  return '$bytes B';
}
