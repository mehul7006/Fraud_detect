import 'dart:async';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import '../core/api_client.dart';
import '../l10n/l10n.dart';

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
  Widget build(BuildContext context) => Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            Icon(error is SocketException || error is http.ClientException || error is TimeoutException
                ? Icons.wifi_off : Icons.error_outline, size: 48, color: Colors.grey),
            const SizedBox(height: 12),
            Text(friendlyError(error, context.l10n), textAlign: TextAlign.center),
            const SizedBox(height: 12),
            FilledButton.icon(onPressed: onRetry, icon: const Icon(Icons.refresh), label: Text(context.l10n.tryAgain)),
          ]),
        ),
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
            Icon(icon, size: 48, color: Colors.green),
            const SizedBox(height: 12),
            Text(text, textAlign: TextAlign.center),
          ]),
        ),
      );
}
