"""해외 앱의 iOS ID를 iTunes Search API(미국·영국)로 찾는다. 첫 결과를 후보로 저장하고 사람이 확인한다."""
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

sys.path.insert(0, os.path.dirname(__file__))
from apps_global import APPS_GLOBAL  # noqa: E402

TERMS = {
    "MyHyundai with Bluelink (US)": "MyHyundai with Bluelink", "Kia Access (US)": "Kia Access",
    "Genesis Intelligent Assistant (US)": "Genesis Intelligent Assistant", "myHyundai (EU)": "myHyundai",
    "Kia App (EU)": "Kia App", "MY GENESIS (EU)": "MY GENESIS", "Charge myHyundai (EU)": "Charge myHyundai",
    "Tesla": "Tesla", "My BMW": "My BMW", "MINI": "MINI app", "Mercedes-Benz": "Mercedes-Benz",
    "Volvo Cars": "Volvo Cars", "Polestar": "Polestar", "myChevrolet": "myChevrolet", "Volkswagen": "Volkswagen",
    "myAudi": "myAudi", "My Porsche": "My Porsche", "BYD AUTO": "BYD AUTO", "ChargePoint": "ChargePoint",
    "Electrify America": "Electrify America", "EVgo": "EVgo", "PlugShare": "PlugShare",
    "Octopus Electroverse": "Octopus Electroverse", "IONITY": "IONITY", "Chargemap": "Chargemap",
    "Optiwatt": "Optiwatt", "ev.energy": "ev.energy", "Octopus Energy (EV 관련만)": "Octopus Energy",
}
out = {}
for group, name, country, app_id, _ in APPS_GLOBAL:
    q = urllib.parse.quote(TERMS[name])
    for wait in (0, 20, 40, 80):
        time.sleep(wait or 3)
        try:
            res = json.load(urllib.request.urlopen(f"https://itunes.apple.com/search?term={q}&country={country}&entity=software&limit=4", timeout=30))["results"]
            break
        except urllib.error.HTTPError as e:
            if e.code != 429:
                raise
    else:
        res = []
    out[f"{country}_{app_id}"] = [{"id": r["trackId"], "title": r["trackName"], "seller": r.get("sellerName"),
                                   "ratings": r.get("userRatingCount"), "avg": r.get("averageUserRating")} for r in res]
json.dump(out, sys.stdout, ensure_ascii=False, indent=1)
