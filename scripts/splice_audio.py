"""Build the Sauti phone model: our fine-tuned text model + Google's untouched Gemma 3n audio parts.

litert-torch cannot export the Gemma 3n audio encoder, but our LoRA never changed it, so the audio
sections are copied from Google's base gemma-3n-E2B-it-int4.litertlm. Vision is left out (the app
does not use it; saves ~170 MB).

    python splice_audio.py --base-parts DIR --ours-parts DIR --out sauti-e2b-v1.litertlm [--builder litert-lm-builder]

Each *-parts DIR is made with:  litert-lm-peek --litertlm_file X.litertlm --dump_files_dir DIR
"""
import argparse
import glob
import os
import subprocess

AUDIO = ("audio_encoder_hw", "audio_adapter", "end_of_audio")
TEXT = ("embedder", "per_layer_embedder", "prefill_decode")


def find(parts, model_type):
    hits = glob.glob(os.path.join(parts, f"*_tf_lite_{model_type}.tflite"))
    if len(hits) != 1:
        raise SystemExit(f"expected one {model_type} in {parts}, found {hits}")
    return os.path.abspath(hits[0])


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base-parts", required=True)
    ap.add_argument("--ours-parts", required=True, help="our export; use the base dir to test the builder")
    ap.add_argument("--out", required=True)
    ap.add_argument("--builder", default="litert-lm-builder")
    a = ap.parse_args()

    bp = os.path.abspath(a.base_parts)
    sec = [
        # Google's metadata: same tokenizer and chat template, plus the Gemma 3n audio tokens the runtime needs
        ('section_type = "LlmMetadata"', os.path.join(bp, "LlmMetadataProto.pbtext")),
        ('section_type = "SP_Tokenizer"', glob.glob(os.path.join(bp, "*SP_Tokenizer*"))[0]),
    ]
    for t in TEXT[:2]:
        # The LoRA never touched the embedding tables, so Google's are an exact stand-in when the
        # low-memory export (export_lowmem.py) skipped ours.
        if glob.glob(os.path.join(a.ours_parts, f"*_tf_lite_{t}.tflite")):
            src = find(a.ours_parts, t)
        else:
            src = find(bp, t)
            print(f"using Google's {t} (not in our export)")
        sec.append((f'model_type = "{t}"\nsection_type = "TFLiteModel"', src))
    for t in AUDIO:
        bc = '\nbackend_constraint = "cpu"' if t != "end_of_audio" else ""
        sec.append((f'model_type = "{t}"{bc}\nsection_type = "TFLiteModel"', find(bp, t)))
    sec.append(('model_type = "prefill_decode"\nbackend_constraint = "cpu,gpu"\nsection_type = "TFLiteModel"',
                find(a.ours_parts, "prefill_decode")))

    toml = ['[system_metadata]\nentries = [\n  { key = "author", value_type = "String", value = "Sauti (fine-tuned Gemma 3n; Gemma Terms of Use)" },\n]\n']
    for head, path in sec:
        toml.append(f'[[section]]\n{head}\ndata_path = "{path}"\n')
    tpath = os.path.splitext(a.out)[0] + ".toml"
    with open(tpath, "w") as f:
        f.write("\n".join(toml))
    subprocess.run([a.builder, "toml", "--path", tpath, "output", "--path", a.out,
                    "--weights-in-model-graph", "true"], check=True)
    print("wrote", a.out, round(os.path.getsize(a.out) / 1e9, 2), "GB")


if __name__ == "__main__":
    main()
