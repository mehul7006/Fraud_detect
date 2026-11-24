import 'dart:async';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import '../core/api_client.dart';
import '../l10n/l10n.dart';
import '../ui/theme.dart';

/// 8.10: one wording for every failure, in plain language. Offline never sounds like "your phone is unprotected":
/// links found while offline are queued and checked later (4.10).
String friendlyError(Object e, [AppLocalizations? l]) {
  final t = l ?? lookupAppLocalizations(const Locale('en'));
  if (e is SocketException || e is http.ClientException || e is TimeoutException || e is HandshakeException) {
    return t.errOffline;
  }
  if (e is ApiException) {
    if (e.status == 429) return t.errTooMany;
    if (e.status == 401) return t.errSession;
    if (e.status >= 500) return t.errServer;
    return e.message;
  }
  return t.errGeneric;
}

/// Error with a retry button (used where a screen could not load).
class ErrorState extends StatelessWidget {
  final Object error;
  final VoidCallback onRetry;
  const ErrorState({super.key, required this.error, required this.onRetry});

  @override
  Widget build(BuildContext context) {
    final offline = error is SocketException || error is http.ClientException || error is TimeoutException;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          _StateBadge(icon: offline ? Icons.wifi_off_rounded : Icons.error_outline_rounded, tone: offline ? Tone.info : Tone.warn),
          const SizedBox(height: 16),
          Text(friendlyError(error, context.l10n), textAlign: TextAlign.center, style: Theme.of(context).textTheme.bodyLarge),
          const SizedBox(height: 16),
          FilledButton.icon(onPressed: onRetry, icon: const Icon(Icons.refresh_rounded), label: Text(context.l10n.tryAgain)),
        ]),
      ),
    );
  }
}

/// Soft tinted circle with an icon (empty / error states).
class _StateBadge extends StatelessWidget {
  final IconData icon;
  final Tone tone;
  const _StateBadge({required this.icon, required this.tone});
  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.all(22),
        decoration: BoxDecoration(color: tone.bg(context), shape: BoxShape.circle),
        child: Icon(icon, size: 44, color: tone.fg(context)),
      );
}

/// Calm empty state ("nothing here" is good news in a security app).
class EmptyState extends StatelessWidget {
  final IconData icon;
  final String text;
  const EmptyState({super.key, required this.icon, required this.text});

  @override
  Widget build(BuildContext context) => Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            _StateBadge(icon: icon, tone: Tone.safe),
            const SizedBox(height: 16),
            Text(text, textAlign: TextAlign.center, style: Theme.of(context).textTheme.bodyLarge),
          ]),
        ),
      );
}
