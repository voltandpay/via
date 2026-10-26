package com.chargecompass.app

import android.content.Context
import com.chargecompass.app.demo.DemoClock
import com.chargecompass.app.drivestate.DriveState
import com.chargecompass.app.drivestate.DriveStateProvider
import com.chargecompass.app.drivestate.GearDriveStateProvider
import com.chargecompass.app.drivestate.ToggleDriveStateProvider
import com.chargecompass.app.navi.MockNaviRepository
import com.chargecompass.app.navi.NaviRepository
import com.chargecompass.app.navi.PleosNaviRepository
import com.chargecompass.app.recommend.RecommendationCoordinator
import com.chargecompass.app.rewards.EventAnnouncer
import com.chargecompass.app.rewards.RewardCoordinator
import com.chargecompass.app.store.FileMembershipStore
import com.chargecompass.app.store.FileRewardLedger
import com.chargecompass.app.store.MembershipStore
import com.chargecompass.app.store.RewardLedger
import com.chargecompass.app.store.FileSummaryLog
import com.chargecompass.app.store.SummaryLog
import com.chargecompass.app.store.FileSeenStore
import com.chargecompass.app.store.SeenStore
import com.chargecompass.app.vehicle.MockVehicleRepository
import com.chargecompass.app.vehicle.PleosVehicleRepository
import com.chargecompass.app.vehicle.VehicleRepository
import com.chargecompass.app.voice.PleosVoiceGateway
import com.chargecompass.app.voice.VoiceGateway
import com.chargecompass.core.catalog.VehicleCatalog
import com.chargecompass.core.data.MockData
import com.chargecompass.core.data.MockDataLoader
import com.chargecompass.core.model.VehicleModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import com.chargecompass.app.recommend.RecoState
import com.chargecompass.app.voice.AnswerContext
import com.chargecompass.app.voice.BestStation
import com.chargecompass.app.voice.VoiceAssistant
import com.chargecompass.core.energy.ArrivalCalculator
import kotlin.math.roundToInt
import java.time.YearMonth
import com.chargecompass.app.insights.monthlyReport
import com.chargecompass.app.insights.reportDue
import com.chargecompass.app.insights.reportSpeech

enum class DataMode { PLEOS, MOCK }

/**
 * 앱 전체의 의존성 묶음. PLEOS는 실제 SDK, MOCK은 Virtual Console 없이 시연을 재생하는 모드다.
 * 음성은 두 모드 모두 Pleos TTS/STT를 쓴다(에뮬레이터에서 On-device로 동작).
 */
class AppContainer(context: Context, val mode: DataMode, scope: CoroutineScope) {
    private val appContext = context.applicationContext
    val data: MockData = MockDataLoader.load { path ->
        checkNotNull(javaClass.classLoader?.getResourceAsStream(path)) { "리소스 없음: $path" }.bufferedReader().use { it.readText() }
    }
    val catalog = VehicleCatalog(data.vehicles)
    val membership: MembershipStore = FileMembershipStore(File(context.filesDir, "profile.txt"))
    val ledger: RewardLedger = FileRewardLedger(File(context.filesDir, "ledger.txt"))

    val demoClock = DemoClock()
    /** 디버그: 분할 화면 세로 창(840dp) 배치를 미리 보기. */
    val narrowPreview = kotlinx.coroutines.flow.MutableStateFlow(false)
    /** 디버그: 도감 배지를 3D 메달 대신 평면 배지로 보기(비교용). */
    val flatBadges = kotlinx.coroutines.flow.MutableStateFlow(false)
    val mockVehicle = MockVehicleRepository(demoClock::now)
    val mockNavi = MockNaviRepository()
    val vehicle: VehicleRepository = if (mode == DataMode.MOCK) mockVehicle else PleosVehicleRepository(context, scope, demoClock::now)
    val navi: NaviRepository = if (mode == DataMode.MOCK) mockNavi else PleosNaviRepository(context)
    val voice: VoiceGateway = PleosVoiceGateway(context)

    fun vehicleModel(): VehicleModel? = membership.vehicleModelId.value?.let(catalog::byId)

    val summaries: SummaryLog = FileSummaryLog(File(context.filesDir, "summaries.txt"))
    val seen: SeenStore = FileSeenStore(File(context.filesDir, "seen.txt"))
    /** 주행 기록(평소 경로 학습). 내비에 넣었던 목적지가 있으면 함께 남긴다. */
    val tripLog: com.chargecompass.app.store.TripLog = com.chargecompass.app.store.FileTripLog(File(context.filesDir, "trips.txt"))
    private var lastRouteName: String? = null
    val rewards = RewardCoordinator(vehicle, ledger, data, ::vehicleModel, voice, scope, { membership.owned.value }, summaries)
    private val chargingActive: StateFlow<Boolean> =
        rewards.activeSession.map { it != null }.stateIn(scope, SharingStarted.Eagerly, false)

    val toggleDrive = ToggleDriveStateProvider(chargingActive, scope)
    val drive: DriveStateProvider =
        if (mode == DataMode.MOCK) toggleDrive else GearDriveStateProvider(vehicle.parked, chargingActive, scope)

    private val driving: StateFlow<Boolean> =
        drive.state.map { it == DriveState.DRIVING }.stateIn(scope, SharingStarted.Eagerly, drive.state.value == DriveState.DRIVING)

    /** 충전기 실시간 상태(본선: 공공 API, 1차: 시연 패널). */
    val statusBoard = com.chargecompass.app.navi.StationStatusBoard()
    val reco = RecommendationCoordinator(vehicle, navi, voice, data, ::vehicleModel, { membership.owned.value }, scope, driving, statusBoard)
    private val announcer = EventAnnouncer(data.events, data.stations)
    private val chargeStopAnnouncer = com.chargecompass.app.rewards.ChargeStopAnnouncer()

    /**
     * 지금 충전을 언제 끝내도 되는지(목적지 이름과 함께). 충전소가 지금 경로 위에 있어야 남은 거리를 안다.
     * 경로 거리는 출발 때 값이라, 남은 거리 = 경로 거리 - 이 충전소까지의 거리.
     */
    fun chargeStopAdvice(session: com.chargecompass.core.session.ChargeSession?, energy: com.chargecompass.core.energy.EnergyState):
        Pair<String, com.chargecompass.core.charge.ChargeStopAdvice>? {
        val route = navi.route.value ?: return null
        val car = vehicleModel() ?: return null
        val station = data.stationsFor(route.destinationName).firstOrNull { it.id == session?.stationId } ?: return null
        val tariff = data.tariffs[station.operatorId] ?: return null
        val price = com.chargecompass.core.pricing.StationRanker.effectivePrice(tariff, membership.owned.value).wonPerKwh
        val advice = com.chargecompass.core.charge.ChargeStopAdvisor.advise(
            energy, car, route.remainingKm - station.alongRouteKm, station.maxKw, price, demoClock.now().month,
        ) ?: return null
        return route.destinationName to advice
    }

    init {
        // 추천에 나온 충전소는 도감에 "발견"으로 남긴다
        scope.launch { reco.state.collect { s -> com.chargecompass.app.recommend.shownStationIds(s)?.let { seen.add(it) } } }
        vehicle.start()
        navi.start()
        voice.start()
        // 출발(정차 → 주행)할 때 오늘의 이벤트 충전소를 하루 한 번 음성으로 알린다
        // 달이 바뀐 뒤 처음 정차했을 때 지난달 절약 리포트를 한 번 말한다(주행 중에는 말하지 않는다)
        scope.launch {
            driving.collect { now -> if (!now) announceMonthlyReport() }
        }
        // 충전 중 목적지까지 충분히 찼으면 "지금 끝내도 돼요"를 충전 1회에 한 번 말한다
        scope.launch {
            // 운전석이 비었으면(차 밖에서 기다리는 중) 말하지 않고, 돌아와 앉으면 그때 말한다
            kotlinx.coroutines.flow.combine(rewards.activeSession, vehicle.energy, vehicle.driverSeated) { s, e, seated -> Triple(s, e, seated) }.collect { (s, e, seated) ->
                val session = s ?: return@collect
                if (seated == false) return@collect
                val (destination, advice) = chargeStopAdvice(session, e) ?: return@collect
                chargeStopAnnouncer.onUpdate(session, e.socPct, destination, advice)?.let { voice.speak(it) }
            }
        }
        // 주행 기록: 끝난 주행을 출발·도착 위치, 차가 잰 거리, 그때 내비 목적지와 함께 남긴다(차 안에만)
        scope.launch { navi.route.collect { r -> if (r != null) lastRouteName = r.destinationName } }
        scope.launch {
            vehicle.tripEnded.collect { t ->
                val from = t.start
                val to = t.end
                if (from != null && to != null) {
                    tripLog.append(listOf(com.chargecompass.core.usual.TripRecord(from.first, from.second, to.first, to.second, t.distanceKm, t.startedAt ?: t.at, lastRouteName)))
                }
                lastRouteName = navi.route.value?.destinationName
            }
        }
        // 내비 없이 출발하면 평소 이 시간 경로로 배터리가 모자라는지 한 번 알린다
        scope.launch {
            var wasDriving = driving.value
            var check: kotlinx.coroutines.Job? = null
            driving.collect { now ->
                if (now && !wasDriving) check = scope.launch { checkUsualRoute() }
                if (!now) check?.cancel()
                wasDriving = now
            }
        }
        scope.launch {
            var wasDriving = driving.value
            driving.collect { now ->
                if (now && !wasDriving) announcer.onDepart(demoClock.now())?.let { voice.speak(it) }
                wasDriving = now
            }
        }
        // 차종을 아직 고르지 않았으면 VIN으로 먼저 찾아본다(에뮬레이터 VIN은 "invalid"라 온보딩에서 고른다)
        scope.launch {
            if (membership.vehicleModelId.value == null) catalog.byVin(vehicle.vin())?.let { membership.setVehicle(it.id) }
        }
    }

    /** 말하기 버튼: 가장 싼 충전소·이번 달 아낀 돈·도착 배터리 등을 음성으로 묻고 답한다. */
    val assistant = VoiceAssistant(voice, ::answerContext, offerStation = {
        when (val s = reco.state.value) {
            is RecoState.AwaitingTouch -> s.choice.station.id
            is RecoState.Suggested -> s.top.first().station.id
            else -> null
        }
    }, addStation = reco::confirmByTouch)
    private var assistantJob: kotlinx.coroutines.Job? = null

    fun askAssistant(scope: kotlinx.coroutines.CoroutineScope) {
        if (assistantJob?.isActive == true) return   // 이미 듣는 중
        assistantJob = scope.launch { assistant.handle() }
    }

    private fun answerContext(): AnswerContext {
        val now = demoClock.now()
        val route = navi.route.value
        val car = vehicleModel()
        val energy = vehicle.energy.value
        val (arrival, best) = when (val s = reco.state.value) {
            is RecoState.Suggested -> s.arrivalSoc to s.top.first()
            is RecoState.AwaitingTouch -> s.arrivalSoc to s.choice
            is RecoState.Enough -> s.arrivalSoc to null
            else -> (route?.let { r -> car?.let { ArrivalCalculator.socAfter(energy, it, r.remainingKm) } }) to null
        }
        val eff = energy.efficiencyKmPerKwh?.takeIf { it > 0 } ?: car?.baseEfficiencyKmPerKwh
        return AnswerContext(
            destination = route?.destinationName,
            arrivalSoc = arrival?.roundToInt()?.coerceAtLeast(0),
            best = best?.let { BestStation(it.station.name, it.station.alongRouteKm.roundToInt(), it.price.wonPerKwh, it.savingWon) },
            monthSavingWon = summaries.summaries.value.filter { YearMonth.from(it.endedAt) == YearMonth.from(now) }.sumOf { it.savingWon },
            balance = ledger.balance(now),
            rangeKm = if (car != null && eff != null) energy.socPct?.let { (it / 100.0 * car.batteryKwh * eff).toInt() } else null,
            socPct = energy.socPct,
        )
    }


    /** 출발 위치·시각은 출발할 때 잡고, 내비에 목적지를 넣을 시간을 준 뒤에도 경로가 없을 때만 말한다. */
    private suspend fun checkUsualRoute() {
        val (lat, lng) = vehicle.currentLocation() ?: return
        val departedAt = demoClock.now()
        kotlinx.coroutines.delay(USUAL_ROUTE_WAIT_MS)
        if (navi.route.value != null) return
        val car = vehicleModel() ?: return
        val usual = com.chargecompass.core.usual.UsualRoutes.predict(tripLog.trips.value, lat, lng, departedAt) ?: return
        com.chargecompass.app.usual.UsualRouteAdvisor.speech(usual, vehicle.energy.value, car)?.let { voice.speak(it) }
    }

    private suspend fun announceMonthlyReport() {
        // init 블록의 수집이 아래쪽 프로퍼티 초기화보다 먼저 돌 수 있어, 파일은 맨 위의 appContext로 여기서 만든다
        val reportMark = File(appContext.filesDir, "report.txt")
        val now = demoClock.now()
        val last = reportMark.takeIf { it.exists() }?.readText()?.trim()?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
        val month = reportDue(now, last) { m -> monthlyReport(m, summaries.summaries.value, ledger.entries.value) != null } ?: return
        reportMark.writeText(month.toString())
        monthlyReport(month, summaries.summaries.value, ledger.entries.value)?.let { voice.speak(reportSpeech(it)) }
    }

    fun stop() {
        vehicle.stop()
        navi.stop()
        voice.stop()
    }

    companion object {
        /** 출발 직후 내비에 목적지를 넣을 시간. */
        const val USUAL_ROUTE_WAIT_MS = 10_000L

        fun savedMode(context: Context): DataMode =
            File(context.filesDir, "mode.txt").takeIf { it.exists() }?.readText()?.trim()
                ?.let { runCatching { DataMode.valueOf(it) }.getOrNull() } ?: DataMode.PLEOS

        fun saveMode(context: Context, mode: DataMode) = File(context.filesDir, "mode.txt").writeText(mode.name)
    }
}
