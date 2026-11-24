# url-v1 (boosted_trees)

Trained 2026-11-23 on 36772 rows; threshold from 8540 validation rows; tested on 11480 rows. Train / validation / test never share a domain.

| model | val AUC | test AUC | test recall | test FPR | precision |
|---|---|---|---|---|---|
| logistic | 0.952 | 0.949 | 0.5654 | 0.0054 | 0.9906 |
| boosted_trees (chosen) | 0.9915 | 0.9899 | 0.8627 | 0.0149 | 0.9833 |

Threshold 0.9479 (false-positive rate <= 1% on the validation domains).

Recall on the test domains at a false-positive rate of exactly <= 1 % (threshold picked on test, for information only): **82.1%** (FPR 0.98%).

Roadmap gate: AUC >= 0.97, recall >= 90 % at FPR <= 1 %.
