package com.chargecompass.app.vehicle

import com.chargecompass.core.energy.EnergyState
import com.chargecompass.core.session.ChargingSample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDateTime

/** 한 번의 주행(D단 진입 → P단 복귀). 전비를 모르면 null. */
data class TripSummary(val distanceKm: Double, val efficiencyKmPerKwh: Double?, val at: LocalDateTime)

/** 차량 데이터. Pleos SDK 구현과 시연·테스트용 Mock 구현이 있다. */
interface VehicleRepository {
    val energy: StateFlow<EnergyState>
    /** P단이면 true, 그 밖의 기어면 false, 아직 모르면 null. */
    val parked: StateFlow<Boolean?>
    val charging: Flow<ChargingSample>
    val tripEnded: Flow<TripSummary>
    suspend fun vin(): String?
    fun start()
    fun stop()
}
