"""iTunes Search API로 앱별 iOS ID 후보를 찾는다."""
import json
import os
import sys
import urllib.parse
import urllib.request

sys.path.insert(0, os.path.dirname(__file__))
from apps import APPS  # noqa: E402

TERMS = {
    "마이현대": "마이현대", "Kia App(기아)": "Kia 기아", "MY GENESIS": "MY GENESIS", "E-pit(현대 충전)": "E-pit 이피트",
    "Tesla": "Tesla", "My BMW": "My BMW", "MINI": "MINI app BMW", "Mercedes-Benz": "Mercedes-Benz", "Volvo Cars": "Volvo Cars",
    "Polestar": "Polestar", "My Renault": "My Renault 르노코리아", "myChevrolet": "myChevrolet", "KGM LINK": "KGM LINK",
    "My Volkswagen": "My Volkswagen", "myAudi": "myAudi", "My Porsche": "My Porsche", "BYD AUTO": "BYD AUTO",
    "렉서스 커넥트": "렉서스 커넥트", "EV Infra": "EV Infra", "차지비(GS)": "차지비", "일렉베리": "일렉베리", "채비": "채비 CHAEVI",
    "SK일렉링크": "SK일렉링크", "에버온": "에버온", "파워큐브": "파워큐브", "플러그링크": "플러그링크", "볼트업(LGU+)": "볼트업",
    "EV이음(환경부)": "EV이음", "KEPCO PLUG(한전)": "KEPCO PLUG", "이지차저": "이지차저", "스타코프": "스타코프",
    "워터": "워터 전기차 충전", "모두의충전": "모두의충전", "투루차저(휴맥스)": "투루차저", "EVSIS": "EVSIS", "해피차저": "HappeCharger",
    "티맵": "티맵", "카카오내비": "카카오내비",
}
out = {}
for group, name, _ in APPS:
    q = urllib.parse.quote(TERMS[name])
    url = f"https://itunes.apple.com/search?term={q}&country=kr&entity=software&limit=5"
    with urllib.request.urlopen(url, timeout=30) as resp:
        res = json.load(resp)["results"]
    out[name] = [{"id": r["trackId"], "title": r["trackName"], "seller": r.get("sellerName"),
                  "ratings": r.get("userRatingCount")} for r in res]
json.dump(out, sys.stdout, ensure_ascii=False, indent=1)
