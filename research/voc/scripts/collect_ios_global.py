"""해외(미국·영국) App Store 공개 '평가 및 리뷰' 페이지 리뷰(앱당 약 10건) + 전체 평점(검색 API 결과) 저장."""
import json
import os
import re
import sys
import time
import urllib.request

sys.path.insert(0, os.path.dirname(__file__))
from apps_global import APPS_GLOBAL, EV_FILTER_EN  # noqa: E402

UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15"


def reviews_in(obj, out):
    if isinstance(obj, dict):
        if obj.get("$kind") == "Review":
            out.append(obj)
            return
        for v in obj.values():
            reviews_in(v, out)
    elif isinstance(obj, list):
        for v in obj:
            reviews_in(v, out)


SEARCH, OUT = sys.argv[1], sys.argv[2]
os.makedirs(OUT, exist_ok=True)
found = json.load(open(SEARCH, encoding="utf-8"))
ev = re.compile(EV_FILTER_EN, re.I)
ratings, total = {}, 0
for group, name, country, app_id, filtered in APPS_GLOBAL:
    key = f"{country}_{app_id}"
    top = found[key][0]
    ratings[key] = {"group": group, "app": name, "country": country, "ios_id": top["id"],
                    "ios_avg": top.get("avg"), "ios_count": top.get("ratings")}
    url = f"https://apps.apple.com/{country}/app/id{top['id']}?see-all=reviews"
    items = []
    try:
        req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept-Language": "en-GB,en"})
        html = urllib.request.urlopen(req, timeout=30).read().decode("utf-8")
        m = re.search(r'<script type="application/json" id="serialized-server-data">(.*?)</script>', html, re.S)
        if m:
            reviews_in(json.loads(m.group(1)), items)
    except Exception as e:  # noqa: BLE001
        print(f"[오류] {name} {country}: {e}", flush=True)
    rows, seen = [], set()
    for r in items:
        text = ((r.get("title") or "") + "\n" + (r.get("contents") or "")).strip()
        if r.get("id") in seen or (filtered and not ev.search(text)):
            continue
        seen.add(r.get("id"))
        rows.append({"app_id": app_id, "group": group, "app": name, "country": country, "store": "ios",
                     "review_id": "ios-" + str(r.get("id")), "score": int(r.get("rating") or 0), "content": text,
                     "thumbs_up": 0, "app_version": None, "at": r.get("date"),
                     "reply": (r.get("response") or {}).get("contents")})
    with open(os.path.join(OUT, key + ".ios.jsonl"), "w", encoding="utf-8") as f:
        for row in rows:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")
    total += len(rows)
    print(f"[완료] {name} {country}: {len(rows)}건 (iOS 전체 평점 {top.get('avg')}, {top.get('ratings')}명)", flush=True)
    time.sleep(1.5)
json.dump(ratings, open(os.path.join(OUT, "_ios_ratings.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(f"ALL_DONE {total}", flush=True)
