"""Build the v1 multimodal (speech + text) Swahili/Sheng training and eval sets.

Output (data/processed/):
  audio/<source>/<id>.wav      16 kHz mono, <=30 s   (Gemma 3n audio encoder format)
  train.jsonl / dev.jsonl      mixed tasks for LoRA training
  eval_<name>.jsonl            held-out benchmarks (never trained on)
  MANIFEST.md                  provenance + licence of every source (Mozilla "work in the open")

Each row: {"task", "source", "license", "audio"?, "prompt", "response", "meta"?}

Licence policy (CLAUDE.md): only CC0/CC-BY/CC-BY-SA/Apache/MIT data goes into train.
CC-BY-NC data (SilencioNetwork) is used for evaluation only.
"""
import argparse
import csv
import glob
import io
import json
import random
import re
from collections import Counter
from pathlib import Path

import numpy as np
import pyarrow.parquet as pq
import soundfile as sf

SR = 16_000
MAX_S = 30.0
MIN_S = 1.0
ROOT = Path(__file__).resolve().parent.parent
RAW = ROOT / "data" / "raw"
OUT = ROOT / "data" / "processed"
AUD = OUT / "audio"

ASR_PROMPTS_SW = [
    "Andika maneno yaliyosemwa kwenye sauti hii.",
    "Nakili sauti hii kwa maandishi kama ilivyosemwa.",
    "Sikiliza na uandike kila neno lililosemwa.",
]
ASR_PROMPTS_EN = [
    "Transcribe this audio exactly as spoken.",
    "Write down what the speaker says, word for word.",
]


def asr_prompt(rng):
    return rng.choice(ASR_PROMPTS_SW if rng.random() < 0.7 else ASR_PROMPTS_EN)


# ---------------------------------------------------------------- audio utils
def decode(data: bytes | str) -> tuple[np.ndarray, int]:
    if isinstance(data, (bytes, bytearray)):
        try:
            return sf.read(io.BytesIO(data), dtype="float32", always_2d=False)
        except Exception:
            import librosa

            wav, sr = librosa.load(io.BytesIO(data), sr=None, mono=True)
            return wav.astype("float32"), sr
    try:
        return sf.read(data, dtype="float32", always_2d=False)
    except Exception:
        import librosa

        wav, sr = librosa.load(data, sr=None, mono=True)
        return wav.astype("float32"), sr


def to16k(wav: np.ndarray, sr: int) -> np.ndarray:
    if wav.ndim > 1:
        wav = wav.mean(axis=1)
    if sr != SR:
        import librosa

        wav = librosa.resample(wav, orig_sr=sr, target_sr=SR)
    peak = float(np.abs(wav).max()) if wav.size else 0.0
    if peak > 1.0:
        wav = wav / peak
    return wav.astype("float32")


def save_wav(wav: np.ndarray, source: str, uid: str) -> str | None:
    dur = len(wav) / SR
    if dur < MIN_S or dur > MAX_S:
        return None
    p = AUD / source / f"{re.sub(r'[^A-Za-z0-9_.-]', '_', uid)}.wav"
    p.parent.mkdir(parents=True, exist_ok=True)
    if not p.exists():
        sf.write(p, wav, SR, subtype="PCM_16")
    return str(p.relative_to(ROOT)).replace("\\", "/")


def clean_text(t: str) -> str:
    t = re.sub(r"\s+", " ", (t or "")).strip()
    return t


def audio_bytes(cell) -> bytes | str | None:
    if isinstance(cell, dict):
        return cell.get("bytes") or cell.get("path")
    return cell


def iter_parquet(pattern: str, columns=None):
    for f in sorted(glob.glob(str(RAW / pattern), recursive=True)):
        pf = pq.ParquetFile(f)
        for rg in range(pf.num_row_groups):
            for row in pf.read_row_group(rg, columns=columns).to_pylist():
                yield row


# ---------------------------------------------------------------- sources
def badrex(n, rng):
    """Afrivoice-derived 400 h Swahili (CC-BY-4.0). Balance speakers / locations."""
    rows, per_spk = [], Counter()
    for r in iter_parquet("badrex__swahili-speech-400hr/**/*.parquet"):
        spk = r.get("voice_creator_id")
        if per_spk[spk] >= 6:  # many speakers > many clips from one speaker
            continue
        txt = clean_text(r.get("transcription"))
        if not txt or float(r.get("audio_duration") or 0) > MAX_S:
            continue
        try:
            wav = to16k(*decode(audio_bytes(r["audio"])))
        except Exception:
            continue
        p = save_wav(wav, "badrex", r["audio_id"])
        if not p:
            continue
        per_spk[spk] += 1
        rows.append({"task": "asr", "source": "badrex/swahili-speech-400hr", "license": "CC-BY-4.0",
                     "audio": p, "prompt": asr_prompt(rng), "response": txt,
                     "meta": {"gender": r.get("gender"), "age": r.get("age_group"), "loc": r.get("location"),
                              "spk": spk}})
        if len(rows) >= n:
            break
    return rows


def waxal(n, rng):
    rows = []
    for r in iter_parquet("google__WaxalNLP/**/swa-train-*.parquet"):
        txt = clean_text(r.get("text"))
        try:
            wav = to16k(*decode(audio_bytes(r["audio"])))
        except Exception:
            continue
        p = save_wav(wav, "waxal", r["id"])
        if p and txt:
            rows.append({"task": "asr", "source": "google/WaxalNLP (swa_tts)", "license": "CC-BY-4.0",
                         "audio": p, "prompt": asr_prompt(rng), "response": txt,
                         "meta": {"gender": r.get("gender"), "spk": r.get("speaker_id")}})
        if len(rows) >= n:
            break
    return rows


def fleurs(split, n, rng, tag):
    rows = []
    for r in iter_parquet(f"google__fleurs/**/sw_ke/{split}-*.parquet"):
        txt = clean_text(r.get("raw_transcription") or r.get("transcription"))
        try:
            wav = to16k(*decode(audio_bytes(r["audio"])))
        except Exception:
            continue
        p = save_wav(wav, f"fleurs_{split}", f"{r.get('id')}_{len(rows)}")
        if p and txt:
            rows.append({"task": "asr", "source": "google/fleurs sw_ke", "license": "CC-BY-4.0",
                         "audio": p, "prompt": asr_prompt(rng) if tag == "train" else ASR_PROMPTS_SW[0],
                         "response": txt, "meta": {"gender": r.get("gender")}})
        if len(rows) >= n:
            break
    return rows


def ndizi(rng):
    """Tanzanian farmer interviews (CC-BY-4.0)."""
    rows = []
    meta = RAW / "smutuvi__ndizi-1-sw" / "data" / "test" / "metadata.csv"
    with open(meta, encoding="utf8") as f:
        for r in csv.DictReader(f):
            txt = clean_text(r["transcript"])
            src = meta.parent / r["file_name"]
            if not txt or not src.exists():
                continue
            try:
                wav = to16k(*decode(str(src)))
            except Exception:
                continue
            p = save_wav(wav, "ndizi", r["file_name"].rsplit(".", 1)[0])
            if p:
                rows.append({"task": "asr", "source": "smutuvi/ndizi-1-sw", "license": "CC-BY-4.0",
                             "audio": p, "prompt": asr_prompt(rng), "response": txt,
                             "meta": {"spk": r.get("speaker_id"), "district": r.get("district"),
                                      "l1": r.get("language")}})
    return rows


TS = re.compile(r"Speaker (\d+) \[(\d+):(\d+):([\d.]+) - (\d+):(\d+):([\d.]+)\]:\s*(.*)")


def liva(rng):
    """Natural conversations incl. Sheng (CC-BY-4.0 sample). Cut into <=30 s windows on turn boundaries."""
    out = {}
    base = RAW / "liva-ai__swahili-asr" / "data"
    with open(base / "metadata.csv", encoding="utf8") as f:
        for r in csv.DictReader(f):
            src = base / r["file_name"]
            if not src.exists():
                continue
            turns = []
            for line in r["transcript"].splitlines():
                m = TS.match(line.strip())
                if m:
                    s = int(m[2]) * 3600 + int(m[3]) * 60 + float(m[4])
                    e = int(m[5]) * 3600 + int(m[6]) * 60 + float(m[7])
                    turns.append((s, e, m[8].strip()))
            if not turns:
                continue
            wav = to16k(*decode(str(src)))
            segs, cur = [], []
            for t in turns:
                if cur and t[1] - cur[0][0] > 25.0:
                    segs.append(cur)
                    cur = []
                cur.append(t)
            if cur:
                segs.append(cur)
            rows = []
            for i, seg in enumerate(segs):
                s, e = max(0.0, seg[0][0] - 0.2), seg[-1][1] + 0.2
                txt = clean_text(" ".join(t[2] for t in seg))
                txt_clean = clean_text(re.sub(r"\[[^\]]*\]", "", txt))
                if len(txt_clean.split()) < 3:
                    continue
                p = save_wav(wav[int(s * SR): int(e * SR)], "liva", f"{src.stem}_{i:03d}")
                if p:
                    rows.append({"task": "asr", "source": "liva-ai/swahili-asr (sample)", "license": "CC-BY-4.0",
                                 "audio": p, "prompt": asr_prompt(rng), "response": txt_clean,
                                 "meta": {"conv": src.stem, "style": "conversational+sheng"}})
            out[src.stem] = rows
    return out


def silencio():
    """Kenyan/Nairobi (Sheng-influenced) free speech. CC-BY-NC -> EVAL ONLY."""
    rows = []
    for i, r in enumerate(iter_parquet("SilencioNetwork__swahili-speech/**/*.parquet")):
        txt = clean_text(r.get("transcript"))
        try:
            wav = to16k(*decode(audio_bytes(r["audio"])))
        except Exception:
            continue
        p = save_wav(wav, "silencio", f"{r.get('speaker_id')}_{i}")
        if p and txt:
            rows.append({"task": "asr", "source": "SilencioNetwork/swahili-speech", "license": "CC-BY-NC-4.0 (eval only)",
                         "audio": p, "prompt": ASR_PROMPTS_SW[0], "response": txt,
                         "meta": {"dialect": r.get("dialect"), "gender": r.get("gender"), "age": r.get("age_band")}})
    return rows


def sartify(n):
    rows = []
    for r in iter_parquet("sartifyllc__kiswahili-asr-challenge-eval/**/*.parquet"):
        txt = clean_text(r.get("text"))
        try:
            wav = to16k(*decode(audio_bytes(r["audio"])))
        except Exception:
            continue
        p = save_wav(wav, "sartify", r["record_id"])
        if p and txt:
            rows.append({"task": "asr", "source": "sartifyllc/kiswahili-asr-challenge-eval", "license": "CC-BY-SA-4.0",
                         "audio": p, "prompt": ASR_PROMPTS_SW[0], "response": txt})
        if len(rows) >= n:
            break
    return rows


def maxyelow(split, n, rng):
    rows = []
    with open(RAW / "Maxyelow__kenyan-code-switch-instruct-50k" / f"{split}.jsonl", encoding="utf8") as f:
        data = [json.loads(l) for l in f if l.strip()]
    rng.shuffle(data)
    for r in data[:n]:
        prompt = r["instruction"] + (("\n\n" + r["input"]) if r.get("input") else "")
        rows.append({"task": "chat_codeswitch", "source": "Maxyelow/kenyan-code-switch-instruct-50k",
                     "license": "Apache-2.0", "prompt": prompt, "response": r["output"],
                     "meta": {"domain": r.get("domain"), "subtask": r.get("task")}})
    return rows


def sheng_pairs(rng):
    """EN<->Sheng translation pairs (EgesaWO/Sheng_sml, Apache-2.0)."""
    rows = []
    with open(RAW / "EgesaWO__Sheng_sml" / "set_1.csv", encoding="utf-8-sig") as f:
        for r in csv.DictReader(f):
            en, sh = clean_text(r.get("English")), clean_text(r.get("Sheng"))
            if len(en.split()) < 3 or len(sh.split()) < 3:
                continue
            if rng.random() < 0.5:
                rows.append({"task": "translate_en_sheng", "source": "EgesaWO/Sheng_sml", "license": "Apache-2.0",
                             "prompt": f"Tafsiri kwa Sheng ya mtaani (Nairobi):\n{en}", "response": sh})
            else:
                rows.append({"task": "translate_sheng_en", "source": "EgesaWO/Sheng_sml", "license": "Apache-2.0",
                             "prompt": f"Translate this Sheng into clear English:\n{sh}", "response": en})
    return rows


def aya(n, rng):
    rows = []
    for r in iter_parquet("CohereForAI__aya_dataset/**/train-*.parquet", columns=["inputs", "targets", "language"]):
        if r.get("language") == "Swahili" and r.get("inputs") and r.get("targets"):
            rows.append({"task": "chat_swahili", "source": "CohereForAI/aya_dataset", "license": "Apache-2.0",
                         "prompt": r["inputs"].strip(), "response": r["targets"].strip()})
    rng.shuffle(rows)
    return rows[:n]


# ---------------------------------------------------------------- main
def write(name, rows):
    p = OUT / name
    with open(p, "w", encoding="utf8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    print(f"  {name}: {len(rows)} rows  {dict(Counter(r['task'] for r in rows))}", flush=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--badrex", type=int, default=6000)
    ap.add_argument("--waxal", type=int, default=1200)
    ap.add_argument("--fleurs-train", type=int, default=1500)
    ap.add_argument("--maxyelow", type=int, default=3500)
    ap.add_argument("--aya", type=int, default=1500)
    ap.add_argument("--seed", type=int, default=7)
    a = ap.parse_args()
    rng = random.Random(a.seed)
    OUT.mkdir(parents=True, exist_ok=True)

    print("text sources...", flush=True)
    mx_train = maxyelow("train", a.maxyelow, rng)
    mx_test = maxyelow("test", 200, rng)
    sp = sheng_pairs(rng)
    rng.shuffle(sp)
    sp_eval, sp_train = sp[:120], sp[120:]
    ay = aya(a.aya + 100, rng)
    ay_eval, ay_train = ay[:100], ay[100:]

    print("audio sources...", flush=True)
    lv = liva(rng)
    convs = sorted(lv)
    lv_eval_convs = convs[:2]  # hold out whole conversations (no speaker leakage)
    lv_eval = [r for c in lv_eval_convs for r in lv[c]]
    lv_train = [r for c in convs[2:] for r in lv[c]]
    print(f"  liva convs={len(convs)} train segs={len(lv_train)} eval segs={len(lv_eval)}", flush=True)

    nd = ndizi(rng)
    nd_spk = sorted({r["meta"]["spk"] for r in nd})
    rng.shuffle(nd_spk)
    hold = set(nd_spk[: max(1, len(nd_spk) // 8)])
    nd_eval = [r for r in nd if r["meta"]["spk"] in hold][:150]
    nd_train = [r for r in nd if r["meta"]["spk"] not in hold]
    print(f"  ndizi train={len(nd_train)} eval={len(nd_eval)}", flush=True)

    fl_train = fleurs("train", a.fleurs_train, rng, "train")
    fl_test = fleurs("test", 300, rng, "eval")
    print(f"  fleurs train={len(fl_train)} test={len(fl_test)}", flush=True)
    wx = waxal(a.waxal, rng)
    print(f"  waxal={len(wx)}", flush=True)
    sar = sartify(300)
    print(f"  sartify eval={len(sar)}", flush=True)
    sil = silencio()
    print(f"  silencio eval={len(sil)}", flush=True)
    bd = badrex(a.badrex, rng)
    bd_spk = sorted({r["meta"]["spk"] for r in bd})
    rng.shuffle(bd_spk)
    bhold = set(bd_spk[:60])
    bd_eval = [r for r in bd if r["meta"]["spk"] in bhold][:250]
    bd_train = [r for r in bd if r["meta"]["spk"] not in bhold]
    print(f"  badrex train={len(bd_train)} eval={len(bd_eval)}", flush=True)

    # Upsample the scarce Sheng / conversational material so it is not drowned out.
    train = (bd_train + wx + fl_train + nd_train + lv_train * 4
             + mx_train + sp_train * 2 + ay_train)
    rng.shuffle(train)
    dev_pool = bd_eval[:40] + lv_eval[:20] + nd_eval[:20] + mx_test[:40] + sp_eval[:30] + ay_eval[:30]
    rng.shuffle(dev_pool)

    print("writing...", flush=True)
    write("train.jsonl", train)
    write("dev.jsonl", dev_pool)
    write("eval_asr_fleurs_swke.jsonl", fl_test)
    write("eval_asr_sartify.jsonl", sar)
    write("eval_asr_afrivoice_heldout.jsonl", bd_eval)
    write("eval_asr_ndizi_heldout.jsonl", nd_eval)
    write("eval_asr_liva_sheng_heldout.jsonl", lv_eval)
    write("eval_asr_silencio_nairobi.jsonl", sil)
    write("eval_text_sheng_translate.jsonl", sp_eval)
    write("eval_text_codeswitch.jsonl", mx_test)
    write("eval_text_aya_swahili.jsonl", ay_eval)

    src = Counter((r["source"], r["license"]) for r in train)
    hours = sum(sf.info(str(ROOT / r["audio"])).duration for r in train if r.get("audio")) / 3600
    with open(OUT / "MANIFEST.md", "w", encoding="utf8") as f:
        f.write("# v1 training data manifest\n\n")
        f.write(f"Training rows: {len(train)} (audio ≈ {hours:.1f} h after upsampling)\n\n")
        f.write("| Source | Licence | Rows in train |\n|---|---|---|\n")
        for (s, lic), c in src.most_common():
            f.write(f"| {s} | {lic} | {c} |\n")
        f.write("\nEval-only (never trained on): FLEURS sw_ke test, Sartify Zindi eval, SilencioNetwork "
                "(CC-BY-NC), held-out speakers/conversations from Afrivoice, ndizi, liva; held-out Sheng "
                "translation pairs, code-switch test split, Aya Swahili.\n")
    print(f"done. train audio ≈ {hours:.1f} h", flush=True)


if __name__ == "__main__":
    main()
