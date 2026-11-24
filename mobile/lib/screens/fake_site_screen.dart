import 'package:flutter/material.dart';

import '../core/api_client.dart';
import '../core/native_bridge.dart';
import '../l10n/l10n.dart';
import 'why_panel.dart';

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
    final seen = FakeSite.host(site.url), real = FakeSite.host(site.officialUrl);
    final diff = differingLetters(seen, real);
    final red = Colors.red.shade800;
    return Scaffold(
      backgroundColor: Colors.red.shade50,
      appBar: AppBar(backgroundColor: Colors.red.shade50, title: Text(l.alertDangerous)),
      body: SafeArea(
        child: ListView(padding: const EdgeInsets.all(20), children: [
          Icon(Icons.gpp_bad, color: red, size: 64),
          const SizedBox(height: 12),
          Text(l.fakeSiteTitle(site.brand), key: const Key('fake-title'), textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.headlineSmall?.copyWith(color: red, fontWeight: FontWeight.bold)),
          const SizedBox(height: 20),
          Semantics(
            label: l.fakeSiteDiffLabel(seen, real),
            excludeSemantics: true,
            child: Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(l.fakeSiteThisLink, style: Theme.of(context).textTheme.labelLarge),
                  const SizedBox(height: 4),
                  Text.rich(
                    key: const Key('fake-host'),
                    TextSpan(children: [
                      for (var i = 0; i < seen.length; i++)
                        TextSpan(
                          text: seen[i],
                          style: diff[i]
                              ? TextStyle(color: Colors.white, backgroundColor: red, fontWeight: FontWeight.bold)
                              : const TextStyle(color: Colors.black87),
                        ),
                    ]),
                    style: const TextStyle(fontSize: 22, fontFamily: 'monospace'),
                  ),
                  const Divider(height: 24),
                  Text(l.fakeSiteReal(site.brand), style: Theme.of(context).textTheme.labelLarge),
                  const SizedBox(height: 4),
                  Row(children: [
                    Icon(Icons.verified, color: Colors.green.shade800),
                    const SizedBox(width: 6),
                    Flexible(
                      child: Text(real, key: const Key('real-host'),
                          style: TextStyle(fontSize: 22, fontFamily: 'monospace', color: Colors.green.shade900)),
                    ),
                  ]),
                ]),
              ),
            ),
          ),
          const SizedBox(height: 8),
          if (WhyPanel.from(site.explanation) case final why?) why,
          const SizedBox(height: 8),
          Text(l.fakeSiteWhy, style: Theme.of(context).textTheme.titleMedium),
          for (final r in site.reasons.take(6))
            Padding(
              padding: const EdgeInsets.only(top: 6),
              child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                const Text('•  '),
                Expanded(child: Text(r)),
              ]),
            ),
          const SizedBox(height: 16),
          Card(
            color: Colors.amber.shade50,
            child: Padding(padding: const EdgeInsets.all(12), child: Text(l.fakeSiteAdvice)),
          ),
          const SizedBox(height: 20),
          FilledButton.icon(
            key: const Key('go-real'),
            style: FilledButton.styleFrom(backgroundColor: Colors.green.shade800, minimumSize: const Size.fromHeight(52)),
            icon: const Icon(Icons.open_in_new),
            label: Text(l.fakeSiteGoReal(site.brand)),
            onPressed: () async {
              await NativeBridge.openUrl(site.officialUrl);
              if (context.mounted) Navigator.pop(context);
            },
          ),
          const SizedBox(height: 10),
          OutlinedButton.icon(
            key: const Key('report'),
            style: OutlinedButton.styleFrom(minimumSize: const Size.fromHeight(48)),
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
