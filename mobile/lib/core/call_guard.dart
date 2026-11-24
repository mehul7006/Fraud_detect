import 'package:flutter/services.dart';

/// Phase 16 call guard: the Kotlin side lives in CallGuardBridge.kt (same channel as NativeBridge).
/// Everything here stays on the phone; only [ApiClient.reportNumber] (the user's Report tap) sends a number.
class CallGuardNative {
  static const _ch = MethodChannel('com.fraudshield.app/native');

  static Future<CallGuardStatus> status() async =>
      CallGuardStatus(Map<String, dynamic>.from((await _ch.invokeMethod<Map>('callGuardStatus')) ?? const {}));
  static Future<bool> requestRole() async => (await _ch.invokeMethod<bool>('callGuardRequestRole')) ?? false;
  static Future<void> setMode(int mode) => _ch.invokeMethod('callGuardSetMode', {'mode': mode});
  /// Returns the stored canonical number, or null when the text is not a phone number.
  static Future<String?> allow(String number, {bool allow = true}) =>
      _ch.invokeMethod<String>('callGuardAllow', {'number': number, 'allow': allow});
  static Future<String?> block(String number, {bool block = true}) =>
      _ch.invokeMethod<String>('callGuardBlock', {'number': number, 'block': block});
  static Future<String?> notSpam(String number) => _ch.invokeMethod<String>('callGuardNotSpam', {'number': number});
  static Future<List<Map<String, dynamic>>> log({int days = 30}) async =>
      ((await _ch.invokeMethod<List>('callGuardLog', {'days': days})) ?? const [])
          .map((e) => Map<String, dynamic>.from(e as Map)).toList();
  static Future<Map<String, dynamic>> sync() async =>
      Map<String, dynamic>.from((await _ch.invokeMethod<Map>('callGuardSync')) ?? const {});
  static Future<void> requestContacts() => _ch.invokeMethod('callGuardRequestContacts');
  /// {canonical, protected, rule, label}: 1600 / 140 numbers are protected (TRAI) and cannot be reported or blocked.
  static Future<Map<String, dynamic>> check(String number) async =>
      Map<String, dynamic>.from((await _ch.invokeMethod<Map>('callGuardCheck', {'number': number})) ?? const {});
  static Future<bool> takeOpenLog() async => (await _ch.invokeMethod<bool>('callGuardTakeOpenLog')) ?? false;
}

class CallGuardStatus {
  final Map<String, dynamic> raw;
  CallGuardStatus(this.raw);
  bool get supported => raw['supported'] == true;
  bool get roleHeld => raw['roleHeld'] == true;
  int get mode => (raw['mode'] as num?)?.toInt() ?? 2;
  List<String> get allow => ((raw['allow'] as List?) ?? const []).cast<String>();
  List<String> get block => ((raw['block'] as List?) ?? const []).cast<String>();
  int get scamListSize => (raw['scamListSize'] as num?)?.toInt() ?? 0;
  bool get contactsPermission => raw['contactsPermission'] == true;
  bool get callLogPermission => raw['callLogPermission'] == true;
  int count(String action) => ((raw['counts'] as Map?)?[action] as num?)?.toInt() ?? 0;
}

/// 16.12: the government's free fraud-reporting portal (DoT Sanchar Saathi "Chakshu").
/// Verified 2026-11-24: https://sancharsaathi.gov.in/sfc/ ("Report Suspected Fraud Communication / UCC (Spam)").
const kChakshuUrl = 'https://sancharsaathi.gov.in/sfc/';
