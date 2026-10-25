"""Google Play에서 검색어별 상위 앱 ID를 찾는다 (수집 대상 확정용)."""
import json
import sys

from google_play_scraper import search

QUERIES = [
    # 현대차그룹
    "마이현대", "현대 블루링크", "기아 커넥트", "마이기아", "제네시스 앱", "E-pit 충전",
    # 경쟁 완성차
    "테슬라", "My BMW", "Mercedes me", "볼보 카스", "폴스타", "KG모빌리티", "르노코리아",
    "쉐보레", "아우디", "폭스바겐", "포르쉐", "BYD", "토요타 렉서스", "MINI",
    # 충전 앱
    "전기차 충전", "EV Infra", "차지비", "일렉베리", "채비 충전", "SK일렉링크", "에버온",
    "파워큐브", "플러그링크", "볼트업", "환경부 전기차 충전", "한전 전기차 충전",
    "티맵", "카카오내비", "이지차저", "스타코프", "휴맥스", "워터 충전",
]

out = {}
for q in QUERIES:
    try:
        res = search(q, lang="ko", country="kr", n_hits=8)
    except Exception as e:  # noqa: BLE001
        print(f"[{q}] 실패: {e}", file=sys.stderr)
        continue
    out[q] = [
        {"appId": r["appId"], "title": r["title"], "developer": r.get("developer"),
         "score": r.get("score"), "installs": r.get("installs")}
        for r in res
    ]
json.dump(out, sys.stdout, ensure_ascii=False, indent=1)
