"""국내 vs 해외(미국·영국) 비교.

1) 해외 사람 판독(30건) vs Gemini 정확도
2) 해외 그룹별 감성 요약 + 카테고리 언급률·감성 (가중)
3) 국내·해외 대응 그룹 나란히 비교
4) 같은 앱(글로벌 완성차·현대차그룹 통합 앱)의 국내·해외 별점 비교
5) 해외 리워드·DR 앱 심층
"""
import json
import os
import sys

import pandas as pd

OUT = sys.argv[1]
G = os.path.join(OUT, "global")
CATS = ["앱 안정성·오류", "UI·UX 불편", "원격제어·차량연결", "로그인·인증", "디지털키", "고객센터·응대",
        "요금·가격", "결제·청구 오류", "충전기 고장·충전 실패", "충전기 정보 부정확", "충전 인프라(충전소 수·위치·속도)",
        "포인트·리워드·멤버십", "리워드 축소·소멸 불만", "경로·내비 (일반)", "배터리·전비", "알림", "대기·혼잡·점유", "기타"]


def load(sample_csv, results_jsonl):
    s = pd.read_csv(sample_csv)
    r = pd.DataFrame([json.loads(x) for x in open(results_jsonl, encoding="utf-8") if x.strip()]).drop_duplicates("uid", keep="last")
    m = s.merge(r[["uid", "overall", "sarcasm", "request", "aspects", "model"]], on="uid")
    if isinstance(m["aspects"].iloc[0], str):
        m["aspects"] = m["aspects"].apply(json.loads)
    for c in CATS:
        m["llm:" + c] = m["aspects"].apply(lambda a, c=c: next((x["sentiment"] for x in a if x["category"] == c), None))
    return m


def summary(fr):
    W = fr["weight"].sum()
    w = lambda mask: round(float((fr["weight"] * mask).sum() / W * 100), 1)  # noqa: E731
    return {"n": int(len(fr)), "글 부정": w(fr["overall"] == "부정"), "글 긍정": w(fr["overall"] == "긍정"),
            "글 혼합": w(fr["overall"] == "혼합"), "개선 요청": w(fr["request"].astype(bool)),
            "반어": w(fr["sarcasm"].astype(bool)), "평균 별점(가중)": round(float((fr["score"] * fr["weight"]).sum() / W), 2)}


def aspects(fr, group):
    W, out = fr["weight"].sum(), []
    for c in CATS:
        s = fr["llm:" + c]; men = s.notna()
        if not men.any():
            continue
        w = fr.loc[men, "weight"]
        out.append({"group": group, "category": c, "n": int(men.sum()), "mention_pct": round(float(w.sum() / W * 100), 1),
                    "pos_pct": round(float(w[s[men] == "긍정"].sum() / w.sum() * 100), 1),
                    "neg_pct": round(float(w[s[men] == "부정"].sum() / w.sum() * 100), 1)})
    return out


kr = load(os.path.join(OUT, "llm_sample.csv"), os.path.join(OUT, "llm_results.jsonl"))
kr = kr[kr["set"] == "main"]
gl = load(os.path.join(G, "llm_sample_global.csv"), os.path.join(G, "llm_results_global.jsonl"))
rep = {"kr_n": int(len(kr)), "global_n": int(len(gl)), "global_models": gl["model"].value_counts().to_dict()}

# 1) 해외 사람 vs Gemini
hv = pd.read_csv(os.path.join(G, "human_val_items_global.csv")).merge(gl[["uid", "overall", "aspects"]], on="uid")
hl = json.load(open(os.path.join(G, "human_val_labels_global.json"), encoding="utf-8"))
agree = [hl[r.vid]["overall"] == r.overall for r in hv.itertuples()]
tp = fp = fn = sm = st = 0
for r in hv.itertuples():
    g = {x["category"]: x["sentiment"] for x in r.aspects if x["category"] != "기타"}
    h = {k: v for k, v in hl[r.vid]["cats"].items() if k != "기타"}
    tp += len(set(g) & set(h)); fp += len(set(g) - set(h)); fn += len(set(h) - set(g))
    for k in set(g) & set(h):
        st += 1; sm += g[k] == h[k]
rep["human_vs_gemini_global"] = {"n": int(len(hv)), "overall_agree": round(sum(agree) / len(hv) * 100, 1),
                                 "category_precision": round(tp / (tp + fp) * 100, 1), "category_recall": round(tp / (tp + fn) * 100, 1),
                                 "aspect_sentiment_agree": round(sm / st * 100, 1) if st else None,
                                 "disagreements": [{"vid": r.vid, "human": hl[r.vid]["overall"], "gemini": r.overall, "text": str(r.content)[:110]}
                                                   for r, ok in zip(hv.itertuples(), agree) if not ok]}

# 2) 해외 요약·카테고리
rep["global_summary"] = {g: summary(f) for g, f in gl.groupby("group_ko")}
rep["kr_summary"] = {g: summary(f) for g, f in kr.groupby("group_ko")}
tbl = []
for g, f in gl.groupby("group_ko"):
    tbl += aspects(f, g)
for g, f in kr.groupby("group_ko"):
    tbl += aspects(f, g)
tbl = pd.DataFrame(tbl)
tbl.to_csv(os.path.join(G, "aspects_kr_vs_global.csv"), index=False, encoding="utf-8-sig")

# 3) 대응 그룹 나란히
PAIRS = [("현대차그룹", "해외 현대차그룹"), ("경쟁 완성차", "해외 경쟁 완성차"), ("충전 앱", "해외 충전 앱")]
side = {}
for a, b in PAIRS:
    ta = tbl[tbl.group == a].set_index("category"); tb = tbl[tbl.group == b].set_index("category")
    idx = [c for c in CATS if c in ta.index or c in tb.index]
    side[f"{a} vs {b}"] = [{"category": c,
                            "kr_mention": float(ta["mention_pct"].get(c, 0)), "kr_neg": float(ta["neg_pct"].get(c, float("nan"))) if c in ta.index else None,
                            "gl_mention": float(tb["mention_pct"].get(c, 0)), "gl_neg": float(tb["neg_pct"].get(c, float("nan"))) if c in tb.index else None}
                           for c in idx]
rep["side_by_side"] = side

# 4) 같은 앱 국내 vs 해외 (전체 리뷰 기준 평균 별점)
krall = pd.read_csv(os.path.join(OUT, "classified_reviews.csv"))
glall = pd.read_csv(os.path.join(G, "global_reviews.csv"))
SAME = {"Tesla": "Tesla", "My BMW": "My BMW", "MINI": "MINI", "Mercedes-Benz": "Mercedes-Benz", "Volvo Cars": "Volvo Cars",
        "Polestar": "Polestar", "myChevrolet": "myChevrolet", "My Volkswagen": "Volkswagen", "myAudi": "myAudi",
        "My Porsche": "My Porsche", "BYD AUTO": "BYD AUTO", "마이현대": "myHyundai (EU)", "Kia App(기아)": "Kia App (EU)",
        "MY GENESIS": "MY GENESIS (EU)"}
same = []
for k, g in SAME.items():
    a, b = krall[krall.app == k]["score"], glall[glall.app == g]["score"]
    same.append({"국내 앱": k, "해외 앱": g, "국내 리뷰": int(len(a)), "국내 평균": round(a.mean(), 2), "국내 부정%": round((a <= 2).mean() * 100, 1),
                 "해외 리뷰": int(len(b)), "해외 평균": round(b.mean(), 2) if len(b) else None, "해외 부정%": round((b <= 2).mean() * 100, 1) if len(b) else None})
pd.DataFrame(same).to_csv(os.path.join(G, "same_app_kr_vs_global.csv"), index=False, encoding="utf-8-sig")
rep["same_app"] = same

# 5) 해외 리워드·DR 앱 심층
rw = gl[gl.group_ko == "해외 리워드·DR 앱"]
rep["reward_dr"] = {"summary": summary(rw), "aspects": aspects(rw, "해외 리워드·DR 앱"),
                    "by_app": {a: summary(f) for a, f in rw.groupby("app")},
                    "evidence": {c: {s: [x["evidence"] for a in rw["aspects"] for x in a if x["category"] == c and x["sentiment"] == s][:10]
                                     for s in ("긍정", "부정")} for c in ["포인트·리워드·멤버십", "요금·가격", "원격제어·차량연결", "앱 안정성·오류", "기타"]}}
# 국내 리워드 언급 감성 vs 해외 리워드·DR 앱의 리워드 언급 감성
rep["reward_aspect_kr_vs_global"] = {
    "kr_charge": tbl[(tbl.group == "충전 앱") & (tbl.category == "포인트·리워드·멤버십")].to_dict("records"),
    "global_reward": tbl[(tbl.group == "해외 리워드·DR 앱") & (tbl.category == "포인트·리워드·멤버십")].to_dict("records"),
    "global_charge": tbl[(tbl.group == "해외 충전 앱") & (tbl.category == "포인트·리워드·멤버십")].to_dict("records")}

json.dump(rep, open(os.path.join(G, "compare.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1, default=str)
print(json.dumps({k: rep[k] for k in ("kr_n", "global_n", "global_models", "human_vs_gemini_global")}, ensure_ascii=False)[:1800])
