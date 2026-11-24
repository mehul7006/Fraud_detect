# Link model (Phase 11)

The model adds a score to the rule-based link check; it can only raise a verdict, never lower one, and is never used
for official brand sites, gov.in / bank.in, or the top-100k popular sites (11.5).

## Rebuild from scratch (about 15 minutes, ~1 GB download on E:)
```bash
python ml/build_dataset.py                    # 11.1 -> ml/data/dataset.tsv (+ manifest.json)
# 11.2 features with the PRODUCTION Java code (no second implementation to drift):
docker run --rm -v "$PWD":/repo:ro -v "$PWD/ml/data":/data -v m2cache:/root/.m2 maven:3.9-eclipse-temurin-21 sh -c \
  'cp -r /repo/backend /w && cd /w && mvn -q -Dtest=FeatureDumpTool -Dsurefire.failIfNoSpecifiedTests=false \
   "-DargLine=-Dml.in=/data/dataset.tsv -Dml.out=/data/features.csv" test'
bash ml/train.sh                              # 11.3 -> backend/src/main/resources/models/url-v1.{onnx,json}
python ml/evaluate.py                         # 11.9 (also runs in every CI build as ModelGateTest)
```
After retraining, copy the test split to `backend/src/test/resources/ml/test-set.tsv` (train.py writes the parity
vectors itself) and run the backend tests. New features = bump `FeatureExtractor.VERSION` and the model name.

## Current model: url-v1 (LightGBM, 428 address features)
See `reports/url-v1.md`. Test domains never seen in training: **AUC 0.990**; at the chosen threshold recall
86.3 % with 1.5 % false positives; at exactly 1 % false positives recall is **82.1 %** - below the roadmap's 90 %.
Most misses are hacked real sites with random-looking paths, which no address-only model can see; the page check
(Phase 10) covers part of that.

## Feedback loop (11.8)
`ADMIN_TOKEN=... bash ml/export_feedback.sh` -> `ml/data/feedback.tsv`; `build_dataset.py` merges it (user reports win).
