package com.chargecompass.core.usual

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UsualRoutesTest {
    // 판교 → 서울역(약 23km), 판교 → 수원(약 30km)
    private val pangyo = 37.4128 to 127.0974
    private val seoul = 37.5547 to 126.9707
    private val suwon = 37.2659 to 127.0001
    private val mon = LocalDateTime.of(2026, 10, 26, 18, 30)   // 월요일

    private fun trip(at: LocalDateTime, to: Pair<Double, Double> = seoul, km: Double = 23.0, dest: String? = null, from: Pair<Double, Double> = pangyo) =
        TripRecord(from.first, from.second, to.first, to.second, km, at, dest)

    @Test
    fun `같은 곳에서 비슷한 시간에 같은 곳으로 간 주행이 3번 넘으면 평소 경로로 본다`() {
        val history = listOf(
            trip(mon.minusDays(7), km = 22.0), trip(mon.minusDays(6).plusMinutes(20), km = 24.0, dest = "서울역"),
            trip(mon.minusDays(5).minusMinutes(40), km = 23.0), trip(mon.minusDays(4), to = suwon, km = 30.0),
        )
        val r = UsualRoutes.predict(history, pangyo.first, pangyo.second, mon)!!
        assertEquals(3, r.trips)
        assertEquals(23.0, r.distanceKm)
        assertEquals("서울역", r.destination)
    }

    @Test
    fun `횟수가 모자라거나 목적지가 갈리면 말하지 않는다`() {
        assertNull(UsualRoutes.predict(listOf(trip(mon.minusDays(7)), trip(mon.minusDays(6))), pangyo.first, pangyo.second, mon))
        // 지난 3주 월·화요일(모두 평일)
        val split = listOf(trip(mon.minusDays(7)), trip(mon.minusDays(14)), trip(mon.minusDays(21)),
            trip(mon.minusDays(6), to = suwon), trip(mon.minusDays(13), to = suwon), trip(mon.minusDays(20), to = suwon))
        assertNull(UsualRoutes.predict(split, pangyo.first, pangyo.second, mon))
    }

    @Test
    fun `다른 출발지, 다른 시간대, 주말, 오래된 주행은 빼고 본다`() {
        val history = listOf(
            trip(mon.minusDays(7), from = suwon),
            trip(mon.minusDays(6).minusHours(3)),
            trip(mon.minusDays(2)),   // 토요일
            trip(mon.minusDays(90)),
        )
        assertNull(UsualRoutes.predict(history + trip(mon.minusDays(7)), pangyo.first, pangyo.second, mon))
    }

    @Test
    fun `자정 근처 출발은 날짜를 넘어도 같은 시간대로 본다`() {
        val late = LocalDateTime.of(2026, 10, 27, 0, 10)   // 화요일 0:10
        val history = (1..3).map { trip(LocalDateTime.of(2026, 10, 19 + it, 23, 40)) }
        assertEquals(3, UsualRoutes.predict(history, pangyo.first, pangyo.second, late)?.trips)
    }
}
