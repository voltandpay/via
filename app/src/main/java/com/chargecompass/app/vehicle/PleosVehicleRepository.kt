package com.chargecompass.app.vehicle

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.EvBatteryEfficiencyListener
import ai.pleos.playground.vehicle.listener.EvBatteryLevelListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.content.Context
import android.util.Log
import com.chargecompass.core.energy.EnergyState
import com.chargecompass.core.session.ChargingSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime
import kotlin.coroutines.resume

/**
 * Pleos Vehicle SDK 구현. 시그니처는 스파이크에서 컴파일러로 확인한 것이다(문서 4-2절).
 * 에뮬레이터에서 안 되는 것: 전비 API 4종, 트립 API → 트립은 총 주행거리 차이, 전비는 배터리 에너지 차이로 구한다.
 * 충전 스위치는 구독 API가 없어 [CHARGE_POLL_MS]마다 조회한다.
 * SDK 콜백은 바인더 스레드에서 오므로 StateFlow에만 쓰고 UI는 Flow를 수집한다.
 */
class PleosVehicleRepository(
    context: Context,
    private val scope: CoroutineScope,
    private val clock: () -> LocalDateTime = LocalDateTime::now,
) : VehicleRepository {
    private val vehicle = Vehicle(context)
    private val location = FusedLocation(context)

    private val _energy = MutableStateFlow(EnergyState(null, null))
    private val _parked = MutableStateFlow<Boolean?>(null)
    private val _charging = MutableSharedFlow<ChargingSample>(extraBufferCapacity = 16)
    private val _trips = MutableSharedFlow<TripSummary>(extraBufferCapacity = 16)
    override val energy: StateFlow<EnergyState> = _energy
    override val parked: StateFlow<Boolean?> = _parked
    override val charging: SharedFlow<ChargingSample> = _charging
    override val tripEnded: SharedFlow<TripSummary> = _trips

    @Volatile private var odometerKm: Float? = null
    @Volatile private var energyWh: Float? = null
    @Volatile private var lastLocation: Pair<Double, Double>? = null
    private var tripStart: Pair<Float, Float?>? = null   // (주행거리 km, 배터리 Wh)
    private var pollJob: Job? = null

    private val socListener = object : EvBatteryStateOfChargeListener {
        override fun onEvBatteryStateOfChargeUpdated(value: Int?) { _energy.update { it.copy(socPct = value) } }
        override fun onFailed(e: Exception) = warn("SoC", e)
    }
    private val efficiencyListener = object : EvBatteryEfficiencyListener {
        override fun onEvBatteryEfficiencyUpdated(efficiency: Float?) {
            _energy.update { it.copy(efficiencyKmPerKwh = efficiency?.toDouble()?.takeIf { e -> e > 0 }) }
        }
        override fun onFailed(e: Exception) = warn("전비(미지원이면 기본 전비 사용)", e)
    }
    private val levelListener = object : EvBatteryLevelListener {
        override fun onEvBatteryLevelUpdated(level: Float?) { energyWh = level }
        override fun onFailed(e: Exception) = warn("배터리 에너지", e)
    }
    private val odometerListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(value: Float?) { odometerKm = value }
        override fun onFailed(e: Exception) = warn("주행거리", e)
    }
    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = onGear(gear)
        override fun onFailed(e: Exception) = warn("기어", e)
    }

    override fun start() {
        vehicle.initialize()
        val ev = vehicle.getEvBattery()
        ev.registerEvBatteryStateOfCharge(socListener)
        ev.registerCurrentEvEfficiency(efficiencyListener)
        ev.registerEvBatteryLevel(levelListener)
        vehicle.getOdometer().registerDistanceDriven(odometerListener)
        vehicle.getGear().registerGearSelection(gearListener)
        startLocation()
        pollJob = scope.launch {
            var failures = 0
            while (isActive) {
                val read = chargeSwitch()
                failures = if (read == null) failures + 1 else 0
                // 충전 상태를 1분 넘게 못 읽으면 충전이 끝난 것으로 본다(충전 화면에 갇히지 않게)
                val on = read ?: if (failures * CHARGE_POLL_MS > 60_000L) false else null
                if (on != null) {
                    val loc = lastLocation
                    _charging.emit(ChargingSample(clock(), on, _energy.value.socPct, loc?.first, loc?.second))
                }
                delay(CHARGE_POLL_MS)
            }
        }
    }

    override fun stop() {
        pollJob?.cancel()
        val ev = vehicle.getEvBattery()
        ev.unregisterEvBatteryStateOfCharge(socListener)
        ev.unregisterCurrentEvEfficiency(efficiencyListener)
        ev.unregisterEvBatteryLevel(levelListener)
        vehicle.getOdometer().unregisterDistanceDriven(odometerListener)
        vehicle.getGear().unregisterGearSelection(gearListener)
        stopLocation()
        vehicle.release()
    }

    override suspend fun vin(): String? = withTimeoutOrNull(SDK_CALL_TIMEOUT_MS) {
        suspendCancellableCoroutine { cont ->
            vehicle.getCarInfo().getVINInfo(
                onComplete = { cont.resumeOnce(it?.trim('"')?.takeIf { v -> v.isNotBlank() && v != "invalid" }) },
                onFailed = { cont.resumeOnce(null) },
            )
        }
    }

    /** SDK가 콜백을 안 부르면 폴링이 멈추지 않게 시간 제한을 두고, 두 번 부르면 무시한다. */
    private suspend fun chargeSwitch(): Boolean? = withTimeoutOrNull(SDK_CALL_TIMEOUT_MS) {
        suspendCancellableCoroutine { cont ->
            vehicle.getEvBattery().getEvChargeSwitchState(
                onComplete = { cont.resumeOnce(it) },
                onFailed = { cont.resumeOnce(null) },
            )
        }
    }

    private fun <T> kotlinx.coroutines.CancellableContinuation<T>.resumeOnce(value: T) {
        if (isActive) resume(value)
    }

    private fun onGear(gear: VehicleGear?) {
        val parkedNow = gear?.let { it == VehicleGear.GEAR_PARK }
        val wasParked = _parked.value
        _parked.value = parkedNow
        when {
            parkedNow == false && wasParked != false -> tripStart = odometerKm?.let { it to energyWh }
            parkedNow == true && wasParked == false -> endTrip()
        }
    }

    private fun endTrip() {
        val (startKm, startWh) = tripStart ?: return
        tripStart = null
        val km = (odometerKm ?: return) - startKm
        if (km <= 0f) return
        val endWh = energyWh   // 리스너가 다른 스레드에서 바꿀 수 있어 한 번만 읽는다
        val usedKwh = if (startWh != null && endWh != null) (startWh - endWh) / 1000f else null
        val efficiency = _energy.value.efficiencyKmPerKwh
            ?: usedKwh?.takeIf { it > 0f }?.let { (km / it).toDouble() }
        _trips.tryEmit(TripSummary(km.toDouble(), efficiency, clock()))
    }

    /** 첫 실행에서 위치 권한을 앱 시작 뒤에 허용한 경우 다시 붙는다. */
    fun retryLocation() {
        stopLocation()
        startLocation()
    }

    /** 위치를 못 쓰면 충전소 매칭만 안 될 뿐 나머지는 동작해야 한다. */
    private fun startLocation() {
        try {
            location.initialize()
            location.registerFusedLocationCallback(locationListener)
        } catch (e: Exception) {
            warn("위치 시작", e)
        }
    }

    private fun stopLocation() {
        runCatching {
            location.unregisterFusedLocationCallback(locationListener)
            location.release()
        }
    }

    private val locationListener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            if (location != null) lastLocation = location.latitude to location.longitude
        }
        override fun onError(exception: Exception) = warn("위치", exception)
    }

    private fun warn(what: String, e: Exception) {
        Log.w(TAG, "$what 실패: $e")
    }

    companion object {
        private const val TAG = "PleosVehicle"
        const val CHARGE_POLL_MS = 5_000L
        private const val SDK_CALL_TIMEOUT_MS = 3_000L
    }
}
