"""Generate jira/jira-import.csv from docs/03-ROADMAP.md (+ small steps from docs/tasks/phase-*.md).
Usage: python jira/gen_jira.py docs/03-ROADMAP.md jira/jira-import.csv
Step line format in docs/tasks/*.md:  - [ ] N.M.k | step | output | 2h
"""
import re, csv, sys, glob, os
src, out = sys.argv[1], sys.argv[2]
phase_names = {}; rows = []; cur = None
for line in open(src, encoding='utf-8'):
    h = re.match(r'^## Phase (\d+) – (.+?) \(', line)
    if h: cur = h.group(1); phase_names[cur] = h.group(2); continue
    m = re.match(r'^\| (\d+\.\d+) \|', line)
    if not m or cur is None: continue
    c = [x.strip() for x in line.strip().strip('|').split('|')]
    pid = c[0]
    if c[1] in ('BE','FE'): track, summ = c[1], c[2]
    else: track, summ = 'BE', c[1]
    rows.append((pid, cur, track, summ, c[-2], c[-1]))
sprint = {'0':1,'1':2,'2':2,'3':3,'4':4,'5':5,'6':6,'7':7,'8':8,'9':8}
for _p in range(10,40): sprint[str(_p)] = _p-1
high = {'1','2','3','7','10','11','12','13','14','16'}

# small steps
steps = []  # (step_id, parent_id, phase, text, output, est, done)
tasks_dir = os.path.join(os.path.dirname(os.path.abspath(src)), 'tasks')
for f in sorted(glob.glob(os.path.join(tasks_dir, 'phase-*.md'))):
    for line in open(f, encoding='utf-8'):
        m = re.match(r'^- \[( |x)\] (\d+\.\d+\.(?:\d+|v)) \| (.+?) \| (.+?) \| (\d+[hd])\s*$', line)
        if not m: continue
        sid = m.group(2); parent = '.'.join(sid.split('.')[:2]); ph = sid.split('.')[0]
        steps.append((sid, parent, ph, m.group(3), m.group(4), m.group(5), m.group(1) == 'x'))

with open(out,'w',newline='',encoding='utf-8') as f:
    w = csv.writer(f)
    w.writerow(['Issue Id','Issue Type','Summary','Description','Priority','Epic Link','Parent Id','Sprint','Story Points','Original Estimate','Labels','Status'])
    for p,n in phase_names.items():
        w.writerow([f'E{p}','Epic',f'Phase {p} - {n}','','High','','',f'Sprint {sprint[p]}','','','',''])
    for pid,p,t,s,done,st in rows:
        status = 'Done' if st.startswith('✅') else 'In Progress' if st.startswith('🟡') else 'To Do'
        w.writerow([pid,'Story',f'[{t} {pid}] {s}',f'Done when: {done}','High' if p in high else 'Medium',f'E{p}','',f'Sprint {sprint[p]}',3,'',t.lower(),status])
    for sid,parent,ph,text,outp,est,done in steps:
        w.writerow([sid,'Sub-task',f'[{sid}] {text}',f'Output: {outp}','High' if ph in high else 'Medium','',parent,f'Sprint {sprint[ph]}','',est,'', 'Done' if done else 'To Do'])
print(len(phase_names),'epics',len(rows),'stories',len(steps),'sub-tasks')
