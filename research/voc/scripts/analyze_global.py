"""해외 리뷰 전수 분석 — 국내 analyze.py와 같은 항목.

입력: data/global/*.jsonl(Play), data/global_ios/*.jsonl(iOS), 메타(_meta.json, _ios_ratings.json)
출력: out/global/full/ (apps_overview, pain_by_group_*, summary.json, quotes.json, ratings_vs_reviews, classified_reviews)
"""
import glob
import json
import os
import sys

import pandas as pd

sys.path.insert(0, os.path.dirname(__file__))
from taxonomy_en import HYPO, PAIN  # noqa: E402

DATA, OUT = sys.argv[1], sys.argv[2]
os.makedirs(OUT, exist_ok=True)
rows = []
for p in glob.glob(os.path.join(DATA, "global", "*.jsonl")) + glob.glob(os.path.join(DATA, "global_ios", "*.jsonl")):
    rows += [json.loads(x) for x in open(p, encoding="utf-8") if x.strip()]
df = pd.DataFrame(rows)
df["content"] = df["content"].fillna("").astype(str)
raw = len(df)
df = df.drop_duplicates(["app_id", "review_id"])
df = df[df["content"].str.len() >= 5].copy()
df["at"] = pd.to_datetime(df["at"], errors="coerce", utc=True, format="ISO8601").dt.tz_localize(None)
df["year"] = df["at"].dt.year
df["text"] = df["content"].str.lower()
GK = {"g_hmg": "해외 현대차그룹", "g_oem": "해외 경쟁 완성차", "g_charge": "해외 충전 앱", "g_reward": "해외 리워드·DR 앱"}
df["group_ko"] = df["group"].map(GK)
pain, hypo = list(PAIN), list(HYPO)
for n, pat in PAIN.items():
    df[n] = df["text"].str.contains(pat, regex=True)
for n, pat in HYPO.items():
    df[n] = df["text"].str.contains(pat, regex=True)
df["neg"] = df["score"] <= 2
cut = df["at"].max() - pd.Timedelta(days=365)
recent = df[df["at"] >= cut]
res = {"raw_total": int(raw), "analyzed_total": int(len(df)), "by_store": df["store"].value_counts().to_dict(),
       "date_min": str(df["at"].min().date()), "date_max": str(df["at"].max().date()), "recent_total": int(len(recent)),
       "label_coverage_neg": round(float(df[df["neg"]][pain].any(axis=1).mean() * 100), 1),
       "score_dist": (df["score"].value_counts(normalize=True).sort_index() * 100).round(1).to_dict()}

ov = (df.groupby(["group_ko", "app"]).agg(리뷰수=("score", "size"), 평균별점=("score", "mean"), 부정비율=("neg", "mean"),
                                          최근1년=("at", lambda s: int((s >= cut).sum()))).reset_index())
ov["평균별점"] = ov["평균별점"].round(2); ov["부정비율"] = (ov["부정비율"] * 100).round(1)


def share(frame):
    neg = frame[frame["neg"]]
    return (neg[pain].mean() * 100).round(1).sort_values(ascending=False) if len(neg) else pd.Series(dtype=float)


top3 = {}
for (g, a), fr in df.groupby(["group_ko", "app"]):
    top3[a] = {k: float(v) for k, v in share(fr).head(3).items()}
ov["상위 불편 3개"] = ov["app"].map(lambda a: " · ".join(f"{k} {v}" for k, v in top3.get(a, {}).items()))
ov.sort_values(["group_ko", "리뷰수"], ascending=[True, False]).to_csv(os.path.join(OUT, "apps_overview.csv"), index=False, encoding="utf-8-sig")

for label, frame in (("all", df), ("recent", recent)):
    t = pd.DataFrame({g: share(frame[frame["group_ko"] == g]) for g in GK.values()})
    t["전체"] = share(frame)
    t.sort_values("전체", ascending=False).to_csv(os.path.join(OUT, f"pain_by_group_{label}.csv"), encoding="utf-8-sig")
    res[f"neg_count_{label}"] = {g: int(frame[(frame["group_ko"] == g) & frame["neg"]].shape[0]) for g in GK.values()}

w = df[df["neg"]].copy(); w["w"] = w["thumbs_up"].fillna(0) + 1
res["pain_weighted_by_thumbs"] = dict(sorted({c: round(float((w[c] * w["w"]).sum() / w["w"].sum() * 100), 1) for c in pain}.items(), key=lambda x: -x[1]))

res["trend"] = {}
for g in GK.values():
    sub = df[(df["group_ko"] == g) & df["neg"] & df["year"].between(2021, 2026)]
    res["trend"][g] = {int(y): {"n": int(len(fr)), **{c: round(float(fr[c].mean() * 100), 1) for c in pain}} for y, fr in sub.groupby("year")}
res["volume_by_year"] = df[df["year"] >= 2019].groupby(["year", "group_ko"]).size().unstack(fill_value=0).astype(int).to_dict()

res["hypotheses"] = {h: {"mentions": int(df[h].sum()), "share_pct": round(float(df[h].mean() * 100), 2),
                         "avg_score": round(float(df[df[h]]["score"].mean()), 2) if df[h].any() else None,
                         "neg_pct": round(float(df[df[h]]["neg"].mean() * 100), 1) if df[h].any() else None,
                         "by_group": {k: int(v) for k, v in df[df[h]]["group_ko"].value_counts().items()},
                         "recent_1y": int(df[h][df["at"] >= cut].sum())} for h in hypo}

ios = df[df["store"] == "ios"]
res["ios_reviews"] = {"n": int(len(ios)), "neg_pct": round(float(ios["neg"].mean() * 100), 1),
                      "pain_share_neg": {k: float(v) for k, v in share(ios).head(10).items()},
                      "by_group": {g: {"n": int(len(f)), "avg": round(float(f["score"].mean()), 2)} for g, f in ios.groupby("group_ko")}}

# 전체 평점(Play·iOS, 국가별) vs 리뷰 본문 평균 (앱 기준)
pm = json.load(open(os.path.join(DATA, "global", "_meta.json"), encoding="utf-8"))
im = json.load(open(os.path.join(DATA, "global_ios", "_ios_ratings.json"), encoding="utf-8"))
rv = []
for key, v in pm.items():
    txt = df[(df["app_id"] == v["app_id"])]["score"]
    i = im.get(key, {})
    rv.append({"그룹": GK[v["group"]], "앱": v["name"], "국가": v["country"].upper(),
               "Play 전체평점": round(v.get("score") or 0, 2), "Play 평가수": v.get("ratings"),
               "iOS 전체평점": round(i.get("ios_avg") or 0, 2), "iOS 평가수": i.get("ios_count"),
               "리뷰본문 평균": round(float(txt.mean()), 2) if len(txt) else None})
pd.DataFrame(rv).to_csv(os.path.join(OUT, "ratings_vs_reviews.csv"), index=False, encoding="utf-8-sig")


def samples(mask, n=12):
    fr = df[mask].copy(); fr["len"] = fr["content"].str.len()
    fr = fr[(fr["len"] >= 40) & (fr["len"] <= 450)].sort_values(["thumbs_up", "at"], ascending=False).head(n)
    return [{"app": r.app, "score": int(r.score), "thumbs": int(r.thumbs_up or 0),
             "date": str(r.at.date()) if pd.notna(r.at) else "", "text": r.content.replace("\n", " ")} for r in fr.itertuples()]


quotes = {f"[불편] {c}": samples(df[c] & df["neg"]) for c in pain}
quotes.update({f"[가설] {h}": samples(df[h], 15) for h in hypo})
json.dump(quotes, open(os.path.join(OUT, "quotes.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
json.dump(res, open(os.path.join(OUT, "summary.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1, default=str)
cols = ["group_ko", "app_id", "review_id", "app", "country", "store", "score", "thumbs_up", "at", "content"] + pain + hypo
df[cols].to_csv(os.path.join(OUT, "classified_reviews.csv"), index=False, encoding="utf-8-sig")
print(json.dumps({k: res[k] for k in ("raw_total", "analyzed_total", "by_store", "date_min", "date_max", "recent_total", "label_coverage_neg")}, ensure_ascii=False))
