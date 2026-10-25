"""App Store(한국) 공개 앱 페이지에 실린 리뷰를 앱별 JSONL로 받는다.

Apple 공개 RSS 리뷰 피드는 2026-10 현재 빈 응답을 돌려줘서, 브라우저로 보이는
'평가 및 리뷰' 페이지(see-all=reviews)에 포함된 리뷰만 수집한다. 앱당 약 10건.
"""
import json
import os
import re
import sys
import time
import urllib.request

sys.path.insert(0, os.path.dirname(__file__))
from apps import APPS  # noqa: E402

SEARCH, OUT = sys.argv[1], sys.argv[2]
os.makedirs(OUT, exist_ok=True)
found = json.load(open(SEARCH, encoding="utf-8"))
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


total = 0
for group, name, app_id in APPS:
    ios_id = found[name][0]["id"]
    url = f"https://apps.apple.com/kr/app/id{ios_id}?see-all=reviews"
    try:
        req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept-Language": "ko-KR"})
        html = urllib.request.urlopen(req, timeout=30).read().decode("utf-8")
        m = re.search(r'<script type="application/json" id="serialized-server-data">(.*?)</script>', html, re.S)
        items = []
        reviews_in(json.loads(m.group(1)), items) if m else None
    except Exception as e:  # noqa: BLE001
        print(f"[오류] {name}: {e}", flush=True)
        items = []
    seen, rows = set(), []
    for r in items:
        if r.get("id") in seen:
            continue
        seen.add(r.get("id"))
        rows.append({
            "app_id": app_id, "group": group, "app": name, "store": "ios",
            "review_id": r.get("id"), "score": int(r.get("rating") or 0),
            "content": ((r.get("title") or "") + "\n" + (r.get("contents") or "")).strip(),
            "thumbs_up": 0, "app_version": None, "at": r.get("date"),
            "reply": (r.get("response") or {}).get("contents"),
        })
    with open(os.path.join(OUT, f"{app_id}.ios.jsonl"), "w", encoding="utf-8") as f:
        for row in rows:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")
    total += len(rows)
    print(f"[완료] {name} (ios {ios_id}): {len(rows)}건", flush=True)
    time.sleep(1)
print(f"ALL_DONE {total}", flush=True)
