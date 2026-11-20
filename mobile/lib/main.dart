import 'package:flutter/material.dart';
import 'core/api_client.dart';
import 'screens/dashboard_screen.dart';
import 'screens/login_screen.dart';

void main() => runApp(const FraudShieldApp());

class FraudShieldApp extends StatelessWidget {
  const FraudShieldApp({super.key});

  @override
  Widget build(BuildContext context) => MaterialApp(
        title: 'FraudShield',
        debugShowCheckedModeBanner: false,
        theme: ThemeData(colorSchemeSeed: const Color(0xFF0B6E4F), useMaterial3: true),
        home: const _Gate(),
      );
}

class _Gate extends StatefulWidget {
  const _Gate();
  @override
  State<_Gate> createState() => _GateState();
}

class _GateState extends State<_Gate> {
  late Future<bool> _loggedIn = ApiClient.instance.loadToken();

  @override
  Widget build(BuildContext context) => FutureBuilder<bool>(
        future: _loggedIn,
        builder: (c, s) {
          if (!s.hasData) return const Scaffold(body: Center(child: CircularProgressIndicator()));
          return s.data!
              ? DashboardScreen(onLogout: () => setState(() => _loggedIn = Future.value(false)))
              : LoginScreen(onDone: () => setState(() => _loggedIn = Future.value(true)));
        },
      );
}
