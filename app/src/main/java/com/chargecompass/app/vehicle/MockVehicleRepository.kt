package com.chargecompass.app.vehicle

import com.chargecompass.core.energy.EnergyState
import com.chargecompass.core.session.ChargingSample
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDateTime

/** 시연 스크립트·테스트용. 값은 디버그 패널이나 테스트가 직접 넣는다. */
class MockVehicleRepository(
    private val clock: () -> LocalDateTime = LocalDateTime::now,
) : VehicleRepository {
    private val _energy = MutableStateFlow(EnergyState(socPct = 80, efficiencyKmPerKwh = null))
    private val _parked = MutableStateFlow<Boolean?>(true)
    private val _charging = MutableSharedFlow<ChargingSample>(extraBufferCapacity = 16)
    private val _trips = MutableSharedFlow<TripSummary>(extraBufferCapacity = 16)
    private var location: Pair<Double, Double>? = 37.4128 to 127.0974   // 시연 출발지(판교)

    override val energy: StateFlow<EnergyState> = _energy
    override val parked: StateFlow<Boolean?> = _parked
    override val charging: SharedFlow<ChargingSample> = _charging
    override val tripEnded: SharedFlow<TripSummary> = _trips

    var vinValue: String? = null

    fun setSoc(pct: Int) { _energy.value = _energy.value.copy(socPct = pct) }
    fun setEfficiency(kmPerKwh: Double?) { _energy.value = _energy.value.copy(efficiencyKmPerKwh = kmPerKwh) }
    fun setParked(value: Boolean) { _parked.value = value }
    fun setLocation(lat: Double, lng: Double) { location = lat to lng }
    fun setCharging(on: Boolean) {
        _charging.tryEmit(ChargingSample(clock(), on, _energy.value.socPct, location?.first, location?.second))
    }
    /** 시연용: 끊김 허용 시간(60초)이 지난 것처럼 두 샘플을 보내 세션을 바로 끝낸다. */
    fun endCharging() {
        val soc = _energy.value.socPct
        val at = clock()
        _charging.tryEmit(ChargingSample(at, false, soc, location?.first, location?.second))
        _charging.tryEmit(ChargingSample(at.plusSeconds(61), false, soc, location?.first, location?.second))
    }
    fun clearLocation() { location = null }
    fun endTrip(trip: TripSummary) { _trips.tryEmit(trip) }

    override suspend fun vin(): String? = vinValue
    override fun start() = Unit
    override fun stop() = Unit
}
