#!/usr/bin/env bash
# 11.8: download reviewed user reports as training labels, then rebuild + retrain:
#   ADMIN_TOKEN=<admin JWT> bash ml/export_feedback.sh [https://api.example.com]
#   python ml/build_dataset.py && <FeatureDumpTool> && bash ml/train.sh
set -euo pipefail
cd "$(dirname "$0")/.."
API="${1:-http://localhost:18080}"
: "${ADMIN_TOKEN:?set ADMIN_TOKEN to an admin access token}"
curl -sSf -H "Authorization: Bearer $ADMIN_TOKEN" "$API/api/v1/admin/feedback/labels.tsv" -o ml/data/feedback.tsv
echo "$(wc -l < ml/data/feedback.tsv) reviewed reports -> ml/data/feedback.tsv"
