package com.chargecompass.app.navi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RouteTrackerTest {
    @Test
    fun `경로 시작 정보가 오면 경로를 알고, 안내가 끝나면 지운다`() {
        val t = RouteTracker(RouteTracker.Cache())
        t.onStarted("서울역", 23_000, 2040)
        assertEquals(RouteSnapshot(1, "서울역", 23.0, 2040), t.route.value)
        t.onState(guiding = false)
        assertNull(t.route.value)
    }

    @Test
    fun `목적지가 같으면 재탐색이어도 같은 경로로 본다`() {
        val t = RouteTracker(RouteTracker.Cache())
        t.onStarted("서울역", 23_000, 2040)
        t.onStarted("서울역", 21_000, 1900)
        assertEquals(1, t.route.value!!.routeId)
        t.onStarted("강남역", 10_000, 900)
        assertEquals(2, t.route.value!!.routeId)
    }

    @Test
    fun `새로 연결했을 때 이미 안내 중이면 같은 앱 프로세스에서 받은 마지막 경로를 되살린다`() {
        val cache = RouteTracker.Cache()
        RouteTracker(cache).onStarted("서울역", 23_000, 2040)
        val again = RouteTracker(cache)   // 시연 모드 전환 등으로 저장소를 새로 만들었다
        again.onState(guiding = true)
        assertEquals("서울역", again.route.value?.destinationName)
        assertFalse(again.waitingForRoute.value)
    }

    @Test
    fun `안내 중인데 경로 정보를 모르면 기다리는 중으로 표시하고, 정보가 오면 푼다`() {
        val t = RouteTracker(RouteTracker.Cache())
        t.onState(guiding = true)
        assertNull(t.route.value)
        assertTrue(t.waitingForRoute.value)
        t.onStarted("서울역", 23_000, 2040)
        assertFalse(t.waitingForRoute.value)
    }

    @Test
    fun `안내가 끝나면 캐시도 지워 다음 연결에서 지난 경로를 되살리지 않는다`() {
        val cache = RouteTracker.Cache()
        val t = RouteTracker(cache)
        t.onStarted("서울역", 23_000, 2040)
        t.onState(guiding = false)
        val again = RouteTracker(cache)
        again.onState(guiding = true)
        assertNull(again.route.value)
    }

    @Test
    fun `경유지를 넣은 직후 내비가 잠깐 보내는 '안내 아님' 상태는 무시한다(재탐색 중)`() {
        var now = 0L
        val t = RouteTracker(RouteTracker.Cache()) { now }
        t.onStarted("서울역", 23_000, 2040)
        t.expectReroute()
        now += 2_000
        t.onState(guiding = false)
        assertEquals("서울역", t.route.value?.destinationName)
        // 재탐색 시간이 지난 뒤의 '안내 아님'은 진짜 안내 종료
        now += RouteTracker.REROUTE_GRACE_MS
        t.onState(guiding = false)
        assertNull(t.route.value)
    }
}
