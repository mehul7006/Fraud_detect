import 'package:flutter/widgets.dart';
import 'dart:ui' show PlatformDispatcher;
import 'app_localizations.dart';

export 'app_localizations.dart';

/// 8.11: `context.l10n.someText`. Falls back to English when a widget is shown without the app's localization setup
/// (e.g. a widget test that pumps one screen in a bare MaterialApp), so a missing delegate never crashes a screen.
extension L10nX on BuildContext {
  AppLocalizations get l10n =>
      Localizations.of<AppLocalizations>(this, AppLocalizations) ?? lookupAppLocalizations(const Locale('en'));
}

/// The app-wide language choice: null = phone default, otherwise 'en' / 'hi'. Set from Settings, read by MaterialApp.
final ValueNotifier<Locale?> appLocale = ValueNotifier<Locale?>(null);

Locale? localeFromSetting(String? code) => code == 'en' || code == 'hi' ? Locale(code!) : null;

/// For code that has no BuildContext (the protection engine's alerts): the user's choice, else the phone's language.
AppLocalizations currentL10n() {
  final chosen = appLocale.value;
  final phone = PlatformDispatcher.instance.locale;
  return lookupAppLocalizations(chosen ?? (phone.languageCode == 'hi' ? const Locale('hi') : const Locale('en')));
}
