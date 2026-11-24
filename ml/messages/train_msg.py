"""14.2 on-device message classifier: hashed character / word n-grams -> multinomial logistic regression.

    bash ml/messages/train_msg.sh   (Docker; reads ml/messages/corpus.csv)

Exports mobile/assets/models/msg-v1.bin (read by MessageClassifier.kt):
  magic "FSMSG1", u32 buckets, u32 classes, then class names (u8 len + utf-8), then float32 bias[classes],
  then float32 weights[classes][buckets] (little endian).
Feature code MUST equal MessageClassifier.kt (parity vectors: mobile/android-native/test/msg-vectors.tsv).
Split by template (see build_corpus.py): test messages never share a template with training messages.
"""
import csv
import json
import os
import re
import struct
import sys

import numpy as np
from scipy.sparse import csr_matrix
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import classification_report, f1_score

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
BUCKETS = 1 << 16
URL = re.compile(r"(https?://\S+|www\.\S+|\b[\w-]+\.(?:top|xyz|online|site|in|co|info|click|com|net|org|me|ly)\b\S*)", re.I)


def fnv(s: str) -> int:
    h = 0x811C9DC5
    for b in s.encode("utf-8"):
        h ^= b
        h = (h * 0x01000193) & 0xFFFFFFFF
    return h


def normalise(text: str) -> str:
    t = text.lower()
    t = URL.sub(" zzurl ", t)
    t = re.sub(r"\d+", "0", t)
    t = re.sub(r"\s+", " ", t).strip()
    return t


def features(text: str) -> dict:
    t = normalise(text)
    padded = f" {t} "
    idx = {}
    for n in (3, 4, 5):
        for i in range(len(padded) - n + 1):
            idx[fnv("c:" + padded[i:i + n]) % BUCKETS] = 1.0
    words = t.split(" ")
    for w in words:
        if w:
            idx[fnv("w:" + w) % BUCKETS] = 1.0
    for a, b in zip(words, words[1:]):
        idx[fnv("b:" + a + " " + b) % BUCKETS] = 1.0
    norm = len(idx) ** 0.5
    return {k: v / norm for k, v in idx.items()}


def matrix(texts):
    rows, cols, vals = [], [], []
    for r, t in enumerate(texts):
        for c, v in features(t).items():
            rows.append(r); cols.append(c); vals.append(v)
    return csr_matrix((vals, (rows, cols)), shape=(len(texts), BUCKETS), dtype=np.float32)


def main():
    data = list(csv.DictReader(open(os.path.join(ROOT, "ml", "messages", "corpus.csv"), encoding="utf-8")))
    label = lambda r: "GENUINE" if r["label"] == "GENUINE" else r["category"]
    classes = sorted({label(r) for r in data})
    train = [r for r in data if r["split"] == "train"]
    test = [r for r in data if r["split"] == "test"]
    Xtr, Xte = matrix([r["text"] for r in train]), matrix([r["text"] for r in test])
    ytr = np.array([classes.index(label(r)) for r in train])
    yte = np.array([classes.index(label(r)) for r in test])
    model = LogisticRegression(C=8.0, max_iter=3000)
    model.fit(Xtr, ytr)
    P = model.predict_proba(Xte)
    g = classes.index("GENUINE")
    scam_true = yte != g
    scam_pred = P[:, g] < 0.5
    f1 = f1_score(scam_true, scam_pred)
    tp = int((scam_pred & scam_true).sum()); fp = int((scam_pred & ~scam_true).sum()); fn = int((~scam_pred & scam_true).sum())
    print(f"scam vs genuine on held-out templates: F1 {f1:.3f} (tp {tp}, fp {fp}, fn {fn}, n {len(test)})")
    cat_acc = float((P[scam_true].argmax(1) == yte[scam_true]).mean())
    print(f"category accuracy on scam test messages: {cat_acc:.3f}")
    print(classification_report(yte, P.argmax(1), labels=list(range(len(classes))), target_names=classes, zero_division=0))

    out_dir = os.path.join(ROOT, "mobile", "assets", "models")  # also copied to backend/src/main/resources/models for the 14.6 cloud check
    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, "msg-v1.bin")
    with open(path, "wb") as f:
        f.write(b"FSMSG1")
        f.write(struct.pack("<II", BUCKETS, len(classes)))
        for c in classes:
            b = c.encode("utf-8"); f.write(struct.pack("<B", len(b))); f.write(b)
        f.write(model.intercept_.astype("<f4").tobytes())
        f.write(model.coef_.astype("<f4").tobytes())
    size = os.path.getsize(path)
    print(f"model file {size / 1e6:.2f} MB")

    # parity vectors for the Kotlin test: 100 test messages + expected P(genuine) and top class
    rnd = np.random.default_rng(20261124)
    pick = rnd.choice(len(test), 100, replace=False)
    with open(os.path.join(ROOT, "mobile", "android-native", "test", "msg-vectors.tsv"), "w", encoding="utf-8", newline="\n") as f:
        for i in sorted(pick):
            p = model.predict_proba(matrix([test[i]["text"]]))[0]
            f.write(f"{classes[int(p.argmax())]}\t{p[g]:.6f}\t{test[i]['text'].replace(chr(9), ' ')}\n")
    json.dump({"version": "msg-v1", "classes": classes, "buckets": BUCKETS, "f1_scam_heldout": round(f1, 4),
               "category_accuracy": round(cat_acc, 4), "test_messages": len(test), "train_messages": len(train),
               "model_mb": round(size / 1e6, 2)}, open(os.path.join(ROOT, "ml", "messages", "msg-v1.json"), "w"), indent=2)
    if f1 < 0.90 or size > 10e6:
        print("GATE FAILED (F1 >= 0.90, size <= 10 MB)", file=sys.stderr); sys.exit(1)


if __name__ == "__main__":
    main()
