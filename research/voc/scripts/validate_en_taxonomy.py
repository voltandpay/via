"""영어 키워드 분류를 해외 Gemini 판정(3,000건)과 비교해 카테고리별 정밀도·재현율을 낸다."""
import json
import os
import sys

import pandas as pd

sys.path.insert(0, os.path.dirname(__file__))
from taxonomy_en import PAIN  # noqa: E402

G = sys.argv[1]
s = pd.read_csv(os.path.join(G, "llm_sample_global.csv"))
r = pd.DataFrame([json.loads(x) for x in open(os.path.join(G, "llm_results_global.jsonl"), encoding="utf-8") if x.strip()])
m = s.merge(r[["uid", "aspects"]], on="uid")
text = m["content"].astype(str).str.lower()
rows = []
for c, pat in PAIN.items():
    k = text.str.contains(pat, regex=True)
    g = m["aspects"].apply(lambda a, c=c: any(x["category"] == c for x in a))
    tp, fp, fn = int((k & g).sum()), int((k & ~g).sum()), int((~k & g).sum())
    rows.append({"category": c, "gemini_n": int(g.sum()), "keyword_n": int(k.sum()),
                 "precision": round(tp / (tp + fp) * 100, 1) if tp + fp else None,
                 "recall": round(tp / (tp + fn) * 100, 1) if tp + fn else None})
out = pd.DataFrame(rows).sort_values("gemini_n", ascending=False)
out.to_csv(os.path.join(G, "en_keyword_vs_gemini.csv"), index=False, encoding="utf-8-sig")
print(out.to_string(index=False))
