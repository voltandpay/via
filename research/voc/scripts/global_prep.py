"""해외 리뷰 정리 + Gemini 분류용 층화 표본 추출.

- 미국·영국 Play가 같은 영어 리뷰를 돌려주는 앱이 있어 (앱 ID, 리뷰 ID)로 중복 제거한다.
- 표본: 그룹별 층화(앱 비례, 앱당 최소 보장) + (그룹, 앱, 최근 여부) 층 가중치.
"""
import glob
import json
import os
import sys

import pandas as pd

DATA, OUT = sys.argv[1], sys.argv[2]
SEED = 20261025
rows = []
for p in glob.glob(os.path.join(DATA, "*.jsonl")):
    rows += [json.loads(line) for line in open(p, encoding="utf-8") if line.strip()]
df = pd.DataFrame(rows)
raw = len(df)
df = df.drop_duplicates(["app_id", "review_id"])
df["content"] = df["content"].fillna("").astype(str)
df = df[df["content"].str.len() >= 10]
df["at"] = pd.to_datetime(df["at"], errors="coerce", utc=True, format="ISO8601").dt.tz_localize(None)
GK = {"g_hmg": "해외 현대차그룹", "g_oem": "해외 경쟁 완성차", "g_charge": "해외 충전 앱", "g_reward": "해외 리워드·DR 앱"}
df["group_ko"] = df["group"].map(GK)
# 같은 앱의 미국·영국판 이름을 하나로 (Tesla us/gb → Tesla)
df["app"] = df["app"].str.replace(r" \((US|EU)\)$", lambda m: f" ({m.group(1)})", regex=True)
df["uid"] = df["app_id"] + "|" + df["review_id"]
df["recent"] = df["at"] >= df["at"].max() - pd.Timedelta(days=365)
df.to_csv(os.path.join(OUT, "global_reviews.csv"), index=False, encoding="utf-8-sig")

ov = (df.groupby(["group_ko", "app"]).agg(리뷰수=("score", "size"), 평균별점=("score", "mean"),
                                           부정비율=("score", lambda s: (s <= 2).mean() * 100),
                                           최초=("at", "min"), 최근=("at", "max"))
        .round(2).reset_index())
ov.to_csv(os.path.join(OUT, "global_apps_overview.csv"), index=False, encoding="utf-8-sig")

PLAN = {"해외 현대차그룹": (800, 40), "해외 경쟁 완성차": (800, 25), "해외 충전 앱": (800, 40), "해외 리워드·DR 앱": (600, 150)}
parts = []
for g, (n_total, min_per_app) in PLAN.items():
    gdf = df[df["group_ko"] == g]
    sizes = gdf["app"].value_counts()
    alloc = (sizes / sizes.sum() * n_total).round().astype(int).clip(lower=min_per_app).clip(upper=sizes)
    while alloc.sum() > n_total:
        alloc[alloc.idxmax()] -= 1
    for app, k in alloc.items():
        parts.append(gdf[gdf["app"] == app].sample(int(k), random_state=SEED))
smp = pd.concat(parts)
key = ["group_ko", "app", "recent"]
smp = smp.join((df.groupby(key).size() / smp.groupby(key).size()).rename("weight"), on=key)
smp["set"] = "main"
smp.to_csv(os.path.join(OUT, "llm_sample_global.csv"), index=False, encoding="utf-8-sig")
print(json.dumps({"raw": raw, "dedup_valid": len(df), "by_group": df["group_ko"].value_counts().to_dict(),
                  "sample": len(smp), "sample_by_group": smp["group_ko"].value_counts().to_dict(),
                  "date": [str(df["at"].min().date()), str(df["at"].max().date())]}, ensure_ascii=False))
