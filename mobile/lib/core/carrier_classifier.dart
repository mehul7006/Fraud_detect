/// Reads a carrier's answer to a call-forwarding USSD query (*#21# ...) and decides ACTIVE / INACTIVE / UNKNOWN.
/// Mirrors backend CarrierClassifier: rules run by ascending priority, first match wins.
/// Rules come from GET /config/carriers (so wording fixes need no app release); [builtInRules] is the offline fallback.
enum CarrierState { active, inactive, unknown }

class CarrierRule {
  final String kind; // ACTIVE | INACTIVE
  final String regex;
  final int priority;
  const CarrierRule(this.kind, this.regex, this.priority);

  factory CarrierRule.fromJson(Map<String, dynamic> j) =>
      CarrierRule(j['kind'] as String, j['regex'] as String, (j['priority'] as num).toInt());
}

class CarrierConfig {
  final String version;
  final List<CarrierRule> rules;
  const CarrierConfig(this.version, this.rules);

  factory CarrierConfig.fromJson(Map<String, dynamic> j) => CarrierConfig(
        j['version'] as String,
        (j['rules'] as List).map((r) => CarrierRule.fromJson(Map<String, dynamic>.from(r as Map))).toList(),
      );
}

class _Compiled {
  final CarrierState state;
  final RegExp pattern;
  final int priority;
  _Compiled(this.state, this.pattern, this.priority);
}

class CarrierClassifier {
  static const int maxText = 500; // carriers answer in a few lines; cap protects against regex abuse
  static const int maxRegex = 200;

  /// Same as the backend seed migration (V2__phase3_forwarding.sql). Generic wording, NOT real carrier captures.
  static const List<CarrierRule> builtInRules = [
    CarrierRule('INACTIVE', r'(?i)not\s+(forwarded|active|activated|registered|provisioned)', 10),
    CarrierRule('INACTIVE', r'(?i)\b(inactive|deactivated|disabled|cancelled|erased)\b', 11),
    CarrierRule('INACTIVE', r'(?i)(forwarding|diversion|divert)\s+(is\s+)?(off|not set)', 12),
    CarrierRule('ACTIVE', r'(?i)forwarded\s+to\s+\+?\d[\d\s-]{6,}', 20),
    CarrierRule('ACTIVE', r'(?i)\b(forward(ing|ed)?|divert(ed)?)\b.*\b(active|enabled|registered)\b', 21),
    CarrierRule('ACTIVE', r'\+?\d{10,15}', 30),
  ];

  final List<_Compiled> _rules;

  CarrierClassifier(Iterable<CarrierRule> rules) : _rules = _compile(rules);
  factory CarrierClassifier.builtIn() => CarrierClassifier(builtInRules);

  int get ruleCount => _rules.length;

  static List<_Compiled> _compile(Iterable<CarrierRule> source) {
    final out = <_Compiled>[];
    for (final r in source) {
      if (r.regex.length > maxRegex) continue;
      final state = r.kind.toUpperCase() == 'ACTIVE'
          ? CarrierState.active
          : r.kind.toUpperCase() == 'INACTIVE'
              ? CarrierState.inactive
              : null;
      if (state == null) continue;
      // Java rules use a leading inline (?i); Dart has no inline flags, so translate it.
      var pattern = r.regex;
      var caseSensitive = true;
      if (pattern.startsWith('(?i)')) {
        pattern = pattern.substring(4);
        caseSensitive = false;
      }
      try {
        out.add(_Compiled(state, RegExp(pattern, caseSensitive: caseSensitive), r.priority));
      } on FormatException {
        continue; // a broken server rule must never break the app
      }
    }
    out.sort((a, b) => a.priority.compareTo(b.priority));
    return out;
  }

  CarrierState classify(String? response) {
    if (response == null || response.trim().isEmpty) return CarrierState.unknown;
    final text = response.length > maxText ? response.substring(0, maxText) : response;
    for (final c in _rules) {
      if (c.pattern.hasMatch(text)) return c.state;
    }
    return CarrierState.unknown;
  }
}
