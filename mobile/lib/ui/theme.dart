import 'package:flutter/material.dart';

/// "Aurora Fashion" (v2) design language: violet -> purple -> pink brand gradient, one curated two-tone gradient per
/// feature (every colour keeps >= 4.5:1 with white text, so gradients carry white text directly),
/// emerald / amber / rose for safe / careful / danger. Poppins (Latin + Devanagari) for English and Hindi alike.
/// All text colours here meet WCAG AA (4.5:1) on their backgrounds - the accessibility tests check it.
class AppColors {
  static const indigo = Color(0xFF3B2DD8);
  static const violet = Color(0xFF7A3FE4);
  static const ink = Color(0xFF14142B);
  static const inkSoft = Color(0xFF4E4B66);
  static const canvas = Color(0xFFF7F4FF); // soft lavender
  static const card = Colors.white;
  static const line = Color(0xFFE4E6F0);

  static const safe = Color(0xFF0B6B4A); // 6.5:1 on white, 5.8:1 on safeBg
  static const safeBg = Color(0xFFE3F6EE);
  static const warn = Color(0xFFB45309);
  static const warnBg = Color(0xFFFFF4E0);
  static const danger = Color(0xFFC8102E);
  static const dangerBg = Color(0xFFFFE8EC);
  static const info = Color(0xFF1D4ED8);
  static const infoBg = Color(0xFFE6EEFF);

  // dark
  static const canvasDark = Color(0xFF0E0B1F);
  static const cardDark = Color(0xFF1A1630);
  static const lineDark = Color(0xFF2A2E4A);

  static const brandGradient = LinearGradient( // hero: deep violet -> purple -> pink (white text 6:1 or more)
    colors: [Color(0xFF3B0F8C), Color(0xFF7E22CE), Color(0xFFBE185D)],
    stops: [0, 0.55, 1], begin: Alignment.topLeft, end: Alignment.bottomRight,
  );
  /// page background behind the home screen: lavender -> blush
  static const canvasGradient = LinearGradient(
    colors: [Color(0xFFF7F4FF), Color(0xFFFDF2F8)], begin: Alignment.topCenter, end: Alignment.bottomCenter,
  );
  static const canvasGradientDark = LinearGradient(
    colors: [Color(0xFF0E0B1F), Color(0xFF1A0E22)], begin: Alignment.topCenter, end: Alignment.bottomCenter,
  );
  static const dangerGradient = LinearGradient(
    colors: [Color(0xFF6E0717), Color(0xFF95091F), danger],
    begin: Alignment.topLeft, end: Alignment.bottomRight,
  );
}

/// One colour family per feature, so the home screen reads at a glance.
enum Feature {
  web(Color(0xFF2563EB), Color(0xFF7C3AED)),      // blue -> violet
  message(Color(0xFF7C3AED), Color(0xFFC026D3)),  // violet -> fuchsia
  call(Color(0xFF0F766E), Color(0xFF0369A1)),     // teal -> deep sky
  apps(Color(0xFFC2410C), Color(0xFFBE185D)),     // orange -> pink
  files(Color(0xFF0369A1), Color(0xFF4338CA)),    // sky -> indigo
  scam(Color(0xFFBE123C), Color(0xFF9D174D)),     // rose -> wine
  cleaner(Color(0xFFB45309), Color(0xFFBE123C)),  // amber -> rose
  device(Color(0xFF4338CA), Color(0xFF7E22CE)),   // indigo -> purple
  account(Color(0xFF047857), Color(0xFF0F766E)),  // emerald -> teal
  history(Color(0xFF334155), Color(0xFF6D28D9));  // slate -> violet

  final Color from, to;
  const Feature(this.from, this.to);
  LinearGradient get gradient => LinearGradient(colors: [from, to], begin: Alignment.topLeft, end: Alignment.bottomRight);
  /// v2: the curated pairs already keep >= 4.5:1 with white text, so banners use the vivid gradient as-is.
  LinearGradient get deepGradient => gradient;
  Color get deep => from;
  /// very light tint of the pair, for group card backgrounds behind dark text
  Color tint(Brightness b) => b == Brightness.dark ? Color.lerp(from, Colors.black, 0.78)! : Color.lerp(from, Colors.white, 0.93)!;
}

enum Tone { safe, warn, danger, info, neutral }

extension ToneColors on Tone {
  Color fg(BuildContext c) {
    final dark = Theme.of(c).brightness == Brightness.dark;
    return switch (this) {
      Tone.safe => dark ? const Color(0xFF5EE0AE) : AppColors.safe,
      Tone.warn => dark ? const Color(0xFFFFC266) : AppColors.warn,
      Tone.danger => dark ? const Color(0xFFFF8597) : AppColors.danger,
      Tone.info => dark ? const Color(0xFF9DB8FF) : AppColors.info,
      Tone.neutral => dark ? const Color(0xFFC4C6DA) : AppColors.inkSoft,
    };
  }

  Color bg(BuildContext c) {
    final dark = Theme.of(c).brightness == Brightness.dark;
    return switch (this) {
      Tone.safe => dark ? const Color(0xFF0F3A2C) : AppColors.safeBg,
      Tone.warn => dark ? const Color(0xFF3D2A0B) : AppColors.warnBg,
      Tone.danger => dark ? const Color(0xFF45121D) : AppColors.dangerBg,
      Tone.info => dark ? const Color(0xFF16254D) : AppColors.infoBg,
      Tone.neutral => dark ? const Color(0xFF23263F) : const Color(0xFFEEEFF6),
    };
  }
}

const _font = 'Poppins';

ThemeData buildTheme(Brightness b) {
  final dark = b == Brightness.dark;
  final scheme = ColorScheme.fromSeed(seedColor: AppColors.indigo, brightness: b).copyWith(
    primary: dark ? const Color(0xFFA99BFF) : AppColors.indigo,
    onPrimary: dark ? const Color(0xFF1A1060) : Colors.white,
    secondary: dark ? const Color(0xFFD0B0FF) : AppColors.violet,
    surface: dark ? AppColors.cardDark : AppColors.card,
    onSurface: dark ? const Color(0xFFF1F1F8) : AppColors.ink,
    onSurfaceVariant: dark ? const Color(0xFFC4C6DA) : AppColors.inkSoft,
    outlineVariant: dark ? AppColors.lineDark : AppColors.line,
    error: dark ? const Color(0xFFFF8597) : AppColors.danger,
  );
  final base = ThemeData(useMaterial3: true, colorScheme: scheme, fontFamily: _font, brightness: b);
  final text = base.textTheme.copyWith(
    headlineMedium: base.textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.w700, letterSpacing: -0.5),
    headlineSmall: base.textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w700, letterSpacing: -0.3),
    titleLarge: base.textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700, letterSpacing: -0.2),
    titleMedium: base.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600),
    titleSmall: base.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w600),
    bodyMedium: base.textTheme.bodyMedium?.copyWith(height: 1.45),
    bodyLarge: base.textTheme.bodyLarge?.copyWith(height: 1.45),
    labelLarge: base.textTheme.labelLarge?.copyWith(fontWeight: FontWeight.w600, letterSpacing: 0.1),
  );
  final radius = BorderRadius.circular(20);
  return base.copyWith(
    scaffoldBackgroundColor: dark ? AppColors.canvasDark : AppColors.canvas,
    textTheme: text,
    appBarTheme: AppBarTheme(
      backgroundColor: dark ? AppColors.canvasDark : AppColors.canvas,
      foregroundColor: scheme.onSurface,
      elevation: 0, scrolledUnderElevation: 0, centerTitle: false,
      titleTextStyle: text.titleLarge?.copyWith(color: scheme.onSurface, fontFamily: _font),
    ),
    cardTheme: CardThemeData(
      color: scheme.surface, elevation: 0, margin: const EdgeInsets.symmetric(vertical: 6),
      shape: RoundedRectangleBorder(borderRadius: radius, side: BorderSide(color: scheme.outlineVariant)),
      clipBehavior: Clip.antiAlias,
    ),
    listTileTheme: ListTileThemeData(
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
      shape: RoundedRectangleBorder(borderRadius: radius),
      titleTextStyle: text.titleMedium?.copyWith(color: scheme.onSurface, fontFamily: _font),
      subtitleTextStyle: text.bodyMedium?.copyWith(color: scheme.onSurfaceVariant, fontFamily: _font),
      iconColor: scheme.onSurfaceVariant,
    ),
    filledButtonTheme: FilledButtonThemeData(style: FilledButton.styleFrom(
      minimumSize: const Size(64, 52), padding: const EdgeInsets.symmetric(horizontal: 24),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)), textStyle: text.labelLarge,
    )),
    outlinedButtonTheme: OutlinedButtonThemeData(style: OutlinedButton.styleFrom(
      minimumSize: const Size(64, 52), padding: const EdgeInsets.symmetric(horizontal: 22),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      side: BorderSide(color: scheme.outlineVariant, width: 1.4), textStyle: text.labelLarge,
    )),
    textButtonTheme: TextButtonThemeData(style: TextButton.styleFrom(
      minimumSize: const Size(48, 48), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
      textStyle: text.labelLarge,
    )),
    inputDecorationTheme: InputDecorationTheme(
      filled: true, fillColor: dark ? const Color(0xFF1F2238) : Colors.white,
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
      border: OutlineInputBorder(borderRadius: BorderRadius.circular(16), borderSide: BorderSide(color: scheme.outlineVariant)),
      enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(16), borderSide: BorderSide(color: scheme.outlineVariant)),
      focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(16), borderSide: BorderSide(color: scheme.primary, width: 2)),
    ),
    chipTheme: base.chipTheme.copyWith(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      side: BorderSide(color: scheme.outlineVariant), labelStyle: text.labelLarge,
    ),
    dialogTheme: DialogThemeData(
      backgroundColor: scheme.surface, shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(28)),
      titleTextStyle: text.titleLarge?.copyWith(color: scheme.onSurface, fontFamily: _font),
    ),
    snackBarTheme: SnackBarThemeData(
      behavior: SnackBarBehavior.floating, shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
      backgroundColor: dark ? const Color(0xFFE9E8FF) : AppColors.ink,
      contentTextStyle: text.bodyMedium?.copyWith(color: dark ? AppColors.ink : Colors.white, fontFamily: _font),
    ),
    switchTheme: SwitchThemeData(
      thumbColor: WidgetStateProperty.resolveWith((s) => s.contains(WidgetState.selected) ? Colors.white : null),
      trackColor: WidgetStateProperty.resolveWith((s) => s.contains(WidgetState.selected) ? scheme.primary : null),
    ),
    dividerTheme: DividerThemeData(color: scheme.outlineVariant, space: 32),
    progressIndicatorTheme: ProgressIndicatorThemeData(color: scheme.primary, linearTrackColor: scheme.outlineVariant),
    pageTransitionsTheme: const PageTransitionsTheme(builders: {
      TargetPlatform.android: FadeForwardsPageTransitionsBuilder(),
    }),
  );
}
