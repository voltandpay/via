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

enum class DataMode { PLEOS, MOCK }

/**
 * 앱 전체의 의존성 묶음. PLEOS는 실제 SDK, MOCK은 Virtual Console 없이 시연을 재생하는 모드다.
 * 음성은 두 모드 모두 Pleos TTS/STT를 쓴다(에뮬레이터에서 On-device로 동작).
 */
class AppContainer(context: Context, val mode: DataMode, scope: CoroutineScope) {
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
        vehicle.start()
        navi.start()
        voice.start()
        // 출발(정차 → 주행)할 때 오늘의 이벤트 충전소를 하루 한 번 음성으로 알린다
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
