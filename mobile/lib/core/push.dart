import 'dart:async';
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';

/// 7.3: a push alert from the server (threats it detected, e.g. account takeover from correlated signals).
class PushAlert {
  final String title, body, type;
  PushAlert(this.title, this.body, this.type);
}

/// Behind an interface so tests (and builds without Firebase) need no Firebase at all.
abstract class PushClient {
  /// FCM registration token, or null when push is unavailable. Sent with the hourly device check-in.
  Future<String?> token();

  /// Pushes that arrive while the app is open (background ones are shown by Android itself).
  Stream<PushAlert> get foreground;
}

class NoPushClient implements PushClient {
  @override
  Future<String?> token() async => null;
  @override
  Stream<PushAlert> get foreground => const Stream.empty();
}

/// Real Firebase Cloud Messaging. Every call is guarded: a missing google-services.json or Play services never
/// breaks the app, it only means no push.
class FirebasePushClient implements PushClient {
  static bool _ready = false;

  /// Called once from main(). false = Firebase not configured in this build.
  static Future<bool> init() async {
    try {
      await Firebase.initializeApp();
      _ready = true;
    } catch (e) {
      debugPrint('Firebase not available: $e');
      _ready = false;
    }
    return _ready;
  }

  @override
  Future<String?> token() async {
    if (!_ready) return null;
    try {
      // The FCM HTTP v1 API the backend uses (PushService, PushIT) targets the classic registration token, which
      // getToken() returns. firebase_messaging 17 deprecates it in favour of register()/onRegistered (installation
      // IDs for "direct send"); switch together with the backend once that pairing is tested. Follow-up 7.3b.
      // ignore: deprecated_member_use
      return await FirebaseMessaging.instance.getToken();
    } catch (e) {
      debugPrint('no push token: $e');
      return null;
    }
  }

  @override
  Stream<PushAlert> get foreground => !_ready
      ? const Stream.empty()
      : FirebaseMessaging.onMessage.map((m) => PushAlert(m.notification?.title ?? 'FraudShield',
          m.notification?.body ?? '', m.data['type']?.toString() ?? ''));
}
