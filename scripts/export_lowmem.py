"""Run `litert-torch export_hf` without exporting Gemma 3n's per-layer embedder.

Why: on a 47 GB Colab machine the per-layer embedder quantize step is the memory peak (~44 GB).
Our LoRA only changed the language model's attention/MLP layers (see adapter_config.json
target_modules), so the per-layer embedding table is identical to Google's. splice_audio.py
takes Google's per_layer_embedder when ours is missing.

Use exactly like the CLI:  python export_lowmem.py export_hf MODEL OUT --task=... [flags]
This is the fallback path; the normal full export stays the default.
"""
import sys

from litert_torch.generative.export_hf.model_ext import exportables

exportables.get_additional_exportables = lambda model_config: {}

from litert_torch.cli import main  # noqa: E402  (import after the patch)

if __name__ == "__main__":
    sys.argv[0] = "litert-torch"
    sys.exit(main())
