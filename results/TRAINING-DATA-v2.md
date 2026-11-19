# v2 training mix (language follows the user)

Rows: 12223. Built by `scripts/build_v2_mix.py` from v1 + raw text sources.

| Task | Rows |
|---|---|
| asr | 5513 |
| chat_sheng | 1722 |
| chat_codeswitch | 1500 |
| chat_english | 1200 |
| translate_sheng_en | 968 |
| translate_en_sheng | 862 |
| chat_swahili | 458 |

Chat reply language: explicit-sw 1500, en 1200, sw 458, sheng 1722

Known gap: the Sheng->Sheng rows are requests to say something in Sheng, answered with Sheng lines from a Sheng Bible-style corpus. Real everyday Sheng conversation data (community recordings / chats, with consent) is still needed.
