"""uiautomator dump helper.  ui.py has|tap|near|texts PATTERN [ANCHOR] FILE"""
import re, sys
import xml.etree.ElementTree as ET

mode = sys.argv[1]
f = sys.argv[-1]
raw = open(f, encoding='utf-8', errors='replace').read()
if '<?xml' in raw:
    raw = raw[raw.index('<?xml'):]
raw = raw[:raw.rfind('>') + 1]
try:
    root = ET.fromstring(raw)
except ET.ParseError:
    sys.exit(2)

nodes = []
for n in root.iter('node'):
    t = (n.get('text') or '') + ' | ' + (n.get('content-desc') or '') + ' | ' + (n.get('resource-id') or '')         + ' | ' + (n.get('class') or '') + (' PASSWORD' if n.get('password') == 'true' else '')
    x1, y1, x2, y2 = map(int, re.findall(r'-?\d+', n.get('bounds', '[0,0][0,0]')))
    nodes.append((t, (x1 + x2) // 2, (y1 + y2) // 2, n))

def match(p):
    return [x for x in nodes if re.search(p, x[0], re.S | re.I)]

if mode == 'texts':
    for t, x, y, n in nodes:
        if t.strip(' |'):
            print(repr(t), x, y, n.get('checked'))
elif mode == 'has':
    sys.exit(0 if match(sys.argv[2]) else 1)
elif mode == 'tap':
    m = match(sys.argv[2])
    if not m:
        sys.exit(1)
    print(m[0][1], m[0][2])
elif mode == 'near':  # PATTERN below ANCHOR, nearest
    a = match(sys.argv[3])
    if not a:
        sys.exit(1)
    ay = a[0][2]
    m = sorted([x for x in match(sys.argv[2]) if x[2] > ay], key=lambda x: x[2] - ay)
    if not m:
        sys.exit(1)
    print(m[0][1], m[0][2])
elif mode == 'checked':  # is the first switch checked
    m = [x for x in nodes if x[3].get('checkable') == 'true']
    sys.exit(0 if m and m[0][3].get('checked') == 'true' else 1)
