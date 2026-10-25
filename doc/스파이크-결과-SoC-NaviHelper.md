# 스파이크 결과 — Pleos 에뮬레이터 SoC 조회 · NaviHelper 경로 연동

> 작성일: 2026-10-25
> 목적: A안(EV 충전 비용 최적화 + 결제)의 기술 리스크 확인. "Pleos Connect 에뮬레이터에서 앱이 뜨고, 차량 배터리 잔량(SoC)을 읽고, 내비 경로 데이터를 받을 수 있는가?"
> 코드: `spike/soc-spike` (버리는 코드, 커밋하지 않음)

---

## 1. 결론

**A안의 핵심 기술 경로는 모두 에뮬레이터에서 실제로 동작한다.** 바이브코딩으로 구현을 진행해도 된다.

| 확인 항목 | 결과 | 비고 |
|---|---|---|
| Pleos 전용 에뮬레이터 부팅 | ✅ | Android 14 Automotive, Pleos 차량·내비·위치 서비스 기본 탑재 |
| SoC 지원 여부 확인 (capability) | ✅ | `CapabilityConfig(accessType=READ)` — 읽기 전용 |
| SoC 1회 조회 (get) | ✅ | `SoC 조회 = 100` |
| SoC 실시간 구독 (register) | ✅ | Virtual Console에서 100→15% 변경 시 즉시 `SoC 변경 알림 = 15` |
| NaviHelper 서비스 연결 | ✅ | 에뮬레이터 안에서 실제 내비 엔진 동작 (현재 위치: 판교 부근) |
| 경로 상태 조회 | ✅ | `RouteStateInfo` 수신 (내비 모드, 경로 옵션, 교통 정보 등) |
| 경로 요청 (서울역) | ✅ | 거리 23km · 소요 58분 · **통행료 1,000원** · 도착 시 배터리값 수신 |

스파이크 전에 남겨뒀던 최대 리스크("NaviHelper 경로·충전 데이터가 에뮬레이터에서 나오는가")는 **해소**됐다.

### 실행 화면

- SoC 15% 실시간 반영: [`spike/soc-spike/spike-result-soc15.png`](../spike/soc-spike/spike-result-soc15.png)
- 서울역 경로 요청 결과: [`spike/soc-spike/spike-result-route.png`](../spike/soc-spike/spike-result-route.png)

### 실제 로그 (발췌)

```
initialize() 반환
NaviHelper 연결됨
경로 요청: 서울역
capability: CapabilityConfig(accessType=READ)
SoC 조회 = 15
SoC 변경 알림 = 15
경로 시작 = RouteStartInfo(duration=3480, distance=23000, tollFare=1000, name=서울역, remainDestinationChargingValue=77397)
경로 상태 = RouteStateInfo(termsAgreement=true, naviMode=ROUTE_GUIDING, isRouting=false, routeOption=RECOMMENDED,
           remainDestinationChargingInt=77397, remainWaypointChargingInt=[0, 0], trafficStatus=...)
```

---

## 2. 본 개발 전에 알아야 할 점 (중요도 순)

### ⚠️ 2-1. "도착 시 배터리값"은 그대로 믿으면 안 된다
- `remainDestinationChargingValue = 77397`은 Virtual Console의 Battery Energy(77.4kWh)에서 거의 줄지 않은 값이다.
- SoC를 15%로 내려도 이 값에는 반영되지 않았다. → **SoC가 아니라 Battery Energy 기준으로 계산되는 것으로 보인다.**
- 단위는 Wh로 추정되지만 **확인하지 못했다.** 에뮬레이터에서는 23km 주행에도 소모량이 거의 계산되지 않는다.
- **대응**: 앱에서 `전비 × 남은 거리`로 도착 SoC를 직접 계산하고, 이 값은 참고용으로만 쓴다. (EvBattery 도메인에 전비 API 4종이 있다)

### ⚠️ 2-2. Virtual Console의 SoC와 Battery Energy는 따로 움직인다
- 두 슬라이더가 연동되지 않는다.
- **시연 시** "배터리 15%" 장면을 보여주려면 SoC와 Battery Energy를 **함께** 맞춰야 앞뒤가 맞는다.

### ⚠️ 2-3. 공식 API 문서와 실제 SDK가 다른 부분이 있다
- API Reference 페이지에 함수 시그니처가 표시되지 않는다(빈 페이지).
- 가이드 문서 예시와 실제가 다른 사례:
  - Capability 콜백: 문서 예시는 `(config, areaMap)` 2개 인자 → **실제는 `CapabilityConfig` 1개**
  - `getRouteStateInfo()`: 값을 반환하지 않음 → **결과는 리스너 `onRouteStateInfo`로만 전달**
- **대응**: 아래 3절의 "컴파일러로 시그니처 확인" 방식을 본 개발에서도 쓴다. AI가 SDK를 잘못 짚어도 컴파일러가 정답을 알려준다.

### 2-4. 화면 레이아웃
- Pleos Connect 화면은 2560×1440이며, **좌측 약 1/3은 시스템 패널**(차량 상태·미디어)이 차지한다.
- 앱 UI는 **오른쪽 영역 기준**으로 설계해야 한다.

### 2-5. 권한
- 매니페스트 선언만으로 동작했다. 실행 중 승인 창은 뜨지 않는다.
- 실제 Pleos App Market 배포 시에는 사전 협의·승인이 필요하다 (공모전 이후 단계).

---

## 3. 확인된 API 시그니처

공식 문서에 표시되지 않아 **컴파일러 오류 메시지로 확인**한 값들이다. (일부러 틀린 인자를 넣어 기대 타입을 확인하는 방식 — SDK 디컴파일이 아니므로 라이선스 3.4조 위반 아님)

### Vehicle SDK 2.3.2

```kotlin
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener

val vehicle = Vehicle(context)
vehicle.initialize()                       // 동기 반환
val ev = vehicle.getEvBattery()

// 지원 여부 — onComplete 인자는 1개
ev.checkEvBatteryStateOfChargeCapabilities(
    onComplete = { config: CapabilityConfig -> },
    onFailed = { e: Exception -> },
)

// 1회 조회
ev.getEvBatteryStateOfCharge(
    onComplete = { soc: Int? -> },
    onFailed = { e: Exception -> },
)

// 구독
val listener = object : EvBatteryStateOfChargeListener {
    override fun onEvBatteryStateOfChargeUpdated(value: Int?) {}
    override fun onFailed(e: Exception) {}
}
ev.registerEvBatteryStateOfCharge(listener)
ev.unregisterEvBatteryStateOfCharge(listener)

vehicle.release()
```

- 권한: `pleos.car.permission.CAR_ENERGY`
- 콜백은 바인더 스레드에서 온다 → UI 갱신은 `runOnUiThread` 필요

### NaviHelper SDK 3.0.5

```kotlin
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.constants.NaviErrorCode   // ← 별도 모듈(navi-data)에 있음

val navi = NaviHelper(applicationContext)
navi.initialize(
    onConnected = { /* 여기서 리스너 등록·API 호출 */ },
    onFailed = { t: Throwable -> },
)

// 리스너 — 모든 메서드에 기본 구현이 있어 필요한 것만 override
val naviListener = object : NaviHelperEventListener {
    override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {}
    override fun onRouteStarted(routeStartInfo: RouteStartInfo) {}
    override fun onError(errorCode: NaviErrorCode) {}   // Exception 아님
}
navi.addListener(naviListener)

navi.getRouteStateInfo()   // 반환값 없음 → onRouteStateInfo로 수신

// 경로 요청 — poiId, address, poiSubId 필수
navi.requestRoute(
    RouteInfo(
        latitude = 37.5547, longitude = 126.9707,
        poiName = "서울역", poiId = "", poiSubId = "",
        address = "서울특별시 용산구 한강대로 405",
    )
)

navi.removeListener(naviListener)
navi.release()
```

- 권한: `pleos.car.permission.NAVI_ROUTE` (조회), `pleos.car.permission.NAVI_ROUTE_SEARCH` (경로 요청·경유지)

### 수신된 데이터 구조

| 타입 | 필드 |
|---|---|
| `RouteStartInfo` | `duration`(초), `distance`(m), `tollFare`(원), `name`, `remainDestinationChargingValue` |
| `RouteStateInfo` | `termsAgreement`, `naviMode`(SAFE_GUIDING / ROUTE_GUIDING), `isRouting`, `routeOption`, `remainDestinationChargingInt`, `remainWaypointChargingInt`(배열), `trafficStatus` |

---

## 4. 환경 구성 기록

### 개발 머신
- macOS, Apple Silicon(arm64), RAM 36GB
- Android Studio 2026.1, JBR 21 (빌드용 JAVA_HOME)
- Android SDK: platform 36, emulator 37.1

### 빌드 설정 (추가 다운로드 없이 기존 캐시 활용)
| 항목 | 버전 |
|---|---|
| Gradle | 8.14.3 |
| Android Gradle Plugin | 8.13.0 |
| Kotlin | 2.2.20 |
| compileSdk / targetSdk / minSdk | 36 / 34 / 28 |
| Vehicle SDK | 2.3.2 |
| NaviHelper SDK | 3.0.5 (navi-data 3.0.4 포함) |

- Maven 저장소: `https://nexus-playground.pleos.ai/repository/maven-releases/` (인증 없이 접근 가능)
- `settings.gradle.kts`의 `dependencyResolutionManagement.repositories`에 등록

### 에뮬레이터 이미지 설치 (CLI)
1. 매니페스트: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
2. 이미지: `crp_car_arm64_emulator.zip` (4.67GB, SHA-1 `24d28eb2f18eba5e87c861a7eb2bdccebb049419` 일치 확인)
3. 설치 위치: `~/Library/Android/sdk/system-images/android-34/pleos_car_emulator/arm64-v8a/` (압축 해제 후 19GB)
4. 42dot SDK 라이선스 동의 후 진행 (사용자 동의: 2026-10-25)

### AVD
- 이름: `Pleos_Connect_v2.1`
- 2560×1440, 160dpi, 가로, RAM 4GB, Cold boot
- 실행: `emulator -avd Pleos_Connect_v2.1 -no-snapshot -no-boot-anim`

### Virtual Console
```bash
adb forward tcp:8088 tcp:8088
```
→ 브라우저에서 `http://127.0.0.1:8088/` → Emulation Controls → **Energy**
- 조작 가능: Remaining Range, Battery SOC, Battery Energy, Battery Capacity, Charge Current Limit, Charge Percent Limit, Charge Switch, Charge Port

### 매니페스트 필수 항목
```xml
<uses-feature android:name="android.hardware.type.automotive" android:required="true" />
<uses-permission android:name="pleos.car.permission.CAR_ENERGY" />
<uses-permission android:name="pleos.car.permission.NAVI_ROUTE" />
<uses-permission android:name="pleos.car.permission.NAVI_ROUTE_SEARCH" />
<!-- 런처 액티비티 안 -->
<meta-data android:name="distractionOptimized" android:value="true" />
```

---

## 5. 바이브코딩 운영 팁 (이번 스파이크에서 얻은 것)

1. **공식 Sample App/가이드를 컨텍스트로 넣어도 AI는 SDK 시그니처를 틀린다.** 문서 예시부터 실제와 다르다.
2. **컴파일러를 정답지로 쓴다.**
   - 인자 타입을 모르면 → `Unit` 같은 틀린 값을 넣어 `expected 'XxxType'` 메시지 확인
   - 인터페이스 메서드를 모르면 → 빈 `object : XxxListener {}`로 구현해 `does not implement abstract members` 목록 확인
   - 생성자 필수값을 모르면 → 일부만 넣어 `No value passed for parameter` 확인
   - 오버라이드 시그니처가 틀리면 → `Potential signatures for overriding` 확인
3. 클래스가 어느 패키지에 있는지 모를 때는 AAR의 **파일 목록**(`unzip -l`)만 본다. 클래스 내부를 여는 디컴파일은 라이선스 금지.
4. 차량 데이터 접근은 `VehicleRepository` 같은 인터페이스 한 겹 뒤로 숨기고, **실제 SDK 구현 / Mock 구현** 두 개를 둔다. 비즈니스 로직(요금 비교, 최적 충전지점)은 순수 Kotlin으로 유지.

---

## 6. 남은 확인 사항 (본 개발 초반에)

- [ ] `remainDestinationChargingValue` 단위·계산 방식 (Wh? 경로 소모 반영 조건?)
- [ ] 전비 API 4종(순간/현재/누적/충전 이후) 값이 에뮬레이터에서 어떻게 나오는지
- [ ] 경유지 추가(`addWaypoint`, `requestRouteWithWaypoints`)로 충전소를 경로에 넣을 수 있는지
- [ ] 주변 충전소·충전사업자 정보(`getChargerOperatorInfo`, `nearby-charging-stations`)가 에뮬레이터에서 나오는지
- [ ] 주행 시뮬레이션(Virtual Console Motion: 기어 D + 가속) 중 데이터 변화
- [ ] TextToSpeech SDK 음성 안내 동작
- [ ] **충전 요금 데이터 출처 확보** — SDK에 없음 (환경부 충전소 공공데이터, 사업자별 요금표)

---

## 7. 정리할 것

- 에뮬레이터는 백그라운드에서 실행 중 (종료: `adb emu kill`)
- 다운로드한 zip(4.67GB)이 임시 폴더에 남아 있음 — SDK 설치가 끝났으므로 삭제 가능
- `spike/soc-spike`는 버리는 코드. 본 개발은 새 프로젝트로 시작하되, 3절의 시그니처와 4절의 설정을 재사용

---

## 4. 2차 스파이크 (2026-10-25) — 경유지 · 주행 상태 · 전비 · 충전 · VIN · 음성

> 코드: `spike/soc-spike/app/src/main/java/com/spike/soc/Spike2Activity.kt` (버리는 코드)
> Virtual Console은 HTTP로도 조작된다: `POST http://127.0.0.1:8088/vehicle/gear?gear=Drive`, `/vehicle/ev-battery?field=charge_switch&value=true`, `field=charge_port_open`, `field=soc&value=15`, `/vehicle/odometer?traveled_distance_km=123` (목록: `GET /vehicle/catalog`). 시연 스크립트 자동화에 쓸 수 있다.

### 4-1. 결과 요약

| # | 확인 항목 | 결과 | 1차 MVP에서 쓰는 방법 |
|---|---|---|---|
| 1 | 경유지 추가 `addWaypoint` | ✅ (조건부) | 경로 안내 중일 때 동작. 내비 화면에 "양재 충전소 18분·11km" 표시, `RouteStateInfo.remainWaypointChargingInt=[76001, 0]` 수신. **대안(목적지 교체)은 필요 없음.** 단, 내비 앱이 준비되기 전 요청은 내비 쪽에서 `navi not ready`로 버려지고 앱에는 오류 콜백도 오지 않는다 → 요청 후 `onWaypointChanged`/`onWayPointInfo`로 결과를 확인하고, 몇 초 안에 안 오면 실패로 처리 |
| 2 | 주행/정차 판별 | ✅ | **Vehicle SDK `Gear` 도메인**으로 된다 (설계 당시 "기어·속도 API 없음"은 틀렸음 — 문서에 없을 뿐 SDK에 있음). `registerGearSelection` → `GEAR_PARK`/`GEAR_DRIVE` 즉시 수신. AAOS UX restrictions는 확인하지 않음. **대안(토글)은 Mock 모드에서만** |
| 3a | 전비 API 4종 (현재·누적·순간·충전 후) | ❌ 에뮬레이터 미지원 | `ERROR_NOT_SUPPORTED_PROPERTY`. 실차에서는 될 수 있으니 Pleos 구현은 먼저 시도하고, 실패하면 **배터리 에너지(Wh) 변화 ÷ 주행거리 변화**로 직접 계산, 그것도 없으면 차종 기본 전비 |
| 3b | 트립 거리·시간 | ❌ 미지원 | 총 주행거리(`getCurrentDistanceDriven`/`registerDistanceDriven`)는 동작 → 트립 = D 단 진입부터 P 단 복귀까지의 총 주행거리 차이 |
| 3c | 배터리 에너지 | ✅ | `getEvBatteryLevel`/`registerEvBatteryLevel` → `77400.0` (Wh). 에뮬레이터에서는 주행해도 줄지 않음 |
| 3d | 충전 상태 | ✅ | `getEvChargeSwitchState` → true/false (**구독 API 없음, 주기 조회 필요**). 충전구 열림은 `registerEvChargePortOpen`으로 구독 가능. 충전 중 = 충전 스위치 true |
| 4 | VIN | ⚠️ | `getVINInfo` → `"invalid"` (따옴표 포함 문자열). 에뮬레이터에선 차종 자동 판별 불가 → **온보딩에서 차종 선택**이 기본 흐름 |
| 5a | TTS | ✅ | 앱 등록 없이 On-device로 한국어 재생 (`speaker=seoyoon`). 서버 모드는 "Client credentials missing" 후 자동으로 On-device 사용 |
| 5b | STT | ✅ (`Mode.ON_DEVICE`) | 기본(HYBRID)은 서버 자격 증명이 없어 실패. `SpeechToText(context, Mode.ON_DEVICE)`로 "응, 추가해줘" 음성 파일을 넣으니 최종 결과 `"응 추가해줘"` 인식. 마이크 입력은 `request()`로 시작(호스트 마이크 연결 필요) |

→ **설계 8-1절의 대안 3가지는 모두 쓰지 않아도 된다.** 경유지 추가·음성 응답·기어 기반 주행 판별이 실제로 동작한다.

### 4-2. 컴파일러로 확인한 시그니처 (2차)

```kotlin
// Gear (문서 없음, SDK에 있음) — 권한: 확인 필요(CAR_DRIVE 선언 상태에서 동작)
vehicle.getGear().registerGearSelection(object : GearSelectionListener {
    override fun onSelected(gear: VehicleGear?) {}      // ai.pleos.playground.vehicle.constant.control.VehicleGear: GEAR_PARK, GEAR_DRIVE, ...
    override fun onFailed(e: Exception) {}
})
// (get 함수는 없음 — 구독 즉시 현재 값이 한 번 온다)

// EvBattery
ev.getEvBatteryLevel(onComplete = { wh: Float? -> }, onFailed = { })
ev.registerEvBatteryLevel(object : EvBatteryLevelListener {
    override fun onEvBatteryLevelUpdated(level: Float?) {}
    override fun onFailed(e: Exception) {}
})
ev.getEvChargeSwitchState(onComplete = { on: Boolean? -> }, onFailed = { })
ev.registerEvChargePortOpen(object : EvChargePortOpenListener {
    override fun onEvChargePortOpenUpdated(isOpened: Boolean?) {}
    override fun onFailed(e: Exception) {}
})
ev.getCurrentEvEfficiency(...) / registerCurrentEvEfficiency(EvBatteryEfficiencyListener { onEvBatteryEfficiencyUpdated(efficiency: Float?) })

// Odometer
odo.getCurrentDistanceDriven(onComplete = { km: Float? -> }, onFailed = { })
odo.registerDistanceDriven(object : DistanceDrivenListener {
    override fun onDistanceDrivenUpdated(value: Float?) {}
    override fun onFailed(e: Exception) {}
})

// CarInfo
vehicle.getCarInfo().getVINInfo(onComplete = { vin: String? -> }, onFailed = { })

// NaviHelper 경유지
navi.addWaypoint(RequestWaypointInfo(latitude, longitude, poiName, waypointIndex = WaypointIndex.FIRST, poiId = "0", address, poiSubId = "0"))
navi.getWaypointInfo()                  // → onWayPointInfo(List<WaypointInfo>)
navi.removeWaypoint(WaypointIndex.FIRST)
// 이벤트: onWaypointChanged(WaypointChangedInfo(index, cmdType=ADD, routeStartInfo)), onRoutingCompleted(), onDestinationChanged()

// TTS / STT
val tts = TextToSpeech(applicationContext); tts.initialize(); tts.speak("...")       // 권한 pleos.car.permission.TTS_SERVICE
val stt = SpeechToText(applicationContext, Mode.ON_DEVICE)                           // 권한 STT_SERVICE + RECORD_AUDIO
stt.initialize(); stt.addListener(resultListener); stt.setLocale(Locale.KOREA); stt.request()
// ResultListener.onUpdated(stt: String, completed: Boolean), onEndedRecognition(), onError(ErrorType)
```

### 4-3. 덤으로 알게 된 것
- 내비의 `DrivingInfo` 로그에 `isChargingStation`, `maxChargingPower`, `targetSoc`, `chargerCount` 필드가 보인다. 내비가 충전소 정보를 일부 다루는 것으로 보이지만 앱 SDK로 받는 방법은 확인하지 않았다(2차 과제).
- 통행료는 같은 경로라도 700원/1,000원으로 요청마다 달랐다(시간대 요금으로 추정).
