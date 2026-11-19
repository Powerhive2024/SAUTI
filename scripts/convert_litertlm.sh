#!/usr/bin/env bash
# Convert a merged fine-tuned Gemma 3n checkpoint to a phone .litertlm (int4), inside WSL2/Linux.
# litert-torch exports the text decoder; it cannot export the Gemma 3n audio encoder (only Gemma 4 /
# Qwen3), so the audio sections are taken from Google's base bundle afterwards
# (our LoRA never touched the audio tower).
#
#   bash scripts/convert_litertlm.sh ~/sauti/merged-e2b-v1 ~/sauti/litert-e2b-v1
#
# Needs ~32 GB RAM: give WSL most of the PC's memory in %UserProfile%\.wslconfig
#   [wsl2]
#   memory=28GB
#   swap=16GB
set -euo pipefail

MERGED="${1:?merged checkpoint dir}"
OUT="${2:?output dir}"
QUANT="${QUANT:-dynamic_wi4_afp32}"   # int4 weights; dynamic_wi8_afp32 for an int8 build
VENV="${VENV:-$HOME/litert-venv}"

if [ ! -d "$VENV" ]; then
  python3 -m venv "$VENV"
  "$VENV/bin/pip" install -U pip
  "$VENV/bin/pip" install torch torchvision --index-url https://download.pytorch.org/whl/cpu
  "$VENV/bin/pip" install litert-torch ai-edge-litert litert-lm-builder tensorflow-cpu timm
  # jax 0.12 lowers erfinv (Gemma 3n activation sparsity) to vhlo.fma, which LiteRT rejects
  "$VENV/bin/pip" install "jax==0.11.2" "jaxlib==0.11.2"
fi

mkdir -p "$OUT"
# litert-torch CLI (fire): litert-torch export_hf MODEL OUT_DIR --flags
echo "== export ($QUANT)"
/usr/bin/time -v "$VENV/bin/litert-torch" export_hf "$MERGED" "$OUT" \
  --task=text_generation \
  --quantization_recipe="$QUANT" \
  --cache_length="${CACHE_LENGTH:-4096}" \
  --use_jinja_template=True \
  --bundle_litert_lm=True \
  --externalize_embedder=True \
  --model_dtype="${MODEL_DTYPE:-float32}" \
  --experimental_lightweight_conversion=True \
  2>&1 | tee "$OUT/export.log"

ls -lh "$OUT"
echo "Next: splice the audio sections from Google's base gemma-3n-E2B-it-int4.litertlm"
