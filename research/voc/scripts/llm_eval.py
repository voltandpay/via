"""Gemini 속성 기반 감성분석 결과를 검증·집계한다.

1) 사람 판독(블라인드 50건) vs Gemini  → Gemini 정확도
2) 키워드 분류기 vs Gemini (main 표본) → 키워드 분류기 정밀도·재현율
3) 별점과 글 감성 불일치, 반어, 개선 요청 비율
4) 그룹 × 카테고리 언급률과 감성 분포 (가중치 적용)
"""
import json
import os
import sys

import pandas as pd

sys.path.insert(0, os.path.dirname(__file__))
from taxonomy import PAIN  # noqa: E402

OUT = sys.argv[1]
sample = pd.read_csv(os.path.join(OUT, "llm_sample.csv"))
rows = [json.loads(line) for line in open(os.path.join(OUT, "llm_results.jsonl"), encoding="utf-8") if line.strip()]
res = pd.DataFrame(rows).drop_duplicates("uid", keep="last")
m = sample.merge(res[["uid", "overall", "sarcasm", "request", "aspects", "model"]], on="uid", how="inner")
report = {"sample": int(len(sample)), "analyzed": int(len(m)),
          "models": m["model"].value_counts().to_dict()}

CATS = list(PAIN)
EXTRA = ["충전 인프라(충전소 수·위치·속도)", "기타"]


def asp(a, cat):
    for x in a:
        if x["category"] == cat:
            return x["sentiment"]
    return None


for c in CATS + EXTRA:
    m["llm:" + c] = m["aspects"].apply(lambda a, c=c: asp(a, c))

# 1) 사람 vs Gemini
hv = pd.read_csv(os.path.join(OUT, "human_val_items.csv"))
hl = json.load(open(os.path.join(OUT, "human_val_labels.json"), encoding="utf-8"))
hv = hv.merge(m[["uid", "overall", "aspects"]], on="uid", how="inner")
agree_overall = []
tp = fp = fn = 0
sent_match = sent_total = 0
for r in hv.itertuples():
    h = hl[r.vid]
    agree_overall.append(h["overall"] == r.overall)
    g = {x["category"]: x["sentiment"] for x in r.aspects if x["category"] != "기타"}
    hc = {k: v for k, v in h["cats"].items() if k != "기타"}
    tp += len(set(g) & set(hc)); fp += len(set(g) - set(hc)); fn += len(set(hc) - set(g))
    for k in set(g) & set(hc):
        sent_total += 1; sent_match += g[k] == hc[k]
polar = lambda s: {"긍정": "+", "부정": "-"}.get(s, "0")  # noqa: E731
report["human_vs_gemini"] = {
    "n": int(len(hv)),
    "overall_exact_agree": round(sum(agree_overall) / len(hv) * 100, 1),
    "overall_polarity_agree": round(sum(polar(hl[r.vid]["overall"]) == polar(r.overall) for r in hv.itertuples()) / len(hv) * 100, 1),
    "category_precision": round(tp / (tp + fp) * 100, 1) if tp + fp else None,
    "category_recall": round(tp / (tp + fn) * 100, 1) if tp + fn else None,
    "aspect_sentiment_agree": round(sent_match / sent_total * 100, 1) if sent_total else None,
    "disagreements": [{"vid": r.vid, "human": hl[r.vid]["overall"], "gemini": r.overall,
                       "text": str(r.content)[:120]} for r, ok in zip(hv.itertuples(), agree_overall) if not ok],
}

# 2) 키워드 vs Gemini (main 표본, Gemini를 기준값으로)
main = m[m["set"] == "main"]
kw = {}
for c in CATS:
    k, g = main[c].astype(bool), main["llm:" + c].notna()
    t, p_, n_ = int((k & g).sum()), int((k & ~g).sum()), int((~k & g).sum())
    kw[c] = {"gemini_n": int(g.sum()), "keyword_n": int(k.sum()),
             "precision": round(t / (t + p_) * 100, 1) if t + p_ else None,
             "recall": round(t / (t + n_) * 100, 1) if t + n_ else None}
report["keyword_vs_gemini"] = kw

# 3) 별점 vs 글 감성, 반어, 요청 (가중)
def wmean(frame, mask):
    return round(float((frame["weight"] * mask).sum() / frame["weight"].sum() * 100), 1)


summ = {}
for g, fr in list(main.groupby("group_ko")) + [("전체", main)]:
    summ[g] = {
        "n": int(len(fr)),
        "글 부정": wmean(fr, fr["overall"] == "부정"), "글 긍정": wmean(fr, fr["overall"] == "긍정"),
        "글 혼합": wmean(fr, fr["overall"] == "혼합"), "글 중립": wmean(fr, fr["overall"] == "중립"),
        "고별점(4~5)인데 글 부정·혼합": wmean(fr, (fr["score"] >= 4) & fr["overall"].isin(["부정", "혼합"])),
        "저별점(1~2)인데 글 긍정": wmean(fr, (fr["score"] <= 2) & (fr["overall"] == "긍정")),
        "반어": wmean(fr, fr["sarcasm"].astype(bool)),
        "개선 요청 포함": wmean(fr, fr["request"].astype(bool)),
    }
report["sentiment_summary"] = summ

# 4) 그룹 × 카테고리: 언급률(가중)과 언급 내 감성 분포
recs = []
for g, fr in list(main.groupby("group_ko")) + [("전체", main)]:
    W = fr["weight"].sum()
    for c in CATS + EXTRA:
        s = fr["llm:" + c]
        men = s.notna()
        if men.sum() == 0:
            continue
        w = fr.loc[men, "weight"]
        recs.append({"group": g, "category": c, "n": int(men.sum()),
                     "mention_pct": round(float(w.sum() / W * 100), 1),
                     "pos_pct": round(float(w[s[men] == "긍정"].sum() / w.sum() * 100), 1),
                     "neg_pct": round(float(w[s[men] == "부정"].sum() / w.sum() * 100), 1),
                     "neu_pct": round(float(w[s[men] == "중립"].sum() / w.sum() * 100), 1)})
asp_tbl = pd.DataFrame(recs)
asp_tbl.to_csv(os.path.join(OUT, "llm_aspects_by_group.csv"), index=False, encoding="utf-8-sig")

# 리워드·요금 심층 (main + boost, 가중치 없이 언급 리뷰 기준)
deep = {}
for c in ["포인트·리워드·멤버십", "리워드 축소·소멸 불만", "요금·가격", "아파트·완속 요금 격차"]:
    fr = m[m["llm:" + c].notna()]
    ev = {s: [x["evidence"] for a in fr["aspects"] for x in a if x["category"] == c and x["sentiment"] == s][:12]
          for s in ("긍정", "부정")}
    deep[c] = {"n": int(len(fr)), "dist": fr["llm:" + c].value_counts().to_dict(),
               "by_group": {g: f["llm:" + c].value_counts().to_dict() for g, f in fr.groupby("group_ko")},
               "evidence": ev}
report["deep_dive"] = deep

json.dump(report, open(os.path.join(OUT, "llm_eval.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
m.drop(columns=["aspects"]).assign(aspects=m["aspects"].apply(lambda a: json.dumps(a, ensure_ascii=False))) \
 .to_csv(os.path.join(OUT, "llm_merged.csv"), index=False, encoding="utf-8-sig")
print(json.dumps({k: report[k] for k in ("sample", "analyzed", "models")}, ensure_ascii=False))
print(json.dumps(report["human_vs_gemini"], ensure_ascii=False)[:1500])
