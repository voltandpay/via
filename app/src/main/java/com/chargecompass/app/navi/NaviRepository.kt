package com.chargecompass.app.navi

import com.chargecompass.core.model.Station
import kotlinx.coroutines.flow.StateFlow

/** 안내 중인 경로. 목적지가 바뀌면 [routeId]가 바뀐다(경유지 추가로는 바뀌지 않는다). */
data class RouteSnapshot(val routeId: Long, val destinationName: String, val remainingKm: Double, val remainingSec: Int)

enum class StopResult { WAYPOINT_ADDED, DESTINATION_REPLACED, FAILED }

interface NaviRepository {
    /** null = 경로 안내 중 아님. */
    val route: StateFlow<RouteSnapshot?>
    suspend fun addChargingStop(station: Station): StopResult
    fun start()
    fun stop()
}
