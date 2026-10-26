package com.chargecompass.app.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import com.chargecompass.core.model.Station
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * NaviHelper 구현. 경유지 추가는 경로 안내 중에만 된다(스파이크 2차).
 * 내비 앱이 준비되지 않으면 요청이 조용히 버려지므로, [WAYPOINT_TIMEOUT_MS] 안에 onWaypointChanged가 안 오면 실패로 본다.
 */
class PleosNaviRepository(context: Context) : NaviRepository {
    private val navi = NaviHelper(context.applicationContext)
    private val tracker = RouteTracker(processCache)
    override val route: StateFlow<RouteSnapshot?> = tracker.route
    override val waitingForRoute: StateFlow<Boolean> = tracker.waitingForRoute
    @Volatile private var pendingWaypoint: CompletableDeferred<Boolean>? = null

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            Log.d(TAG, "경로 시작: ${routeStartInfo.name} ${routeStartInfo.distance}m")
            tracker.onStarted(routeStartInfo.name, routeStartInfo.distance.toInt(), routeStartInfo.duration.toInt())
        }

        // 연결 직후 getRouteStateInfo()의 응답도 여기로 온다: 이미 안내 중이면 마지막 경로를 되살린다
        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
            Log.d(TAG, "경로 상태: ${routeStateInfo.naviMode} routing=${routeStateInfo.isRouting}")
            tracker.onState(guiding = routeStateInfo.naviMode == NaviMode.ROUTE_GUIDING)
        }

        override fun onDestinationChanged() {
            Log.d(TAG, "목적지 바뀜")
            navi.getRouteStateInfo()
        }

        override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) {
            Log.d(TAG, "경유지 바뀜")
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

    /** 시연용(디버그 패널): 서울역으로 경로를 요청한다. 실제 사용자는 내비에서 목적지를 정한다. */
    fun requestDemoRoute() {
        navi.requestRoute(RouteInfo(latitude = 37.5547, longitude = 126.9707, poiName = "서울역", poiId = "0", address = "서울특별시 용산구 한강대로 405", poiSubId = "0"))
    }

    /** 장거리 다중 충전 시연용: 부산역으로 안내 요청. */
    fun requestLongTripRoute() {
        navi.requestRoute(RouteInfo(latitude = 35.1151, longitude = 129.0422, poiName = "부산역", poiId = "0", address = "부산광역시 동구 중앙대로 206", poiSubId = "0"))
    }

    override suspend fun addChargingStop(station: Station): StopResult {
        val done = CompletableDeferred<Boolean>().also { pendingWaypoint = it }
        tracker.expectReroute()
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

    /** 첫 경유지(앞서 넣은 충전소)를 지우고 새 충전소를 넣는다. */
    override suspend fun replaceChargingStop(station: Station): StopResult {
        tracker.expectReroute()
        navi.removeWaypoint(WaypointIndex.FIRST)
        return addChargingStop(station)
    }

    companion object {
        private const val TAG = "PleosNavi"
        /** 앱 프로세스 동안 이어지는 마지막 경로(시연 모드 전환으로 저장소를 새로 만들어도 남는다). */
        private val processCache = RouteTracker.Cache()
        // 에뮬레이터에서 경유지 추가 확인 콜백이 요청 뒤 약 6.4초에 왔다(재탐색 포함) → 넉넉히 12초
        const val WAYPOINT_TIMEOUT_MS = 12_000L
    }
}
