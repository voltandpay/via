package com.chargecompass.app.navi

import com.chargecompass.core.model.Station
import kotlinx.coroutines.flow.StateFlow

/** 안내 중인 경로. 목적지가 바뀌면 [routeId]가 바뀐다(경유지 추가로는 바뀌지 않는다). */
data class RouteSnapshot(val routeId: Long, val destinationName: String, val remainingKm: Double, val remainingSec: Int)

enum class StopResult { WAYPOINT_ADDED, DESTINATION_REPLACED, FAILED }

interface NaviRepository {
    /** null = 경로 안내 중 아님. */
    val route: StateFlow<RouteSnapshot?>
    /** 내비는 안내 중인데 경로 정보(목적지·거리)를 아직 모른다 → 내비에서 경로를 다시 시작해야 추천할 수 있다. */
    val waitingForRoute: StateFlow<Boolean> get() = NOT_WAITING
    suspend fun addChargingStop(station: Station): StopResult
    fun start()
    fun stop()
}

private val NOT_WAITING: StateFlow<Boolean> = kotlinx.coroutines.flow.MutableStateFlow(false)
