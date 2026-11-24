import 'api_client.dart';
import 'message_rules.dart';
import 'native_bridge.dart';
import 'url_extractor.dart';

/// 14.4 / 14.8: one verdict for a message = on-device rules (why) + on-device model (new wording) + every link in it
/// through the normal link check (Phases 10-11). The text itself never leaves the phone here; the cloud check is a
/// separate, opt-in call (14.6 / 14.7).
class LinkResult {
  final String url, verdict;
  final String? brand, officialUrl;
  const LinkResult(this.url, this.verdict, this.brand, this.officialUrl);
}

class MessageVerdict {
  final String verdict; // SAFE / SUSPICIOUS / MALICIOUS
  final int score;
  final String? category; // model's scam type when it is confident
  final List<RuleHit> hits;
  final List<LinkResult> links;
  final bool cloud; // includes the opt-in cloud check
  final Map<String, dynamic>? cloudExplanation;
  const MessageVerdict(this.verdict, this.score, this.category, this.hits, this.links, {this.cloud = false, this.cloudExplanation});
}

class MessageCheck {
  final ApiClient api;
  MessageCheck(this.api);

  static String verdictOf(int score) => score >= 70 ? 'MALICIOUS' : score >= 40 ? 'SUSPICIOUS' : 'SAFE';

  Future<MessageVerdict> local(String text, {String? sender}) async {
    final rules = MessageRules.check(text, sender: sender);
    double modelScam = 0;
    String? category;
    try {
      final m = await NativeBridge.classifyMessage(text);
      modelScam = (m['scam'] as num).toDouble();
      if ((m['categoryProbability'] as num) >= 0.5 && modelScam >= 0.5) category = m['category'] as String;
    } catch (_) {/* model missing (tests / very old phone): rules only */}
    final links = <LinkResult>[];
    for (final u in UrlExtractor.extract(text).take(3)) {
      try {
        final r = await api.scanUrl(u, 'message-check');
        links.add(LinkResult(u, r['verdict'] as String, r['impersonatedBrand'] as String?, r['officialUrl'] as String?));
      } catch (_) {
        links.add(LinkResult(u, 'UNKNOWN', null, null));
      }
    }
    var score = [rules.score, (modelScam * 100).round()].reduce((a, b) => a > b ? a : b);
    if (links.any((l) => l.verdict == 'MALICIOUS')) score = score < 90 ? 90 : score;
    // a confident scam model without any rule hit stays "suspicious" (it cannot explain itself)
    if (rules.hits.isEmpty && score >= 70 && !links.any((l) => l.verdict == 'MALICIOUS')) score = 60;
    return MessageVerdict(verdictOf(score), score, category, rules.hits, links);
  }

  /// 14.6 / 14.7: only after the user's explicit tap; the server keeps nothing.
  Future<MessageVerdict> withCloud(String text, MessageVerdict local) async {
    final r = await api.scanMessage(text);
    final cloudScore = (r['score'] as num).toInt();
    final score = cloudScore > local.score ? cloudScore : local.score;
    final cat = (r['category'] as String?) == 'GENUINE' ? local.category : (r['category'] as String? ?? local.category);
    return MessageVerdict(verdictOf(score), score, cat, local.hits, local.links, cloud: true,
        cloudExplanation: r['explanation'] is Map ? (r['explanation'] as Map).cast<String, dynamic>() : null);
  }
}
