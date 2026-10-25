# VIA 1차 MVP 구현 계획

> **에이전트 작업자용:** 필수 하위 스킬 — superpowers:subagent-driven-development(권장) 또는 superpowers:executing-plans로 태스크 단위 실행. 단계는 체크박스(`- [ ]`)로 추적한다.

**목표:** 11/15 1차 접수용으로, Pleos 에뮬레이터에서 장면 A(배터리 부족 → 경로상 최저가 충전소 추천 → 음성 안내 → 경유지 추가)와 장면 C(이벤트 충전소), 스탬프 도감이 실제로 동작하는 앱과 APK·영상을 만든다.

**아키텍처:** 계산 로직(도착 잔량, 실효 단가, 순위, 리워드, 충전 세션 감지)은 Android에 의존하지 않는 순수 Kotlin 모듈 `:core`에 두고 JVM 단위 테스트로 검증한다. `:app`은 Pleos SDK를 Repository 한 겹 뒤에 숨기고, 모든 Repository에 Mock 구현을 둬서 Virtual Console 없이도 시연을 재생할 수 있게 한다. UI는 Compose로 만들고, 화면 노출은 주행/정차/충전 상태로 막는다.

**기술 스택:** Kotlin 2.2.20, AGP 8.13.0, Gradle 8.14.3, JDK 17 타깃(JBR 21로 빌드), Jetpack Compose, kotlinx.serialization(JSON 목업), kotlinx.coroutines(Flow), JUnit 5 + kotlin.test, Pleos SDK Vehicle 2.3.2 · NaviHelper 3.0.5 · TextToSpeech 3.3.1 · SpeechToText · FusedLocation 1.1.3

**설계 문서:** [설계문서-초안.md](설계문서-초안.md) (v0.2, 승인됨). 실행자는 이 계획과 설계 문서를 함께 읽는다.

## 전역 제약

- minSdk 28, targetSdk 34, compileSdk 36, jvmTarget 17 (스파이크와 같은 값)
- Maven 저장소: `https://nexus-playground.pleos.ai/repository/maven-releases/`
- **SDK 시그니처는 컴파일러로 확인한다.** SDK 디컴파일 금지(42dot 라이선스 3.4조). AAR은 파일 목록만 본다.
- SDK 콜백은 바인더 스레드에서 온다 → Repository에서 Flow로 바꾸고 UI는 메인 스레드에서 수집한다.
- 차량 제어 권한(`CONTROL_*`)은 쓰지 않는다. 권한: `CAR_ENERGY`, `CAR_DRIVE`, `CAR_INFO`, `NAVI_ROUTE`, `NAVI_ROUTE_SEARCH`, 위치, 마이크.
- **주행 중에는 터치 입력 UI를 띄우지 않는다.** 주행 중 화면은 상태 카드 1개만, 안내는 음성으로.
- 터치 반응 0.25초 이내, 2초를 넘는 작업은 로딩 표시, 앱 실행 10초 이내 (Pleos 심사 기준).
- 레이아웃은 2560×1440 기준, 좌측 약 1/3 시스템 패널을 뺀 영역에 그린다.
- 금액은 원 단위 정수로 표시한다(`1,234원`). kWh는 보조 정보.
- 광고·판매 배너 없음. 앱 분류는 충전 유틸리티(게임 아님).
- 포인트는 현금성 아님. 제휴 할인 쿠폰 교환 화면만 있고, 실제 교환은 하지 않는다.
- 서버 없음. 영속 데이터는 로컬 저장소만.
- 결과 APK 경로·이름: `apk/VoltAndPay_VIA_release.apk` (팀 볼트앤페이, 앱 VIA — 2026-10-25 확정).
- 기본값([제안], 설정 상수 한 곳에 모은다): 충전 필요 기준 도착 SoC **15%**, 목표 SoC **80%**, 우회 시간 가치 **0원**, 음성 추천 **1곳**, 비교 화면 **3곳**, 효율 운전 최소 트립 **10km**, 이벤트 배수 **2배**(하루 1회), 스탬프 충전소당 1회, 포인트 유효기간 **12개월**.
- 이 계획이 정한 포인트 값(설계 문서에서 "조정 가능"으로 남긴 것): 효율 운전 트립 **10점**, 새 스탬프 **20점**, 충전 1회 기본 **10점**(이벤트면 ×2).

## 리뷰 중점 (테스트가 따로 다루지 않으면 사용자를 가장 먼저 괴롭힐 경우)

1. **SoC·전비가 null이거나 전비가 0** — 출발 직후나 SDK 실패 시. 0으로 나누지 말고 차종 기본 전비로 대체한다. SoC가 없으면 추천하지 않는다(잘못된 추천보다 침묵). → Task 3 테스트
2. **추천할 충전소가 하나도 없음** (전부 고장이거나 커넥터가 안 맞거나 도착 전에 닿지 못함) — 아무 말도 안 하면 안 된다. "경로 위에 쓸 수 있는 충전소가 없어요"를 1회 안내한다. → Task 4, Task 10 테스트
3. **충전 상태 신호가 깜빡임** (충전 중↔대기가 몇 초 사이 반복) — 스탬프나 포인트가 두 번 쌓이면 안 된다. 짧은 끊김은 같은 세션으로 본다. → Task 6 테스트
4. **멤버십이 하나도 없음** — 비회원가로 계산하고, 로밍가가 있으면 로밍가를 쓴다. → Task 4 테스트
5. **경로 안내 중 목적지 취소·변경** — 이전 경로 기준의 추천·음성 확인이 남아 있으면 안 된다. 경로가 바뀌면 진행 중인 확인을 취소하고 다시 계산한다. → Task 10 테스트

---

## 파일 구조

```
/ (저장소 루트 = 이 프로젝트 폴더)
├── settings.gradle.kts, build.gradle.kts, gradle/, gradlew      ← Task 1
├── core/                                   순수 Kotlin (Android 의존 없음)
│   └── src/main/kotlin/com/chargecompass/core/
│       ├── model/Models.kt                 Task 2  VehicleModel, Station, Tariff, ...
│       ├── catalog/VehicleCatalog.kt       Task 2  VIN → 차종
│       ├── energy/ArrivalCalculator.kt     Task 3
│       ├── pricing/StationRanker.kt        Task 4
│       ├── rewards/RewardEngine.kt         Task 5
│       ├── session/ChargeSessionDetector.kt Task 6
│       ├── data/MockDataLoader.kt          Task 7
│       └── Defaults.kt                     Task 2  전역 제약의 기본값 상수
│   └── src/main/resources/mock/*.json      Task 7
├── app/                                    Android 앱
│   └── src/main/java/com/chargecompass/app/
│       ├── vehicle/                        Task 8  VehicleRepository + Pleos/Mock
│       ├── navi/                           Task 8  NaviRepository + Pleos/Mock
│       ├── drivestate/                     Task 8  DriveStateProvider + 토글
│       ├── voice/                          Task 9  VoiceGateway + Pleos/Mock
│       ├── store/                          Task 11 로컬 저장 (원장·스탬프·멤버십)
│       ├── recommend/RecommendationCoordinator.kt  Task 10
│       └── ui/                             Task 12 Compose 화면
├── apk/                                    Task 13
├── README.md                               Task 13
├── doc/ research/ spike/                   기존 (spike/는 커밋 제외)
```

---

### Task 0: 스파이크 2차 — 남은 SDK 동작 확인 (버리는 코드)

설계 문서 8절 1~5번을 확인한다. **TDD·커밋 없음.** `spike/soc-spike`에 이어서 작성한다.

**산출물:** [스파이크-결과-SoC-NaviHelper.md](스파이크-결과-SoC-NaviHelper.md)에 "2차" 절 추가 — 항목별 성공/실패, 컴파일러로 확인한 시그니처, 실패 시 설계 8-1절의 대안 중 무엇을 쓰는지.

- [ ] **Step 1:** NaviHelper `addWaypoint` / `requestRouteWithWaypoints`로 경로 중간에 충전소 좌표 1곳 추가 → 경로 거리·시간이 바뀌는지 확인. 실패하면 `requestRoute`로 목적지를 충전소로 바꾸는 대안 동작 확인.
- [ ] **Step 2:** `CarUxRestrictionsManager`(AAOS 표준) 주행 제한 이벤트가 에뮬레이터에서 오는지 확인 (Virtual Console 속도·기어 조작). 안 오면 대안 = 시연 토글.
- [ ] **Step 3:** EvBattery 전비 API 4종, 충전 상태(충전 스위치/포트/전류), Odometer 트립 거리·시간(트립 종료를 알 수 있는지) 조회·구독이 주는 값과 단위를 Virtual Console 조작으로 확인.
- [ ] **Step 4:** CarInfo VIN 조회 값과 형식 확인.
- [ ] **Step 5:** TextToSpeech로 한국어 문장 재생, SpeechToText로 "응"/"아니" 인식 결과 확인. 실패하면 대안 = 정차 중 터치.
- [ ] **Step 6:** 결과를 스파이크 문서에 정리. **Task 8·9의 인터페이스는 바뀌지 않는다** — 바뀌는 것은 Pleos 구현 쪽과 대안 사용 여부뿐.

---

### Task 1: 프로젝트 골격과 빌드

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/wrapper/*`, `gradlew`, `.gitignore`
- Create: `core/build.gradle.kts`, `core/src/test/kotlin/com/chargecompass/core/SmokeTest.kt`
- Create: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/chargecompass/app/MainActivity.kt`

**Interfaces:**
- Produces: 모듈 `:core`(Kotlin JVM, JUnit 5) · `:app`(Android, `:core` 의존). 패키지 `com.chargecompass.core` / `com.chargecompass.app`. applicationId `com.chargecompass.app`.

- [ ] **Step 1:** 저장소 루트에서 `git init`. `.gitignore`에 `build/`, `.gradle/`, `local.properties`, `*.iml`, `.idea/`, `spike/`, `research/voc/.env`, `research/voc/.venv/`, `research/voc/data/`(원자료 용량 큼 — 결과 out/만 커밋).
- [ ] **Step 2:** Gradle 설정. 스파이크의 `settings.gradle.kts`(Pleos maven 포함), wrapper 8.14.3, AGP·Kotlin 버전을 그대로 쓴다. `:app`에 Compose, kotlinx-coroutines-android, Pleos SDK 5종 의존성. `:core`에 kotlinx-serialization-json, kotlinx-coroutines-core, JUnit 5.
- [ ] **Step 3:** 매니페스트 — 스파이크 매니페스트 기준(automotive feature, `distractionOptimized`) + 전역 제약의 권한 목록.
- [ ] **Step 4:** `SmokeTest`에 `assertEquals(2, 1 + 1)` 하나.
- [ ] **Step 5:** 확인.
  Run: `JAVA_HOME=<JDK 17 이상 경로> ./gradlew :core:test :app:assembleDebug`
  Expected: `BUILD SUCCESSFUL`, 에뮬레이터에 설치하면 빈 화면이 뜬다.
- [ ] **Step 6:** Commit — `chore: 프로젝트 골격 (core·app 모듈, Pleos SDK)`

---

### Task 2: 도메인 모델 · 기본값 · 차종 카탈로그

**Files:**
- Create: `core/src/main/kotlin/com/chargecompass/core/model/Models.kt`, `.../Defaults.kt`, `.../catalog/VehicleCatalog.kt`
- Test: `core/src/test/kotlin/com/chargecompass/core/catalog/VehicleCatalogTest.kt`

**Interfaces:**
- Produces:
```kotlin
enum class Connector { DC_COMBO, AC_TYPE1, AC_TYPE2, CHADEMO, NACS }
enum class ChargerStatus { AVAILABLE, IN_USE, BROKEN, MAINTENANCE, UNKNOWN }
data class VehicleModel(val id: String, val name: String, val batteryKwh: Double,
    val baseEfficiencyKmPerKwh: Double, val connectors: Set<Connector>, val vinPrefixes: List<String>)
data class Membership(val id: String, val operatorId: String, val name: String)
data class Tariff(val operatorId: String, val memberWonPerKwh: Int, val roamingWonPerKwh: Int?,
    val nonMemberWonPerKwh: Int, val membershipIds: Set<String>)   // 이 요금을 회원가로 받는 멤버십들
data class Station(val id: String, val name: String, val operatorId: String,
    val lat: Double, val lng: Double, val maxKw: Int, val connectors: Set<Connector>,
    val status: ChargerStatus, val alongRouteKm: Double, val detourKm: Double)
data class RewardEvent(val id: String, val stationId: String, val title: String,
    val startHour: Int, val endHour: Int, val multiplier: Int)    // 22~2시처럼 자정을 넘을 수 있다
object Defaults { const val NEED_CHARGE_SOC = 15.0; const val TARGET_SOC = 80.0; /* 전역 제약의 나머지 값 */ }
class VehicleCatalog(models: List<VehicleModel>) { fun byVin(vin: String?): VehicleModel?; fun byId(id: String): VehicleModel?; val all: List<VehicleModel> }
```
- `Station.alongRouteKm`·`detourKm`는 현재 경로 기준 값이다. MVP에서는 목업 데이터에 시연 경로 기준으로 미리 넣는다(Task 7). 실제 경로 형상 계산은 2차.

- [ ] **Step 1: 실패하는 테스트**
  - `byVin("KMHK381...")` → `vinPrefixes`에 `"KMHK3"`가 있는 차종 반환
  - `byVin(null)` → `null`, 어느 접두어에도 안 맞으면 `null`
  - 접두어가 겹치면 더 긴 접두어 우선
- [ ] **Step 2:** `./gradlew :core:test --tests '*VehicleCatalogTest'` → FAIL (클래스 없음)
- [ ] **Step 3:** 구현.
- [ ] **Step 4:** 같은 명령 → PASS
- [ ] **Step 5:** Commit — `feat(core): 도메인 모델과 차종 카탈로그`

---

### Task 3: 도착 잔량 계산

**Files:**
- Create: `core/src/main/kotlin/com/chargecompass/core/energy/ArrivalCalculator.kt`
- Test: `core/src/test/kotlin/com/chargecompass/core/energy/ArrivalCalculatorTest.kt`

**Interfaces:**
- Consumes: `VehicleModel`, `Defaults` (Task 2)
- Produces:
```kotlin
data class EnergyState(val socPct: Int?, val efficiencyKmPerKwh: Double?)
object ArrivalCalculator {
    /** 전비가 null이거나 0 이하면 vehicle.baseEfficiencyKmPerKwh를 쓴다. socPct가 null이면 null. */
    fun socAfter(state: EnergyState, vehicle: VehicleModel, distanceKm: Double): Double?
    fun needsCharge(arrivalSoc: Double?, thresholdPct: Double = Defaults.NEED_CHARGE_SOC): Boolean  // null이면 false
}
```
- 공식은 설계 문서 5-2절. 결과는 음수가 될 수 있다(도착 전에 방전 = 반드시 충전 필요).

- [ ] **Step 1: 실패하는 테스트** (용량 77.4kWh, 기본 전비 5.0 차종 기준)
  - SoC 40%, 전비 5.0, 100km → `socAfter` ≈ `40 − 100/5/77.4×100` = **14.16** (±0.01), `needsCharge` = true
  - SoC 80%, 전비 5.0, 23km → ≈ **74.06**, `needsCharge` = false
  - 전비 `null` → 기본 전비로 같은 결과 / 전비 `0.0` → 기본 전비 (리뷰 중점 1)
  - SoC `null` → `socAfter` = null, `needsCharge(null)` = false (리뷰 중점 1)
  - SoC 5%, 300km → 음수, `needsCharge` = true
- [ ] **Step 2:** `--tests '*ArrivalCalculatorTest'` → FAIL
- [ ] **Step 3:** 구현.
- [ ] **Step 4:** → PASS
- [ ] **Step 5:** Commit — `feat(core): 도착 잔량 계산`

---

### Task 4: 실효 단가와 충전소 순위

**Files:**
- Create: `core/src/main/kotlin/com/chargecompass/core/pricing/StationRanker.kt`
- Test: `core/src/test/kotlin/com/chargecompass/core/pricing/StationRankerTest.kt`

**Interfaces:**
- Consumes: Task 2 모델, `ArrivalCalculator.socAfter` (Task 3)
- Produces:
```kotlin
enum class PriceBasis { MEMBER, ROAMING, NON_MEMBER }
data class EffectivePrice(val wonPerKwh: Int, val basis: PriceBasis, val membershipId: String?)
data class RankedStation(val station: Station, val price: EffectivePrice, val chargeKwh: Double,
    val totalCostWon: Int, val nonMemberCostWon: Int, val savingWon: Int, val arrivalSocPct: Double)
object StationRanker {
    fun effectivePrice(tariff: Tariff, owned: Set<String>): EffectivePrice
    fun rank(candidates: List<Station>, tariffs: Map<String, Tariff>, owned: Set<String>,
             state: EnergyState, vehicle: VehicleModel,
             targetSocPct: Double = Defaults.TARGET_SOC, minArrivalSocPct: Double = 0.0): List<RankedStation>
}
```
- 규칙 (설계 5-2절):
  - 실효 단가 = 가진 멤버십이 `tariff.membershipIds`에 있으면 회원가, 없으면 `min(로밍가, 비회원가)`.
  - 그 충전소 도착 SoC = `socAfter(state, vehicle, alongRouteKm + detourKm)`.
  - 충전량 = `(target − 도착 SoC) × 용량 / 100`, 0 미만이면 0.
  - 총비용 = 충전량 × 실효 단가 + 우회 에너지 비용(`detourKm / 전비 × 실효 단가`). 반올림해 원 단위.
  - 절약액 = 비회원가 기준 비용 − 총비용.
  - 제외: 상태가 `AVAILABLE`이 아님(`UNKNOWN` 포함), 내 차 커넥터와 겹치지 않음, 도착 SoC < `minArrivalSocPct`, 요금표 없음.
  - 정렬: 총비용 오름차순 → 같으면 `maxKw` 내림차순.

- [ ] **Step 1: 실패하는 테스트**
  - `effectivePrice`: 멤버십 있음 → MEMBER / 없음 + 로밍가 더 쌈 → ROAMING / 멤버십 없고 로밍가 null → NON_MEMBER (리뷰 중점 4)
  - 같은 위치의 A(회원가 300원), B(비회원가 450원) → A가 1위, `savingWon` > 0
  - 고장·점검·사용 중·UNKNOWN 충전소는 결과에 없음
  - 커넥터가 `CHADEMO`뿐인 충전소는 `DC_COMBO` 차량 결과에 없음
  - 총비용이 같으면 200kW가 100kW보다 앞
  - 모든 후보가 제외되면 빈 리스트 (리뷰 중점 2)
- [ ] **Step 2:** → FAIL
- [ ] **Step 3:** 구현.
- [ ] **Step 4:** → PASS
- [ ] **Step 5:** Commit — `feat(core): 멤버십 기준 실효 단가와 충전소 순위`

---

### Task 5: 리워드 엔진

**Files:**
- Create: `core/src/main/kotlin/com/chargecompass/core/rewards/RewardEngine.kt`
- Test: `core/src/test/kotlin/com/chargecompass/core/rewards/RewardEngineTest.kt`

**Interfaces:**
- Consumes: `RewardEvent`, `VehicleModel` (Task 2), `ChargeSession` (Task 6 — 이 태스크에서 data class만 먼저 정의해도 된다: `data class ChargeSession(val stationId: String?, val startedAt: java.time.LocalDateTime, val endedAt: java.time.LocalDateTime?, val startSoc: Int?, val endSoc: Int?)`)
- Produces:
```kotlin
enum class RewardKind { EFFICIENT_TRIP, CHARGE, EVENT_BONUS, NEW_STAMP }
data class RewardEntry(val kind: RewardKind, val points: Int, val at: LocalDateTime,
    val stationId: String? = null, val eventId: String? = null)
interface RewardHistory {   // Task 11이 영속 구현을 만든다
    fun stampedStationIds(): Set<String>
    fun eventBonusDates(eventId: String): Set<LocalDate>
}
object RewardEngine {
    fun onTripEnded(distanceKm: Double, tripEfficiency: Double?, vehicle: VehicleModel, at: LocalDateTime): List<RewardEntry>
    fun onChargeStarted(session: ChargeSession, events: List<RewardEvent>, history: RewardHistory): List<RewardEntry>
}
```
- 규칙 (설계 5-3절, 점수는 전역 제약):
  - 효율 운전: `distanceKm ≥ 10` 이고 `tripEfficiency ≥ vehicle.baseEfficiencyKmPerKwh` → 10점. 전비 null이면 없음. 주행거리 자체엔 보상하지 않는다.
  - 충전 시작: `stationId`가 있으면 CHARGE 10점. 처음 보는 충전소면 NEW_STAMP 20점 추가.
  - 이벤트: 그 충전소의 이벤트이고 시작 시각이 이벤트 시간대 안이면 EVENT_BONUS = 기본 10점 × (multiplier − 1). 같은 이벤트는 하루 1회. 이벤트 날짜는 **시작 시각이 속한 이벤트 회차**의 시작일(22~2시 이벤트에서 01시 충전 → 전날 회차).
  - `stationId`가 null(충전소 매칭 실패)이면 아무것도 주지 않는다.

- [ ] **Step 1: 실패하는 테스트**
  - 12km·전비 5.5(기본 5.0) → `EFFICIENT_TRIP` 10점 1건 / 9km → 없음 / 전비 4.9 → 없음 / 전비 null → 없음
  - 새 충전소 → CHARGE 10 + NEW_STAMP 20 / 이미 스탬프 있음 → CHARGE만
  - 이벤트(22~2시, ×2) 충전소에서 23:30 시작 → EVENT_BONUS 10 / 01:00 시작 → EVENT_BONUS 10 (전날 회차) / 21:59 → 없음
  - 같은 이벤트 회차에 이미 보너스 받음 → EVENT_BONUS 없음
  - `stationId` null → 빈 리스트
- [ ] **Step 2:** → FAIL
- [ ] **Step 3:** 구현.
- [ ] **Step 4:** → PASS
- [ ] **Step 5:** Commit — `feat(core): 효율 운전·충전·이벤트·스탬프 리워드 규칙`

---

### Task 6: 충전 세션 감지

**Files:**
- Create: `core/src/main/kotlin/com/chargecompass/core/session/ChargeSessionDetector.kt`
- Test: `core/src/test/kotlin/com/chargecompass/core/session/ChargeSessionDetectorTest.kt`

**Interfaces:**
- Consumes: `ChargeSession` (Task 5에서 정의)
- Produces:
```kotlin
data class ChargingSample(val at: LocalDateTime, val charging: Boolean, val socPct: Int?, val lat: Double?, val lng: Double?)
sealed interface SessionEvent { data class Started(val session: ChargeSession) : SessionEvent
                                data class Ended(val session: ChargeSession) : SessionEvent }
class ChargeSessionDetector(private val stationMatcher: (lat: Double, lng: Double) -> String?,
                            private val gapTolerance: java.time.Duration = java.time.Duration.ofSeconds(60)) {
    fun onSample(sample: ChargingSample): SessionEvent?
}
```
- 세션 시작 = `charging`이 false→true. 종료 = false가 `gapTolerance` 넘게 이어진 뒤 첫 샘플에서 `Ended`. 그 안에 다시 true면 같은 세션(리뷰 중점 3).
- 충전소 매칭은 시작 시점 좌표로 1회. 매칭기는 Task 7이 제공(반경 **100m** 이내 가장 가까운 충전소, 이 계획이 정한 값).
- Task 0에서 확인한 "충전 중" 판단 신호(충전 스위치·전류 등)를 `charging: Boolean`으로 바꾸는 일은 Task 8의 Pleos 구현이 맡는다.

- [ ] **Step 1: 실패하는 테스트**
  - false, true → `Started` 1회 (stationId·startSoc 채워짐)
  - true 이후 false 30초 → true → 새 `Started` 없음 (깜빡임)
  - true 이후 false가 61초 넘게 → `Ended` 1회, `endSoc` 채워짐
  - 좌표 null → `Started`의 stationId null
- [ ] **Step 2:** → FAIL
- [ ] **Step 3:** 구현.
- [ ] **Step 4:** → PASS
- [ ] **Step 5:** Commit — `feat(core): 충전 세션 감지 (깜빡임 허용)`

---

### Task 7: 목업 데이터와 로더

**Files:**
- Create: `core/src/main/resources/mock/vehicles.json`, `operators_tariffs.json`, `stations_demo_route.json`, `events.json`, `memberships.json`
- Create: `core/src/main/kotlin/com/chargecompass/core/data/MockDataLoader.kt`
- Test: `core/src/test/kotlin/com/chargecompass/core/data/MockDataLoaderTest.kt`

**Interfaces:**
- Consumes: Task 2 모델 (`@Serializable` 추가)
- Produces:
```kotlin
data class MockData(val vehicles: List<VehicleModel>, val tariffs: Map<String, Tariff>,
    val stations: List<Station>, val events: List<RewardEvent>, val memberships: List<Membership>)
object MockDataLoader { fun load(read: (path: String) -> String): MockData }
fun nearestStation(stations: List<Station>, lat: Double, lng: Double, radiusM: Double = 100.0): String?
```
- 데이터 내용(값은 실제 공개 요금·제원에서 가져온다 — 설계 12절):
  - 차종 4종 이상(아이오닉 5, 아이오닉 6, EV6, 코나 일렉트릭 등): 용량·기본 전비·커넥터·VIN 접두어. **Task 0에서 확인한 에뮬레이터 VIN이 이 중 하나에 매칭되게** 넣는다.
  - 충전사업자 **10곳**의 회원가·로밍가·비회원가 (소비자원 270~590원 범위 안).
  - 시연 경로(판교 부근 → 서울역, 23km) 주변 충전소 **8곳 이상**: 고장 1곳, 사용 중 1곳, 커넥터 불일치 1곳, 이벤트 대상 1곳 포함. 각자 `alongRouteKm`·`detourKm`.
  - 이벤트 1개: "22시~02시 포인트 2배".

- [ ] **Step 1: 실패하는 테스트**
  - `load`가 리소스를 모두 읽고, 모든 `Station.operatorId`가 `tariffs`에 있다
  - 시연 경로 충전소에 `BROKEN`과 `IN_USE`가 각각 1곳 이상
  - `events`의 `stationId`가 실제 충전소다
  - `nearestStation`: 50m 거리 → 그 충전소 id / 150m → null
- [ ] **Step 2:** → FAIL
- [ ] **Step 3:** 데이터 파일과 로더 작성.
- [ ] **Step 4:** → PASS
- [ ] **Step 5:** Commit — `feat(core): 시연 경로 목업 데이터와 로더`

---

### Task 8: 차량·내비·주행 상태 Repository

**Files:**
- Create: `app/src/main/java/com/chargecompass/app/vehicle/{VehicleRepository,PleosVehicleRepository,MockVehicleRepository}.kt`
- Create: `app/src/main/java/com/chargecompass/app/navi/{NaviRepository,PleosNaviRepository,MockNaviRepository}.kt`
- Create: `app/src/main/java/com/chargecompass/app/drivestate/{DriveStateProvider,UxRestrictionsDriveStateProvider,ToggleDriveStateProvider}.kt`
- Test: `app/src/test/java/com/chargecompass/app/vehicle/MockVehicleRepositoryTest.kt` (JVM 단위 테스트, Mock만)

**Interfaces:**
- Consumes: `EnergyState`, `ChargingSample` (Task 3, 6)
- Produces:
```kotlin
interface VehicleRepository {
    val energy: StateFlow<EnergyState>
    val charging: Flow<ChargingSample>
    val tripEnded: Flow<TripSummary>           // data class TripSummary(val distanceKm: Double, val efficiencyKmPerKwh: Double?, val at: LocalDateTime)
    suspend fun vin(): String?
    fun start(); fun stop()
}
data class RouteSnapshot(val routeId: Long, val destinationName: String, val remainingKm: Double, val remainingSec: Int)
interface NaviRepository {
    val route: StateFlow<RouteSnapshot?>       // null = 안내 중 아님. 목적지가 바뀌면 routeId가 바뀐다
    suspend fun addChargingStop(station: Station): StopResult   // enum StopResult { WAYPOINT_ADDED, DESTINATION_REPLACED, FAILED }
}
enum class DriveState { DRIVING, PARKED, CHARGING }
interface DriveStateProvider { val state: StateFlow<DriveState> }
```
- `PleosVehicleRepository`: 스파이크에서 확인한 SoC 구독 + Task 0에서 확인한 전비·충전 상태·VIN·트립 API. SDK 콜백 → `MutableStateFlow`/`callbackFlow`. 실패 콜백은 값 `null`로 반영(앱을 죽이지 않는다).
- `PleosNaviRepository.addChargingStop`: Task 0 결과에 따라 `addWaypoint`(→ `WAYPOINT_ADDED`) 또는 대안인 목적지 교체(→ `DESTINATION_REPLACED`, 원래 목적지는 기억해 두고 충전 세션 `Ended` 때 다시 `requestRoute`). `RouteInfo`의 필수 인자 6개는 스파이크 문서 참고.
- `DriveStateProvider` 구현은 생성자로 `chargingActive: StateFlow<Boolean>`을 받고, true면 `CHARGING`이 우선이다. Task 11이 `RewardCoordinator.activeSession`을 이 값으로 연결한다(그 전에는 `MutableStateFlow(false)`). Task 0에서 UX restrictions가 안 되면 `ToggleDriveStateProvider`(디버그 메뉴에서 전환)를 기본으로 쓴다.
- `Mock*`: 시연 스크립트용. `MockVehicleRepository`에 `setSoc(Int)`, `setCharging(Boolean)`, `endTrip(TripSummary)`; `MockNaviRepository`에 `setRoute(RouteSnapshot?)`.
- 구현 선택(Pleos/Mock)은 `BuildConfig` 플래그 `USE_MOCK` + 디버그 메뉴 전환.

- [ ] **Step 1: 실패하는 테스트** — `MockVehicleRepository`: `setSoc(15)` 후 `energy.value.socPct == 15`; `setCharging(true)`가 `charging`에 `charging=true` 샘플 1개를 낸다.
- [ ] **Step 2:** `./gradlew :app:testDebugUnitTest` → FAIL
- [ ] **Step 3:** 인터페이스·Mock 구현 → 테스트 PASS.
- [ ] **Step 4:** Pleos 구현 작성. 시그니처는 컴파일러로 확인한다. `./gradlew :app:assembleDebug` 성공.
- [ ] **Step 5:** 에뮬레이터 확인: 디버그 화면에 SoC·전비·충전 여부·경로 남은 거리·DriveState를 띄우고, Virtual Console에서 SoC를 바꾸면 1초 안에 반영되는지 본다.
- [ ] **Step 6:** Commit — `feat(app): 차량·내비·주행 상태 Repository (Pleos/Mock)`

---

### Task 9: 음성 안내와 확인

**Files:**
- Create: `app/src/main/java/com/chargecompass/app/voice/{VoiceGateway,PleosVoiceGateway,MockVoiceGateway}.kt`
- Test: `app/src/test/java/com/chargecompass/app/voice/YesNoParserTest.kt`

**Interfaces:**
- Produces:
```kotlin
enum class Answer { YES, NO, UNKNOWN, TIMEOUT }
interface VoiceGateway {
    suspend fun speak(text: String)
    /** 질문을 읽고 답을 듣는다. STT를 못 쓰면 바로 UNKNOWN. */
    suspend fun ask(question: String, timeoutMs: Long = 8_000): Answer
    val sttAvailable: Boolean
}
object YesNoParser { fun parse(utterance: String?): Answer }
```
- `YesNoParser`: "응/네/예/그래/좋아/추가해" → YES, "아니/아니요/됐어/싫어/취소" → NO, 그 밖(빈 문자열·null 포함) → UNKNOWN. 공백·문장부호 무시, 부분 일치("응 추가해줘" → YES). 둘 다 들어가면 NO.
- Task 0에서 STT가 안 되면 `sttAvailable = false` → 확인은 정차 중 터치로 넘어간다(Task 10이 처리).

- [ ] **Step 1: 실패하는 테스트** — 위 단어 목록 각각, `"응 추가해줘"` → YES, `"아니 됐어"` → NO, `"응 아니"` → NO, `null`/`""` → UNKNOWN.
- [ ] **Step 2:** → FAIL
- [ ] **Step 3:** 파서·Mock·Pleos 구현.
- [ ] **Step 4:** → PASS. 에뮬레이터에서 `speak("도착 시 배터리가 8%예요")`가 들리는지 확인.
- [ ] **Step 5:** Commit — `feat(app): 음성 안내·예/아니오 확인`

---

### Task 10: 추천 흐름 조율 (장면 A)

**Files:**
- Create: `app/src/main/java/com/chargecompass/app/recommend/RecommendationCoordinator.kt`
- Test: `app/src/test/java/com/chargecompass/app/recommend/RecommendationCoordinatorTest.kt` (Fake Repository + `kotlinx-coroutines-test`)

**Interfaces:**
- Consumes: `VehicleRepository`, `NaviRepository`, `DriveStateProvider` (Task 8), `VoiceGateway` (Task 9), `StationRanker`, `ArrivalCalculator` (Task 3·4), `MockData` (Task 7), `MembershipStore` (Task 11 — 여기선 `() -> Set<String>`로 받는다)
- Produces:
```kotlin
sealed interface RecoState {
    data object Idle : RecoState
    data class Enough(val arrivalSoc: Double) : RecoState
    data class Suggested(val routeId: Long, val arrivalSoc: Double, val top: List<RankedStation>) : RecoState // 최대 3곳
    data class AwaitingTouch(val routeId: Long, val choice: RankedStation) : RecoState  // STT 불가 → 정차 시 확인
    data class Added(val station: RankedStation, val result: StopResult) : RecoState
    data object NoneAvailable : RecoState
}
class RecommendationCoordinator(/* 위 의존성 */, scope: CoroutineScope) {
    val state: StateFlow<RecoState>
    fun confirmByTouch(stationId: String)   // 정차 중 화면에서 호출
}
```
- 흐름:
  1. `route`나 `energy`가 바뀔 때마다 도착 SoC 계산 → 기준 이상이면 `Enough`.
  2. 미만이면 `rank` → 비면 `NoneAvailable` + "경로 위에 쓸 수 있는 충전소가 없어요" 1회 음성 (리뷰 중점 2).
  3. 있으면 `Suggested`. 1위 1곳만 음성 질문(설계 4절 장면 A 문구): "도착 시 배터리가 {n}%예요. {km}km 앞 {이름}에 들르면 내 멤버십 기준 kWh당 {차액}원 더 저렴해요. 경유지로 추가할까요?" — 차액은 1위와 비회원가의 kWh당 차이.
  4. YES → `addChargingStop` → `Added`. NO → 같은 `routeId`에서는 다시 묻지 않는다. UNKNOWN·TIMEOUT 또는 `sttAvailable=false` → `AwaitingTouch`(정차 화면에서 확인).
  5. `routeId`가 바뀌거나 `route`가 null이 되면 진행 중인 질문을 취소하고 1부터 다시 (리뷰 중점 5).
  6. 같은 `routeId`에서 음성 질문은 최대 1회. SoC가 1%p 바뀔 때마다 다시 묻지 않는다.

- [ ] **Step 1: 실패하는 테스트**
  - SoC 80%, 23km → `Enough`, 음성 없음
  - SoC 15%, 23km → `Suggested`, `top.size ≤ 3`, 음성 질문 1회, 문구에 1위 충전소 이름 포함
  - YES → `addChargingStop` 1회 호출, `Added`
  - NO → 같은 routeId에서 SoC가 14로 바뀌어도 질문 0회 추가
  - `sttAvailable=false` → `AwaitingTouch`, `confirmByTouch` 후 `Added`
  - 모든 충전소 BROKEN → `NoneAvailable`, 안내 음성 1회
  - 질문 도중 routeId 변경 → 이전 질문의 답이 와도 `addChargingStop` 호출 없음
- [ ] **Step 2:** → FAIL
- [ ] **Step 3:** 구현.
- [ ] **Step 4:** → PASS
- [ ] **Step 5:** Commit — `feat(app): 경로상 충전 추천 흐름 (음성 확인·터치 대안)`

---

### Task 11: 로컬 저장 — 멤버십 · 포인트 원장 · 스탬프

**Files:**
- Create: `app/src/main/java/com/chargecompass/app/store/{MembershipStore,RewardLedger,LocalStores}.kt`
- Create: `app/src/main/java/com/chargecompass/app/rewards/RewardCoordinator.kt`
- Test: `app/src/test/java/com/chargecompass/app/rewards/RewardCoordinatorTest.kt`

**Interfaces:**
- Consumes: `RewardEngine`, `RewardHistory`, `RewardEntry`, `ChargeSessionDetector`, `nearestStation` (Task 5·6·7), `VehicleRepository.charging`·`tripEnded` (Task 8)
- Produces:
```kotlin
interface MembershipStore { val owned: StateFlow<Set<String>>; suspend fun set(ids: Set<String>);
                            val vehicleModelId: StateFlow<String?>; suspend fun setVehicle(id: String) }
interface RewardLedger : RewardHistory { val entries: StateFlow<List<RewardEntry>>; suspend fun append(e: List<RewardEntry>)
                                         fun balance(now: LocalDateTime): Int }   // 12개월 지난 항목 제외
class RewardCoordinator(vehicle: VehicleRepository, ledger: RewardLedger, data: MockData,
                        catalog: VehicleCatalog, membership: MembershipStore, voice: VoiceGateway, scope: CoroutineScope) {
    val activeSession: StateFlow<ChargeSession?>   // DriveState CHARGING 판단에도 쓴다
}
```
- 저장 방식은 빌더 선택(설계 12절). 단, 앱 재시작 후에도 남아야 한다.
- `RewardCoordinator`: `charging` 샘플 → 세션 감지 → `Started`면 `onChargeStarted` → 원장 기록 → 새 스탬프면 음성 "새 스탬프 획득!" 1회. `tripEnded` → `onTripEnded`.

- [ ] **Step 1: 실패하는 테스트** (인메모리 Ledger)
  - 새 충전소에서 충전 시작 → 원장 30점(10+20), 음성 1회
  - 같은 충전소에서 두 번째 세션 → 10점만, 음성 없음
  - 충전 깜빡임(30초 끊김) → 추가 적립 없음
  - `balance`: 13개월 전 항목은 빠진다
- [ ] **Step 2:** → FAIL
- [ ] **Step 3:** 인메모리 구현으로 PASS → 영속 구현 추가.
- [ ] **Step 4:** → PASS. 에뮬레이터에서 앱을 재시작해도 포인트가 남는지 확인.
- [ ] **Step 5:** Commit — `feat(app): 멤버십·포인트 원장·스탬프 저장과 리워드 적립`

---

### Task 12: 화면 (Compose)

**Files:**
- Create: `app/src/main/java/com/chargecompass/app/ui/` 아래 `AppRoot.kt`, `DriveCard.kt`, `ParkedHome.kt`, `CompareScreen.kt`, `ChargingScreen.kt`, `StampBook.kt`, `EventsScreen.kt`, `PointsExchange.kt`, `Onboarding.kt`, `DebugPanel.kt`, `Format.kt`
- Test: `app/src/test/java/com/chargecompass/app/ui/FormatTest.kt`

**Interfaces:**
- Consumes: `DriveStateProvider.state`, `RecommendationCoordinator.state`·`confirmByTouch`, `RewardLedger`, `RewardCoordinator.activeSession`, `MembershipStore`, `MockData`, `VehicleCatalog`
- Produces: `fun won(amount: Int): String` (`1234` → `"1,234원"`, 음수는 `"-1,234원"`), `fun AppRoot()`

- 화면 노출 규칙 (설계 6-1, 6-2절):
  - `DRIVING`: `DriveCard`만. 도착 SoC, 추천 1곳 이름·거리. **클릭 가능한 요소 없음.**
  - `PARKED`: `ParkedHome`(이번 주 포인트, 오늘의 이벤트 카드, 추천 상태) + 하단 탭 4개(비교·도감·이벤트·포인트). `AwaitingTouch`면 홈 맨 위에 "○○충전소 경유지 추가" 버튼 1개.
  - `CHARGING`: `ChargingScreen`(적용 요금·기준, 비회원가 대비 예상 절약액, 이번에 받은 포인트·스탬프). 요금·절약액은 세션의 충전소 하나로 `StationRanker.rank(listOf(station), …)`을 불러 얻는다. 충전소 매칭이 안 됐으면 요금 칸은 "충전소 확인 불가"로 둔다.
  - 첫 실행(차종·멤버십 미설정)이면 정차 중 `Onboarding`: VIN으로 차종 자동 선택(실패하면 목록에서 고르기), 멤버십 다중 선택, "읽는 데이터: 배터리·전비·충전 상태·주행거리·위치 — 충전소 추천과 포인트 확인에만 씁니다" 안내.
  - `CompareScreen`: 최대 3곳, 실효 단가·기준(회원/로밍/비회원)·총비용·절약액·우회 km·충전 속도.
  - `StampBook`: 방문한 충전소 목록(이름, 첫 방문일). `EventsScreen`: 이벤트 목록과 시간대. `PointsExchange`: 잔액 + 제휴 쿠폰 카드 2~3개, 버튼은 "준비 중" 안내만.
  - `DebugPanel`(디버그 빌드만): Mock/Pleos 전환, DriveState 토글, Mock SoC·충전·트립 종료 조작.
- 레이아웃: 우측 2/3 영역 기준, 큰 글씨, 한 번의 터치. 2초 넘는 로딩은 표시.

- [ ] **Step 1: 실패하는 테스트** — `won(0)="0원"`, `won(1234)="1,234원"`, `won(1234567)="1,234,567원"`, `won(-500)="-500원"`.
- [ ] **Step 2:** → FAIL
- [ ] **Step 3:** `Format.kt` 구현 → PASS. 화면 작성.
- [ ] **Step 4:** 에뮬레이터 확인 (Mock 모드): DriveState를 바꿀 때마다 위 규칙대로 화면이 바뀐다. 주행 중 화면에서 터치할 수 있는 요소가 없다. 탭 전환이 눈에 띄게 끊기지 않는다.
- [ ] **Step 5:** Commit — `feat(app): 주행·정차·충전 상태별 화면`

---

### Task 13: 시연 시나리오 · 패키징 · 제출물

**Files:**
- Create: `doc/시연-시나리오.md`, `README.md`, `apk/VoltAndPay_VIA_release.apk`
- Modify: `app/build.gradle.kts` (release 서명 설정 — 키스토어는 저장소 밖, 경로는 `local.properties`)

- [ ] **Step 1:** `doc/시연-시나리오.md` — 장면 A·C·도감을 Pleos 모드와 Mock 모드 각각으로 재현하는 순서. Pleos 모드에서는 Virtual Console에서 **SoC와 Battery Energy를 함께 맞추는** 단계를 포함한다(스파이크 2-2). 각 단계에서 화면·음성으로 확인할 것을 적는다.
- [ ] **Step 2:** 시나리오대로 에뮬레이터에서 처음부터 끝까지 2회 실행. 막히는 곳이 있으면 해당 태스크로 돌아가 고친다.
- [ ] **Step 3:** README — 한 줄 정의, 핵심 장면, 사용 SDK와 권한, 빌드 방법, Mock 모드, 설계·조사 문서 링크.
- [ ] **Step 4:** Release APK 빌드 → `apk/VoltAndPay_VIA_release.apk`로 복사.
  Run: `./gradlew :core:test :app:testDebugUnitTest :app:assembleRelease`
  Expected: 테스트 전부 PASS, `BUILD SUCCESSFUL`. 새로 깐 에뮬레이터에 `adb install`해서 10초 안에 실행된다.
- [ ] **Step 5:** 시연 영상 녹화(장면 A → C → 도감), 접수양식 5번(개발 계획: 사용 SDK·API와 쓰는 곳 표)은 이 계획의 Task 8~11 인터페이스 표로 작성.
- [ ] **Step 6:** Commit — `docs: 시연 시나리오·README·release APK`

---

## 일정 (설계 문서 11절 기준, 1명 전업)

| 날짜 | 태스크 |
|---|---|
| 10/26~28 | Task 0 (스파이크 2차), 10/28 설명회 |
| 10/29~31 | Task 1~7 (core 전부) |
| 11/1~4 | Task 8~9 |
| 11/5~7 | Task 10~11 |
| 11/8~11 | Task 12 |
| 11/12~14 | Task 13 |
| 11/15 | 제출 |

2차(11/24~12/2) 범위(F4 공공 API, F6 절약액 정산, 비피크 충전, 주간 리포트, 차종 리그)는 1차 결과를 보고 별도 계획으로 쓴다.
