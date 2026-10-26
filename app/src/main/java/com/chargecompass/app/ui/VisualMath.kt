package com.chargecompass.app.ui

import com.chargecompass.app.rewards.ChargeSummary
import java.time.LocalDate
import kotlin.math.ceil

/** 시작~끝 시(時)를 24시간 띠 위의 [시작, 끝) 비율 구간으로. 자정을 넘으면 두 조각. */
fun dayWindowSegments(startHour: Int, endHour: Int): List<Pair<Float, Float>> =
    if (startHour < endHour) listOf(startHour / 24f to endHour / 24f)
    else listOf(startHour / 24f to 1f, 0f to endHour / 24f)

/** 목표까지 남은 충전 시간(분, 올림). 최대 출력으로 계속 충전한다고 본 근사값. SoC를 모르면 null. */
fun chargeEtaMinutes(socPct: Int?, targetPct: Int, batteryKwh: Double, powerKw: Int): Int? {
    val soc = socPct ?: return null
    if (soc >= targetPct || powerKw <= 0) return 0
    val kwh = (targetPct - soc) / 100.0 * batteryKwh
    return ceil(kwh / powerKw * 60).toInt()
}

/** 오늘을 마지막으로 하는 최근 7일의 날짜별 절약액. */
fun dailySavings(summaries: List<ChargeSummary>, today: LocalDate): List<Int> =
    (6 downTo 0).map { back ->
        val day = today.minusDays(back.toLong())
        summaries.filter { it.endedAt.toLocalDate() == day }.sumOf { it.savingWon }
    }

data class Coupon(val name: String, val cost: Int)
data class NextCoupon(val coupon: Coupon, val remaining: Int)

/** 지금 포인트로 아직 못 받는 쿠폰 중 가장 싼 것과 남은 포인트. 다 받을 수 있으면 null. */
fun nextCoupon(balance: Int, coupons: List<Coupon>): NextCoupon? =
    coupons.filter { it.cost > balance }.minByOrNull { it.cost }?.let { NextCoupon(it, it.cost - balance) }

/** 제휴 쿠폰(1차 MVP는 화면만). */
val partnerCoupons = listOf(Coupon("한전 EV 충전 1,000원 할인", 100), Coupon("채비 충전 2,000원 할인", 200), Coupon("워터 초급속 3,000원 할인", 300))
