import 'package:flutter/material.dart';

import '../core/api_client.dart';
import '../core/native_bridge.dart';
import '../l10n/l10n.dart';
import 'why_panel.dart';
import '../ui/components.dart';
import '../ui/theme.dart';

/// 10.11: what the server said about a link that imitates a brand (scan response fields).
class FakeSite {
  final String url, brand, officialUrl;
  final List<String> reasons;
  final Map<String, dynamic>? explanation; // 11.10
  const FakeSite({required this.url, required this.brand, required this.officialUrl, required this.reasons, this.explanation});

  /// From a /scan/url answer; null when the link does not imitate a known brand.
  static FakeSite? fromScan(String url, Map<String, dynamic> r) {
    final brand = r['impersonatedBrand'], official = r['officialUrl'];
    if (brand is! String || official is! String || !official.startsWith('https://')) return null;
    return FakeSite(url: url, brand: brand, officialUrl: official, reasons: ((r['reasons'] as List?) ?? []).cast<String>(),
        explanation: r['explanation'] is Map ? (r['explanation'] as Map).cast<String, dynamic>() : null);
  }

  /// Host only, without "www.", e.g. "fleepkart.com".
  static String host(String url) {
    final h = (Uri.tryParse(url.contains('://') ? url : 'http://$url')?.host ?? url).toLowerCase();
    return h.startsWith('www.') ? h.substring(4) : h;
  }
}

/// Which characters of [seen] are not part of [real] (longest common subsequence), so "fleepkart" vs "flipkart"
/// marks the two "e". Inputs are short host names.
List<bool> differingLetters(String seen, String real) {
  final n = seen.length, m = real.length;
  final dp = List.generate(n + 1, (_) => List.filled(m + 1, 0));
  for (var i = n - 1; i >= 0; i--) {
    for (var j = m - 1; j >= 0; j--) {
      dp[i][j] = seen[i] == real[j] ? dp[i + 1][j + 1] + 1 : (dp[i + 1][j] > dp[i][j + 1] ? dp[i + 1][j] : dp[i][j + 1]);
    }
  }
  final diff = List.filled(n, true);
  var i = 0, j = 0;
  while (i < n && j < m) {
    if (seen[i] == real[j]) {
      diff[i] = false;
      i++;
      j++;
    } else if (dp[i + 1][j] >= dp[i][j + 1]) {
      i++;
    } else {
      j++;
    }
  }
  return diff;
}

/// Full-screen warning: "This looks like Flipkart but is NOT Flipkart", the address with the different letters
/// highlighted next to the real one, why, and three ways out: go to the real site, report, close.
class FakeSiteScreen extends StatelessWidget {
  final FakeSite site;
  final ApiClient? api;
  const FakeSiteScreen({super.key, required this.site, this.api});

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    final t = Theme.of(context);
    final seen = FakeSite.host(site.url), real = FakeSite.host(site.officialUrl);
    final diff = differingLetters(seen, real);
    final red = Tone.danger.fg(context);
    final green = Tone.safe.fg(context);
    return Scaffold(
      appBar: AppBar(title: Text(l.alertDangerous)),
      body: SafeArea(
        child: ListView(padding: const EdgeInsets.fromLTRB(16, 4, 16, 24), children: [
          // bold danger hero
          Container(
            padding: const EdgeInsets.fromLTRB(20, 24, 20, 24),
            decoration: BoxDecoration(
              gradient: AppColors.dangerGradient,
              borderRadius: BorderRadius.circular(28),
              boxShadow: [BoxShadow(color: AppColors.danger.withValues(alpha: 0.35), blurRadius: 24, offset: const Offset(0, 10))],
            ),
            child: Column(children: [
              Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(color: Colors.white.withValues(alpha: 0.16), shape: BoxShape.circle),
                child: const Icon(Icons.gpp_bad_rounded, color: Colors.white, size: 52),
              ),
              const SizedBox(height: 14),
              Text(l.fakeSiteTitle(site.brand), key: const Key('fake-title'), textAlign: TextAlign.center,
                  style: t.textTheme.headlineSmall?.copyWith(color: Colors.white, fontWeight: FontWeight.w700, height: 1.25)),
            ]),
          ),
          const SizedBox(height: 16),
          Semantics(
            label: l.fakeSiteDiffLabel(seen, real),
            excludeSemantics: true,
            child: Card(
              child: Padding(
                padding: const EdgeInsets.all(18),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Pill(l.fakeSiteThisLink, tone: Tone.danger, icon: Icons.link_off_rounded),
                  const SizedBox(height: 10),
                  Text.rich(
                    key: const Key('fake-host'),
                    TextSpan(children: [
                      for (var i = 0; i < seen.length; i++)
                        TextSpan(
                          text: seen[i],
                          style: diff[i]
                              ? const TextStyle(color: Colors.white, backgroundColor: AppColors.danger, fontWeight: FontWeight.bold)
                              : TextStyle(color: t.colorScheme.onSurface),
                        ),
                    ]),
                    style: const TextStyle(fontSize: 22, fontFamily: 'monospace'),
                  ),
                  const Divider(height: 28),
                  Pill(l.fakeSiteReal(site.brand), tone: Tone.safe, icon: Icons.verified_rounded),
                  const SizedBox(height: 10),
                  Row(children: [
                    Icon(Icons.verified_rounded, color: green),
                    const SizedBox(width: 8),
                    Flexible(
                      child: Text(real, key: const Key('real-host'),
                          style: TextStyle(fontSize: 22, fontFamily: 'monospace', color: green, fontWeight: FontWeight.w600)),
                    ),
                  ]),
                ]),
              ),
            ),
          ),
          if (WhyPanel.from(site.explanation) case final why?) why,
          SectionTitle(l.fakeSiteWhy),
          Card(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 14),
              child: Column(children: [
                for (final r in site.reasons.take(6))
                  Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Padding(padding: const EdgeInsets.only(top: 2), child: Icon(Icons.warning_amber_rounded, size: 20, color: red)),
                      const SizedBox(width: 10),
                      Expanded(child: Text(r)),
                    ]),
                  ),
              ]),
            ),
          ),
          const SizedBox(height: 8),
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(color: Tone.warn.bg(context), borderRadius: BorderRadius.circular(20)),
            child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Icon(Icons.tips_and_updates_rounded, color: Tone.warn.fg(context)),
              const SizedBox(width: 12),
              Expanded(child: Text(l.fakeSiteAdvice, style: t.textTheme.bodyMedium?.copyWith(color: t.colorScheme.onSurface, fontWeight: FontWeight.w500))),
            ]),
          ),
          const SizedBox(height: 16),
          FilledButton.icon(
            key: const Key('go-real'),
            style: FilledButton.styleFrom(backgroundColor: AppColors.safe, foregroundColor: Colors.white, minimumSize: const Size.fromHeight(56)),
            icon: const Icon(Icons.open_in_new_rounded),
            label: Text(l.fakeSiteGoReal(site.brand)),
            onPressed: () async {
              await NativeBridge.openUrl(site.officialUrl);
              if (context.mounted) Navigator.pop(context);
            },
          ),
          const SizedBox(height: 10),
          OutlinedButton.icon(
            key: const Key('report'),
            style: OutlinedButton.styleFrom(minimumSize: const Size.fromHeight(52)),
            icon: const Icon(Icons.flag_outlined),
            label: Text(l.fakeSiteReport),
            onPressed: () => _report(context),
          ),
          const SizedBox(height: 6),
          TextButton(
            key: const Key('close'),
            style: TextButton.styleFrom(minimumSize: const Size.fromHeight(48)),
            onPressed: () => Navigator.pop(context),
            child: Text(l.close),
          ),
        ]),
      ),
    );
  }

  Future<void> _report(BuildContext context) async {
    final l = context.l10n;
    final choice = await showModalBottomSheet<String>(
      context: context,
      builder: (c) => SafeArea(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          ListTile(
            key: const Key('report-cyber'),
            leading: const Icon(Icons.local_police_outlined),
            title: Text(l.fakeSiteReportCyber),
            onTap: () => Navigator.pop(c, 'cyber'),
          ),
          ListTile(
            key: const Key('report-wrong'),
            leading: const Icon(Icons.thumb_down_outlined),
            title: Text(l.fakeSiteWrong),
            onTap: () => Navigator.pop(c, 'wrong'),
          ),
        ]),
      ),
    );
    if (choice == 'cyber') {
      await NativeBridge.openUrl('https://cybercrime.gov.in/');
    } else if (choice == 'wrong') {
      try {
        await (api ?? ApiClient.instance).reportUrl(site.url, 'FALSE_POSITIVE', 'fake-site screen: not ${site.brand}');
      } catch (_) {
        // offline: nothing lost for the user, the thank-you is still shown
      }
      if (context.mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(l.fakeSiteThanks)));
    }
  }
}
