import 'package:flutter/material.dart';
import '../core/api_client.dart';

class LoginScreen extends StatefulWidget {
  final VoidCallback onDone;
  final ApiClient? api; // injectable for tests; defaults to the app-wide client
  const LoginScreen({super.key, required this.onDone, this.api});
  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _email = TextEditingController();
  final _pass = TextEditingController();
  bool _busy = false, _register = false;
  String? _error;

  Future<void> _submit() async {
    setState(() { _busy = true; _error = null; });
    try {
      final api = widget.api ?? ApiClient.instance;
      _register ? await api.register(_email.text.trim(), _pass.text) : await api.login(_email.text.trim(), _pass.text);
      widget.onDone();
    } on ApiException catch (e) {
      setState(() => _error = e.status == 401
          ? 'Wrong email or password'
          : e.status == 409
              ? 'Email already registered'
              : e.status == 400
                  ? e.message
                  : 'Error ${e.status}');
    } catch (_) {
      setState(() => _error = 'Cannot reach server');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        body: SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
              const Icon(Icons.shield, size: 72, color: Color(0xFF0B6E4F)),
              const SizedBox(height: 8),
              Text('FraudShield', style: Theme.of(context).textTheme.headlineMedium),
              const SizedBox(height: 24),
              TextField(controller: _email, keyboardType: TextInputType.emailAddress, decoration: const InputDecoration(labelText: 'Email', border: OutlineInputBorder())),
              const SizedBox(height: 12),
              TextField(controller: _pass, obscureText: true, decoration: const InputDecoration(labelText: 'Password (min 8)', border: OutlineInputBorder())),
              if (_error != null) Padding(padding: const EdgeInsets.only(top: 8), child: Text(_error!, style: const TextStyle(color: Colors.red))),
              const SizedBox(height: 16),
              SizedBox(width: double.infinity, child: FilledButton(onPressed: _busy ? null : _submit, child: Text(_register ? 'Create account' : 'Login'))),
              TextButton(onPressed: () => setState(() => _register = !_register), child: Text(_register ? 'Have an account? Login' : 'New here? Register')),
            ]),
          ),
        ),
      );
}
