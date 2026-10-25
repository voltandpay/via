package com.chargecompass.app.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import com.chargecompass.core.model.Station
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * NaviHelper 구현. 경유지 추가는 경로 안내 중에만 된다(스파이크 2차).
 * 내비 앱이 준비되지 않으면 요청이 조용히 버려지므로, [WAYPOINT_TIMEOUT_MS] 안에 onWaypointChanged가 안 오면 실패로 본다.
 */
class PleosNaviRepository(context: Context) : NaviRepository {
    private val navi = NaviHelper(context.applicationContext)
    private val _route = MutableStateFlow<RouteSnapshot?>(null)
    override val route: StateFlow<RouteSnapshot?> = _route

    private var nextRouteId = 1L
    @Volatile private var pendingWaypoint: CompletableDeferred<Boolean>? = null

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) = onRoute(routeStartInfo)

        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
            if (routeStateInfo.naviMode != NaviMode.ROUTE_GUIDING) _route.value = null
        }

        override fun onDestinationChanged() = navi.getRouteStateInfo()

        override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) {
            pendingWaypoint?.complete(true)
        }

        override fun onError(errorCode: NaviErrorCode) {
            Log.w(TAG, "NaviHelper 오류: $errorCode")
            pendingWaypoint?.complete(false)
        }
    }

    override fun start() {
        navi.initialize(
            onConnected = {
                navi.addListener(listener)
                navi.getRouteStateInfo()
            },
            onFailed = { Log.w(TAG, "NaviHelper 연결 실패: $it") },
        )
    }

    override fun stop() {
        navi.removeListener(listener)
        navi.release()
    }

    /** 목적지 이름이 같으면 같은 경로로 본다(재탐색·경유지 추가로 routeId가 바뀌면 추천을 다시 묻게 된다). */
    private fun onRoute(info: RouteStartInfo) {
        val current = _route.value
        val id = if (current != null && current.destinationName == info.name) current.routeId else nextRouteId++
        _route.value = RouteSnapshot(id, info.name, info.distance / 1000.0, info.duration)
    }

    override suspend fun addChargingStop(station: Station): StopResult {
        val done = CompletableDeferred<Boolean>().also { pendingWaypoint = it }
        navi.addWaypoint(
            RequestWaypointInfo(
                latitude = station.lat,
                longitude = station.lng,
                poiName = station.name,
                waypointIndex = WaypointIndex.FIRST,
                poiId = "0",
                address = station.name,
                poiSubId = "0",
            ),
        )
        val ok = withTimeoutOrNull(WAYPOINT_TIMEOUT_MS) { done.await() } ?: false
        pendingWaypoint = null
        return if (ok) StopResult.WAYPOINT_ADDED else StopResult.FAILED
    }

    companion object {
        private const val TAG = "PleosNavi"
        const val WAYPOINT_TIMEOUT_MS = 5_000L
    }
}
