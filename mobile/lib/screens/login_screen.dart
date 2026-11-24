import 'package:flutter/foundation.dart' show kDebugMode;
import 'package:flutter/material.dart';
import '../core/api_client.dart';
import 'states.dart';
import '../l10n/l10n.dart';

/// 9.6: test-crash button, compiled in only with --dart-define CRASH_TEST=true.
const bool kCrashTest = bool.fromEnvironment('CRASH_TEST');

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
      if (!mounted) return;
      final l = context.l10n;
      setState(() => _error = e.status == 401
          ? l.wrongPassword
          : e.status == 409
              ? l.emailTaken
              : e.status == 400
                  ? e.message
                  : friendlyError(e, l));
    } catch (e) {
      if (kDebugMode) debugPrint('login/register failed: ${e.runtimeType}: $e'); // debug builds only; no password / token
      if (!mounted) return;
      setState(() => _error = friendlyError(e, context.l10n));
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
              Text(context.l10n.appTitle, style: Theme.of(context).textTheme.headlineMedium),
              const SizedBox(height: 24),
              TextField(controller: _email, keyboardType: TextInputType.emailAddress, decoration: InputDecoration(labelText: context.l10n.email, border: const OutlineInputBorder())),
              const SizedBox(height: 12),
              TextField(controller: _pass, obscureText: true, decoration: InputDecoration(labelText: context.l10n.passwordHint, border: const OutlineInputBorder())),
              if (_error != null) Padding(padding: const EdgeInsets.only(top: 8), child: Text(_error!, style: const TextStyle(color: Colors.red))),
              const SizedBox(height: 16),
              SizedBox(width: double.infinity, child: FilledButton(onPressed: _busy ? null : _submit,
                  child: _busy
                      ? SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2, semanticsLabel: context.l10n.signingIn))
                      : Text(_register ? context.l10n.createAccount : context.l10n.login))),
              TextButton(onPressed: () => setState(() => _register = !_register), child: Text(_register ? context.l10n.haveAccount : context.l10n.newHere)),
              // 9.6: only in builds made with --dart-define CRASH_TEST=true (never in store builds) - checks Crashlytics
              if (kCrashTest)
                TextButton(key: const Key('test-crash'), onPressed: () => throw StateError('FraudShield Crashlytics test crash'),
                    child: const Text('Send test crash')),
            ]),
          ),
        ),
      );
}
