import 'package:flutter/foundation.dart' show kDebugMode;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../core/api_client.dart';
import '../ui/theme.dart';
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
  Widget build(BuildContext context) {
    final l = context.l10n;
    final t = Theme.of(context);
    final top = MediaQuery.paddingOf(context).top;
    return Scaffold(
      body: AnnotatedRegion<SystemUiOverlayStyle>(
        value: SystemUiOverlayStyle.light,
        child: ListView(padding: EdgeInsets.zero, children: [
          // brand hero: shield, name, one-line promise
          Container(
            padding: EdgeInsets.fromLTRB(24, top + 40, 24, 56),
            decoration: const BoxDecoration(
              gradient: AppColors.brandGradient,
              borderRadius: BorderRadius.vertical(bottom: Radius.circular(36)),
            ),
            child: Column(children: [
              Container(
                padding: const EdgeInsets.all(18),
                decoration: BoxDecoration(
                  color: Colors.white.withValues(alpha: 0.16), shape: BoxShape.circle,
                  border: Border.all(color: Colors.white.withValues(alpha: 0.35), width: 2),
                ),
                child: const Icon(Icons.shield_moon, size: 56, color: Colors.white),
              ),
              const SizedBox(height: 16),
              Text(l.appTitle, style: t.textTheme.headlineMedium?.copyWith(color: Colors.white)),
              const SizedBox(height: 6),
              Text(l.ui2LoginPromise, textAlign: TextAlign.center,
                  style: t.textTheme.bodyLarge?.copyWith(color: Colors.white)),
            ]),
          ),
          // form card, lifted over the hero edge
          Transform.translate(
            offset: const Offset(0, -28),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Card(
                elevation: 6,
                shadowColor: AppColors.indigo.withValues(alpha: 0.25),
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(20, 24, 20, 12),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                    TextField(controller: _email, keyboardType: TextInputType.emailAddress,
                        decoration: InputDecoration(labelText: l.email, prefixIcon: const Icon(Icons.alternate_email_rounded))),
                    const SizedBox(height: 14),
                    TextField(controller: _pass, obscureText: true,
                        decoration: InputDecoration(labelText: l.passwordHint, prefixIcon: const Icon(Icons.lock_outline_rounded))),
                    if (_error != null)
                      Container(
                        margin: const EdgeInsets.only(top: 14),
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(color: Tone.danger.bg(context), borderRadius: BorderRadius.circular(14)),
                        child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                          Icon(Icons.error_outline_rounded, color: Tone.danger.fg(context), size: 20),
                          const SizedBox(width: 8),
                          Expanded(child: Text(_error!, style: t.textTheme.bodyMedium?.copyWith(color: Tone.danger.fg(context)))),
                        ]),
                      ),
                    const SizedBox(height: 20),
                    FilledButton(
                      onPressed: _busy ? null : _submit,
                      child: _busy
                          ? SizedBox(width: 22, height: 22, child: CircularProgressIndicator(strokeWidth: 2.4, semanticsLabel: l.signingIn))
                          : Text(_register ? l.createAccount : l.login),
                    ),
                    const SizedBox(height: 6),
                    TextButton(onPressed: () => setState(() => _register = !_register), child: Text(_register ? l.haveAccount : l.newHere)),
                    // 9.6: only in builds made with --dart-define CRASH_TEST=true (never in store builds) - checks Crashlytics
                    if (kCrashTest)
                      TextButton(key: const Key('test-crash'), onPressed: () => throw StateError('FraudShield Crashlytics test crash'),
                          child: const Text('Send test crash')),
                  ]),
                ),
              ),
            ),
          ),
        ]),
      ),
    );
  }
}
