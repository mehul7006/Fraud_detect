# Small-step task breakdown

Every unfinished roadmap task (`docs/03-ROADMAP.md`) is split into small steps of **1-4 hours**, one file per phase:
`phase-00.md`, `phase-02.md` ... `phase-16.md` (Phase 1 is complete, so it has no file).

**Step format:** `- [ ] N.M.k | what to do | output / proof | estimate`
The last step of every task, `N.M.v`, is the **verify gate**: it shows the roadmap's "Done when" line with real evidence. Only after it passes is the task marked ✅ in the roadmap.

**How to work:** pick one task, do its steps in order, tick `[x]` as you go, run the verify step, then update the roadmap status and regenerate Jira:
`python jira/gen_jira.py docs/03-ROADMAP.md jira/jira-import.csv`
(steps become Jira Sub-tasks under their task; `Original Estimate` carries the hours).

| Phase | Tasks | Steps | Hours |
|---|---|---|---|
| 0 Setup | 4 | 28 | 42 |
| 2 Mobile base | 9 | 64 | 98 |
| 3 Call/SMS forwarding | 8 | 56 | 96 |
| 4 Links | 10 | 77 | 142 |
| 5 Files | 8 | 52 | 86 |
| 6 Ad abuse | 8 | 54 | 100 |
| 7 Clone/takeover | 11 | 83 | 152 |
| 8 Hardening | 13 | 92 | 192 |
| 9 Release | 7 | 54 | 116 |
| 10 Look-alike websites | 12 | 92 | 204 |
| 11 AI classification | 10 | 76 | 169 |
| 12 Local VPN | 11 | 84 | 204 |
| 13 Leaky apps | 14 | 93 | 199 |
| 14 Fake messages | 10 | 64 | 145 |
| 15 Public-safety extras | 10 | 70 | 152 |
| 16 Call guard | 14 | 102 | 214 |
| **Total** | **159** | **1,141** | **2,311** |

Hours are first estimates for one developer; revise them after the first two or three tasks.

**Known notes from the breakdown**
- Flyway migration numbers (V2 brands, V3 numbers, plus new ones in 3.2, 4.3, 4.4) must be assigned in the order they are actually built: use the next free version number.
- The folder is not a git repository yet; step 0.3.1 starts with `git init`.
- No emulator exists in the Docker-only setup, so device-dependent steps are marked as real-device tests.
- Facts that need checking before use (Safe Browsing terms, carrier steps, Android API levels, TRAI number series, dataset licences) are written as explicit "verify" steps.
