package com.chargecompass.app.vehicle

import com.chargecompass.core.energy.EnergyState
import com.chargecompass.core.session.ChargingSample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDateTime

/** 한 번의 주행(D단 진입 → P단 복귀). 전비를 모르면 null. 위치를 몰랐으면 [start]·[end]가 null(평소 경로 학습에서 빠진다). */
data class TripSummary(
    val distanceKm: Double,
    val efficiencyKmPerKwh: Double?,
    val at: LocalDateTime,
    val start: Pair<Double, Double>? = null,
    val end: Pair<Double, Double>? = null,
    val startedAt: LocalDateTime? = null,
)

/** 차량 데이터. Pleos SDK 구현과 시연·테스트용 Mock 구현이 있다. */
interface VehicleRepository {
    val energy: StateFlow<EnergyState>
    /** P단이면 true, 그 밖의 기어면 false, 아직 모르면 null. */
    val parked: StateFlow<Boolean?>
    val charging: Flow<ChargingSample>
    val tripEnded: Flow<TripSummary>
    /** 운전석에 사람이 앉아 있으면 true, 비었으면 false, 모르면 null(좌석 신호를 못 받는 차는 늘 null). */
    val driverSeated: StateFlow<Boolean?> get() = UNKNOWN_SEAT
    /** 마지막으로 받은 위치(위도, 경도). 모르면 null. */
    fun currentLocation(): Pair<Double, Double>? = null
    suspend fun vin(): String?
    fun start()
    fun stop()
}

private val UNKNOWN_SEAT: StateFlow<Boolean?> = kotlinx.coroutines.flow.MutableStateFlow(null)
