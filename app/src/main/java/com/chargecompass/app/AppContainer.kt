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
    val rewards = RewardCoordinator(vehicle, ledger, data, ::vehicleModel, voice, scope, { membership.owned.value }, summaries)
    private val chargingActive: StateFlow<Boolean> =
        rewards.activeSession.map { it != null }.stateIn(scope, SharingStarted.Eagerly, false)

    val toggleDrive = ToggleDriveStateProvider(chargingActive, scope)
    val drive: DriveStateProvider =
        if (mode == DataMode.MOCK) toggleDrive else GearDriveStateProvider(vehicle.parked, chargingActive, scope)

    private val driving: StateFlow<Boolean> =
        drive.state.map { it == DriveState.DRIVING }.stateIn(scope, SharingStarted.Eagerly, drive.state.value == DriveState.DRIVING)

    val reco = RecommendationCoordinator(vehicle, navi, voice, data, ::vehicleModel, { membership.owned.value }, scope, driving)
    private val announcer = EventAnnouncer(data.events, data.stations)

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
        fun savedMode(context: Context): DataMode =
            File(context.filesDir, "mode.txt").takeIf { it.exists() }?.readText()?.trim()
                ?.let { runCatching { DataMode.valueOf(it) }.getOrNull() } ?: DataMode.PLEOS

        fun saveMode(context: Context, mode: DataMode) = File(context.filesDir, "mode.txt").writeText(mode.name)
    }
}
