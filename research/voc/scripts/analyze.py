"""수집한 리뷰를 분류·집계하고 요약 표·인용문 샘플을 만든다.

입력: data/play/*.jsonl  출력: out/ (classified.csv, summary.md 재료 JSON, 인용문)
"""
import glob
import json
import os
import re
import sys

import pandas as pd

sys.path.insert(0, os.path.dirname(__file__))
from taxonomy import EV_FILTER, HYPO, PAIN  # noqa: E402

DATA, OUT = sys.argv[1], sys.argv[2]
os.makedirs(OUT, exist_ok=True)

rows = []
for p in glob.glob(os.path.join(DATA, "play", "*.jsonl")) + glob.glob(os.path.join(DATA, "ios", "*.jsonl")):
    with open(p, encoding="utf-8") as f:
        rows += [json.loads(line) for line in f if line.strip()]
df = pd.DataFrame(rows)
df["content"] = df["content"].fillna("").astype(str)
df = df.drop_duplicates(subset=["app_id", "review_id"])
df["at"] = pd.to_datetime(df["at"], errors="coerce", utc=True, format="ISO8601").dt.tz_localize(None)
df["year"] = df["at"].dt.year
df["text"] = df["content"].str.lower()
raw_total = len(df)

ev = re.compile(EV_FILTER, re.I)
navi_mask = df["group"] == "navi"
df = df[~navi_mask | df["text"].str.contains(ev)].copy()
df = df[df["text"].str.len() >= 5]  # "굿", "ㅇㅇ" 같은 초단문 제외

for name, pat in PAIN.items():
    df[name] = df["text"].str.contains(pat, regex=True)
for name, pat in HYPO.items():
    df[name] = df["text"].str.contains(pat, regex=True)
# 내비 앱의 '배터리'는 대부분 휴대폰 배터리 소모 불만이라 차량 배터리 분류에서 뺀다
df.loc[df["group"] == "navi", "배터리·전비"] = df.loc[df["group"] == "navi", "text"].str.contains(r"전비|전기차.{0,10}배터리|배터리.{0,10}(충전|잔량|%)", regex=True)
pain_cols, hypo_cols = list(PAIN), list(HYPO)
df["neg"] = df["score"] <= 2
df["any_pain"] = df[pain_cols].any(axis=1)

GROUP_KO = {"hmg": "현대차그룹", "oem": "경쟁 완성차", "charge": "충전 앱", "navi": "내비(EV 리뷰만)"}
df["group_ko"] = df["group"].map(GROUP_KO)
recent = df[df["at"] >= (df["at"].max() - pd.Timedelta(days=365))]

res = {"by_store": {k: int(v) for k, v in df["store"].value_counts().items()},
       "raw_total": int(raw_total), "analyzed_total": int(len(df)),
       "date_min": str(df["at"].min().date()), "date_max": str(df["at"].max().date()),
       "recent_total": int(len(recent))}

# 앱별 개요
apps = (df.groupby(["group_ko", "app"])
          .agg(리뷰수=("score", "size"), 평균별점=("score", "mean"),
               부정비율=("neg", "mean"), 최근1년=("at", lambda s: int((s >= recent["at"].min()).sum())))
          .reset_index().sort_values(["group_ko", "리뷰수"], ascending=[True, False]))
apps["평균별점"] = apps["평균별점"].round(2)
apps["부정비율"] = (apps["부정비율"] * 100).round(1)
apps.to_csv(os.path.join(OUT, "apps_overview.csv"), index=False, encoding="utf-8-sig")


def pain_share(frame):
    neg = frame[frame["neg"]]
    if len(neg) == 0:
        return pd.Series(dtype=float)
    return (neg[pain_cols].mean() * 100).round(1).sort_values(ascending=False)


# 그룹별 부정 리뷰 불편 비중 (전체 기간 / 최근 1년)
for label, frame in (("all", df), ("recent", recent)):
    tbl = pd.DataFrame({g: pain_share(frame[frame["group_ko"] == g]) for g in GROUP_KO.values()})
    tbl["전체"] = pain_share(frame)
    tbl = tbl.sort_values("전체", ascending=False)
    tbl.to_csv(os.path.join(OUT, f"pain_by_group_{label}.csv"), encoding="utf-8-sig")
    res[f"neg_count_{label}"] = {g: int(frame[(frame['group_ko'] == g) & frame['neg']].shape[0]) for g in GROUP_KO.values()}

# 공감수 가중 (많이 공감받은 불편 = 더 많은 사람이 겪는 문제)
w = df[df["neg"]].copy()
w["w"] = w["thumbs_up"].fillna(0) + 1
wt = {c: round(float((w[c] * w["w"]).sum() / w["w"].sum() * 100), 1) for c in pain_cols}
res["pain_weighted_by_thumbs"] = dict(sorted(wt.items(), key=lambda x: -x[1]))

# 충전 앱 앱별 상위 불편 3개
per_app = {}
for app, frame in df[df["group"] == "charge"].groupby("app"):
    s = pain_share(frame)
    per_app[app] = {k: float(v) for k, v in s.head(3).items()}
res["charge_app_top3"] = per_app
per_app_oem = {}
for app, frame in df[df["group"].isin(["hmg", "oem"])].groupby("app"):
    s = pain_share(frame)
    per_app_oem[app] = {k: float(v) for k, v in s.head(3).items()}
res["oem_app_top3"] = per_app_oem

# 연도별 추이 (충전 앱 + 현대차그룹 부정 리뷰)
trend = {}
for g in ("charge", "hmg", "oem"):
    sub = df[(df["group"] == g) & df["neg"] & df["year"].between(2021, 2026)]
    trend[GROUP_KO[g]] = {int(y): {"n": int(len(fr)), **{c: round(float(fr[c].mean() * 100), 1) for c in pain_cols}}
                          for y, fr in sub.groupby("year")}
res["trend"] = trend

# 가설별 근거: 언급 수, 언급 리뷰의 평균 별점, 그룹별 분포
hyp = {}
for h in hypo_cols:
    fr = df[df[h]]
    hyp[h] = {"mentions": int(len(fr)), "share_of_all_pct": round(len(fr) / len(df) * 100, 2),
              "avg_score": round(float(fr["score"].mean()), 2) if len(fr) else None,
              "neg_pct": round(float(fr["neg"].mean() * 100), 1) if len(fr) else None,
              "by_group": {k: int(v) for k, v in fr["group_ko"].value_counts().items()},
              "recent_1y": int(fr["at"].ge(recent["at"].min()).sum())}
res["hypotheses"] = hyp

# iOS 리뷰는 앱당 약 10건뿐이라 그룹 단위로만 비교한다
ios = df[df["store"] == "ios"]
res["ios_reviews"] = {"n": int(len(ios)), "neg_pct": round(float(ios["neg"].mean() * 100), 1),
                      "pain_share_neg": {k: float(v) for k, v in pain_share(ios).head(10).items()},
                      "by_group": {g: {"n": int(len(fr)), "avg": round(float(fr["score"].mean()), 2)}
                                   for g, fr in ios.groupby("group_ko")}}
play = df[df["store"] == "play"]
res["play_pain_share_neg_top10"] = {k: float(v) for k, v in pain_share(play).head(10).items()}

# 전체 평점(별점만 준 사용자 포함) vs 리뷰 본문 평균 — 리뷰 작성자 편향 확인용
try:
    ios_r = json.load(open(os.path.join(DATA, "ios_ratings.json"), encoding="utf-8"))
    play_r = json.load(open(os.path.join(DATA, "play", "_play_meta.json"), encoding="utf-8"))
    rows_r = []
    for app_id, v in ios_r.items():
        txt = df[df["app_id"] == app_id]["score"]
        rows_r.append({"그룹": GROUP_KO[v["group"]], "앱": v["app"],
                       "Play 전체평점": round(play_r.get(app_id, {}).get("score") or 0, 2),
                       "Play 평가수": play_r.get(app_id, {}).get("ratings"),
                       "iOS 전체평점": round(v["ios_avg"] or 0, 2), "iOS 평가수": v["ios_count"],
                       "리뷰본문 평균": round(float(txt.mean()), 2) if len(txt) else None})
    pd.DataFrame(rows_r).to_csv(os.path.join(OUT, "ratings_vs_reviews.csv"), index=False, encoding="utf-8-sig")
except FileNotFoundError:
    pass

json.dump(res, open(os.path.join(OUT, "summary.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)

# 인용문 샘플: 카테고리·가설별 공감수 상위 + 최근 리뷰
def samples(mask, n=12):
    fr = df[mask].copy()
    fr["len"] = fr["content"].str.len()
    fr = fr[(fr["len"] >= 25) & (fr["len"] <= 400)]
    top = fr.sort_values(["thumbs_up", "at"], ascending=False).head(n)
    return [{"app": r.app, "score": int(r.score), "thumbs": int(r.thumbs_up or 0),
             "date": str(r.at.date()) if pd.notna(r.at) else "", "text": r.content.replace("\n", " ")}
            for r in top.itertuples()]

quotes = {f"[불편] {c}": samples(df[c] & df["neg"]) for c in pain_cols}
quotes.update({f"[가설] {h}": samples(df[h], 15) for h in hypo_cols})
json.dump(quotes, open(os.path.join(OUT, "quotes.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)

cols = ["group_ko", "app_id", "review_id", "app", "store", "score", "thumbs_up", "at", "content"] + pain_cols + hypo_cols
df[cols].to_csv(os.path.join(OUT, "classified_reviews.csv"), index=False, encoding="utf-8-sig")
print(json.dumps({k: res[k] for k in ("raw_total", "analyzed_total", "date_min", "date_max", "recent_total")}, ensure_ascii=False))
