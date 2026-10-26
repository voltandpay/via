package com.chargecompass.app.ui

import com.chargecompass.app.rewards.ChargeSummary
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VisualMathTest {
    @Test
    fun `이벤트 시간대를 24시간 띠의 구간으로 바꾼다 - 자정을 넘으면 두 조각`() {
        assertEquals(listOf(22f / 24 to 1f, 0f to 2f / 24), dayWindowSegments(22, 2))
        assertEquals(listOf(10f / 24 to 14f / 24), dayWindowSegments(10, 14))
    }

    @Test
    fun `목표까지 남은 충전 시간(분)은 필요한 kWh를 출력으로 나눈 값`() {
        // 15→80%, 77.4kWh → 50.31kWh, 200kW → 15.1분 → 16분(올림)
        assertEquals(16, chargeEtaMinutes(socPct = 15, targetPct = 80, batteryKwh = 77.4, powerKw = 200))
        assertEquals(0, chargeEtaMinutes(socPct = 85, targetPct = 80, batteryKwh = 77.4, powerKw = 200))
        assertNull(chargeEtaMinutes(socPct = null, targetPct = 80, batteryKwh = 77.4, powerKw = 200))
    }

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
}
