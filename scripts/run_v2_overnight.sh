# v2 overnight: continue from v1 on the v2 mix (reply in the user's language, more real chat), then evaluate.
set -e
cd "$(dirname "$0")/.."
PY=.venv/Scripts/python
echo "$(date +%H:%M) training v2 (300 steps from v1)"
$PY scripts/train_lora.py --model models/gemma-3n-E2B-it --train data/processed/train_v2.jsonl --eval data/processed/dev_v2.jsonl \
  --out runs/e2b-v2 --max-steps 300 --batch 1 --grad-accum 16 --lr 1e-4 --rank 16 --max-len 1024 --eval-every 100 --resume runs/e2b-v1/best > runs/e2b-v2.log 2>&1
echo "$(date +%H:%M) evaluating v2"
$PY scripts/evaluate.py --model models/gemma-3n-E2B-it --adapter runs/e2b-v2/best --tag e2b-v2 --n 40 > results-e2b-v2.log 2>&1
echo "$(date +%H:%M) v2 DONE"
