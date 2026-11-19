"""Evaluate a Gemma 3n model (base or base+LoRA) on the held-out Swahili/Sheng sets.

Speech: WER and CER (lower is better) after light normalisation.
Text:   chrF (higher is better) against the reference answer/translation.
Also records edge metrics: peak VRAM and generation speed.

Usage:
  .venv/Scripts/python scripts/evaluate.py --model models/gemma-3n-E2B-it --tag e2b-base --n 100
  .venv/Scripts/python scripts/evaluate.py --model models/gemma-3n-E2B-it --adapter runs/e2b-v1/best --tag e2b-v1
"""
import argparse
import json
import re
import time
import unicodedata
from collections import defaultdict
from pathlib import Path

import jiwer
import numpy as np
import soundfile as sf
import torch
import sys
sys.path.insert(0, str(Path(__file__).resolve().parent))
from langid import detect, family  # same detector as the app
from transformers import AutoProcessor, BitsAndBytesConfig, Gemma3nForConditionalGeneration

ROOT = Path(__file__).resolve().parent.parent
PROC = ROOT / "data" / "processed"
SETS = {
    "asr_fleurs_swke": "eval_asr_fleurs_swke.jsonl",
    "asr_sartify": "eval_asr_sartify.jsonl",
    "asr_afrivoice": "eval_asr_afrivoice_heldout.jsonl",
    "asr_ndizi": "eval_asr_ndizi_heldout.jsonl",
    "asr_liva_sheng": "eval_asr_liva_sheng_heldout.jsonl",
    "asr_silencio_nairobi": "eval_asr_silencio_nairobi.jsonl",
    "txt_sheng_translate": "eval_text_sheng_translate.jsonl",
    "txt_codeswitch": "eval_text_codeswitch.jsonl",
    "txt_aya_swahili": "eval_text_aya_swahili.jsonl",
    "txt_english": "eval_text_english.jsonl",
    "txt_lang_follow": "eval_text_langfollow.jsonl",
}


def norm(t: str) -> str:
    t = unicodedata.normalize("NFKC", t or "").lower()
    t = re.sub(r"\[[^\]]*\]|<[^>]*>", " ", t)          # tags like [laughing]
    t = re.sub(r"[^\w\s']", " ", t)                     # punctuation
    t = re.sub(r"\s+", " ", t).strip()
    return t


def chrf(hyp: str, ref: str, n: int = 6, beta: float = 2.0) -> float:
    def grams(s, k):
        s = s.replace(" ", "")
        return [s[i:i + k] for i in range(len(s) - k + 1)]
    ps, rs = [], []
    for k in range(1, n + 1):
        h, r = grams(hyp, k), grams(ref, k)
        if not h or not r:
            continue
        from collections import Counter
        hc, rc = Counter(h), Counter(r)
        m = sum((hc & rc).values())
        ps.append(m / len(h))
        rs.append(m / len(r))
    if not ps:
        return 0.0
    p, r = sum(ps) / len(ps), sum(rs) / len(rs)
    return 0.0 if p + r == 0 else 100 * (1 + beta**2) * p * r / (beta**2 * p + r)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True)
    ap.add_argument("--adapter", default=None)
    ap.add_argument("--tag", required=True)
    ap.add_argument("--n", type=int, default=100, help="examples per set")
    ap.add_argument("--sets", default=",".join(SETS))
    ap.add_argument("--max-new", type=int, default=256)
    ap.add_argument("--four-bit", action="store_true", default=True)
    a = ap.parse_args()

    proc = AutoProcessor.from_pretrained(a.model)
    q = BitsAndBytesConfig(load_in_4bit=True, bnb_4bit_quant_type="nf4", bnb_4bit_compute_dtype=torch.bfloat16,
                           llm_int8_skip_modules=["audio_tower", "vision_tower", "embed_audio", "embed_vision", "lm_head", "altup", "correction_coefs", "prediction_coefs", "modality_router"])
    model = Gemma3nForConditionalGeneration.from_pretrained(
        a.model, quantization_config=q, torch_dtype=torch.bfloat16, device_map={"": 0})
    if a.adapter:
        from peft import PeftModel
        model = PeftModel.from_pretrained(model, a.adapter)
    model.eval()
    torch.cuda.reset_peak_memory_stats()

    out_dir = ROOT / "results" / a.tag
    out_dir.mkdir(parents=True, exist_ok=True)
    summary, tok_s = {}, []
    for name in a.sets.split(","):
        p = PROC / SETS[name]
        if not p.exists():
            print(f"skip {name} (missing)")
            continue
        rows = [json.loads(l) for l in open(p, encoding="utf8")][: a.n]
        preds = []
        for r in rows:
            content = []
            if r.get("audio"):
                wav, _ = sf.read(ROOT / r["audio"], dtype="float32")
                content.append({"type": "audio", "audio": wav})
            content.append({"type": "text", "text": r["prompt"]})
            enc = proc.apply_chat_template([{"role": "user", "content": content}], add_generation_prompt=True,
                                           tokenize=True, return_dict=True, return_tensors="pt").to(model.device)
            t0 = time.time()
            with torch.inference_mode():
                g = model.generate(**enc, max_new_tokens=a.max_new, do_sample=False)
            new = g[0, enc["input_ids"].shape[1]:]
            dt = time.time() - t0
            tok_s.append(len(new) / max(dt, 1e-6))
            preds.append(proc.decode(new, skip_special_tokens=True).strip())
        refs = [r["response"] for r in rows]
        recs = [{"prompt": x["prompt"], "ref": r, "hyp": h, "hyp_lang": detect(h), "audio": x.get("audio")} for r, h, x in zip(refs, preds, rows)]
        with open(out_dir / f"{name}.jsonl", "w", encoding="utf8") as f:
            for x in recs:
                f.write(json.dumps(x, ensure_ascii=False) + "\n")
        if name.startswith("asr"):
            R = [norm(x) for x in refs]
            H = [norm(x) for x in preds]
            keep = [i for i, x in enumerate(R) if x]
            R, H = [R[i] for i in keep], [H[i] or "<empty>" for i in keep]
            summary[name] = {"n": len(R), "WER": round(100 * jiwer.wer(R, H), 2),
                             "CER": round(100 * jiwer.cer(R, H), 2)}
        else:
            res = {"n": len(rows)}
            pairs = [(h, r) for h, r in zip(preds, refs) if r]
            if pairs:
                res["chrF"] = round(float(np.mean([chrf(norm(h), norm(r)) for h, r in pairs])), 2)
            # Does the reply come back in the language it should? Expected = the row's tagged language,
            # else the reference answer's language. "family" counts Sheng and Swahili as one.
            want = [x.get("meta", {}).get("lang") or detect(r) for x, r in zip(rows, refs)]
            got = [detect(h) for h in preds]
            res["lang_match_%"] = round(100 * float(np.mean([w == g for w, g in zip(want, got)])), 1)
            res["lang_family_match_%"] = round(100 * float(np.mean([family(w) == family(g) for w, g in zip(want, got)])), 1)
            by = defaultdict(list)
            for w, g in zip(want, got):
                by[w].append(g)
            res["replies_by_expected_lang"] = {w: dict(sorted({k: gs.count(k) for k in set(gs)}.items())) for w, gs in by.items()}
            summary[name] = res
        print(name, summary[name], flush=True)
    summary["_edge"] = {"peak_vram_gb": round(torch.cuda.max_memory_allocated() / 1e9, 2),
                        "gen_tokens_per_s_median": round(float(np.median(tok_s)), 1) if tok_s else None,
                        "note": "4-bit NF4 on RTX 4080 Laptop; on-device numbers measured separately"}
    (out_dir / "summary.json").write_text(json.dumps(summary, indent=2))
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
