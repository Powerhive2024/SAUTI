"""Download the starting dataset mix listed in DATASETS.md into data/raw/.

Run from the project root:  python scripts/download_datasets.py
Re-running is safe: files that are already complete are skipped.
"""
import sys
import time
from pathlib import Path

from huggingface_hub import snapshot_download
from huggingface_hub.errors import GatedRepoError

ROOT = Path(__file__).resolve().parent.parent / "data" / "raw"

# (repo_id, allow_patterns or None for everything, note)
DATASETS = [
    # --- Sheng text ---
    ("Maxyelow/kenyan-code-switch-instruct-50k", None, "Sheng/code-switch SFT pairs"),
    ("BlakHasan/sheng-augmented-collection", None, "EN-Sheng bitext + mono Sheng"),
    ("JeunesseAfricaine/sheng_nlu", None, "real Sheng utterances"),
    ("EgesaWO/Sheng_sml", None, "Sheng seed set"),
    # --- Swahili text ---
    ("CohereForAI/aya_dataset", None, "multilingual instructions (filter Swahili)"),
    ("lelapa/Inkuba-instruct", ["data/swahili_*", "*.md"], "Swahili instructions (gated)"),
    # --- Conversational / code-switched audio ---
    ("liva-ai/swahili-asr", None, "conversational sample incl. Sheng"),
    ("SilencioNetwork/swahili-speech", None, "Nairobi Sheng-influenced speech (CC-BY-NC)"),
    ("smutuvi/ndizi-1-sw", None, "Tanzanian interviews"),
    # --- Eval audio ---
    ("google/fleurs", ["parquet-data/sw_ke/*", "README.md"], "Kenyan Swahili read speech (eval)"),
    ("sartifyllc/kiswahili-asr-challenge-eval", None, "conversational eval set"),
    # --- Bulk Swahili audio ---
    ("google/WaxalNLP", ["data/TTS/swa/*", "README.md"], "WAXAL Swahili"),
    ("badrex/swahili-speech-400hr", None, "~400 h Swahili ASR (~44 GB)"),
]


def main() -> int:
    ok, failed = [], []
    for repo, patterns, note in DATASETS:
        dest = ROOT / repo.replace("/", "__")
        print(f"\n=== {repo}  ({note}) -> {dest}", flush=True)
        last_err = None
        for attempt in range(1, 7):
            try:
                snapshot_download(
                    repo_id=repo,
                    repo_type="dataset",
                    local_dir=dest,
                    allow_patterns=patterns,
                    max_workers=4,
                )
                last_err = None
                break
            except GatedRepoError as e:  # needs the user to accept terms; retrying won't help
                last_err = e
                break
            except Exception as e:  # rate limits (429) / network blips: back off and resume
                last_err = e
                wait = min(60 * attempt, 300)
                print(f"  attempt {attempt} failed ({type(e).__name__}); retrying in {wait}s", flush=True)
                time.sleep(wait)
        if last_err is None:
            ok.append(repo)
            print(f"OK {repo}", flush=True)
        else:  # keep going; report at the end
            failed.append((repo, f"{type(last_err).__name__}: {str(last_err).splitlines()[0][:200]}"))
            print(f"FAILED {repo}: {failed[-1][1]}", flush=True)

    print("\n===== SUMMARY =====")
    print(f"Downloaded: {len(ok)}")
    for r in ok:
        print(f"  OK      {r}")
    for r, err in failed:
        print(f"  FAILED  {r}: {err}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
