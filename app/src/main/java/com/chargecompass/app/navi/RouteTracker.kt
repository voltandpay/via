package com.chargecompass.app.navi

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 내비 콜백으로 지금 경로를 추적한다(SDK와 분리해 단위 테스트).
 * NaviHelper는 경로의 목적지·거리를 경로가 시작될 때(onRouteStarted)만 주고, 경로 상태(onRouteStateInfo)에는 없다.
 * 그래서 이미 안내 중일 때 새로 연결하면 경로를 알 수 없다 → 같은 앱 프로세스에서 받은 마지막 경로를 [Cache]로 되살리고,
 * 그래도 모르면 [waitingForRoute]로 "내비에서 경로를 다시 시작해 주세요"를 안내한다.
 */
class RouteTracker(private val cache: Cache, private val clockMs: () -> Long = System::currentTimeMillis) {
    /** 앱 프로세스 안에서 저장소를 새로 만들어도(시연 모드 전환 등) 이어지는 마지막 경로. */
    class Cache {
        @Volatile var last: RouteSnapshot? = null
        @Volatile var nextId = 1L
    }

    private val _route = MutableStateFlow<RouteSnapshot?>(null)
    val route: StateFlow<RouteSnapshot?> = _route
    private val _waiting = MutableStateFlow(false)
    val waitingForRoute: StateFlow<Boolean> = _waiting

    /** 목적지 이름이 같으면 같은 경로로 본다(재탐색·경유지 추가로 routeId가 바뀌면 추천을 다시 묻게 된다). */
    fun onStarted(name: String, distanceM: Int, durationSec: Int) {
        val current = _route.value ?: cache.last
        val id = if (current != null && current.destinationName == name) current.routeId else cache.nextId++
        val snapshot = RouteSnapshot(id, name, distanceM / 1000.0, durationSec)
        cache.last = snapshot
        _route.value = snapshot
        _waiting.value = false
    }

    private var rerouteUntil = 0L

    /**
     * 경유지를 넣으면 내비가 재탐색하면서 '안내 아님'(SAFE_GUIDING)을 한 번 보내고, 안내가 이어져도 다시 알려 주지 않는다
     * (에뮬레이터에서 확인). 그 사이의 '안내 아님'은 무시해 경로와 추천 상태를 지키지 않으면 "경유지로 추가했어요"가 사라진다.
     */
    fun expectReroute() { rerouteUntil = clockMs() + REROUTE_GRACE_MS }

    fun onState(guiding: Boolean) {
        if (!guiding && clockMs() < rerouteUntil && _route.value != null) return
        if (!guiding) {
            cache.last = null
            _route.value = null
            _waiting.value = false
            return
        }
        if (_route.value != null) return
        val last = cache.last
        if (last != null) _route.value = last else _waiting.value = true
    }

    companion object {
        const val REROUTE_GRACE_MS = 20_000L   // 경유지 확인을 기다리는 시간(12초)보다 길게
    }
}
