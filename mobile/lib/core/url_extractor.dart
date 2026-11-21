// ignore_for_file: prefer_interpolation_to_compose_strings  (regex is assembled from named parts on purpose)
/// Dart port of android-native/kotlin/UrlExtractor.kt (used for text shared into the app, 4.8).
/// Both are tested with the same 20 samples.
class UrlExtractor {
  static const _tlds = 'com|in|xyz|top|click|me|ly|co|info|net|org|apk|online|site|shop|icu|live|club|cc|tk|ml|gq|cf|'
      'buzz|link|app|io|store|vip|pw|ru|cn|biz|us|uk|zip|cfd|monster|rest|fun|work';
  static const _stop = r'\s<>"' "'" r'ऀ-ॿ';
  static final _re = RegExp(
    r'(https?://[^' + _stop + r']+'
    r'|\bwww\.[^' + _stop + r']+'
    r'|(?<![@\w.-])(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+(?:' + _tlds + r')\b(?![@\w-])(?::\d+)?(?:/[^' + _stop + r']*)?)',
    caseSensitive: false,
  );
  static const _trailing = '.,;:!?)]}\'"';

  static List<String> extract(String text) {
    final out = <String>[];
    for (final m in _re.allMatches(text)) {
      var s = m.group(0)!;
      while (s.isNotEmpty && _trailing.contains(s[s.length - 1])) {
        s = s.substring(0, s.length - 1);
      }
      s = s.replaceFirst(RegExp(r'^[(\[{]+'), '');
      if (s.length >= 4 && !out.contains(s)) out.add(s);
    }
    return out;
  }

  /// Host of a link in lower case, or null when it cannot be read.
  static String? hostOf(String url) {
    try {
      final u = Uri.parse(url.contains('://') ? url : 'http://$url');
      return u.host.isEmpty ? null : u.host.toLowerCase();
    } catch (_) {
      return null;
    }
  }
}
