"""Build cloud/Sauti_convert_kaggle.ipynb: the Colab conversion notebook adapted for a Kaggle TPU VM.

Differences from Colab: the zip comes from a Kaggle dataset input, the Hugging Face token comes from a
Kaggle secret (HF_TOKEN) typed in by the account owner, big intermediate files go to the largest free
disk, and the finished model is written to /kaggle/working and pushed to a private Hugging Face repo.
"""
import json, os

cells = []
def md(s): cells.append({"cell_type": "markdown", "metadata": {}, "source": s.strip("\n")})
def code(s): cells.append({"cell_type": "code", "metadata": {}, "execution_count": None, "outputs": [], "source": s.strip("\n")})

md("""
# Sauti: build the fine-tuned phone model on Kaggle (Gemma 3n E2B -> .litertlm)

Settings: Accelerator **TPU VM** (for its RAM; the TPU itself is not used), Internet **on**.
Inputs: the `sauti-cloud` dataset (sauti-cloud.zip). Secrets: `HF_TOKEN` (a Hugging Face read+write token
from the account that accepted Gemma's licence). Run all cells in order.
""")

code(r"""
# 1. Check the machine and pick a work folder with enough space
import os, shutil, glob
ram = os.sysconf('SC_PAGE_SIZE') * os.sysconf('SC_PHYS_PAGES') / 1e9
cands = ['/kaggle/tmp', '/tmp', '/kaggle/working', '/root']
free = {d: shutil.disk_usage(d if os.path.exists(d) else os.path.dirname(d)).free / 1e9 for d in cands}
W = max(free, key=free.get) + '/sauti-work'
os.makedirs(W, exist_ok=True)
os.environ['W'] = W
print(f"RAM {ram:.0f} GB, CPUs {os.cpu_count()}, free disk {({k: round(v) for k, v in free.items()})}, work dir {W}")
assert ram >= 45, "Need 45 GB+ RAM: pick the TPU VM accelerator"
assert max(free.values()) >= 60, "Need 60 GB+ free disk"
""")

code(r"""
# 2. Unzip sauti-cloud.zip from the dataset input
import subprocess
z = (glob.glob('/kaggle/input/**/sauti-cloud.zip', recursive=True) or [None])[0]
src = z or (glob.glob('/kaggle/input/**/merge_lora.py', recursive=True) or [None])[0]
assert src, "Add the sauti-cloud dataset as an input (Add Input -> Datasets -> Your Datasets)"
S = W + '/sauti'
shutil.rmtree(S, ignore_errors=True)
if z:
    subprocess.run(['unzip', '-q', '-o', z, '-d', S], check=True)
else:  # Kaggle unpacked the zip into the dataset folder
    shutil.copytree(os.path.dirname(src), S)
os.environ['S'] = S
print(sorted(os.listdir(S)))
""")

code(r"""
# 3. Hugging Face token from Kaggle secrets (Add-ons -> Secrets -> HF_TOKEN, attached to this notebook)
from kaggle_secrets import UserSecretsClient
os.environ['HF_TOKEN'] = UserSecretsClient().get_secret('HF_TOKEN')
print('HF token loaded' if os.environ['HF_TOKEN'] else 'HF_TOKEN is empty')
""")

code(r"""
%%bash
# 4. Install tools in two environments (merge and convert need different library versions), ~5 min
set -e
pip -q install uv
[ -x $W/merge-env/bin/python ] || {
  uv venv -q $W/merge-env --python 3.12
  uv pip install -q --python $W/merge-env/bin/python torch==2.5.1 --index-url https://download.pytorch.org/whl/cpu
  uv pip install -q --python $W/merge-env/bin/python transformers==4.57.1 peft==0.19.1 accelerate==1.14.0 timm==1.0.27 pillow sentencepiece huggingface_hub==0.36.2 'numpy<2.3'
}
[ -x $W/conv-env/bin/litert-torch ] || {
  uv venv -q $W/conv-env --python 3.12
  uv pip install -q --python $W/conv-env/bin/python torch==2.14.1 torchvision==0.29.2 --index-url https://download.pytorch.org/whl/cpu
  uv pip install -q --python $W/conv-env/bin/python -r $S/requirements-convert.txt
}
JAX_PLATFORMS=cpu $W/conv-env/bin/python -c "import litert_torch, jax; print('converter ready, jax', jax.__version__)"
$W/merge-env/bin/python -c "import peft, transformers, huggingface_hub as h; print('merger ready', transformers.__version__, peft.__version__); print('HF user:', h.whoami()['name'])"
""")

code(r"""
%%bash
# 5. Download, merge, convert to phone format, add audio (~35-50 min). Output: /kaggle/working/sauti-e2b-v1.litertlm
set -eo pipefail
trap 'echo FAILED; tail -15 $W/download.log $W/merge.log 2>/dev/null' ERR
export JAX_PLATFORMS=cpu
cd $W
M=$W/merge-env/bin/python
C=$W/conv-env/bin
FLAGS="--task=text_generation --quantization_recipe=dynamic_wi4_afp32 --cache_length=4096 --use_jinja_template=True --bundle_litert_lm=True --externalize_embedder=True --model_dtype=float32 --experimental_lightweight_conversion=True"

echo "$(date +%H:%M) downloading Google's models"
$M -c "from huggingface_hub import snapshot_download as s, hf_hub_download as h; s('google/gemma-3n-E2B-it', local_dir='$W/gemma-3n-E2B-it'); h('google/gemma-3n-E2B-it-litert-lm', 'gemma-3n-E2B-it-int4.litertlm', local_dir='$W/google')" > $W/download.log 2>&1

if [ ! -f $W/merged-e2b-v1/config.json ]; then
  echo "$(date +%H:%M) merging our Swahili/Sheng adapter"
  $M $S/merge_lora.py --model $W/gemma-3n-E2B-it --adapter $S/adapter --out $W/merged-e2b-v1 > $W/merge.log 2>&1
fi

if ! ls $W/litert-out/*.litertlm > /dev/null 2>&1; then
  echo "$(date +%H:%M) converting to phone format (longest step, ~20 min)"
  rm -rf $W/litert-out
  if ! $C/litert-torch export_hf $W/merged-e2b-v1 $W/litert-out $FLAGS > $W/export.log 2>&1; then
    grep -v '^\[ERROR\]' $W/export.log | tail -5
    echo "$(date +%H:%M) full conversion failed; retrying in low-memory mode"
    rm -rf $W/litert-out
    $C/python $S/export_lowmem.py export_hf $W/merged-e2b-v1 $W/litert-out $FLAGS > $W/export-lowmem.log 2>&1 || { grep -v '^\[ERROR\]' $W/export-lowmem.log | tail -30; exit 1; }
  fi
fi
ls -lh $W/litert-out

echo "$(date +%H:%M) adding Google's audio parts"
OURS=$(ls $W/litert-out/*.litertlm | head -1)
rm -rf $W/parts-ours $W/parts-google
$C/litert-lm-peek --litertlm_file "$OURS" --dump_files_dir $W/parts-ours > /dev/null
$C/litert-lm-peek --litertlm_file $W/google/gemma-3n-E2B-it-int4.litertlm --dump_files_dir $W/parts-google > /dev/null
$C/python $S/splice_audio.py --base-parts $W/parts-google --ours-parts $W/parts-ours --out /kaggle/working/sauti-e2b-v1.litertlm --builder $C/litert-lm-builder
$C/litert-lm-peek --litertlm_file /kaggle/working/sauti-e2b-v1.litertlm | grep -E 'model_type' || true
ls -lh /kaggle/working
echo "$(date +%H:%M) BUILT: /kaggle/working/sauti-e2b-v1.litertlm"
""")

code(r"""
# 6. Copy the model to a PRIVATE Hugging Face repo so it can be downloaded to the laptop or phone
from huggingface_hub import HfApi
api = HfApi()
repo = api.whoami()['name'] + '/sauti-e2b-v1-litertlm'
api.create_repo(repo, private=True, exist_ok=True)
api.upload_file(path_or_fileobj='/kaggle/working/sauti-e2b-v1.litertlm', path_in_repo='sauti-e2b-v1.litertlm', repo_id=repo)
print('DONE: private repo https://huggingface.co/' + repo)
""")

nb = {"nbformat": 4, "nbformat_minor": 0,
      "metadata": {"kernelspec": {"name": "python3", "display_name": "Python 3"}, "language_info": {"name": "python"}},
      "cells": cells}
out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "Sauti_convert_kaggle.ipynb")
json.dump(nb, open(out, "w", encoding="utf-8"), indent=1)
print("wrote", out)
