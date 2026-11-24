import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Phase 15 phone side. Kotlin: SafetyBridge.kt / ScamSignalCollector.kt (same channel as NativeBridge).
class SafetyNative {
  static const _ch = MethodChannel('com.fraudshield.app/native');

  /// {enabled, otpGuard, running, phoneState, callLog, contacts, usageAccess, notificationAccess, remoteAccessApps}
  static Future<Map<String, dynamic>> guardStatus() async =>
      Map<String, dynamic>.from((await _ch.invokeMethod<Map>('scamGuardStatus')) ?? const {});
  static Future<void> setGuard(bool on) => _ch.invokeMethod('scamGuardSet', {'enabled': on});
  static Future<void> setOtpGuard(bool on) => _ch.invokeMethod('scamGuardOtp', {'enabled': on});
  static Future<void> requestPermissions() => _ch.invokeMethod('scamGuardPermissions');
  /// Debug builds only: runs four test signals through the guard (device test of the STOP path).
  static Future<bool> simulate() async => (await _ch.invokeMethod<bool>('scamGuardSimulate')) ?? false;
  /// 15.3: Android text-to-speech. false = no voice for [lang] on this phone.
  static Future<bool> speak(String text, String lang) async =>
      (await _ch.invokeMethod<bool>('speak', {'text': text, 'lang': lang})) ?? false;
  static Future<void> stopSpeaking() => _ch.invokeMethod('stopSpeaking');
  /// Opens the dialler with [number] filled in; the user presses Call.
  static Future<bool> dial(String number) async => (await _ch.invokeMethod<bool>('dialNumber', {'number': number})) ?? false;
  static Future<void> shareText(String text, {String subject = 'FraudShield'}) =>
      _ch.invokeMethod('shareText', {'text': text, 'subject': subject});
}

/// One official number from assets/data/care-numbers.csv (each row read on the brand's own website).
class CareEntry {
  final String brand, number, purpose, sourceUrl;
  const CareEntry(this.brand, this.number, this.purpose, this.sourceUrl);
  bool get smsOnly => purpose.toLowerCase().contains('sms only');
  bool get forFraud {
    final p = purpose.toLowerCase();
    return p.contains('fraud') || p.contains('unauthorised') || p.contains('block') || p.contains('lost');
  }
}

class CareDirectory {
  static const banks = {'State Bank of India', 'HDFC Bank', 'ICICI Bank', 'Axis Bank', 'Kotak Mahindra Bank',
    'Punjab National Bank', 'Bank of Baroda', 'Airtel Payments Bank', 'PhonePe', 'Paytm'};

  static List<CareEntry> parse(String csv) => [
        for (final line in csv.split('\n'))
          if (line.trim().isNotEmpty && !line.startsWith('#') && !line.startsWith('brand,'))
            if (line.split(',') case [final b, final n, final p, final s, ...]) CareEntry(b.trim(), n.trim(), p.trim(), s.trim()),
      ];

  static Future<List<CareEntry>> load([AssetBundle? bundle]) async =>
      parse(await (bundle ?? rootBundle).loadString('assets/data/care-numbers.csv'));

  /// Recovery kit: per bank, the numbers to call to block / report fraud (fraud lines first, never SMS-only lines,
  /// never the bank's own outgoing 1600 numbers).
  static Map<String, List<CareEntry>> blockNumbers(List<CareEntry> all) {
    final out = <String, List<CareEntry>>{};
    for (final e in all) {
      if (!banks.contains(e.brand) || e.smsOnly || e.purpose.contains('outgoing') || e.purpose.contains('office')) continue;
      out.putIfAbsent(e.brand, () => []).add(e);
    }
    for (final l in out.values) {
      l.sort((a, b) => (b.forFraud ? 1 : 0) - (a.forFraud ? 1 : 0));
    }
    return out;
  }
}

/// 15.6: the "golden hour" starts when the user opens the kit after a scam; kept on the phone only.
class RecoveryState {
  static const _start = 'recovery_started_at';
  static const _done = 'recovery_done';

  static Future<DateTime?> startedAt() async {
    final ms = (await SharedPreferences.getInstance()).getInt(_start);
    return ms == null ? null : DateTime.fromMillisecondsSinceEpoch(ms);
  }

  static Future<DateTime> start() async {
    final p = await SharedPreferences.getInstance();
    final now = DateTime.now();
    await p.setInt(_start, now.millisecondsSinceEpoch);
    await p.setStringList(_done, []);
    return now;
  }

  static Future<void> reset() async {
    final p = await SharedPreferences.getInstance();
    await p.remove(_start);
    await p.remove(_done);
  }

  static Future<Set<String>> done() async => ((await SharedPreferences.getInstance()).getStringList(_done) ?? []).toSet();
  static Future<void> setDone(String step, bool v) async {
    final p = await SharedPreferences.getInstance();
    final s = (p.getStringList(_done) ?? []).toSet();
    v ? s.add(step) : s.remove(step);
    await p.setStringList(_done, s.toList());
  }
}

/// 15.10: drill results, on this phone only.
class DrillStore {
  static String _key(String id) => 'drill_best_$id';
  static Future<int?> best(String id) async => (await SharedPreferences.getInstance()).getInt(_key(id));
  static Future<void> save(String id, int score) async {
    final p = await SharedPreferences.getInstance();
    final old = p.getInt(_key(id));
    if (old == null || score > old) await p.setInt(_key(id), score);
  }
}
