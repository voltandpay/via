package com.chargecompass.app.navi

import com.chargecompass.core.model.Station
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MockNaviRepository : NaviRepository {
    private val _route = MutableStateFlow<RouteSnapshot?>(null)
    override val route: StateFlow<RouteSnapshot?> = _route

    val addedStops = mutableListOf<Station>()
    var nextResult = StopResult.WAYPOINT_ADDED

    fun setRoute(route: RouteSnapshot?) { _route.value = route }

    override suspend fun addChargingStop(station: Station): StopResult {
        addedStops += station
        return nextResult
    }

    override fun start() = Unit
    override fun stop() = Unit
}
