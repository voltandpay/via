"""Google Play 한국 리뷰를 앱별 JSONL로 수집한다. 이미 받은 앱은 건너뛴다(재실행 가능)."""
import json
import os
import sys
import time

from google_play_scraper import Sort, app as app_info, reviews

sys.path.insert(0, os.path.dirname(__file__))
from apps import APPS, CAP  # noqa: E402

OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)

meta = {}
for group, name, app_id in APPS:
    path = os.path.join(OUT, f"{app_id}.jsonl")
    try:
        info = app_info(app_id, lang="ko", country="kr")
        meta[app_id] = {"group": group, "name": name, "title": info.get("title"),
                        "score": info.get("score"), "ratings": info.get("ratings"),
                        "reviews_total": info.get("reviews"), "installs": info.get("installs"),
                        "updated": info.get("updated")}
    except Exception as e:  # noqa: BLE001
        meta[app_id] = {"group": group, "name": name, "error": str(e)}
        print(f"[meta 실패] {name}: {e}", flush=True)
    if os.path.exists(path + ".done"):
        print(f"[skip] {name}", flush=True)
        continue

    cap, n, token = CAP[group], 0, None
    with open(path, "w", encoding="utf-8") as f:
        while n < cap:
            try:
                batch, token = reviews(app_id, lang="ko", country="kr", sort=Sort.NEWEST,
                                       count=200, continuation_token=token)
            except Exception as e:  # noqa: BLE001
                print(f"[오류] {name} {n}건 후: {e}", flush=True)
                time.sleep(5)
                continue
            for r in batch:
                f.write(json.dumps({
                    "app_id": app_id, "group": group, "app": name, "store": "play",
                    "review_id": r["reviewId"], "score": r["score"], "content": r["content"],
                    "thumbs_up": r["thumbsUpCount"], "app_version": r.get("reviewCreatedVersion"),
                    "at": r["at"].isoformat() if r.get("at") else None,
                    "reply": r.get("replyContent"),
                }, ensure_ascii=False) + "\n")
            n += len(batch)
            if not batch or token is None or token.token is None:
                break
    open(path + ".done", "w").close()
    print(f"[완료] {name}: {n}건", flush=True)

with open(os.path.join(OUT, "_play_meta.json"), "w", encoding="utf-8") as f:
    json.dump(meta, f, ensure_ascii=False, indent=1)
print("ALL_DONE", flush=True)
