"""v2 training mix: answer in the user's language (English -> English, Kiswahili -> Kiswahili, Sheng -> Sheng).

v1 problem: every chat example was an English question with a Swahili answer (the code-switch set),
and there was no English->English or Sheng->Sheng chat, so the model learned to drift into Swahili.

v2, built from the v1 files plus the raw text sources (audio rows are reused unchanged, no re-decoding):
  * keep all speech (ASR) rows and the EN<->Sheng translation rows
  * cut the explicit "explain in Swahili" code-switch rows from 3,500 to 1,500
  * add English->English chat (Aya, Apache-2.0)
  * Swahili->Swahili chat (Aya) upsampled x2
  * Sheng->Sheng: a Sheng request ("Hii nitaisema aje kwa Sheng ...") answered in Sheng (EgesaWO/Sheng_sml)
  * every chat prompt ends with the SAME hidden language note the Android app adds
    (app/src/main/java/app/sauti/lang/Language.kt -> replyInstruction), so the model learns to obey it.

    python scripts/build_v2_mix.py
"""
import json
import random
from collections import Counter
from pathlib import Path

import pandas as pd

ROOT = Path(__file__).resolve().parents[1]
RAW, PROC = ROOT / "data" / "raw", ROOT / "data" / "processed"

# Must stay identical to LanguageDetector.replyInstruction() in the app.
NOTE = {
    "en": "(Reply in English only.)",
    "sw": "(Jibu kwa Kiswahili tu.)",
    "sheng": "(Jibu kwa Sheng ya Nairobi kama mtumiaji: msingi wa Kiswahili wenye maneno ya Sheng "
             "kama niaje, poa, msee, fiti, doh, keja, na Kiingereza kidogo tu. Si slang ya Kiingereza, si Kiswahili sanifu.)",
}

SHENG_ASKS = [  # how a Sheng speaker might ask; written as requests, the answer is the dataset's Sheng line
    "Niaje msee, hii nitaisema aje kwa Sheng: \"{en}\"",
    "Manze, nisaidie kuweka hii kwa Sheng: \"{en}\"",
    "Sasa buda, hii ingeskika aje kwa Sheng ya mtaa? \"{en}\"",
    "Ebu niambie kwa Sheng: \"{en}\"",
]


def load(name):
    with open(PROC / name, encoding="utf8") as f:
        return [json.loads(l) for l in f if l.strip()]


def write(name, rows):
    with open(PROC / name, "w", encoding="utf8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    print(f"  {name}: {len(rows)}  {dict(Counter(r['task'] for r in rows))}")


def noted(prompt, lang):
    return prompt + "\n\n" + NOTE[lang]


def main():
    rng = random.Random(11)
    v1 = load("train.jsonl")
    held_sheng = {r["prompt"].split("\n", 1)[1] for r in load("eval_text_sheng_translate.jsonl")} | \
                 {r["response"] for r in load("eval_text_sheng_translate.jsonl")}
    held_aya = {r["prompt"] for r in load("eval_text_aya_swahili.jsonl")}

    asr = [r for r in v1 if r["task"] == "asr"]
    trans = [r for r in v1 if r["task"].startswith("translate_")]
    codesw = [r for r in v1 if r["task"] == "chat_codeswitch"]
    rng.shuffle(codesw)
    codesw = codesw[:1500]  # explicit "explain in Swahili" requests: still valid, just no longer dominant

    aya = pd.read_parquet(RAW / "CohereForAI__aya_dataset" / "data" / "train-00000-of-00001.parquet",
                          columns=["inputs", "targets", "language"])
    aya = aya[aya.inputs.str.len().between(8, 1500) & aya.targets.str.len().between(2, 2500)]
    en = aya[aya.language == "English"].sample(frac=1, random_state=3)
    sw = aya[(aya.language == "Swahili") & ~aya.inputs.str.strip().isin(held_aya)]

    def chat(df, lang, task):
        return [{"task": task, "source": "CohereForAI/aya_dataset", "license": "Apache-2.0",
                 "prompt": noted(r.inputs.strip(), lang), "response": r.targets.strip(), "meta": {"lang": lang}}
                for r in df.itertuples()]

    en_dev, en_train = chat(en[:60], "en", "chat_english"), chat(en[60:1260], "en", "chat_english")
    sw_train = chat(sw, "sw", "chat_swahili")

    pairs = pd.read_csv(RAW / "EgesaWO__Sheng_sml" / "set_1.csv", encoding="utf-8-sig").dropna()
    sheng = []
    for r in pairs.itertuples():
        e, s = " ".join(str(r.English).split()).lstrip(".( "), " ".join(str(r.Sheng).split()).lstrip(".( ")
        if len(e.split()) < 3 or len(s.split()) < 3 or e in held_sheng or s in held_sheng:
            continue
        sheng.append({"task": "chat_sheng", "source": "EgesaWO/Sheng_sml", "license": "Apache-2.0",
                      "prompt": noted(rng.choice(SHENG_ASKS).format(en=e), "sheng"), "response": s,
                      "meta": {"lang": "sheng"}})

    train = asr + trans + codesw + en_train + sw_train * 2 + sheng * 2
    rng.shuffle(train)
    dev = [r for r in load("dev.jsonl") if r["task"] != "chat_codeswitch"][:120] + en_dev[:30] + sheng[:30]
    rng.shuffle(dev)
    write("train_v2.jsonl", train)
    write("dev_v2.jsonl", dev)
    write("eval_text_english.jsonl", en_dev)

    chat_rows = [r for r in train if r["task"].startswith("chat_")]
    langs = Counter(r.get("meta", {}).get("lang", "explicit-sw") for r in chat_rows)
    print("chat reply language balance:", dict(langs))
    with open(PROC / "MANIFEST_v2.md", "w", encoding="utf8") as f:
        f.write("# v2 training mix (language follows the user)\n\n")
        f.write(f"Rows: {len(train)}. Built by `scripts/build_v2_mix.py` from v1 + raw text sources.\n\n")
        f.write("| Task | Rows |\n|---|---|\n")
        for t, c in Counter(r["task"] for r in train).most_common():
            f.write(f"| {t} | {c} |\n")
        f.write("\nChat reply language: " + ", ".join(f"{k} {v}" for k, v in langs.items()) + "\n\n")
        f.write("Known gap: the Sheng->Sheng rows are requests to say something in Sheng, answered with "
                "Sheng lines from a Sheng Bible-style corpus. Real everyday Sheng conversation data "
                "(community recordings / chats, with consent) is still needed.\n")


if __name__ == "__main__":
    main()
