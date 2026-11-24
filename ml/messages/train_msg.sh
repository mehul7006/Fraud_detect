#!/usr/bin/env bash
# 14.2: train + export the on-device message classifier in Docker (same image / packages as ml/train.sh).
set -euo pipefail
cd "$(dirname "$0")/../.."
export MSYS_NO_PATHCONV=1
W=$(pwd -W 2>/dev/null || pwd)
docker run --rm --memory 2g -v "$W":/repo -v fs-pipcache:/root/.cache/pip -w /repo python:3.12-slim sh -c \
  "(apt-get -qq update >/dev/null && apt-get -qq install -y libgomp1 >/dev/null) && pip install -q -r ml/requirements.txt scipy && python ml/messages/train_msg.py"
