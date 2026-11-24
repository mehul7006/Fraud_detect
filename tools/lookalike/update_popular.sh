#!/usr/bin/env bash
# 10.2 / 10.4: refresh the two public data files the look-alike engine ships with. Run monthly (or before a release),
# then run the backend tests: LookalikeMatcherTest must stay green.
#   - Public Suffix List (Mozilla, MPL-2.0)      -> backend/src/main/resources/psl/public_suffix_list.dat
#   - Tranco top 100k popular sites (tranco-list.eu) -> backend/src/main/resources/seed/popular-domains.txt
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$ROOT/_devtools/phase10"; mkdir -p "$TMP"
curl -sSfL -o "$TMP/public_suffix_list.dat" https://publicsuffix.org/list/public_suffix_list.dat
grep -q "===END ICANN DOMAINS===" "$TMP/public_suffix_list.dat" || { echo "PSL download looks wrong"; exit 1; }
curl -sSfL -o "$TMP/tranco.zip" https://tranco-list.eu/top-1m.csv.zip
(cd "$TMP" && unzip -o -q tranco.zip)
[ "$(wc -l < "$TMP/top-1m.csv")" -gt 500000 ] || { echo "Tranco download looks wrong"; exit 1; }
cp "$TMP/public_suffix_list.dat" "$ROOT/backend/src/main/resources/psl/public_suffix_list.dat"
{ echo "# 10.4 Popular-domain allowlist: Tranco top 100,000 (https://tranco-list.eu, list downloaded $(date +%F))."
  echo "# Registrable domains never reported as brand look-alikes. Regenerate: tools/lookalike/update_popular.sh"
  head -100000 "$TMP/top-1m.csv" | cut -d, -f2 | tr -d '\r'; } > "$ROOT/backend/src/main/resources/seed/popular-domains.txt"
echo "updated: PSL $(grep -vc '^//' "$TMP/public_suffix_list.dat") lines, popular 100000 domains"
