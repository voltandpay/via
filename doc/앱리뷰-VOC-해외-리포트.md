# 앱 리뷰 VOC 분석 리포트 ② 해외 — 미국·영국 현대차그룹·경쟁 완성차·EV 충전·리워드 앱 30종

| 항목 | 내용 |
|---|---|
| 문서 버전 | v2.0 (2026-10-25) — 국내·해외·비교 3개 문서로 분리 |
| 분석 대상 | 미국·영국 30개 앱, 영어 리뷰 **58,986건** (Google Play 58,589 + App Store 397) |
| 수집 원본 | Google Play 76,891건 (앱·국가 41개 조합) + App Store 397건 + 41개 앱·국가의 Play·iOS 전체 평점 |
| 감성·카테고리 (LLM) | Gemini 속성 기반 감성분석 3,000건, 블라인드 사람 판독 30건으로 검증 |
| 기간 | 2014-04-25 ~ 2026-10-24 (최근 1년 20,004건) |
| 원자료·코드 | `research/voc/` (`data/global`, `data/global_ios`, `out/global`) |
| 관련 문서 | **① [국내 리포트](앱리뷰-VOC-국내-리포트.md)** · **③ [국내·해외 비교 요약](앱리뷰-VOC-국내해외-비교.md)** · [유사사례 조사](유사사례-조사.md) |

> **이 리포트를 근거로 인용할 때**
> - 비중(%)은 **부정 리뷰(1~2점) 안에서 해당 불편이 언급된 비율**이다. 한 리뷰가 여러 불편에 걸릴 수 있어 합이 100%가 아니다.
> - 리뷰를 쓰는 사람은 불만층에 치우쳐 있다(6-7절). **"앱 리뷰에서 불만을 말한 사용자 중 N%"** 로 인용한다.
> - **키워드 분류의 비중은 '하한값'이다.** 영어 키워드는 Gemini 대비 재현율 20~75%(5-3절). **"얼마나 자주 언급되는가"는 6-9절 Gemini 추정치를 우선 인용**하고, 키워드 수치는 순위·추이 비교에 쓴다.
> - **리뷰가 많은 앱(Tesla, myChevrolet 등)은 수집 상한 때문에 최근 약 1년치만** 들어 있다. 국내와 같은 앱을 비교할 때는 같은 기간으로 맞춘 ③ 비교 요약의 표를 쓴다.
> - 국내·해외 비교는 이 문서가 아니라 **③ [비교 요약](앱리뷰-VOC-국내해외-비교.md)** 에 있다.

---

## 목차
1. [핵심 결과](#1-핵심-결과)
2. [조사 설계](#2-조사-설계)
3. [데이터 수집](#3-데이터-수집)
4. [전처리](#4-전처리)
5. [분류 체계와 정확도 검증](#5-분류-체계와-정확도-검증)
6. [분석 결과](#6-분석-결과)
7. [대표 리뷰 모음](#7-대표-리뷰-모음)
8. [프로젝트 시사점](#8-프로젝트-시사점)
9. [한계와 신뢰도](#9-한계와-신뢰도)
10. [재현 방법과 데이터 사전](#10-재현-방법과-데이터-사전)
11. [변경 이력](#11-변경-이력)

---

## 1. 핵심 결과

1. **해외에서도 1순위 불만은 '앱이 작동하지 않는다'다.** 부정 리뷰의 35.3%가 앱 오류·먹통, 22.3%가 원격제어·차량 연결 실패다. 공감 가중으로 보면 앱 안정성(39.8%)과 원격제어(32.0%)가 압도적이다.
2. **차량 앱(현대차그룹·경쟁 완성차)의 핵심 불만은 원격제어·차량 연결이다** (부정 리뷰의 27%대). 브랜드를 가리지 않는 구조적 문제다.
3. **충전 앱의 핵심 불만은 신뢰성과 결제다.** 충전기 고장·충전 실패(Gemini 기준 언급 14.1%), 충전기 정보 부정확(12.2%, 그중 74% 부정), 결제 오류(부정 리뷰의 16.4%, 2026년 23.3%).
4. **요금 불만이 꾸준히 늘고 있다.** 충전 앱 부정 리뷰의 요금 언급이 2022년 9.9%에서 2026년 21.7%로 올랐다.
5. **여러 사업자를 묶는 통합 앱이 단일 사업자 앱보다 높다.** PlugShare 4.05·Octopus Electroverse 3.86 vs ChargePoint 2.55·IONITY 2.67·Electrify America 2.91.
6. **유럽판 현대·기아 통합 앱은 매우 낮은 평가를 받는다.** 리뷰 평균 Kia App(EU) 1.74·myHyundai(EU) 1.89, Play 전체 평점도 3.1점대. 미국 기존 플랫폼(MyHyundai with Bluelink 2.83, Kia Access 3.14)보다 낮다. 불만은 앱 안정성·원격제어에 더해 **UI·UX(17~20%)** — "메뉴를 세 번 눌러야 같은 정보가 나온다", "차를 쓰게 돕기보다 새 차를 팔려 한다".
7. **리워드·DR 앱(Optiwatt·ev.energy·Octopus)의 실패 지점은 차량 연결이다.** Gemini 기준 언급 20.4%, 그중 87% 부정 — "차가 '연결 끊김'으로 떠서 보상을 못 받는다", "피크 시간에 충전해 버렸다". 리워드 자체는 언급 3.9%로 드물고 대부분 긍정("amazing bonus").
8. **해외 사용자는 리워드를 거의 말하지 않는다.** 리워드 관련 리뷰 120건(전체의 0.2%), 평균 3.09점. 대신 장거리 여행·경로 계획은 자주 말한다(577건).
9. **리뷰를 쓰는 사람은 불만층이다.** 미국 MyHyundai with Bluelink는 iOS 전체 평점 4.78(50만 명)인데 리뷰 본문 평균은 2.83이다.

---

## 2. 조사 설계

### 2-1. 조사 질문
1. 해외(미국·영국) EV 운전자는 차량·충전 앱에서 무엇이 불편한가?
2. 국내 기획(요금 최적화·리워드)의 해외 선례는 어떤 반응을 얻는가? 특히 **전력회사가 보상을 주는 리워드·DR 앱**은?
3. 현대차그룹의 해외 앱(특히 국내 통합 앱과 같은 계열인 유럽판)은 어떤 평가를 받는가?
4. 국내 가설 H1~H6은 해외에서도 성립하는가?

### 2-2. 대상 앱 선정

4개 그룹, 30개 앱 (앱·국가 조합 41개).

| 그룹 | 선정 기준 | 앱 (국가, Google Play ID) |
|---|---|---|
| **해외 현대차그룹 (7)** | 미국 기존 플랫폼 + 영국(유럽) 통합 앱 + 충전 앱 | MyHyundai with Bluelink (US, `com.stationdm.bluelink`), Kia Access (US, `com.myuvo.link`), Genesis Intelligent Assistant (US, `com.stationdm.genesis`), **myHyundai (EU, `com.hyundai.oneapp.eu`)**, **Kia App (EU, `com.kia.oneapp.eu`)**, MY GENESIS (EU, `com.genesis.oneapp.eu`), Charge myHyundai (EU, `com.digitalchargingsolutions.hyundai`) |
| **해외 경쟁 완성차 (11)** | 국내 경쟁 완성차 앱 중 글로벌판이 있는 브랜드 | Tesla (US·GB), My BMW (US `.na` / GB `.row`), MINI (US·GB), Mercedes-Benz (US `com.mbusa.mmusa.android` / GB `com.daimler.ris.mercedesme.ece.android`), Volvo Cars (US·GB), Polestar (US·GB), myChevrolet (US), Volkswagen (US `com.vw.carnet.release` / GB `com.volkswagen.weconnect`), myAudi (US·GB), My Porsche (US `com.porsche.one` / GB `de.porsche.one`), BYD AUTO (GB) |
| **해외 충전 앱 (7)** | 미국·영국 주요 충전 네트워크·통합 앱 | ChargePoint (US·GB), Electrify America (US), EVgo (US), PlugShare (US·GB), Octopus Electroverse (GB·US), IONITY (GB), Chargemap (GB) |
| **해외 리워드·DR 앱 (3)** | 전력회사가 비피크 충전에 보상하는 앱 (국내 기획의 보상 재원 모델 선례) | Optiwatt (US), ev.energy (US·GB), Octopus Energy (GB, **EV 관련 리뷰만**) |

제외: My Genesis(동명의 의료 앱), Kia Hypercharge·Shell EV Driver(리뷰 없음). 국내 전용 앱(국내 충전 앱 18개, 티맵·카카오내비, KGM·르노코리아·렉서스 커넥트)은 해외판이 없다.

App Store ID는 `research/voc/data/ios_search_global.json` 참조.

### 2-3. 분석 단위와 정의
- **리뷰 1건** = 한 사용자가 한 앱에 남긴 리뷰 1개. **부정** = 1~2점, **긍정** = 4~5점.
- **최근 1년** = 2025-10-24 ~ 2026-10-24.
- 국가: **미국(US)·영국(GB)**. 유럽 브랜드와 유럽판 현대차그룹 앱을 넣기 위해 영국을 포함했다.

---

## 3. 데이터 수집

### 3-1. Google Play

| 항목 | 설정 |
|---|---|
| 도구 | `google-play-scraper` |
| 언어·국가 | `lang=en`, `country=us` / `gb` |
| 정렬·상한 | 최신순, 앱·국가당 **최신 3,000건** (Octopus Energy는 EV 관련만 남기려고 3만 건까지 받아 필터) |
| 코드 | `scripts/apps_global.py`, `scripts/collect_global.py` |

**수집 결과 (원본 건수, 앱·국가별)**

| 그룹 | 앱별 수집 건수 |
|---|---|
| 해외 현대차그룹 | MyHyundai with Bluelink 3,000 / Kia Access 3,000 / Genesis IA 1,684 / Kia App EU 1,546 / myHyundai EU 1,065 / Charge myHyundai 81 / MY GENESIS EU 10 |
| 해외 경쟁 완성차 | Tesla 3,000+3,000 / My BMW 3,000+3,000 / Mercedes-Benz 3,000+3,000 / Volvo 3,000+3,000 / myAudi 3,000+3,000 / Volkswagen 3,000+2,844 / myChevrolet 3,000 / Polestar 1,009+1,009 / BYD 947 / MINI 424+828 / My Porsche 222+247 |
| 해외 충전 앱 | ChargePoint 3,000+3,000 / PlugShare 3,000+3,000 / EVgo 3,000 / Electrify America 2,834 / Octopus Electroverse 1,229+1,229 / Chargemap 533 / IONITY 511 |
| 해외 리워드·DR 앱 | ev.energy 512+512 / Optiwatt 238 / Octopus Energy 5,548건 중 EV 관련 377 |
| **합계** | **76,891건** |

**국가 구분에 대한 주의**: Polestar·ev.energy·Electroverse처럼 **미국·영국 건수가 똑같은 앱**이 있다. Google Play가 영어 리뷰를 국가별로 나누지 않고 같은 리뷰를 돌려주는 것으로 보여, 분석에서는 **(앱 ID, 리뷰 ID) 기준으로 중복을 제거**했다. 따라서 이 리포트의 결과는 "미국 vs 영국"이 아니라 **"영어권 리뷰 전체"** 로 읽어야 한다. (현대차그룹은 미국·유럽 앱이 아예 다른 앱이라 지역 구분이 유지된다.)

### 3-2. App Store

국내와 같은 방식이다. Apple 공개 리뷰 RSS 피드는 비어 있고, 웹 내부 API는 접근 토큰 추출이 필요해 쓰지 않았다.

| 방법 | 결과 |
|---|---|
| 공개 '평가 및 리뷰' 페이지 (`apps.apple.com/{us|gb}/app/id{ID}?see-all=reviews`) | 앱·국가당 약 10건 → **397건** (Octopus Energy는 EV 관련만) |
| iTunes Search API (국가별) | 41개 앱·국가의 **iOS 전체 평균 별점·평가 수** |
| 코드 | `scripts/find_ios_global.py`, `scripts/collect_ios_global.py` |

### 3-3. 수집 결과 요약

| 출처 | 원본 | 분석 대상 |
|---|---:|---:|
| Google Play 리뷰 | 76,891 | 58,589 |
| App Store 리뷰 | 397 | 397 |
| **합계** | **77,288** | **58,986** |
| 전체 평점 통계 | 41개 앱·국가 × (Play, iOS) | — |

---

## 4. 전처리

| 단계 | 처리 | 이유 |
|---|---|---|
| 1. 중복 제거 | (앱 ID, 리뷰 ID) 기준 | 미국·영국 Play가 같은 영어 리뷰를 돌려줌 |
| 2. 날짜 정규화 | ISO 8601 → UTC → 시간대 제거 | Play·iOS 형식 통일 |
| 3. EV 필터 | Octopus Energy(전력 공급사 앱)는 `EV`, `charg`, `Intelligent Octopus`, `smart charging`, `off-peak`, `Tesla`, `car`가 들어간 리뷰만 | 대부분의 리뷰는 가정용 전기 요금 관련 |
| 4. 초단문 제외 | 5자 미만 | 분류 불가 |

**분석 대상 그룹별 규모**

| 그룹 | 리뷰 | 평균 별점 | 부정 비율 | 부정 리뷰 | 최근 1년 부정 |
|---|---:|---:|---:|---:|---:|
| 해외 현대차그룹 | 10,377 | 2.68 | 54.2% | 5,620 | 2,804 |
| 해외 경쟁 완성차 | 33,419 | 2.92 | 46.7% | 15,603 | 5,657 |
| 해외 충전 앱 | 14,034 | 3.25 | 38.7% | 5,430 | 1,480 |
| 해외 리워드·DR 앱 | 1,156 | 2.81 | 48.9% | 565 | 100 |
| **전체** | **58,986** | | | **27,218** | **10,041** |

별점 분포: 1점 35.2% / 2점 10.9% / 3점 10.3% / 4점 10.3% / 5점 33.3% (국내보다 중간 점수가 많다).

스토어별: 현대차그룹 Play 10,307 + iOS 70 / 경쟁 완성차 33,219 + 200 / 충전 13,939 + 95 / 리워드·DR 1,124 + 32.

---

## 5. 분류 체계와 정확도 검증

### 5-1. 방법
- 국내와 같은 **키워드(정규식) 다중 라벨 분류**를 영어로 만들었다 (`scripts/taxonomy_en.py`).
- **카테고리 이름은 국내와 똑같이** 맞춰 국내·해외를 바로 비교할 수 있게 했다. 국내 18개에 **"충전 인프라(충전소 수·위치·속도)"** 를 더했다.
- 영어는 띄어쓰기 변형이 적어 단어 경계(`\b`)를 활용했다.

### 5-2. 불편 카테고리와 대표 패턴

| 카테고리 | 대표 패턴 (요약) |
|---|---|
| 앱 안정성·오류 | crash, bug, glitch, freeze, slow, loading, won't open, stopped working, not working, broken, error, after the update, useless |
| 로그인·인증 | log in/login, sign in, logged out, password, verification, 2FA, sign up, register, account |
| 결제·청구 오류 | payment, charged twice, refund, billing, credit card, card declined, overcharge, pre-auth |
| 요금·가격 | price, expensive, cost, per kWh, $/£/€ 금액, fee, tariff, rate, cheap, subscription |
| 충전기 고장·충전 실패 | charger broken/down/out of order, won't/didn't start charging, charging failed/stopped/interrupted, stuck on, not charging, can't charge, retry |
| 충전기 정보 부정확 | status/availability/location wrong·inaccurate·out of date, shows available, real-time, accurate, up to date |
| 대기·혼잡·점유 | waiting for a charger, all stalls taken/occupied, queue, ICEd, blocked by |
| 원격제어·차량연결 | remote, precondition, climate, lock/unlock, doors, horn, connect to the car, vehicle offline/asleep, wake the car, disconnect, command, timed out, heated seats |
| 디지털키 | digital key, phone as key, key card, keyless, NFC, key fob |
| 경로·내비 (일반) | route, navigation, directions, trip plan, destination, waypoint, road trip, map |
| 배터리·전비 | battery level/%, remaining range, efficiency, mi/kWh, state of charge, SoC, 12V |
| 포인트·리워드·멤버십 | reward, cash back, gift card, incentive, rebate, membership, loyalty, perk, voucher, bonus |
| UI·UX 불편 | interface, UI, UX, layout, design, menu, intuitive, confusing, cluttered, easy to use, user-friendly, widget, clunky |
| 고객센터·응대 | customer service/support, support, contacted, chat, no response, representative, agent |
| 알림 | notification, alert, push, notify |
| 미출차·점유 수수료 | idle fee, overstay, parking fee |
| 리워드 축소·소멸 불만 | (reward·points·incentive·cash back) + (reduced, cut, removed, expired, never received) |
| 충전 인프라 | lots of/few/not enough chargers, coverage, network, fast/slow charging, kW, infrastructure, easy to find |

**가설 패턴 (7개)**: 국내와 같은 가설을 영어 표현으로 정의했다 (H1 compare prices·member price·roaming·hidden fee / H2 road trip·long drive·route planning·highway / H3 toll / H4 bored·waste of time while charging / H5 efficiency·range drop·winter range / H6 reward·cash back·gift card·incentive·bonus / H6a driving·mileage reward).

### 5-3. 정확도 검증 — 영어 키워드 vs Gemini

Gemini 판정(해외 표본 3,000건)을 기준값으로 정밀도·재현율을 쟀다. 1차 결과에서 오탐이 큰 패턴을 고쳤다.

| 수정 | 이유 | 효과 |
|---|---|---|
| "point(s)" 제거 | **"ChargePoint", "at this point", "selling point"** 를 리워드로 잡음 | 리워드 정밀도 15% → 68% |
| "winter", "mileage" 제거 | 원격시동 맥락의 "winter", 연비 "mileage"를 배터리로 잡음 | 배터리 정밀도 19% → 23% |
| 충전 실패 표현 추가 | "stops charging", "won't enable charging", "retry" 등을 놓침 | 충전기 고장 재현율 9% → 21% |
| 정보 정확도 표현 추가 | "location not correct", "nowhere near", "up to date" 등 | 충전기 정보 재현율 12% → 23% |

**최종 정확도**

| 카테고리 | Gemini | 키워드 | 정밀도 | 재현율 |
|---|---:|---:|---:|---:|
| 앱 안정성·오류 | 750 | 643 | 55.1% | 47.2% |
| 원격제어·차량연결 | 608 | 425 | 68.7% | 48.0% |
| UI·UX | 414 | 251 | 71.3% | 43.2% |
| 로그인·인증 | 287 | 303 | 66.3% | 70.0% |
| 고객센터·응대 | 281 | 212 | **84.4%** | 63.7% |
| 요금·가격 | 223 | 343 | 48.7% | **74.9%** |
| 충전기 고장·충전 실패 | 205 | 66 | 63.6% | 20.5% |
| 충전 인프라 | 181 | 125 | 54.4% | 37.6% |
| 충전기 정보 부정확 | 146 | 63 | 52.4% | 22.6% |
| 결제·청구 오류 | 119 | 141 | 47.5% | 56.3% |
| 알림 | 80 | 81 | 75.3% | 76.2% |
| 경로·내비 (일반) | 55 | 104 | 39.4% | 74.5% |
| 배터리·전비 | 31 | 70 | 22.9% | 51.6% |
| 포인트·리워드·멤버십 | 30 | 31 | 67.7% | 70.0% |
| 디지털키 | 27 | 27 | 70.4% | 70.4% |

- 국내 한국어 키워드 분류와 비슷한 수준이다. 고장·정보 부정확처럼 **표현이 다양한 주제는 재현율이 낮아 과소 집계**된다.
- 결제·요금 정밀도(약 48%)는 "pay for the subscription" 같은 표현까지 잡기 때문이다. 이 두 카테고리는 6-9절 Gemini 추정과 함께 인용한다.
- 정밀도가 낮은 카테고리(배터리, 경로)는 비중을 인용하지 않는다.
- **라벨 커버리지**: 부정 리뷰의 73.8%가 하나 이상의 카테고리에 분류됐다 (국내 64.0%).
- 결과 파일: `out/global/en_keyword_vs_gemini.csv`

### 5-4. Gemini 속성 기반 감성분석

| 항목 | 설정 |
|---|---|
| 모델 | Gemini 무료 API, **100% `gemini-3.5-flash-lite`** (상위 모델은 무료 한도 소진) |
| 방식·출력 | 국내와 동일 — 25건 배치, 온도 0, JSON 스키마. 리뷰별 전체 감성·반어·개선 요청·카테고리별 감성·근거 원문 |
| 표본 | 3,000건 — 현대차그룹 800 / 경쟁 완성차 800 / 충전 800 / 리워드·DR 600. 그룹 안에서 앱 비례(앱당 최소 보장). (그룹, 앱, 최근 여부) 층 가중치로 비중 추정 |
| 코드 | `scripts/global_prep.py`(표본), `scripts/llm_absa.py`(국내와 같은 프롬프트), `scripts/compare_kr_global.py`(집계) |

**사람 판독 vs Gemini** — Gemini 결과를 보기 전에 판독자(Claude)가 블라인드로 30건에 라벨을 달고 비교했다.

| 지표 | 결과 (30건) |
|---|---:|
| 전체 감성 일치 | **96.7%** |
| 카테고리 정밀도 | 65.8% |
| 카테고리 재현율 | 69.4% |
| 공통 카테고리의 감성 일치 | 84.0% |

불일치 1건: "It's pretty good but it would be better if..."를 사람은 '긍정', Gemini는 '혼합'. 카테고리 일치도가 국내(81~83%)보다 낮은 것은 표본이 작고, 차량 앱의 "기능이 없어졌다" 같은 표현을 '기타'와 'UI·UX' 중 어디로 볼지가 갈렸기 때문이다.

---

## 6. 분석 결과

### 6-1. 앱별 개요

| 그룹 | 앱 | 리뷰 | 평균 | 부정 % | 최근 1년 | 부정 리뷰 상위 불편 3개 (부정 리뷰 중 %) |
|---|---|---:|---:|---:|---:|---|
| 해외 현대차그룹 | MyHyundai with Bluelink (US) | 2,990 | 2.83 | 50.3 | 996 | 앱 안정성 44.0 · 원격제어 29.3 · 로그인 10.4 |
| | Kia Access (US) | 2,982 | 3.14 | 42.3 | 1,609 | 앱 안정성 32.8 · 원격제어 29.2 · 결제 14.2 |
| | Genesis Intelligent Assistant (US) | 1,679 | 2.95 | 45.6 | 166 | 앱 안정성 46.7 · 원격제어 32.1 · 로그인 12.0 |
| | **Kia App (EU)** | 1,546 | **1.74** | **79.0** | 973 | 앱 안정성 35.1 · 원격제어 24.4 · **UI·UX 20.2** |
| | **myHyundai (EU)** | 1,069 | **1.89** | **75.2** | 1,060 | 앱 안정성 30.7 · 원격제어 23.9 · **UI·UX 17.5** |
| | Charge myHyundai (EU) | 91 | 2.32 | 57.1 | 15 | 로그인 30.8 · 앱 안정성 26.9 · 요금 23.1 |
| | MY GENESIS (EU) | 20 | 2.15 | 65.0 | 20 | (표본 적음) |
| 해외 경쟁 완성차 | Mercedes-Benz | 5,951 | 3.51 | 30.2 | 1,695 | 앱 안정성 30.0 · 원격제어 28.0 · 로그인 20.1 |
| | My BMW | 5,925 | **3.57** | 31.0 | 1,032 | 앱 안정성 34.4 · 원격제어 21.9 · 로그인 17.3 |
| | Volkswagen | 5,839 | 2.39 | 60.0 | 1,642 | 앱 안정성 38.4 · 원격제어 32.6 · 로그인 14.0 |
| | Volvo Cars | 3,013 | 2.31 | 62.2 | 491 | 앱 안정성 36.9 · 원격제어 31.8 · 로그인 9.4 |
| | myAudi | 2,998 | 2.22 | 66.7 | 1,020 | 앱 안정성 43.4 · 원격제어 31.9 · UI·UX 8.7 |
| | Tesla | 2,986 | 3.05 | 43.9 | 2,174 | 앱 안정성 31.6 · 원격제어 13.0 · 로그인 9.8 |
| | myChevrolet | 2,979 | 3.00 | 46.2 | 2,970 | 앱 안정성 34.1 · 원격제어 28.1 · 로그인 23.5 |
| | MINI | 1,265 | 2.94 | 47.3 | 174 | 앱 안정성 35.3 · 로그인 19.4 · 원격제어 13.0 |
| | Polestar | 1,027 | 2.45 | 56.8 | 181 | 앱 안정성 40.1 · 원격제어 31.0 · 디지털키 14.9 |
| | BYD AUTO | 949 | 2.78 | 44.4 | 413 | 앱 안정성 27.6 · 원격제어 18.1 · 디지털키 11.9 |
| | My Porsche | 487 | 2.39 | 61.4 | 114 | 앱 안정성 29.8 · 원격제어 25.4 · 로그인 13.0 |
| 해외 충전 앱 | ChargePoint | 3,007 | 2.55 | 57.7 | 970 | 앱 안정성 28.5 · **결제 21.0** · 로그인 20.7 |
| | EVgo | 2,966 | 3.38 | 36.5 | 531 | 앱 안정성 33.7 · 로그인 26.8 · 결제 13.6 |
| | PlugShare | 2,944 | **4.05** | 16.8 | 514 | 앱 안정성 24.2 · 경로·내비 16.0 · 충전 인프라 10.7 |
| | Electrify America | 2,828 | 2.91 | 46.2 | 501 | 앱 안정성 33.2 · 로그인 22.1 · 요금 19.4 |
| | Octopus Electroverse | 1,231 | **3.86** | 22.7 | 352 | 앱 안정성 32.9 · 요금 20.7 · 로그인 20.7 |
| | Chargemap | 539 | 2.93 | 45.1 | 120 | 앱 안정성 31.7 · 경로·내비 24.7 · 로그인 17.3 |
| | IONITY | 519 | 2.67 | 55.3 | 89 | 앱 안정성 31.0 · 결제 23.7 · 요금 23.7 |
| 해외 리워드·DR 앱 | ev.energy | 530 | 2.94 | 47.7 | 85 | 앱 안정성 34.0 · 고객센터 22.1 · 로그인 20.9 |
| | Octopus Energy (EV) | 379 | 2.50 | 53.6 | 86 | **요금 36.0** · 앱 안정성 35.0 · 고객센터 21.7 |
| | Optiwatt | 247 | 3.02 | 44.1 | 11 | 앱 안정성 33.0 · 로그인 26.6 · 요금 19.3 |

원본: `out/global/full/apps_overview.csv`

### 6-2. 그룹별 불편 구조 (부정 리뷰 중 %, 키워드)

**전체 기간**

| 불편 유형 | 현대차그룹 | 경쟁 완성차 | 충전 앱 | 리워드·DR | 전체 |
|---|---:|---:|---:|---:|---:|
| 앱 안정성·오류 | **37.9** | **35.9** | **30.8** | **34.2** | **35.3** |
| 원격제어·차량연결 | 27.6 | 27.3 | 4.4 | 6.4 | 22.3 |
| 로그인·인증 | 11.7 | 14.1 | 20.8 | 19.6 | 15.0 |
| 요금·가격 | 8.5 | 6.6 | 16.4 | **26.0** | 9.3 |
| 결제·청구 오류 | 7.3 | 4.4 | **16.4** | 7.6 | 7.4 |
| UI·UX | 10.2 | 6.4 | 6.6 | 5.1 | 7.2 |
| 고객센터·응대 | 5.7 | 6.2 | 10.3 | **19.6** | 7.2 |
| 경로·내비 (일반) | 3.0 | 3.9 | 6.8 | 0.5 | 4.2 |
| 알림 | 4.5 | 3.4 | 3.2 | 1.4 | 3.6 |
| 충전 인프라 | 1.3 | 1.2 | 10.1 | 3.2 | 3.0 |
| 충전기 고장·충전 실패 | 1.0 | 1.3 | 5.5 | 6.9 | 2.2 |
| 충전기 정보 부정확 | 1.6 | 2.0 | 2.0 | 1.6 | 1.9 |
| 디지털키 | 1.7 | 2.1 | 1.0 | 0.2 | 1.8 |
| 포인트·리워드·멤버십 | 0.3 | 0.2 | 1.3 | 2.8 | 0.5 |
| 대기·혼잡·점유 | 0.1 | 0.1 | 1.2 | 0.2 | 0.3 |

**최근 1년**

| 불편 유형 | 현대차그룹 | 경쟁 완성차 | 충전 앱 | 리워드·DR |
|---|---:|---:|---:|---:|
| 앱 안정성·오류 | 33.9 | 34.5 | 28.0 | 27.0 |
| 원격제어·차량연결 | 28.6 | 26.1 | 5.3 | 5.0 |
| 로그인·인증 | 12.0 | 15.8 | **25.7** | 14.0 |
| 요금·가격 | 9.0 | 7.4 | **22.2** | **27.0** |
| UI·UX | **13.5** | 6.6 | 10.8 | 5.0 |
| 결제·청구 오류 | 7.0 | 5.2 | **22.8** | 10.0 |
| 고객센터·응대 | 6.1 | 6.6 | 11.1 | **25.0** |
| 충전기 고장·충전 실패 | 1.2 | 1.4 | 5.2 | 8.0 |
| 포인트·리워드·멤버십 | 0.2 | 0.3 | 1.6 | 7.0 |

부정 리뷰 수 — 최근 1년: 현대차그룹 2,804 / 경쟁 완성차 5,657 / 충전 1,480 / 리워드·DR 100.

**공감 수 가중** (부정 리뷰 전체, 가중치 = 공감 수 + 1): 앱 안정성 39.8% · 원격제어 32.0% · 로그인 17.9% · 요금 13.1% · 결제 10.5% · 고객센터 10.3% · UI·UX 10.1% · 경로·내비 6.5% · 알림 6.4%. → 공감을 가장 많이 받는 불만은 **"작동하지 않는다"** (안정성·원격제어).

원본: `out/global/full/pain_by_group_all.csv`, `pain_by_group_recent.csv`

### 6-3. 연도별 추이

**리뷰 수 (분석 대상)**

| 연도 | 현대차그룹 | 경쟁 완성차 | 충전 앱 | 리워드·DR |
|---|---:|---:|---:|---:|
| 2021 | 256 | 659 | 620 | 169 |
| 2022 | 183 | 2,201 | 1,277 | 162 |
| 2023 | 214 | 5,386 | 2,241 | 161 |
| 2024 | 586 | 6,958 | 4,032 | 232 |
| 2025 | **4,986** | 8,850 | 3,138 | 210 |
| 2026 (~10/24) | 3,618 | 9,292 | 2,475 | 149 |

- 현대차그룹 2025년 급증 = **유럽판 통합 앱 출시**(Kia App EU 2025-05, myHyundai EU 2025-09 첫 리뷰)와 미국 앱 리뷰 증가.
- 대형 앱은 수집 상한(3,000건) 때문에 최근 연도 비중이 과대하다. 연도별 **비중**만 해석한다.

**충전 앱 — 부정 리뷰 중 불편 비중 (%)**

| 연도 | 부정 리뷰 | 앱 안정성 | 로그인 | **요금** | **결제** | 고장 | 리워드 |
|---|---:|---:|---:|---:|---:|---:|---:|
| 2021 | 300 | 35.7 | 21.7 | 12.7 | 14.7 | 5.3 | 1.3 |
| 2022 | 617 | 39.1 | 33.9 | 9.9 | 13.8 | 4.1 | 0.6 |
| 2023 | 707 | 31.3 | 18.7 | 13.2 | 12.6 | 6.1 | 1.6 |
| 2024 | 1,304 | 29.3 | 13.1 | 15.6 | 12.7 | 6.1 | 1.5 |
| 2025 | 1,163 | 30.4 | 16.5 | 17.8 | 17.5 | 6.2 | 1.5 |
| 2026 | 1,235 | 27.7 | 26.8 | **21.7** | **23.3** | 5.0 | 1.3 |

→ **요금(2022년 9.9% → 2026년 21.7%)과 결제(2024년 12.7% → 2026년 23.3%) 불만이 함께 상승.**

**현대차그룹 — 부정 리뷰 중 불편 비중 (%)**

| 연도 | 부정 리뷰 | 앱 안정성 | 원격제어 | **UI·UX** | 로그인 | 요금 | 결제 |
|---|---:|---:|---:|---:|---:|---:|---:|
| 2021 | 127 | 55.9 | 19.7 | 8.7 | 13.4 | 5.5 | 6.3 |
| 2022 | 75 | 37.3 | 33.3 | 0.0 | 9.3 | 12.0 | 9.3 |
| 2023 | 128 | 49.2 | 40.6 | 4.7 | 7.8 | 5.5 | 4.7 |
| 2024 | 267 | 44.2 | 24.3 | 3.0 | 8.2 | 10.1 | 8.2 |
| 2025 | 2,779 | 38.4 | 26.5 | 10.4 | 12.7 | 8.1 | 7.3 |
| 2026 | 2,000 | 34.0 | 28.0 | **12.2** | 11.0 | 9.3 | 7.2 |

→ UI·UX 불만이 2024년 3%에서 2026년 12%로 — 유럽판 통합 앱 출시 시점과 겹친다.

**경쟁 완성차 — 부정 리뷰 중 불편 비중 (%)**

| 연도 | 부정 리뷰 | 앱 안정성 | 원격제어 | UI·UX | 로그인 | 요금 |
|---|---:|---:|---:|---:|---:|---:|
| 2021 | 482 | 41.1 | 21.0 | 5.4 | 11.4 | 3.7 |
| 2022 | 1,272 | 39.5 | 29.0 | 6.4 | 15.0 | 5.7 |
| 2023 | 2,691 | 37.0 | 31.4 | 5.9 | 15.5 | 5.7 |
| 2024 | 2,806 | 35.6 | 28.0 | 6.2 | 12.1 | 6.2 |
| 2025 | 3,745 | 36.7 | 27.2 | 6.4 | 11.9 | 7.0 |
| 2026 | 4,557 | 33.2 | 24.6 | 6.8 | 16.3 | 7.4 |

→ 구조가 안정적이다: 안정성 33~41%, 원격제어 21~31%가 매년 1·2위.

**리워드·DR 앱 — 부정 리뷰 중 불편 비중 (%)** (표본 작음)

| 연도 | 부정 리뷰 | 앱 안정성 | 요금 | 로그인 | 고객센터 | 원격제어 |
|---|---:|---:|---:|---:|---:|---:|
| 2022 | 74 | 37.8 | 24.3 | 25.7 | 20.3 | 4.1 |
| 2023 | 100 | 40.0 | 35.0 | 24.0 | 19.0 | 4.0 |
| 2024 | 134 | 28.4 | 23.1 | 10.4 | 16.4 | 3.0 |
| 2025 | 106 | 43.4 | 24.5 | 24.5 | 23.6 | 15.1 |
| 2026 | 85 | 25.9 | 28.2 | 15.3 | 25.9 | 3.5 |

원본: `out/global/full/summary.json` (`trend`, `volume_by_year`)

### 6-4. 현대차그룹 vs 경쟁 완성차 (해외)

| 비교 | 해외 현대차그룹 | 해외 경쟁 완성차 |
|---|---|---|
| 리뷰 본문 평균 | 2.68 (미국 2.83~3.14 / 유럽 1.74~1.89) | 2.92 (BMW 3.57, Benz 3.51, Tesla 3.05) |
| 공통 불편 | 앱 안정성, 원격제어 | 앱 안정성, 원격제어 |
| 두드러진 불편 | 유럽판 **UI·UX**(17~20%), 미국 Kia Access **결제**(14.2% — 원문 확인 결과 대부분 **커넥티드 기능 유료 구독**에 대한 불만: "I refuse to pay $15 a month for most features like programming the charging time") | 로그인(14.1%), Polestar·BYD **디지털키** |

해석:
- **미국 현대차그룹 앱(기존 Bluelink 플랫폼)은 경쟁사와 비슷한 수준**이다 (Kia Access 3.14는 Tesla 3.05보다 높다).
- **유럽판 통합 앱은 경쟁사 중 가장 낮은 앱(myAudi 2.22, Volvo 2.31)보다도 낮다.** 통합 개편이 만든 문제로 보인다.

### 6-5. 충전 앱 심층 (해외)

**① 통합 앱 vs 단일 사업자 앱**

| 유형 | 앱 | 리뷰 본문 평균 | Play 전체 (US / GB) | iOS 전체 (US / GB) |
|---|---|---:|---|---|
| 통합·로밍 | PlugShare | **4.05** | 4.60 / 4.30 | 4.71 / 4.58 |
| | Octopus Electroverse | **3.86** | 4.24 / 4.60 | 4.56 / 4.70 |
| 단일 사업자 | EVgo | 3.38 | 4.56 | 4.69 |
| | Chargemap | 2.93 | — / 4.66 | — / 4.55 |
| | Electrify America | 2.91 | 4.62 | 4.60 |
| | IONITY | 2.67 | — / 4.66 | — / 4.64 |
| | ChargePoint | **2.55** | 3.81 / **2.03** | 4.31 / **2.91** |

**② 결제** — 부정 리뷰의 16.4%(2026년 23.3%). ChargePoint(21.0%)·IONITY(23.7%)·Kia Access(14.2%)가 높다. 내용: 선불 잔액 자동 충전, 카드 반복 입력·중복 저장, "processing payment"에서 멈춤.

**③ 요금** — 부정 리뷰의 16.4%(2026년 22.2%). IONITY·Electrify America·Electroverse 상위. 내용: 단가 자체("expensive", "ridiculous prices"), 구독제(IONITY 구독은 오히려 칭찬도 받음).

**④ 신뢰성** — Gemini 기준 충전기 고장·충전 실패 언급 14.1%, 충전기 정보 부정확 12.2%(그중 74% 부정). "initiating charge 화면에서 멈춤", "눈앞의 충전기를 인식 못함", "가용 상태가 틀림".

### 6-6. 기획 가설별 근거 (해외, 키워드)

| 가설 | 언급 리뷰 | 평균 별점 | 부정 % | 최근 1년 | 판정 |
|---|---:|---:|---:|---:|---|
| H1 요금 비교 어려움·손해 | 48 | 2.67 | 50.0 | 18 | ❌ 드묾 — 회원가·로밍가 구조 불만이 거의 없음 |
| H2 장거리 충전 계획 부담 | **577** | 3.12 | 38.6 | 154 | ✅ 자주 언급 (road trip, route planning, highway) |
| H3 통행료 | 11 | 3.45 | 27.3 | 1 | ❌ 근거 없음 |
| H4 충전 대기 지루함 | 17 | 2.82 | 47.1 | 7 | ❌ 근거 없음 |
| H5 전비 저하 원인 | 16 | 1.88 | 75.0 | 4 | ❌ 근거 약함 |
| H6 리워드 반응 | 120 | 3.09 | 42.5 | 39 | 🟡 드물고 반응 미지근 (리워드·DR 앱에 몰림: 54건) |
| H6a 주행 연동 리워드 | 2 | — | — | 0 | ❌ 선례 거의 없음 |

### 6-7. 전체 평점 vs 리뷰 본문

| 그룹 | 앱 | 국가 | Play 전체 (평가 수) | iOS 전체 (평가 수) | 리뷰 본문 |
|---|---|---|---|---|---:|
| 현대차그룹 | MyHyundai with Bluelink | US | 4.59 (116,734) | 4.78 (501,302) | 2.83 |
| | Kia Access | US | 4.69 (102,177) | 4.73 (430,420) | 3.14 |
| | Genesis Intelligent Assistant | US | 4.71 (10,106) | 4.76 (61,959) | 2.95 |
| | **myHyundai (EU)** | GB | **3.13** (10,491) | 4.31 (3,499) | **1.89** |
| | **Kia App (EU)** | GB | **3.18** (13,178) | 4.18 (4,400) | **1.74** |
| | Charge myHyundai (EU) | GB | 3.91 (2,578) | 4.13 (361) | 2.32 |
| 경쟁 완성차 | Tesla | US / GB | 4.64 / 4.46 | 4.71 / 4.58 | 3.05 |
| | My BMW | US / GB | 4.74 / 4.63 | 4.85 / 4.75 | 3.59 / 3.56 |
| | Mercedes-Benz | US / GB | 4.70 / 4.63 | 4.87 / 4.77 | 3.63 / 3.39 |
| | Volkswagen | US / GB | 4.42 / 4.34 | 4.69 / 4.54 | 2.56 / 2.22 |
| | myAudi | US / GB | 4.74 / 4.66 | 4.60 / 4.40 | 2.22 |
| | Volvo Cars | US / GB | 3.55 / 3.32 | 4.71 / 4.58 | 2.31 |
| | myChevrolet | US | 4.63 (321,769) | 4.82 (985,733) | 3.00 |
| | Polestar | US / GB | 4.32 / 4.34 | 4.44 / 4.39 | 2.45 |
| | BYD AUTO | GB | 3.25 | 2.81 | 2.78 |
| 충전 앱 | (6-5절 표 참조) | | | | |
| 리워드·DR | Optiwatt | US | 3.51 (1,064) | 4.76 (3,136) | 3.02 |
| | ev.energy | US / GB | 4.37 / 4.41 | 4.77 / 4.60 | 2.94 |
| | Octopus Energy (앱 전체) | GB | 4.32 (47,527) | 4.83 (266,821) | 2.50 (EV 리뷰) |

- 차량 앱은 **"전체 평점 높음 / 리뷰 본문 낮음"** — 리뷰 작성자 편향.
- **유럽판 현대·기아 통합 앱은 Play 전체 평점까지 3.1점대** — 소수 불만층이 아니라 사용자 전반의 평가.
- 미국·영국 Play 전체 평점이 같은 앱(Tesla·Volvo·Polestar 등)은 스토어가 국가를 구분하지 않고 같은 값을 준 것.

전체 표: `out/global/full/ratings_vs_reviews.csv`

### 6-8. App Store 리뷰

- 397건 (앱·국가당 약 10건), 부정 47.6%.
- 부정 리뷰 중 불편: 원격제어 50.8% · 앱 안정성 47.1% · 요금 39.2% · 고객센터 30.2% · 결제 27.5% · UI·UX 27.0% · 로그인 24.3%.
- 그룹 평균: 충전 3.13 / 리워드·DR 3.03 / 경쟁 완성차 2.81 / 현대차그룹 2.54. Play와 같은 상위 순서.
- 공개 페이지는 Apple이 고른 리뷰로 무작위 표본이 아니다. 순서 비교용.

### 6-9. Gemini 감성분석 결과

**글의 감성 (가중 추정, 3,000건)**

| 그룹 | 글 부정 | 글 긍정 | 혼합 | 개선 요청 | 반어 | 평균 별점(가중) |
|---|---:|---:|---:|---:|---:|---:|
| 해외 현대차그룹 | **65.0%** | 25.4% | 7.5% | 16.6% | 0.8% | 2.55 |
| 해외 경쟁 완성차 | 54.5% | 32.1% | 10.5% | 18.1% | 0.7% | 2.84 |
| 해외 충전 앱 | 44.4% | 40.9% | 10.3% | 13.7% | 0.9% | 3.21 |
| 해외 리워드·DR 앱 | 58.1% | 27.0% | 10.2% | 18.6% | 1.0% | 2.71 |

한 리뷰에 장단점을 함께 쓰는 '혼합'이 약 10%로 많다.

**카테고리 언급률과 감성 (리뷰 전체 중 언급 비율, 가중)**

해외 현대차그룹

| 카테고리 | 언급률 | 긍정 | 부정 |
|---|---:|---:|---:|
| 앱 안정성·오류 | 30.9% | 13.5% | 85.7% |
| 원격제어·차량연결 | 30.3% | 21.1% | 76.6% |
| UI·UX | 14.4% | 37.8% | 61.2% |
| 로그인·인증 | 10.6% | 1.1% | 98.9% |
| 고객센터·응대 | 7.3% | 20.0% | 76.3% |
| 요금·가격 | 4.9% | 6.1% | 93.9% |

해외 경쟁 완성차

| 카테고리 | 언급률 | 긍정 | 부정 |
|---|---:|---:|---:|
| 원격제어·차량연결 | 28.4% | 23.6% | 75.0% |
| 앱 안정성·오류 | 25.5% | 19.2% | 80.1% |
| UI·UX | 12.8% | 49.2% | 49.0% |
| 로그인·인증 | 10.5% | 1.2% | 98.8% |
| 고객센터·응대 | 5.3% | 29.5% | 70.5% |
| 알림 | 4.0% | 29.2% | 70.8% |

해외 충전 앱

| 카테고리 | 언급률 | 긍정 | 부정 |
|---|---:|---:|---:|
| **충전 인프라** | **20.4%** | 68.5% | 29.0% |
| 앱 안정성·오류 | 19.6% | 26.7% | 73.3% |
| **충전기 고장·충전 실패** | **14.1%** | 22.6% | 77.4% |
| UI·UX | 13.7% | 60.7% | 37.4% |
| **충전기 정보 부정확** | **12.2%** | 24.7% | **74.2%** |
| 결제·청구 오류 | 9.6% | 3.1% | **95.6%** |
| 요금·가격 | 8.3% | 35.3% | 63.2% |
| 고객센터·응대 | 8.0% | 36.2% | 60.8% |
| 로그인·인증 | 7.6% | 6.2% | 93.8% |
| 포인트·리워드·멤버십 | **0.7%** | 82.7% | 17.3% |

해외 리워드·DR 앱 → 6-10절.

원본: `out/global/aspects_kr_vs_global.csv`, `out/global/compare.json`

### 6-10. 리워드·DR 앱 심층 — 국내 '수요 분산 보상' 모델의 선례

Optiwatt(미국)·ev.energy(미국·영국)·Octopus Energy(영국, EV 관련만)는 **전력회사가 비피크 충전에 보상**하는 구조로, 국내 기획의 보상 재원 모델과 같다.

| 앱 | Gemini 표본 | 글 부정 | 글 긍정 | 평균 별점 | iOS 전체 평점 |
|---|---:|---:|---:|---:|---:|
| Optiwatt | 150 | 51.4% | 36.9% | 2.87 | 4.76 |
| ev.energy | 244 | 56.3% | 32.5% | 2.76 | 4.77 / 4.60 |
| Octopus Energy (EV) | 206 | 64.5% | 13.9% | 2.53 | 4.83 (앱 전체) |
| **합계** | 600 | 58.1% | 27.0% | 2.71 | |

**무엇이 언급되는가 (Gemini, 가중)**

| 카테고리 | 언급률 | 긍정 | 부정 |
|---|---:|---:|---:|
| 앱 안정성·오류 | 24.5% | 22.6% | 76.1% |
| **원격제어·차량연결 (차량 연동·충전 제어)** | **20.4%** | 12.2% | **86.9%** |
| 고객센터·응대 | 20.1% | 37.5% | 60.9% |
| 요금·가격 (요금제 설정·절약) | 14.6% | 45.3% | 51.7% |
| UI·UX | 13.4% | 52.4% | 40.9% |
| 충전기 고장·충전 실패 | 12.0% | 7.2% | 92.8% |
| 로그인·인증 | 9.0% | 1.8% | 98.2% |
| **포인트·리워드** | **3.9%** | **82.1%** | 13.4% |

키워드(전수)로 보면 Octopus Energy는 **요금(36.0%)** 이 1위 불만이다 — 요금제(Intelligent Octopus) 설정·표시 문제.

**Gemini가 뽑은 근거 구절**
- 리워드 긍정: "you get Amazon gift card", "rewards are a nice perk!", "rewards are an amazing bonus", "Great opportunity to earn some cash back"
- 리워드 부정: "never received any of them", "reward system is not very clear"
- 요금 긍정: "charges my EV at the cheapest times", "getting low overnight rates", "saves you money"
- 요금 부정: "Started charging during peak rate time!", "randomly charges outside set hours and pricing", "stopped tracking costs"
- 차량 연결 부정: "app also was constantly waking my vehicle", "fails to connect despite rebooting", "didn't found my Vehicle"

**해석**
1. **보상은 '덤', 핵심은 '제대로 작동해서 돈을 아끼는 것'.** 리워드 언급은 4% 미만이고 대부분 긍정. 불만은 작동 신뢰성에 몰려 있다.
2. **가장 큰 실패 지점은 차량 연결.** 휴대폰·클라우드에서 제조사 API로 차를 깨우고 제어하는 구조라 "연결 끊김으로 보상 누락", "피크 시간 충전", "차를 계속 깨움"이 반복된다.
3. **개인정보 거부감**("Highly invasive") — 차량·위치 데이터 사용 범위를 투명하게 보여줘야 한다.
4. **금액으로 보여줘야 한다** ("kWh가 아니라 £가 중요").

---

## 7. 대표 리뷰 모음

영어 원문 발췌. 철자·문법은 원문 그대로. `👍`는 공감 수.

### 7-1. 차량 연결·원격제어
> "The communication between this app and the car is the worst. I asked for 100% charge SO many times and it kept resetting to 85% ... I wake up and it's at 85%." — Tesla, 1★, **👍126**, 2026-07

> "the latest update of the app has now somehow locked my car from charging and I now have to press the 'start charging' button for it to update. Left it on all knight and it didn't charge at all" — myHyundai (EU), 1★, 👍51, 2026-02

### 7-2. 유럽판 현대·기아 통합 앱
> "UI is dire, only 4 shortcuts and many commonly used items are buried under layers of menus, for example ev charging limits. ... There should be no ads in or around a product that has already been bought and paid for" — myHyundai (EU), 1★, 👍66, 2025-12

> "The app looks good in principle, but is very cluttered, and getting to the most common information such as the odometer reading and efficiency is far too complex." — Kia App (EU), 1★, 👍8, 2025-05

> "Bluelink has a status page where your car information is there ... This app, press through 3 menus just to see the same info." — myHyundai (EU), 1★, 👍5, 2025-12

> "Totally not intuitive. More about selling a new car than supporting use of an existing car." — myHyundai (EU), 1★, 👍5, 2025-12

### 7-3. 충전 신뢰성
> "So many connection errors, always gets stuck on "connecting to vehicle", "processing payment", or "initiating charging" and then it times out and tells me to unplug. It took me like 30min of switching plugs and spots to finally get it to start charging." — Electrify America, 1★, 👍14, 2023-05

> "we traveled about 250 mi out of state and stopped to charge four different times. Each time the app hung on the "initiating charge" screen." — Electrify America, 1★, 👍32, 2023-06

> "inconsistent. when it works, it's great. but there are times when it doesn't register a charge point station directly in front of me." — ChargePoint, 1★, 👍7, 2025-06

> "App never lets me start the charge myself. So my only option is calling customer support to start it remotely. ... Its simpler to use a credit card and pay for a single charge." — Electrify America, 1★, 👍7, 2024-06

### 7-4. 결제
> "Automatically puts $10 on the card if the balance gets low instead of sending a notification that balance is low and providing the option to refill." — ChargePoint, 1★, 👍48, 2024-09

> "Asks for credit card info over and over. Didn't seem to save the info I had entered, yet later I saw that my account had this same card saved multiple times." — EVgo, 1★, 👍53, 2022-06

### 7-5. 리워드·DR 앱
> "Very often and randomly the car appears to be "disconnected" so I don't get any incentives. ... I've contacted their support and they told me they're aware of the glitch but they can't do anything. I can always buy their Wi-Fi charger which would fix this (!)." — ev.energy, 1★, 👍5, 2025-05

> "App charges my car during peak times and doesn't bother at off-peak times." — ev.energy, 1★, 👍5, 2023-07

> "Started charging during peak rate time! This never happened with the Tesla app." — ev.energy, 1★, 2025-04

> "Highly invasive! It wants to connect to your car, find it's location, connect to your Tesla account and plenty more" — ev.energy, 1★, 2021-08

> "Allows me to charge my Tesla Model 3 at the cheapest times overnight ... Plus rewards are an amazing bonus. Highly recommended!" — ev.energy, 5★, 2021-02

> "Great app, easy to use and saves you money and you get Amazon gift card on top, what's not to like." — ev.energy, 5★, 2021-06

> "The new update gives you usage statistics only in kWh with the intelligent tariff. I am interested in usage in £ as that's what really matters." — Octopus Energy, 2★, 👍14, 2026-08

### 7-6. 장거리·경로 (H2)
> "Just did a trip throughout Europe across 7 countries by charging exclusively with the IONITY network. No issues whatsoever, plenty of chargers, steady price (the subscriptions are really worth it)" — IONITY, 5★, 👍19, 2025-04

> "It would be useful if when planning route the option was available to link to a third party router waze for example." — Octopus Electroverse, 5★, 👍57, 2024-05

전체 인용문: `out/global/full/quotes.json` (카테고리·가설별 공감 상위 12~15건. 결제·요금 등 정밀도가 낮은 카테고리는 오탐 인용이 섞여 있으니 원문 확인)

---

## 8. 프로젝트 시사점

해외 데이터만으로 본 시사점이다. 국내와 합친 기획 판단은 ③ [비교 요약](앱리뷰-VOC-국내해외-비교.md)에 있다.

| 발견 | 우리 기획에 주는 의미 |
|---|---|
| 리워드·DR 앱의 1순위 실패 = 차량 연결 (87% 부정) | **Pleos 차량 안에서 Vehicle SDK로 배터리·충전 상태를 직접 읽는 구조**가 이 실패 지점을 구조적으로 피한다 → 핵심 기술 차별점 |
| DR 앱 사용자에게 보상은 '덤' | 리워드보다 **확실히 작동하는 절약**을 먼저 보여준다 |
| "피크 시간에 충전해 버렸다" | 요금 시간대 판단은 **사용자가 확인할 수 있게** 보여주고, 예상과 다르면 알린다 |
| "kWh가 아니라 £로" | 절약액은 **원 단위**로 |
| "Highly invasive" | 차량·위치 데이터를 어디에 쓰는지 **투명하게** 표시 |
| 통합·로밍 앱이 높은 평가 | 해외에서도 **중립적 통합 추천**이 신뢰를 얻는다 |
| 결제 불만 16~23% | 결제는 직접 만들지 않고 **카페이 등 검증된 수단에 연결** (유사사례 조사의 결론과 같음) |
| 유럽판 통합 앱 반발 | 차량 앱은 **빠르고 단순해야** 한다는 설계 원칙이 국내만의 이야기가 아님 |

---

## 9. 한계와 신뢰도

| 한계 | 영향 | 대응 |
|---|---|---|
| **국가 구분 불가** — Play가 영어 리뷰를 미국·영국으로 나누지 않음 | "미국 vs 영국" 비교 불가 | "영어권 리뷰"로 해석. 현대차그룹은 지역별 앱이 달라 구분 유지 |
| **수집 상한 3,000건** — 대형 앱은 최근 약 1년치 | 앱 간·연도 간 기간 불균등 | 연도별은 비중만 해석. 국내 비교는 같은 기간으로 (③) |
| **영어 키워드 재현율** 20~75% | 비중은 하한값 | Gemini 대비 측정(5-3절). 수준은 Gemini(6-9절)로 인용 |
| 결제·요금 키워드 정밀도 약 48% | "pay for subscription" 등 포함 | Gemini 추정과 함께 인용 |
| 배터리·경로 키워드 정밀도 23~39% | 비중 신뢰 낮음 | 비중 인용 금지 |
| **리뷰 작성자 편향** | 불편 규모 과대 | 불만층 안의 상대 순위로 인용 |
| **App Store 리뷰** 앱·국가당 약 10건 | iOS 비중 판단 불가 | 전체 평점(41개 앱·국가)으로 보완 |
| **Gemini 100% flash-lite** | 상위 모델보다 판정 정밀도가 낮을 수 있음 | 사람 판독 30건으로 측정 (5-4절) |
| **사람 판독자 1명·30건** | 카테고리 일치도 추정이 불안정 | 팀원 판독 추가 시 신뢰도↑ |
| 리워드·DR 앱 표본 작음 (전수 1,156건, 최근 1년 부정 100건) | 연도별 추이 불안정 | 경향으로만 해석 |
| 미국·영국 영어 리뷰만 | 유럽 대륙(독일·프랑스 등) 미반영 | `apps_global.py`에 국가·언어 추가 가능 |
| Gemini 무료 티어 | 입력이 Google 서비스 개선에 쓰일 수 있음 | 공개 리뷰만 전송 |

---

## 10. 재현 방법과 데이터 사전

### 10-1. 파일 구조
```
research/voc/
├── scripts/
│   ├── apps_global.py           # 해외 30개 앱 (그룹·이름·국가·Play ID·EV 필터)
│   ├── collect_global.py        # 해외 Play 영어 리뷰 수집
│   ├── find_ios_global.py       # 해외 iOS ID (미국·영국)
│   ├── collect_ios_global.py    # 해외 App Store 공개 리뷰 + 전체 평점
│   ├── taxonomy_en.py           # 영어 분류 체계 (카테고리 이름은 국내와 동일)
│   ├── validate_en_taxonomy.py  # 영어 키워드 vs Gemini 정밀도·재현율
│   ├── analyze_global.py        # 해외 전수 분석 (국내 analyze.py와 같은 항목)
│   ├── global_prep.py           # 중복 제거·Gemini 표본 3,000
│   ├── llm_absa.py, gemini_client.py  # Gemini 분석 (국내와 공용)
│   └── compare_kr_global.py     # Gemini 집계·국내 비교·리워드·DR 심층
├── data/
│   ├── global/{국가}_{appId}.jsonl        # Play 원본 76,891건 (+ _meta.json 전체 평점)
│   ├── global_ios/{국가}_{appId}.ios.jsonl # App Store 397건 (+ _ios_ratings.json)
│   └── ios_search_global.json
└── out/global/
    ├── global_reviews.csv, global_apps_overview.csv     # 중복 제거본 (10자 이상, Gemini 표본용)
    ├── llm_sample_global.csv / llm_results_global.jsonl  # Gemini 표본·결과
    ├── human_val_items_global.csv / human_val_labels_global.json  # 블라인드 판독 30건
    ├── en_keyword_vs_gemini.csv          # 영어 키워드 정확도
    ├── aspects_kr_vs_global.csv, compare.json, same_app_*.csv
    └── full/                             # 전수 분석 (5자 이상)
        ├── classified_reviews.csv (58,986건), apps_overview.csv, ratings_vs_reviews.csv
        ├── pain_by_group_all.csv / pain_by_group_recent.csv
        └── summary.json, quotes.json
```

### 10-2. 재실행
```bash
.venv/bin/python -I research/voc/scripts/collect_global.py research/voc/data/global
.venv/bin/python -I research/voc/scripts/find_ios_global.py > research/voc/data/ios_search_global.json
.venv/bin/python -I research/voc/scripts/collect_ios_global.py research/voc/data/ios_search_global.json research/voc/data/global_ios
.venv/bin/python -I research/voc/scripts/global_prep.py research/voc/data/global research/voc/out/global
.venv/bin/python -I research/voc/scripts/llm_absa.py research/voc/out/global/llm_sample_global.csv research/voc/out/global/llm_results_global.jsonl
.venv/bin/python -I research/voc/scripts/validate_en_taxonomy.py research/voc/out/global
.venv/bin/python -I research/voc/scripts/analyze_global.py research/voc/data research/voc/out/global/full
.venv/bin/python -I research/voc/scripts/compare_kr_global.py research/voc/out
```
- Gemini 실행은 `research/voc/.env`의 `GEMINI_API_KEY`가 필요하다. 한도에 걸려 멈추면 같은 명령으로 이어서 진행한다.
- 영어 패턴을 바꿨다면 `validate_en_taxonomy.py` → `analyze_global.py` 순서로 다시 돌린다.

### 10-3. `full/classified_reviews.csv` 열 정의

| 열 | 설명 |
|---|---|
| `group_ko` | 해외 현대차그룹 / 해외 경쟁 완성차 / 해외 충전 앱 / 해외 리워드·DR 앱 |
| `app_id`, `review_id` | 앱 Play ID, 리뷰 ID (iOS는 `ios-` 접두어) |
| `app`, `country` | 앱 이름, 수집 국가(us/gb) — 중복 제거 시 먼저 수집된 국가 |
| `store`, `score`, `thumbs_up`, `at`, `content` | 국내와 동일 |
| 불편 19개 열 | 해당 불편 언급 여부 |
| `H1`~`H6a` 7개 열 | 가설 패턴 해당 여부 |

### 10-4. 다음에 할 수 있는 분석
- 독일어·프랑스어 리뷰 추가 (유럽 대륙)
- 유료 모델로 재분류, 팀원 판독 추가
- 유럽판 통합 앱의 버전별 분석 (업데이트 전후 반응)

---

## 11. 변경 이력

| 날짜 | 버전 | 내용 |
|---|---|---|
| 2026-10-25 | v1.1 (통합 리포트) | 해외 30개 앱 수집, Gemini 3,000건 분석, 리워드·DR 앱 심층 |
| 2026-10-25 | v1.2 (통합 리포트) | 해외 전수 분석 — 영어 분류 체계(Gemini 대비 검증), 58,986건, 해외 App Store·전체 평점 |
| 2026-10-25 | v2.0 | 해외 리포트로 분리. 국내와 같은 목차로 재구성 |
