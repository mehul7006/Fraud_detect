"""Turns the app's 'forwarding <code> -> "<reply>"' log lines into JSON rows (phone numbers masked like the app).
parse_capture.py LOG API LANG STATE FLAG TILE"""
import json, re, sys

log, api, lang, state, flag, tile = sys.argv[1:7]
text = open(log, encoding='utf-8', errors='replace').read().replace('\r', '')


def mask(s):
    def one(m):
        return re.sub(r'\d', '9', m.group(0)) if len(re.sub(r'\D', '', m.group(0))) >= 7 else m.group(0)
    return re.sub(r'\+?\d[\d\s-]{5,}\d', one, s)


for m in re.finditer(r'forwarding (\*#\d+#) -> (?:"(.*?)"(?=\n|\Z)|(\S+))', text, re.S):
    reply = m.group(2) if m.group(2) is not None else m.group(3)
    print(json.dumps({'api': int(api), 'lang': lang, 'state': state, 'code': m.group(1), 'text': mask(reply),
                      'androidFlag': flag, 'tile': tile.strip()}, ensure_ascii=False))
