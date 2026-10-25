"""iTunes Lookup API로 앱별 iOS 전체 평균 별점·평가 수를 받는다 (리뷰 본문이 아닌 전체 평점 통계)."""
import json
import os
import sys
import urllib.request

sys.path.insert(0, os.path.dirname(__file__))
from apps import APPS  # noqa: E402

SEARCH, OUT = sys.argv[1], sys.argv[2]
found = json.load(open(SEARCH, encoding="utf-8"))
ids = {name: found[name][0]["id"] for _, name, _ in APPS}
url = "https://itunes.apple.com/lookup?country=kr&id=" + ",".join(str(i) for i in ids.values())
res = {r["trackId"]: r for r in json.load(urllib.request.urlopen(url, timeout=30))["results"]}
out = {}
for group, name, app_id in APPS:
    r = res.get(ids[name], {})
    out[app_id] = {"group": group, "app": name, "ios_id": ids[name],
                   "ios_avg": r.get("averageUserRating"), "ios_count": r.get("userRatingCount")}
json.dump(out, open(OUT, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
for v in out.values():
    print(f"{v['group']}\t{v['app']}\t{v['ios_avg']}\t{v['ios_count']}")
