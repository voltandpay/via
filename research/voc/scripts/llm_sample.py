"""Gemini 감성분석용 표본을 뽑는다.

- main (2,400건): 그룹별 층화 + 그룹 안에서 앱 비례(앱당 최소 보장) + 최근 1년 비중 확대.
  비중(%) 추정에 쓴다. 그룹별 추출률이 달라 그룹 간 합산 시에는 가중치(weight)를 쓴다.
- boost (300건): 리워드(150)·요금(150) 키워드 리뷰를 추가로 뽑아 속성별 감성의 표본을 늘린다.
  비중 추정에는 쓰지 않는다.
"""
import sys

import pandas as pd

SRC, OUT = sys.argv[1], sys.argv[2]
SEED = 20261025
df = pd.read_csv(SRC, parse_dates=["at"])
df["uid"] = df["app_id"].astype(str) + "|" + df["review_id"].astype(str)
df = df[df["content"].astype(str).str.len() >= 10]
recent_cut = df["at"].max() - pd.Timedelta(days=365)
df["recent"] = df["at"] >= recent_cut

PLAN = {"충전 앱": (1000, 15), "현대차그룹": (700, 60), "경쟁 완성차": (500, 12), "내비(EV 리뷰만)": (200, 80)}
RECENT_SHARE = {"충전 앱": 0.5, "현대차그룹": 0.6, "경쟁 완성차": 0.4, "내비(EV 리뷰만)": 0.0}

parts = []
for g, (n_total, min_per_app) in PLAN.items():
    gdf = df[df["group_ko"] == g]
    sizes = gdf["app"].value_counts()
    alloc = (sizes / sizes.sum() * n_total).round().astype(int).clip(lower=min_per_app)
    alloc = alloc.clip(upper=sizes)
    while alloc.sum() > n_total:  # 최소 보장으로 넘친 만큼 큰 앱에서 덜어낸다
        alloc[alloc.idxmax()] -= 1
    for app, k in alloc.items():
        adf = gdf[gdf["app"] == app]
        k_recent = min(int(round(k * RECENT_SHARE[g])), int(adf["recent"].sum()))
        a = adf[adf["recent"]].sample(k_recent, random_state=SEED)
        rest = adf.drop(a.index)
        b = rest.sample(min(k - k_recent, len(rest)), random_state=SEED)
        parts.append(pd.concat([a, b]))
main = pd.concat(parts)
main["set"] = "main"
# 추정 가중치: (그룹, 앱, 최근 여부) 층의 모집단 수 / 표본 수
key = ["group_ko", "app", "recent"]
pop = df.groupby(key).size().rename("N")
smp = main.groupby(key).size().rename("n")
w = (pop / smp).rename("weight")
main = main.join(w, on=key)

pool = df[~df["uid"].isin(main["uid"])]
boost = pd.concat([
    pool[pool["포인트·리워드·멤버십"] | pool["H6 리워드 반응"]].sample(150, random_state=SEED),
    pool[pool["요금·가격"] & (pool["group_ko"] == "충전 앱")].sample(150, random_state=SEED + 1),
]).drop_duplicates("uid")
boost["set"] = "boost"
boost["weight"] = 0.0

out = pd.concat([main, boost])
out.to_csv(OUT, index=False, encoding="utf-8-sig")
print("main", len(main), main["group_ko"].value_counts().to_dict())
print("boost", len(boost), "| 최근1년 비중(main)", round(main["recent"].mean() * 100, 1), "% | 부정(main)", round((main["score"] <= 2).mean() * 100, 1), "%")
