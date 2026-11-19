"""Merge a trained LoRA adapter into Gemma 3n and save a plain HF checkpoint.

litert-torch's export_hf has no LoRA support, so the phone build is made from merged weights.
Only the language model was adapted; the audio and vision towers are copied unchanged.

    python scripts/merge_lora.py --model models/gemma-3n-E2B-it --adapter runs/e2b-v1/best --out models/merged/e2b-v1
"""
import argparse
import shutil
from pathlib import Path

import torch
from peft import PeftModel
from transformers import AutoProcessor, Gemma3nForConditionalGeneration


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True)
    ap.add_argument("--adapter", required=True)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()

    out = Path(a.out)
    out.mkdir(parents=True, exist_ok=True)
    print("loading base (bf16, CPU)…", flush=True)
    base = Gemma3nForConditionalGeneration.from_pretrained(a.model, torch_dtype=torch.bfloat16, device_map="cpu")
    print("applying adapter…", flush=True)
    model = PeftModel.from_pretrained(base, a.adapter).merge_and_unload()
    model.save_pretrained(out, safe_serialization=True, max_shard_size="4GB")
    AutoProcessor.from_pretrained(a.model).save_pretrained(out)
    # keep generation defaults + chat template identical to the base model
    for f in ("generation_config.json", "chat_template.jinja", "chat_template.json"):
        src = Path(a.model) / f
        if src.exists():
            shutil.copy(src, out / f)
    print(f"merged checkpoint written to {out}", flush=True)


if __name__ == "__main__":
    main()
