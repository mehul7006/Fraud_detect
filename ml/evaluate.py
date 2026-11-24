"""11.9 evaluate a trained link model on the held-out test domains (Python side, for training runs).

    docker ... python ml/evaluate.py [models/url-v1]      (ml/train.sh installs the same packages)

Reads ml/data/features.csv (Java features) and the ONNX model, prints AUC / recall / false-positive rate at the
model's threshold, exits 1 below the gate. The same gate runs in every CI build in Java: ModelGateTest.
"""
import csv
import json
import os
import sys

import numpy as np
import onnxruntime as ort
from sklearn.metrics import roc_auc_score

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GATE = {"auc": 0.98, "recall": 0.84, "fpr": 0.02}


def main():
    base = os.path.join(ROOT, "backend", "src", "main", "resources", sys.argv[1] if len(sys.argv) > 1 else "models/url-v1")
    meta = json.load(open(base + ".json"))
    rows = [r for r in csv.reader(open(os.path.join(ROOT, "ml", "data", "features.csv"), encoding="utf-8"))]
    head, rows = rows[0], [r for r in rows[1:] if r[1] == "test"]
    assert head[2:] == meta["features"], "features.csv and the model use different features"
    y = np.array([int(r[0]) for r in rows])
    X = np.array([[float(v) for v in r[2:]] for r in rows], dtype=np.float32)
    sess = ort.InferenceSession(base + ".onnx", providers=["CPUExecutionProvider"])
    p = [o for o in sess.run(None, {sess.get_inputs()[0].name: X}) if getattr(o, "ndim", 0) == 2][0][:, 1]
    flag = p >= meta["threshold"]
    m = {"auc": float(roc_auc_score(y, p)), "recall": float((flag & (y == 1)).sum() / (y == 1).sum()),
         "fpr": float((flag & (y == 0)).sum() / (y == 0).sum()), "rows": int(len(y))}
    print(json.dumps(m, indent=2))
    bad = [k for k in ("auc", "recall") if m[k] < GATE[k]] + (["fpr"] if m["fpr"] > GATE["fpr"] else [])
    if bad:
        print("GATE FAILED:", bad, file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
