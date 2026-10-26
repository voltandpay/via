package com.chargecompass.app.ui

import com.chargecompass.app.rewards.ChargeSummary
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VisualMathTest {
    @Test
    fun `최근 7일 날짜별 절약액(오늘이 마지막)`() {
        val today = LocalDate.of(2026, 11, 7)
        fun s(day: Int, won: Int, month: Int = 11) = ChargeSummary("st", 10.0, 1000, won, 10, LocalDateTime.of(2026, month, day, 12, 0))
        // 11/1~11/7이 최근 7일, 10/31은 빠진다
        val got = dailySavings(listOf(s(7, 3000), s(7, 1000), s(5, 2000), s(31, 9999, month = 10)), today)
        assertEquals(listOf(0, 0, 0, 0, 2000, 0, 4000), got)
    }

    @Test
    fun `다음 쿠폰까지 남은 포인트는 아직 못 받는 가장 싼 쿠폰 기준`() {
        val coupons = listOf(Coupon("한전 1,000원", 100), Coupon("채비 2,000원", 200), Coupon("워터 3,000원", 300))
        assertEquals(NextCoupon(coupons[0], 60), nextCoupon(40, coupons))
        assertEquals(NextCoupon(coupons[2], 50), nextCoupon(250, coupons))
        assertNull(nextCoupon(300, coupons))
    }
    @Test
    fun `주행 가능 거리는 남은 kWh에 전비를 곱한 값(내림)`() {
        // 60% × 77.4kWh × 5.0km/kWh = 232.2km
        assertEquals(232, rangeKm(socPct = 60, batteryKwh = 77.4, efficiencyKmPerKwh = 5.0))
        assertNull(rangeKm(socPct = null, batteryKwh = 77.4, efficiencyKmPerKwh = 5.0))
    }

    @Test
    fun `경로 띠의 충전소 위치는 남은 거리 대비 비율, 배터리는 그 지점 예상값`() {
        // 100km 경로, 시작 40%, 1km당 0.3%p
        val stops = routeStops(100.0, listOf("a" to 30.0, "b" to 120.0)) { km -> 40 - km * 0.3 }
        assertEquals(listOf(RouteStop("a", 0.3f, 31), RouteStop("b", 1f, 4)), stops)
        // 배터리를 모르면 null
        assertEquals(listOf(RouteStop("a", 0.3f, null)), routeStops(100.0, listOf("a" to 30.0)) { null })
    }

    @Test
    fun `막대 위 금액은 만 원 이상이면 만 단위로 줄인다`() {
        assertEquals("", compactWon(0))
        assertEquals("8,200", compactWon(8200))
        assertEquals("1.1만", compactWon(11118))
        assertEquals("3만", compactWon(30000))
    }

    @Test
    fun `앞으로 24시간 띠 - 지금이 왼쪽 끝이라 자정을 넘는 이벤트도 한 덩어리`() {
        // 10시 기준 22~2시 → 12시간 뒤부터 4시간
        assertEquals(listOf(12f / 24 to 16f / 24), upcomingWindowSegments(22, 2, nowMinutes = 10 * 60))
        // 23시(진행 중) → 지금~3시간 뒤, 그리고 다음 날 22시(23시간 뒤)부터 끝까지
        assertEquals(listOf(0f to 3f / 24, 23f / 24 to 1f), upcomingWindowSegments(22, 2, nowMinutes = 23 * 60))
    }

    @Test
    fun `이벤트까지 남은 시간 문구`() {
        assertEquals("12시간 뒤 시작", eventStatus(22, 2, nowMinutes = 10 * 60))
        // 3시간 이상 남으면 시간 단위로 반올림(11시간 55분 → 12시간)
        assertEquals("12시간 뒤 시작", eventStatus(22, 2, nowMinutes = 10 * 60 + 5))
        assertEquals("30분 뒤 시작", eventStatus(22, 2, nowMinutes = 21 * 60 + 30))
        assertEquals("1시간 30분 뒤 시작", eventStatus(22, 2, nowMinutes = 20 * 60 + 30))
        assertEquals("지금 진행 중 · 3시간 남음", eventStatus(22, 2, nowMinutes = 23 * 60))
        assertEquals("지금 진행 중 · 2시간 남음", eventStatus(10, 14, nowMinutes = 12 * 60))
    }

    @Test
    fun `시즌 트랙 채움은 단계 사이를 고르게 나눈 위치`() {
        val t = listOf(50, 100, 160)   // 단계 위치 1/3, 2/3, 1
        assertEquals(0f, seasonTrackFraction(0, t))
        assertEquals(1f / 6, seasonTrackFraction(25, t), 1e-4f)      // 0~50의 가운데 → 0~1/3의 가운데
        assertEquals(2f / 3, seasonTrackFraction(100, t), 1e-4f)
        assertEquals(2f / 3 + 1f / 6, seasonTrackFraction(130, t), 1e-4f)
        assertEquals(1f, seasonTrackFraction(999, t))
    }
}
