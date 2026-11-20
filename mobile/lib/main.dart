import 'package:flutter/material.dart';
import 'core/api_client.dart';
import 'core/protection_engine.dart';
import 'screens/dashboard_screen.dart';
import 'screens/login_screen.dart';

void main() => runApp(const FraudShieldApp());

class FraudShieldApp extends StatelessWidget {
  final ApiClient? api; // injectable for tests
  final ProtectionEngine? engine;
  final bool autoStart;
  const FraudShieldApp({super.key, this.api, this.engine, this.autoStart = true});

  @override
  Widget build(BuildContext context) => MaterialApp(
        title: 'FraudShield',
        debugShowCheckedModeBanner: false,
        theme: ThemeData(colorSchemeSeed: const Color(0xFF0B6E4F), useMaterial3: true),
        home: AppGate(api: api ?? ApiClient.instance, engine: engine, autoStart: autoStart),
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

  @override
  Widget build(BuildContext context) => FutureBuilder<bool>(
        future: _loggedIn,
        builder: (c, s) {
          if (!s.hasData) return const Scaffold(body: Center(child: CircularProgressIndicator()));
          return s.data!
              ? DashboardScreen(
                  api: widget.api,
                  engine: widget.engine,
                  autoStart: widget.autoStart,
                  onLogout: () => setState(() { _loggedIn = Future.value(false); }),   // block body: setState must not return a Future
                )
              : LoginScreen(api: widget.api, onDone: () => setState(() { _loggedIn = Future.value(true); }));
        },
      );
}
