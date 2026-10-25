"""Gemini로 리뷰별 전체 감성 + 속성(카테고리)별 감성을 판정한다.

입력: out/llm_sample.csv  출력: out/llm_results.jsonl (리뷰 1건당 1줄, 재실행 시 이미 받은 리뷰는 건너뜀)
"""
import json
import os
import sys
import time

import pandas as pd

sys.path.insert(0, os.path.dirname(__file__))
from gemini_client import generate  # noqa: E402

SAMPLE, OUT = sys.argv[1], sys.argv[2]
MODELS = ["gemini-3.5-flash", "gemini-3.5-flash-lite"]
BATCH, PAUSE = 25, 7

CATEGORIES = [
    "앱 안정성·오류", "로그인·인증", "결제·청구 오류", "요금·가격", "충전기 고장·충전 실패",
    "충전기 정보 부정확", "대기·혼잡·점유", "원격제어·차량연결", "디지털키", "경로·내비 (일반)",
    "배터리·전비", "포인트·리워드·멤버십", "UI·UX 불편", "고객센터·응대", "알림",
    "아파트·완속 요금 격차", "미출차·점유 수수료", "리워드 축소·소멸 불만",
    "충전 인프라(충전소 수·위치·속도)", "기타",
]
GUIDE = """너는 한국 자동차·전기차 충전 앱 리뷰를 분석하는 VOC 분석가다.
각 리뷰에 대해 아래를 판정해 JSON 배열로만 답한다. 리뷰 순서와 id를 그대로 유지한다.

1) overall: 리뷰 글 전체의 감성. 별점이 아니라 '글의 내용'으로 판단한다.
   - 긍정 / 부정 / 중립(정보·질문 위주) / 혼합(뚜렷한 긍정과 부정이 함께 있음)
2) sarcasm: 반어·비꼼으로 겉말과 속뜻이 반대이면 true (예: "너무 좋은 앱 잘 썼습니다ㅋㅋ 진짜 앱이 왜 있는건지").
3) request: 기능 추가·개선 요청이 들어 있으면 true.
4) aspects: 리뷰가 실제로 언급한 주제(카테고리)마다 하나씩. 언급하지 않은 카테고리는 넣지 않는다.
   - sentiment: 그 주제에 대한 감성 (긍정/부정/중립)
   - evidence: 근거가 되는 원문 구절 (30자 이내, 원문 그대로)

카테고리 정의:
- 앱 안정성·오류: 앱 자체의 오류·먹통·튕김·느림·로딩. (충전기 고장은 제외)
- 로그인·인증: 가입·로그인·본인인증·로그아웃 문제
- 결제·청구 오류: 카드 등록·결제 실패·이중결제·환불·청구 문제
- 요금·가격: 충전 요금·서비스 요금 수준, 인상, 회원가·로밍가·비회원가
- 충전기 고장·충전 실패: 충전기 고장, 충전 시작 안 됨·중단
- 충전기 정보 부정확: 앱에 표시된 충전기 상태·위치·요금 정보가 틀림
- 대기·혼잡·점유: 충전 대기 줄, 자리 없음, 다른 차의 점유
- 원격제어·차량연결: 원격시동·공조·문 잠금, 앱-차량 연결·통신
- 디지털키: 폰 키·디지털 키
- 경로·내비 (일반): 길 안내·경로·목적지·경로상 충전소
- 배터리·전비: 차량 배터리·전비·주행가능거리 (휴대폰 배터리는 '앱 안정성·오류'로)
- 포인트·리워드·멤버십: 포인트·적립·쿠폰·멤버십·혜택
- UI·UX 불편: 화면 구성·메뉴·사용 흐름·가독성 (칭찬이면 sentiment 긍정)
- 고객센터·응대: 상담·문의·답변·응대
- 알림: 푸시·알림
- 아파트·완속 요금 격차: 아파트·완속 충전 요금이 다른 곳보다 비쌈
- 미출차·점유 수수료: 충전 완료 후 미출차·점유 수수료
- 리워드 축소·소멸 불만: 혜택 축소, 포인트 소멸·사용 불가
- 충전 인프라(충전소 수·위치·속도): 충전소가 많다/적다, 위치, 충전 속도
- 기타: 위에 없는 주제

같은 내용이 두 카테고리에 걸리면 더 구체적인 쪽 하나만 쓴다 (예: 포인트 소멸 → '리워드 축소·소멸 불만').
"""

SCHEMA = {
    "type": "ARRAY",
    "items": {
        "type": "OBJECT",
        "properties": {
            "id": {"type": "STRING"},
            "overall": {"type": "STRING", "enum": ["긍정", "부정", "중립", "혼합"]},
            "sarcasm": {"type": "BOOLEAN"},
            "request": {"type": "BOOLEAN"},
            "aspects": {"type": "ARRAY", "items": {
                "type": "OBJECT",
                "properties": {
                    "category": {"type": "STRING", "enum": CATEGORIES},
                    "sentiment": {"type": "STRING", "enum": ["긍정", "부정", "중립"]},
                    "evidence": {"type": "STRING"},
                },
                "required": ["category", "sentiment", "evidence"],
            }},
        },
        "required": ["id", "overall", "sarcasm", "request", "aspects"],
    },
}

df = pd.read_csv(SAMPLE)
done = set()
if os.path.exists(OUT):
    with open(OUT, encoding="utf-8") as f:
        done = {json.loads(line)["uid"] for line in f if line.strip()}
todo = df[~df["uid"].isin(done)].reset_index(drop=True)
print(f"전체 {len(df)} / 완료 {len(done)} / 남음 {len(todo)}", flush=True)

model_i, tokens = 0, 0
for start in range(0, len(todo), BATCH):
    chunk = todo.iloc[start:start + BATCH]
    ids = {f"r{i + 1}": row.uid for i, row in enumerate(chunk.itertuples())}
    lines = [f'[id={k}] {str(r.content)[:600]}' for k, r in zip(ids, chunk.itertuples())]
    prompt = GUIDE + "\n리뷰 목록:\n" + "\n".join(lines)
    while True:
        try:
            text, usage = generate(MODELS[model_i], prompt, schema=SCHEMA)
            items = json.loads(text)
            break
        except Exception as e:  # noqa: BLE001
            msg = str(e)
            if ("429" in msg or "RESOURCE_EXHAUSTED" in msg) and model_i + 1 < len(MODELS):
                model_i += 1
                print(f"[한도] 모델 전환 → {MODELS[model_i]}", flush=True)
                continue
            print(f"[실패] 배치 {start}: {msg[:200]}", flush=True)
            items = None
            break
    if items is None:
        if "429" in msg:
            print("STOP_QUOTA", flush=True)
            break
        continue
    tokens += usage.get("totalTokenCount", 0)
    got = 0
    with open(OUT, "a", encoding="utf-8") as f:
        for it in items:
            uid = ids.get(it.get("id"))
            if not uid:
                continue
            it["uid"], it["model"] = uid, MODELS[model_i]
            f.write(json.dumps(it, ensure_ascii=False) + "\n")
            got += 1
    print(f"[{start + len(chunk)}/{len(todo)}] {got}/{len(chunk)}건 저장, 누적 토큰 {tokens:,}", flush=True)
    time.sleep(PAUSE)
print("ALL_DONE", flush=True)
