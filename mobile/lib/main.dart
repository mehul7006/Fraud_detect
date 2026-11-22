import 'package:flutter/material.dart';
import 'core/api_client.dart';
import 'package:firebase_crashlytics/firebase_crashlytics.dart';
import 'package:flutter/foundation.dart';
import 'core/push.dart';
import 'core/settings_store.dart';
import 'l10n/l10n.dart';
import 'core/protection_engine.dart';
import 'core/native_bridge.dart';
import 'screens/dashboard_screen.dart';
import 'screens/consent_screen.dart';
import 'screens/login_screen.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  appLocale.value = localeFromSetting(await SettingsStore().language()); // 8.11: the user's language choice
  // 7.3 push + 9.6 crash reports. Without Firebase configured the app runs normally, just without both.
  final firebase = await FirebasePushClient.init();
  if (firebase) {
    try {
      await FirebaseCrashlytics.instance.setCrashlyticsCollectionEnabled(kReleaseMode); // release builds only
      FlutterError.onError = FirebaseCrashlytics.instance.recordFlutterFatalError;
      PlatformDispatcher.instance.onError = (e, st) {
        FirebaseCrashlytics.instance.recordError(e, st, fatal: true);
        return true;
      };
    } catch (e) {
      debugPrint('crash reporting not available: $e');
    }
  }
  runApp(FraudShieldApp(engine: ProtectionEngine(push: firebase ? FirebasePushClient() : NoPushClient())));
}

class FraudShieldApp extends StatelessWidget {
  final ApiClient? api; // injectable for tests
  final ProtectionEngine? engine;
  final bool autoStart;
  const FraudShieldApp({super.key, this.api, this.engine, this.autoStart = true});

  @override
  Widget build(BuildContext context) => ValueListenableBuilder<Locale?>(
        valueListenable: appLocale,
        builder: (c, locale, _) => MaterialApp(
          title: 'FraudShield',
          debugShowCheckedModeBanner: false,
          theme: ThemeData(colorSchemeSeed: const Color(0xFF0B6E4F), useMaterial3: true),
          // 8.11 English + Hindi; locale null = follow the phone's language
          locale: locale,
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: AppGate(api: api ?? ApiClient.instance, engine: engine, autoStart: autoStart),
        ),
      );
}

/// Decides the first screen: dashboard when a saved session exists, login otherwise.
class AppGate extends StatefulWidget {
  final ApiClient api;
  final ProtectionEngine? engine;
  final bool autoStart;
  const AppGate({super.key, required this.api, this.engine, this.autoStart = true});
  @override
  State<AppGate> createState() => _AppGateState();
}

class _AppGateState extends State<AppGate> {
  late Future<bool> _loggedIn = widget.api.loadToken();
  late Future<int> _consent = SettingsStore().consentVersion(); // 9.4

  @override
  void initState() {
    super.initState();
    // server rejected the token (account deleted / token revoked): back to login, and stop background scanning
    widget.api.onSessionExpired = () {
      NativeBridge.clearBackgroundSession().catchError((_) {});
      if (mounted) setState(() { _loggedIn = Future.value(false); });
    };
  }

  @override
  Widget build(BuildContext context) => FutureBuilder<bool>(
        future: _loggedIn,
        builder: (c, s) {
          if (!s.hasData) return const Scaffold(body: Center(child: CircularProgressIndicator()));
          if (!s.data!) return LoginScreen(api: widget.api, onDone: () => setState(() { _loggedIn = Future.value(true); }));
          // 9.4: the privacy notice comes before the dashboard (and so before any permission request)
          return FutureBuilder<int>(
            future: _consent,
            builder: (c, cs) {
              if (!cs.hasData) return const Scaffold(body: Center(child: CircularProgressIndicator()));
              if (cs.data! < kConsentVersion) {
                return ConsentScreen(
                  onAccept: () async {
                    await SettingsStore().setConsentVersion(kConsentVersion);
                    if (mounted) setState(() { _consent = Future.value(kConsentVersion); });
                  },
                  onDecline: () async {
                    await widget.api.logout();
                    if (mounted) setState(() { _loggedIn = Future.value(false); });
                  },
                );
              }
              return DashboardScreen(
                api: widget.api,
                engine: widget.engine,
                autoStart: widget.autoStart,
                onLogout: () => setState(() { _loggedIn = Future.value(false); }),   // block body: setState must not return a Future
              );
            },
          );
        },
      );
}
