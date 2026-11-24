"""11.3 train the link model and export it to ONNX (production runs it with ONNX Runtime Java - no Python).

One command (Docker, nothing installed on the PC):
    bash ml/train.sh
Inputs : ml/data/features.csv  (written by the Java FeatureExtractor - FeatureDumpTool; same code as production)
Outputs: backend/src/main/resources/models/url-v1.onnx + url-v1.json (feature names, threshold, metrics)
         ml/reports/url-v1.md, backend/src/test/resources/ml/feature-vectors.tsv (Java parity test)
Models : logistic regression (baseline) and gradient-boosted trees; the one with the better validation AUC is kept.
Threshold: highest recall with false-positive rate <= 1 % on the validation domains (never the test domains).
"""
import csv
import datetime
import hashlib
import json
import os
import sys

import numpy as np
import onnx
import onnxruntime as ort
from skl2onnx import to_onnx
import lightgbm as lgb
from onnxmltools import convert_lightgbm
from onnxmltools.convert.common.data_types import FloatTensorType
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import roc_auc_score, roc_curve
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
VERSION = os.environ.get("MODEL_VERSION", "url-v1")
FPR_TARGET = 0.01
SEED = 20261123


def load():
    rows = list(csv.reader(open(os.path.join(ROOT, "ml", "data", "features.csv"), encoding="utf-8")))
    names = rows[0][2:]
    y = np.array([int(r[0]) for r in rows[1:]])
    split = np.array([r[1] for r in rows[1:]])
    X = np.array([[float(v) for v in r[2:]] for r in rows[1:]], dtype=np.float32)
    return names, X, y, split


def recall_at_fpr(y, p, fpr_target):
    fpr, tpr, thr = roc_curve(y, p)
    ok = fpr <= fpr_target
    i = np.argmax(tpr * ok)
    return float(tpr[i]), float(fpr[i]), float(thr[i])


def metrics(y, p, thr):
    pred = p >= thr
    tp = int(((pred == 1) & (y == 1)).sum()); fp = int(((pred == 1) & (y == 0)).sum())
    fn = int(((pred == 0) & (y == 1)).sum()); tn = int(((pred == 0) & (y == 0)).sum())
    return {"auc": round(float(roc_auc_score(y, p)), 4), "recall": round(tp / max(1, tp + fn), 4),
            "fpr": round(fp / max(1, fp + tn), 4), "precision": round(tp / max(1, tp + fp), 4),
            "tp": tp, "fp": fp, "fn": fn, "tn": tn}


def main():
    names, X, y, split = load()
    fit, val, te = split == "train", split == "val", split == "test"   # by domain: no domain in two parts
    models = {
        "logistic": make_pipeline(StandardScaler(), LogisticRegression(max_iter=2000, C=1.0)),
        "boosted_trees": lgb.LGBMClassifier(n_estimators=900, learning_rate=0.04, num_leaves=63, min_child_samples=10,
                                            subsample=0.8, subsample_freq=1, colsample_bytree=0.5, reg_lambda=1.0,
                                            random_state=SEED, n_jobs=2, verbose=-1),
    }
    results = {}
    for name, m in models.items():
        m.fit(X[fit], y[fit])
        pv = m.predict_proba(X[val])[:, 1]
        results[name] = {"val_auc": round(float(roc_auc_score(y[val], pv)), 4)}
        print(name, results[name], file=sys.stderr)
    best = max(results, key=lambda k: results[k]["val_auc"])
    model = models[best]
    pv = model.predict_proba(X[val])[:, 1]
    _, _, thr = recall_at_fpr(y[val], pv, FPR_TARGET)
    # final model = the one trained on TRAIN only, so the validation threshold stays valid for it
    pt = model.predict_proba(X[te])[:, 1]
    test = metrics(y[te], pt, thr)
    for name, m in models.items():
        if name != best:
            results[name]["test"] = metrics(y[te], m.predict_proba(X[te])[:, 1], thr)
    results[best]["test"] = test
    print("best", best, "threshold", round(thr, 4), test, file=sys.stderr)

    if best == "boosted_trees":
        onx = convert_lightgbm(model, initial_types=[("features", FloatTensorType([None, X.shape[1]]))],
                               zipmap=False, target_opset=15)
    else:
        onx = to_onnx(model, X[:1], target_opset={"": 15, "ai.onnx.ml": 3}, options={id(model.steps[-1][1]): {"zipmap": False}})
    out_dir = os.path.join(ROOT, "backend", "src", "main", "resources", "models")
    os.makedirs(out_dir, exist_ok=True)
    onnx_path = os.path.join(out_dir, VERSION + ".onnx")
    onnx.save_model(onx, onnx_path)
    sess = ort.InferenceSession(onnx_path, providers=["CPUExecutionProvider"])
    outs = sess.run(None, {sess.get_inputs()[0].name: X[te]})
    prob = [o for o in outs if getattr(o, "ndim", 0) == 2][0][:, 1]
    diff = np.abs(prob - pt)
    agree = float(((prob >= thr) == (pt >= thr)).mean())
    print(f"onnx vs python: max diff {diff.max():.2e}, verdict agreement {agree:.5f}", file=sys.stderr)
    # ONNX trees compare in float32 (LightGBM in float64): tiny score differences, verdicts must agree
    assert diff.max() < 1e-2 and agree >= 0.999, "ONNX model differs from the trained model"
    urls_all = [l.rstrip("\n").split("\t", 2)[2] for l in open(os.path.join(ROOT, "ml", "data", "dataset.tsv"), encoding="utf-8")]
    te_idx = np.where(te)[0]
    missed = sorted([(pt[k], urls_all[i]) for k, i in enumerate(te_idx) if y[i] == 1 and pt[k] < thr])
    fps = sorted([(-pt[k], urls_all[i]) for k, i in enumerate(te_idx) if y[i] == 0 and pt[k] >= thr])
    os.makedirs(os.path.join(ROOT, "ml", "reports"), exist_ok=True)
    with open(os.path.join(ROOT, "ml", "reports", VERSION + "-errors.txt"), "w", encoding="utf-8") as f:
        f.write("# missed phishing (lowest score first)\n" + "\n".join(f"{p_:.3f} {u}" for p_, u in missed))
        f.write("\n\n# false positives\n" + "\n".join(f"{-p_:.3f} {u}" for p_, u in fps) + "\n")
    meta = {
        "version": VERSION, "algorithm": best, "feature_version": 2, "features": names,
        "input": sess.get_inputs()[0].name, "threshold": round(float(thr), 6), "fpr_target": FPR_TARGET,
        "trained": datetime.date.today().isoformat(), "test": test, "candidates": results,
        "data_manifest_sha256": hashlib.sha256(open(os.path.join(ROOT, "ml", "data", "manifest.json"), "rb").read()).hexdigest(),
        "onnx_sha256": hashlib.sha256(open(onnx_path, "rb").read()).hexdigest(),
    }
    json.dump(meta, open(os.path.join(out_dir, VERSION + ".json"), "w"), indent=2)

    # parity vectors for the Java tests: 100 test URLs with their features and the model's probability
    urls = [l.rstrip("\n").split("\t", 2) for l in open(os.path.join(ROOT, "ml", "data", "dataset.tsv"), encoding="utf-8")]
    idx = [i for i in range(len(y)) if te[i]]
    pick = sorted(np.random.default_rng(SEED).choice(idx, 100, replace=False).tolist())
    vec_dir = os.path.join(ROOT, "backend", "src", "test", "resources", "ml")
    os.makedirs(vec_dir, exist_ok=True)
    with open(os.path.join(vec_dir, "feature-vectors.tsv"), "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, delimiter="\t", lineterminator="\n")
        w.writerow(["url", "probability"] + names)
        for i in pick:
            w.writerow([urls[i][2], f"{prob[idx.index(i)]:.6f}"] + [f"{v:g}" for v in X[i]])
    os.makedirs(os.path.join(ROOT, "ml", "reports"), exist_ok=True)
    with open(os.path.join(ROOT, "ml", "reports", VERSION + ".md"), "w", encoding="utf-8") as f:
        f.write(f"# {VERSION} ({best})\n\nTrained {meta['trained']} on {int(fit.sum())} rows; threshold from {int(val.sum())} "
                f"validation rows; tested on {int(te.sum())} rows. Train / validation / test never share a domain.\n\n| model | val AUC | test AUC | test recall | test FPR | precision |\n|---|---|---|---|---|---|\n")
        for k, v in results.items():
            t = v["test"]
            f.write(f"| {k}{' (chosen)' if k == best else ''} | {v['val_auc']} | {t['auc']} | {t['recall']} | {t['fpr']} | {t['precision']} |\n")
        oracle_recall, oracle_fpr, _ = recall_at_fpr(y[te], pt, FPR_TARGET)
        meta["test_recall_at_1pct_fpr"] = round(oracle_recall, 4)
        json.dump(meta, open(os.path.join(out_dir, VERSION + ".json"), "w"), indent=2)
        f.write(f"\nThreshold {thr:.4f} (false-positive rate <= {FPR_TARGET:.0%} on the validation domains).\n\n"
                f"Recall on the test domains at a false-positive rate of exactly <= 1 % (threshold picked on test, for "
                f"information only): **{oracle_recall:.1%}** (FPR {oracle_fpr:.2%}).\n\n"
                f"Roadmap gate: AUC >= 0.97, recall >= 90 % at FPR <= 1 %.\n")
    print(json.dumps(meta["test"]))


if __name__ == "__main__":
    main()
