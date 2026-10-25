"""해외(미국·영국) Google Play 영어 리뷰 수집. 앱·국가별 JSONL, 재실행 시 완료분 건너뜀."""
import json
import os
import re
import sys
import time

from google_play_scraper import Sort, app as app_info, reviews

sys.path.insert(0, os.path.dirname(__file__))
from apps_global import APPS_GLOBAL, CAP_GLOBAL, CAP_GLOBAL_FILTERED, EV_FILTER_EN  # noqa: E402

OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
ev = re.compile(EV_FILTER_EN, re.I)
meta = {}
for group, name, country, app_id, filtered in APPS_GLOBAL:
    key = f"{country}_{app_id}"
    path = os.path.join(OUT, key + ".jsonl")
    try:
        info = app_info(app_id, lang="en", country=country)
        meta[key] = {"group": group, "name": name, "country": country, "app_id": app_id,
                     "score": info.get("score"), "ratings": info.get("ratings"), "installs": info.get("installs")}
    except Exception as e:  # noqa: BLE001
        meta[key] = {"group": group, "name": name, "country": country, "app_id": app_id, "error": str(e)}
    if os.path.exists(path + ".done"):
        print(f"[skip] {name} {country}", flush=True)
        continue
    cap = CAP_GLOBAL_FILTERED if filtered else CAP_GLOBAL
    n = kept = 0
    token = None
    with open(path, "w", encoding="utf-8") as f:
        while n < cap:
            try:
                batch, token = reviews(app_id, lang="en", country=country, sort=Sort.NEWEST,
                                       count=200, continuation_token=token)
            except Exception as e:  # noqa: BLE001
                print(f"[오류] {name} {country} {n}건 후: {e}", flush=True)
                time.sleep(5)
                continue
            for r in batch:
                text = r["content"] or ""
                if filtered and not ev.search(text):
                    continue
                f.write(json.dumps({
                    "app_id": app_id, "group": group, "app": name, "country": country, "store": "play",
                    "review_id": r["reviewId"], "score": r["score"], "content": text,
                    "thumbs_up": r["thumbsUpCount"], "app_version": r.get("reviewCreatedVersion"),
                    "at": r["at"].isoformat() if r.get("at") else None, "reply": r.get("replyContent"),
                }, ensure_ascii=False) + "\n")
                kept += 1
            n += len(batch)
            if not batch or token is None or token.token is None:
                break
    open(path + ".done", "w").close()
    print(f"[완료] {name} {country}: {n}건 중 {kept}건 저장", flush=True)
with open(os.path.join(OUT, "_meta.json"), "w", encoding="utf-8") as f:
    json.dump(meta, f, ensure_ascii=False, indent=1)
print("ALL_DONE", flush=True)
