# VIA — Pleos Connect 앱

> **V**erified rewards · **I**n-route · **A**ffordable — 가는 길에, 가장 싸게, 차가 확인한 보상
>
> 차 안에서 지금 배터리와 목적지를 보고, **내가 가진 멤버십 기준으로 가장 싸고 확실히 되는 충전소**를 경로에 넣어주고, 아낀 돈과 효율 운전을 **차가 직접 확인해 보상**해 주는 앱.

현대자동차그룹 차량용 앱 개발 공모전(2026) 출품작 · 팀 **볼트앤페이(Volt&Pay)** · 1차 MVP.

이름의 뜻: *via*는 "~을 경유하여"라는 뜻이다. 충전소를 경로의 경유지로 넣는 이 앱의 핵심 동작과 같다.

## 핵심 장면
1. **주행 중 — 경로상 최저가 충전소 추천**: 도착 시 배터리가 15% 아래면, 고장·사용 중이 아니고 내 차 커넥터가 맞는 충전소 중 내 멤버십 기준 총비용이 가장 싼 곳을 **음성으로** 묻고("경유지로 추가할까요?"), "응" 하면 내비 경유지로 넣는다. 주행 중 화면은 터치할 것이 없는 상태 카드뿐이다.
2. **충전 — 요금·절약액·스탬프**: 충전을 시작하면 위치로 충전소를 찾아 적용 요금(회원가/로밍가/비회원가)과 비회원가 대비 절약 예상액을 보여주고, 처음 온 충전소면 스탬프를 찍는다.
3. **레이드형 이벤트 충전소**: 한산한 시간·장소(예: 22~02시)에 충전하면 포인트 2배 — 충전 수요를 분산하는 대가를 사업자가 낸다.
4. **차량 데이터로 자동 검증되는 리워드**: 충전·스탬프·이벤트·효율 운전 포인트는 사용자가 아무것도 누르지 않아도 차량 신호로 쌓인다. 포인트는 현금성이 아니라 제휴 충전 할인 쿠폰 교환용이다.
5. **수집과 꾸준함**: 포켓몬 도감식 충전소 도감(지역·발견/등록·동·은·금 3D 메달), 주간 미션·리서치 돌파, 경부하 부스트, 사업자 데이(한정 메달), 4주 단위 충전 혜택 시즌. 수집·시즌 화면은 **차를 세워 둔 충전 중에만** 보여 주고, 홈·주행 화면은 충전 유틸리티로 둔다.

## 사용 SDK와 권한

| SDK | 버전 | 쓰는 곳 | 권한 |
|---|---|---|---|
| Vehicle | 2.3.2 | SoC·배터리 에너지·전비, 충전 스위치, 기어(주행/정차), 주행거리, VIN | `CAR_ENERGY`, `CAR_DRIVE`, `CAR_INFO` |
| NaviHelper | 3.0.5 | 경로 정보, 경유지 추가 | `NAVI_ROUTE`, `NAVI_ROUTE_SEARCH` |
| FusedLocation | 1.1.3 | 충전소 매칭(반경 100m) | `FUSED_LOCATION`, `ACCESS_FINE_LOCATION` |
| TextToSpeech | 3.3.1 | 주행 중 음성 안내 | `TTS_SERVICE` |
| SpeechToText | 3.3.1 (ON_DEVICE) | "응/아니" 음성 응답 | `STT_SERVICE`, `RECORD_AUDIO` |

차량 제어 권한(`CONTROL_*`)은 쓰지 않는다.

## 서체
Pleos Connect System UI 서체 **Asta Sans**(42dot)를 앱에 포함한다. [SIL Open Font License 1.1](app/src/main/assets/licenses/AstaSans-OFL.txt) · 출처 https://github.com/42dot/Asta-Sans

## 충전소 도감
포켓몬 도감처럼 번호(No.001~), 지역(서울 도심·강남·서초·경기 남부), 출력 타입(초급속·급속·완속)으로 나눈다.
- 경로 추천에 나온 곳은 **발견**(흑백 실루엣), 충전한 곳은 **등록**(컬러 메달), 못 본 곳은 ???
- 같은 곳을 다시 충전하면 메달 테두리가 동(1회) → 은(3회) → 금(5회)
- 지역을 다 모으면 +50P와 음성 안내

## 충전 중 화면: 메달 진열장 · 충전 혜택 시즌
충전은 차를 세워 두는 20~40분이라, 수집·시즌 요소는 이 화면에만 모은다(홈·주행 화면은 유틸리티 그대로).
- **메달 진열장**: 3D 선반 위에 도감 9곳의 메달(등록=단계 메달, 발견=흑백, 미발견=흐림)
- **충전 혜택 시즌**: 2026-10-05부터 4주 단위. 시즌 안에 받은 포인트가 경험치, 6단계 보상(코인·상자·시즌 메달 +10~50P)

## 주간 미션 · 경부하 부스트 · 사업자 데이
포켓몬 고에서 빌린 장치. 모두 절약 행동에만 보상하고, 주행 거리에는 보상하지 않는다.
- **주간 미션**(필드 리서치): 회원가·로밍가로 2번 충전, 효율 운전 2번, 새 충전소 1곳 등록. 미션마다 +15P, 7개 모으면 리서치 돌파 +100P
- **경부하 부스트**(날씨 부스트): 23~9시 충전 시작 시 충전 포인트 2배 (시연용 예시 시간대)
- **사업자 데이**(커뮤니티 데이): 정해진 날 그 사업자 충전소에서 충전하면 포인트 3배 + 한정 메달 (`mock/operator_days.json`)

3D 이미지는 모두 같은 스타일(채도 높은 반광 플라스틱과 위가 밝은 그라데이션, 포인트로 반투명 유리·푸른빛 은색, 솟은 테두리의 두툼한 동전, 흰 글자, 살짝 비튼 3/4 시점)이다. 재질·조명은 `tools/blender_style.py` 한 곳에서 정한다. 전체를 다시 만들 때는
`BLENDER=/path/to/Blender bash tools/render_all.sh` (렌더 76장, 앱 리소스·PT 표지 그림까지 갱신).

메달은 Blender(Cycles)로 렌더링한 이미지다(`app/src/main/res/drawable-nodpi/medal_*.webp`, 사업자 9곳 × 동·은·금 + 잠긴 메달).
`Blender -b -P tools/blender_medal.py -- <asta_sans.ttf> <out.png> "#00A06A" "채" [bronze|silver|gold|locked]`
`python3 tools/medal_to_webp.py <out.png>... app/src/main/res/drawable-nodpi`

그 밖의 3D 소품(포인트 코인, 리서치 돌파 상자, 지역 엠블럼, VIA 심볼, 경부하 달)도 같은 조명으로 렌더링한다.
`Blender -b -P tools/blender_props.py -- <asta_sans.ttf> <out.png> coin | chest closed|open [t] | emblem 강 gold|locked | symbol | moon | card "#00A06A" 채 | ticket "#00A06A" "2,000원" [--size N]`
리서치 돌파 상자는 열린 정도(t) 0~1을 6장면으로 렌더링해 `python3 tools/frames_to_webp.py prop_chest_f <장면들> <res>`로 같은 구도로 자른다. PT 표지용 심볼은 `doc/assets/via_symbol_2048.png`(투명 배경).
새 스탬프 획득 연출은 메달을 `--spin 0~315`(45° 간격)로 8장면 렌더링한다. PT 표지·영상 썸네일용 히어로 컷 `doc/assets/via_hero_1920x1080.png`은 `python3 tools/compose_hero.py <렌더 폴더> <symbol.png> <asta_sans.ttf> <out.png>`로 만든다.

## 구조
- `core/` — 순수 Kotlin. 도착 잔량(`ArrivalCalculator`), 멤버십 기준 실효 단가·순위(`StationRanker`), 리워드 규칙(`RewardEngine`), 충전 세션 감지(`ChargeSessionDetector`), 도감(`dex/Dex`), 주간 미션(`missions/Missions`), 충전 혜택 시즌(`season/Seasons`), 목업 데이터. JVM 단위 테스트.
- `app/` — Android(Compose). SDK는 Repository 뒤에 숨기고(`Pleos*`/`Mock*`), 추천 흐름(`RecommendationCoordinator`)과 리워드 적립(`RewardCoordinator`)이 이를 조합한다. 기록은 앱 내부 파일(포인트 원장·발견한 충전소·충전 요약)에 남는다(서버 없음).
- `tools/` — Blender 3D 렌더 스크립트(`blender_style.py`·`blender_medal.py`·`blender_props.py`, 전체는 `render_all.sh`), Virtual Console 조작(`vc.sh`).
- 단위 테스트 138개(core 63 · app 75).

## 제출 APK
`apk/VoltAndPay_VIA_release.apk` (공모전 규칙: `팀명_앱이름_release.apk`)

## 빌드
```bash
./gradlew :core:test :app:testDebugUnitTest   # 테스트
./gradlew :app:installDebug                   # 에뮬레이터 설치
./gradlew :app:assembleRelease                # 릴리스 APK
```
- JDK 17 이상(JBR 21 사용), Android SDK 36, Pleos Connect Emulator(API 34 Automotive).
- 릴리스 서명 키는 저장소 밖에 두고 `local.properties`에 `release.storeFile`, `release.storePassword`, `release.keyAlias`, `release.keyPassword`를 적는다(없으면 디버그 키로 서명).

## 테스트 환경 및 필요 설정
1. Pleos Connect Emulator(`Pleos_Connect_v2.1`, Android 14 Automotive)를 켜고 APK를 설치한다: `adb install -r --user 10 apk/VoltAndPay_VIA_release.apk`
2. 처음 실행하면 **위치·마이크 권한을 허용**하고, 온보딩에서 **차종**(시연: 아이오닉 5 롱레인지)과 **가진 충전 멤버십**(시연: 채비)을 고른다. 에뮬레이터 VIN은 `"invalid"`라 차종을 자동으로 알 수 없다.
3. 차량 상태(배터리·기어·충전)는 Virtual Console로 바꾼다: `adb forward tcp:8088 tcp:8088` 후 `tools/vc.sh soc 15`, `tools/vc.sh gear Drive`, `tools/vc.sh charge on`. **Virtual Console은 웹 페이지(http://127.0.0.1:8088)가 브라우저 앞에 열려 있을 때만 동작한다.**
4. 경로는 내비 앱에서 목적지를 정한다(시연: 서울역). 경유지 추가는 경로 안내가 시작된 뒤에 된다.
5. 디버그 빌드(`./gradlew :app:installDebug`)에는 오른쪽 위 **DEV** 시연 패널이 있어 Virtual Console 없이(MOCK 모드) 같은 장면을 재생할 수 있다. 릴리스 APK에는 없다.

## 시연
[doc/시연-시나리오.md](doc/시연-시나리오.md) — Pleos 모드(Virtual Console) / Mock 모드(디버그 패널), 장면 A~E.

## 문서
- [설계 문서](doc/설계문서-초안.md) · [구현 계획](doc/구현계획-1차MVP.md) · [스파이크 결과](doc/스파이크-결과-SoC-NaviHelper.md)
- 근거: [운전자 페인포인트 조사](doc/운전자-페인포인트-조사.md) · [앱 리뷰 VOC 국내·해외 비교](doc/앱리뷰-VOC-국내해외-비교.md) · [유사사례 조사](doc/유사사례-조사.md)
