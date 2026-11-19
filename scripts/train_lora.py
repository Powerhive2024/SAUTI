"""QLoRA fine-tune of Gemma 3n (E2B or E4B) on mixed Swahili/Sheng speech + text.

Edge-first: we train small LoRA adapters on a 4-bit base so the same recipe runs
on a single 12 GB laptop GPU, then merge + quantize for on-device runtimes.

Usage (from project root):
  .venv/Scripts/python scripts/train_lora.py --model models/gemma-3n-E2B-it \
      --train data/processed/train.jsonl --eval data/processed/dev.jsonl \
      --out runs/e2b-v1 --max-steps 3000
"""
import argparse
import json
import math
import os
import random
import time
from pathlib import Path

import numpy as np
import soundfile as sf
import torch
from peft import LoraConfig, get_peft_model, prepare_model_for_kbit_training
from transformers import (
    AutoProcessor,
    BitsAndBytesConfig,
    Gemma3nForConditionalGeneration,
    get_cosine_schedule_with_warmup,
)

SR = 16_000
MAX_AUDIO_S = 30.0


def load_audio(path: str) -> np.ndarray:
    wav, sr = sf.read(path, dtype="float32", always_2d=False)
    if wav.ndim > 1:
        wav = wav.mean(axis=1)
    if sr != SR:  # data prep already resamples; this is a safety net
        import librosa

        wav = librosa.resample(wav, orig_sr=sr, target_sr=SR)
    return wav[: int(MAX_AUDIO_S * SR)]


def to_messages(ex: dict) -> list:
    user = []
    if ex.get("audio"):
        user.append({"type": "audio", "audio": load_audio(ex["audio"])})
    user.append({"type": "text", "text": ex["prompt"]})
    msgs = []
    if ex.get("system"):
        msgs.append({"role": "system", "content": [{"type": "text", "text": ex["system"]}]})
    msgs.append({"role": "user", "content": user})
    msgs.append({"role": "assistant", "content": [{"type": "text", "text": ex["response"]}]})
    return msgs


class Collator:
    """Tokenize a batch with the chat template; train only on the assistant reply."""

    def __init__(self, processor, max_len: int):
        self.p = processor
        self.max_len = max_len
        tok = processor.tokenizer
        # tokens that start the assistant turn in Gemma's template
        self.resp_ids = tok.encode("<start_of_turn>model\n", add_special_tokens=False)
        self.pad_id = tok.pad_token_id
        special = [
            getattr(processor, "audio_token_id", None),
            getattr(processor, "image_token_id", None),
            getattr(tok, "boa_token_id", None) if hasattr(tok, "boa_token_id") else None,
        ]
        self.ignore_ids = {i for i in special if i is not None}

    def _mask(self, ids: torch.Tensor) -> torch.Tensor:
        labels = ids.clone()
        n = len(self.resp_ids)
        for b in range(ids.size(0)):
            row = ids[b].tolist()
            start = None
            for i in range(len(row) - n, -1, -1):  # last assistant turn
                if row[i : i + n] == self.resp_ids:
                    start = i + n
                    break
            labels[b, : start if start is not None else len(row)] = -100
        labels[ids == self.pad_id] = -100
        for t in self.ignore_ids:
            labels[ids == t] = -100
        return labels

    def __call__(self, batch: list) -> dict:
        msgs = [to_messages(ex) for ex in batch]
        enc = self.p.apply_chat_template(
            msgs,
            tokenize=True,
            return_dict=True,
            return_tensors="pt",
            add_generation_prompt=False,
            padding=True,
        )
        if enc["input_ids"].size(1) > self.max_len:
            for k in ("input_ids", "attention_mask", "token_type_ids"):
                if k in enc:
                    enc[k] = enc[k][:, : self.max_len]
        enc["labels"] = self._mask(enc["input_ids"])
        return enc


def read_jsonl(p):
    with open(p, encoding="utf8") as f:
        return [json.loads(l) for l in f if l.strip()]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True)
    ap.add_argument("--train", required=True)
    ap.add_argument("--eval", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--max-steps", type=int, default=3000)
    ap.add_argument("--batch", type=int, default=2)
    ap.add_argument("--grad-accum", type=int, default=8)
    ap.add_argument("--lr", type=float, default=1e-4)
    ap.add_argument("--rank", type=int, default=16)
    ap.add_argument("--max-len", type=int, default=1024)
    ap.add_argument("--eval-every", type=int, default=250)
    ap.add_argument("--eval-n", type=int, default=120)
    ap.add_argument("--train-audio-tower", action="store_true",
                    help="also LoRA the audio encoder (helps accents/Sheng, costs VRAM)")
    ap.add_argument("--resume", default=None, help="adapter dir to continue from")
    ap.add_argument("--seed", type=int, default=42)
    args = ap.parse_args()

    random.seed(args.seed)
    torch.manual_seed(args.seed)
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    (out / "args.json").write_text(json.dumps(vars(args), indent=2))

    processor = AutoProcessor.from_pretrained(args.model)
    bnb = BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_quant_type="nf4",
        bnb_4bit_use_double_quant=True,
        bnb_4bit_compute_dtype=torch.bfloat16,
        # keep the speech/vision encoders in bf16: they are small and quantizing them hurts audio
        llm_int8_skip_modules=["audio_tower", "vision_tower", "embed_audio", "embed_vision", "lm_head", "altup", "correction_coefs", "prediction_coefs", "modality_router"],
    )
    model = Gemma3nForConditionalGeneration.from_pretrained(
        args.model,
        quantization_config=bnb,
        torch_dtype=torch.bfloat16,
        device_map={"": 0},
        attn_implementation="eager",
    )
    model = prepare_model_for_kbit_training(model, use_gradient_checkpointing=True)
    model.config.use_cache = False

    proj = "(q_proj|k_proj|v_proj|o_proj|gate_proj|up_proj|down_proj)"
    target = rf".*language_model.*\.{proj}"
    if args.train_audio_tower:
        target = rf"(.*language_model.*\.{proj})|(.*audio_tower.*\.(q_proj|k_proj|v_proj|post|ffw_layer_1|ffw_layer_2))"
    lcfg = LoraConfig(
        r=args.rank,
        lora_alpha=args.rank * 2,
        lora_dropout=0.05,
        target_modules=target,
        bias="none",
        task_type="CAUSAL_LM",
    )
    if args.resume:
        from peft import PeftModel

        model = PeftModel.from_pretrained(model, args.resume, is_trainable=True)
    else:
        model = get_peft_model(model, lcfg)
    model.print_trainable_parameters()

    train = read_jsonl(args.train)
    dev = read_jsonl(args.eval)[: args.eval_n]
    coll = Collator(processor, args.max_len)
    print(f"train={len(train)} dev={len(dev)}", flush=True)

    params = [p for p in model.parameters() if p.requires_grad]
    opt = torch.optim.AdamW(params, lr=args.lr, weight_decay=0.0, betas=(0.9, 0.999))
    sched = get_cosine_schedule_with_warmup(opt, int(0.03 * args.max_steps), args.max_steps)

    def batches():
        while True:
            random.shuffle(train)
            for i in range(0, len(train) - args.batch + 1, args.batch):
                yield train[i : i + args.batch]

    @torch.no_grad()
    def evaluate():
        model.eval()
        tot, n = 0.0, 0
        for i in range(0, len(dev), args.batch):
            enc = coll(dev[i : i + args.batch])
            enc = {k: v.to(model.device) for k, v in enc.items()}
            with torch.autocast("cuda", dtype=torch.bfloat16):
                loss = model(**enc).loss
            if torch.isfinite(loss):
                tot += loss.item()
                n += 1
        model.train()
        return tot / max(n, 1)

    log = open(out / "log.jsonl", "a", encoding="utf8")
    gen = batches()
    model.train()
    best = math.inf
    t0 = time.time()
    for step in range(1, args.max_steps + 1):
        run = 0.0
        for _ in range(args.grad_accum):
            batch = next(gen)
            try:
                enc = coll(batch)
            except Exception as e:  # bad audio file etc. -> skip, don't crash a long run
                print(f"skip batch: {type(e).__name__}: {e}", flush=True)
                continue
            enc = {k: v.to(model.device) for k, v in enc.items()}
            with torch.autocast("cuda", dtype=torch.bfloat16):
                loss = model(**enc).loss / args.grad_accum
            if not torch.isfinite(loss):
                print("non-finite loss, skipping micro-batch", flush=True)
                continue
            loss.backward()
            run += loss.item()
        torch.nn.utils.clip_grad_norm_(params, 1.0)
        opt.step()
        sched.step()
        opt.zero_grad(set_to_none=True)

        if step % 10 == 0:
            el = time.time() - t0
            rec = {"step": step, "loss": round(run, 4), "lr": sched.get_last_lr()[0],
                   "elapsed_min": round(el / 60, 1), "eta_min": round(el / step * (args.max_steps - step) / 60, 1),
                   "vram_gb": round(torch.cuda.max_memory_allocated() / 1e9, 2)}
            print(json.dumps(rec), flush=True)
            log.write(json.dumps(rec) + "\n")
            log.flush()
        if step % args.eval_every == 0 or step == args.max_steps:
            ev = evaluate()
            rec = {"step": step, "eval_loss": round(ev, 4)}
            print(json.dumps(rec), flush=True)
            log.write(json.dumps(rec) + "\n")
            log.flush()
            model.save_pretrained(out / "last")
            if ev < best:
                best = ev
                model.save_pretrained(out / "best")
                processor.save_pretrained(out / "best")
    print(f"done. best eval loss {best:.4f}", flush=True)


if __name__ == "__main__":
    main()
