# Phase 11 - AI classification and explanations: results (2026-11-23)

## Model url-v1 (LightGBM, 428 address features, ONNX)
Data: 56,792 links (half phishing, half legitimate), split by domain. Test = 11,480 links of domains never seen in
training. Same numbers from Python (ml/evaluate.py) and Java (ModelGateTest, every CI build):

| | Result | Roadmap target |
|---|---|---|
| AUC | **0.990** | >= 0.97 ✅ |
| Recall at the shipped threshold | 86.3 % (false positives 1.49 %) | |
| Recall at exactly <= 1 % false positives | **82.1 %** | >= 90 % ❌ |
| Logistic baseline AUC | 0.949 | |

Remaining misses are mostly hacked real websites with random-looking paths (`magv.mx/entcustroptrancfernow`);
the address alone cannot reveal them. In the product the model is one layer: rules + look-alike engine + page check
(Phase 10) + Safe Browsing. On the 10.12 corpus the whole check keeps 100 % precision with the model switched on.

## Policy (11.5)
Model can only raise; official brand / gov.in / bank.in sites never flagged; no model points for the top-100k.
30 golden links stable (GoldenVerdictsTest).

## Explanations (11.6 / 11.7)
English + Hindi templates for all 35 reason codes (test fails on a missing one). Claude (`claude-opus-5-5`) words
them in the background when `ANTHROPIC_API_KEY` is set; it only receives verdict + reason codes + brand.
Prompt-injection test: a hostile page title never reaches the prompt. **Not yet run live** (no key).

## Tests
Backend: 260 unit tests green; integration tests Phase10IT, AdminFeedbackIT, ApiFlowIT, ApiContractIT, Phase7IT
(35) green. App: 292 tests green (incl. "Why is this risky?" EN/HI in alert, fake-site screen, threat history).
