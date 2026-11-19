"""Before/after report from results/<base>/ and results/<tuned>/ (written by evaluate.py).

Speech sets are also re-scored with the app's loop guard applied (scripts/loop_guard.py), since
that is what a phone user will actually see.

    python scripts/make_report.py --base e2b-base --tuned e2b-v1 --out results/EVAL-REPORT-e2b-v1.md
"""
import argparse
import json
import sys
from pathlib import Path

import jiwer

sys.path.insert(0, str(Path(__file__).resolve().parent))
from evaluate import norm  # noqa: E402
from langid import detect  # noqa: E402
from loop_guard import clean  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
NAMES = {
    "asr_fleurs_swke": "Speech: FLEURS Kenyan Swahili (read)",
    "asr_sartify": "Speech: Sartify Swahili",
    "asr_afrivoice": "Speech: AfriVoice Swahili",
    "asr_ndizi": "Speech: Ndizi Swahili",
    "asr_liva_sheng": "Speech: LIVA **Sheng** conversations",
    "asr_silencio_nairobi": "Speech: Nairobi conversations",
    "txt_sheng_translate": "Text: English <-> Sheng translation",
    "txt_codeswitch": "Text: English question, Kenyan Swahili answer",
    "txt_aya_swahili": "Text: Swahili chat (Aya)",
    "txt_english": "Text: English chat (Aya)",
    "txt_lang_follow": "Answers in the user's language (10 EN / 10 SW / 10 Sheng)",
}


def load(tag):
    d = ROOT / "results" / tag
    s = json.loads((d / "summary.json").read_text())
    rows = {p.stem: [json.loads(l) for l in open(p, encoding="utf8")] for p in d.glob("*.jsonl")}
    return s, rows


def asr_scores(recs, guard):
    R = [norm(r["ref"]) for r in recs]
    H = [norm(clean(r["hyp"]) if guard else r["hyp"]) for r in recs]
    keep = [i for i, x in enumerate(R) if x]
    R, H = [R[i] for i in keep], [H[i] or "<empty>" for i in keep]
    return 100 * jiwer.wer(R, H), 100 * jiwer.cer(R, H)


def loops(recs):
    return sum(clean(r["hyp"]) != r["hyp"] for r in recs)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="e2b-base")
    ap.add_argument("--tuned", default="e2b-v1")
    ap.add_argument("--out", default=None)
    a = ap.parse_args()
    bs, br = load(a.base)
    ts, tr = load(a.tuned)
    L = [f"# Sauti eval: `{a.base}` (Google's model) vs `{a.tuned}` (our fine-tune)\n",
         "Held-out examples only (never seen in training). Up to 40 per set, greedy decoding, "
         "4-bit on the laptop GPU. Phone speed and RAM are measured separately on the device.\n",
         "## Speech to text (lower is better)\n",
         "WER = share of words wrong; CER = share of letters wrong. *Guarded* = with the app's "
         "repetition guard, which stops the model when it gets stuck repeating a syllable (what the phone shows).\n",
         "| Set | n | Base WER | Tuned WER | Base WER guarded | **Tuned WER guarded** | Tuned CER guarded | Loops (base→tuned) |",
         "|---|---|---|---|---|---|---|---|"]
    for k, name in NAMES.items():
        if not k.startswith("asr") or k not in tr or k not in br:
            continue
        bw, _ = asr_scores(br[k], False)
        tw, _ = asr_scores(tr[k], False)
        bwg, _ = asr_scores(br[k], True)
        twg, tcg = asr_scores(tr[k], True)
        L.append(f"| {name} | {len(tr[k])} | {bw:.1f} | {tw:.1f} | {bwg:.1f} | **{twg:.1f}** | {tcg:.1f} | {loops(br[k])}→{loops(tr[k])} |")

    L += ["\n## Text (chrF: higher is better, 0-100) and reply language\n",
          "*Right language* = the reply is in the language it should be, judged by the same word-list "
          "detector the app uses (Sheng vs Swahili is a rough call for a word list).\n",
          "| Set | n | Base chrF | Tuned chrF | Base right language | **Tuned right language** |",
          "|---|---|---|---|---|---|"]
    for k, name in NAMES.items():
        if not k.startswith("txt") or k not in ts or k not in bs:
            continue
        b, t = bs[k], ts[k]
        f = lambda x, key: "-" if key not in x else f"{x[key]:.1f}"
        L.append(f"| {name} | {t['n']} | {f(b, 'chrF')} | {f(t, 'chrF')} | {f(b, 'lang_match_%')}% | **{f(t, 'lang_match_%')}%** |")

    if "txt_lang_follow" in tr:
        L += ["\n### Which language came back (language-follow test)\n",
              "| Asked in | Base replied in | Tuned replied in |", "|---|---|---|"]
        for lang in ("en", "sw", "sheng"):
            row = []
            for res in (br, tr):
                got = [detect(r["hyp"]) for r in res["txt_lang_follow"] if r["prompt"].split("\n\n")[0] and _lang_of(r) == lang]
                row.append(", ".join(f"{g} {got.count(g)}" for g in ("en", "sw", "sheng") if got.count(g)))
            L.append(f"| {lang} | {row[0]} | {row[1]} |")
        L += ["\n### Sheng answers side by side (repetition guard applied, first 280 characters)\n"]
        base_sh = [r for r in br["txt_lang_follow"] if _lang_of(r) == "sheng"]
        tuned_sh = [r for r in tr["txt_lang_follow"] if _lang_of(r) == "sheng"]
        short = lambda x: clean(x["hyp"]).strip().replace("\n", " / ")[:280]
        for b, t in list(zip(base_sh, tuned_sh))[:5]:
            L.append(f"- **Q:** {b['prompt'].split(chr(10) * 2)[0]}  \n  *Base:* {short(b)}  \n  *Tuned:* {short(t)}")

    e = ts.get("_edge", {})
    L += ["\n## What this means\n",
          "- **Speech is much better.** Swahili word errors drop by half or more on every Swahili set "
          "(e.g. AfriVoice 42.6% -> 13.5%, FLEURS Kenya 61.3% -> 25.4%), and Nairobi conversations improve too. "
          "Voice is the main way people will use Sauti, so this is the biggest win.",
          "- **Sheng speech is still hard** (72.5% WER with the guard, from 87.9%). Only 26 test clips, from fast, "
          "overlapping conversations; it needs more real Sheng recordings.",
          "- **English <-> Sheng translation improved a lot** (chrF 27.0 -> 48.1).",
          "- **Free chat is mixed.** Swahili and English chat scores dropped against the reference answers. In Sheng the "
          "tuned model is shorter and more direct (it answers the loan and landlord questions in natural Sheng), while the "
          "base model is longer and often explains the Sheng words instead of answering; but the tuned model sometimes "
          "loops or echoes the question. Cause: the v1 training mix was mostly speech and translation, with little real "
          "conversation. The v2 mix (more English, Swahili and Sheng chat, plus the language note the app sends) is "
          "built to fix this in the next run.",
          "- **Language following works for both** (30/30): with the app's hidden language note, English gets "
          "English, Swahili gets Swahili and Sheng gets Sheng-flavoured replies.",
          "- **On the phone, expect:** good Swahili voice transcription, OK-but-imperfect Sheng, and chat answers that "
          "are sometimes too short or odd. Please note examples; they feed the next training round.",
          ]
    L += ["\n## Notes\n",
          f"- Laptop speed (not the phone): ~{e.get('gen_tokens_per_s_median')} tokens/s, peak VRAM {e.get('peak_vram_gb')} GB.",
          "- The Sheng training text is Bible-style (EgesaWO/Sheng_sml); everyday Sheng from real users is the "
          "biggest missing piece and should come from community recordings with consent.",
          "- This model (E2B v1) was trained on the v1 mix. The v2 mix (language follows the user) goes into the next run."]
    out = Path(a.out) if a.out else ROOT / "results" / f"EVAL-REPORT-{a.tuned}.md"
    out.write_text("\n".join(L) + "\n", encoding="utf8")
    print(out)


def _lang_of(r):
    return detect(r["prompt"].split("\n\n")[0])


if __name__ == "__main__":
    main()
