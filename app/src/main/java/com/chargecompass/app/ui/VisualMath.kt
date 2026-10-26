package com.chargecompass.app.ui

import com.chargecompass.app.rewards.ChargeSummary
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.roundToInt

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

/** 지금 배터리로 갈 수 있는 거리(km, 내림). SoC를 모르면 null. */
fun rangeKm(socPct: Int?, batteryKwh: Double, efficiencyKmPerKwh: Double): Int? =
    socPct?.let { floor(it / 100.0 * batteryKwh * efficiencyKmPerKwh).toInt() }

/** 경로 띠 위 충전소: 출발(0)~목적지(1) 사이 위치와 그 지점의 예상 배터리(%). */
data class RouteStop(val id: String, val fraction: Float, val socPct: Int?)

fun routeStops(remainingKm: Double, stops: List<Pair<String, Double>>, socAt: (km: Double) -> Double?): List<RouteStop> =
    stops.map { (id, km) ->
        val f = if (remainingKm > 0) (km / remainingKm).toFloat().coerceIn(0f, 1f) else 0f
        RouteStop(id, f, socAt(km)?.roundToInt())
    }

/** 막대 위 짧은 금액: 0은 빈칸, 만 원 미만은 쉼표, 이상은 "1.1만". */
fun compactWon(v: Int): String = when {
    v == 0 -> ""
    v < 10_000 -> won(v).removeSuffix("원")
    else -> (round(v / 1000.0) / 10).let { if (it % 1.0 == 0.0) "${it.toInt()}만" else "${it}만" }
}

private const val DAY_MIN = 24 * 60

/** 지금을 왼쪽 끝(0)으로 하는 앞으로 24시간 띠 위의 이벤트 구간. 자정을 넘는 이벤트도 한 덩어리로 보인다. */
fun upcomingWindowSegments(startHour: Int, endHour: Int, nowMinutes: Int): List<Pair<Float, Float>> {
    val start = startHour * 60
    val len = Math.floorMod(endHour * 60 - start, DAY_MIN).let { if (it == 0) DAY_MIN else it }
    val out = mutableListOf<Pair<Float, Float>>()
    // 어제 시작해 지금까지 이어지는 회차와 앞으로 시작할 회차
    for (dayOffset in listOf(-DAY_MIN, 0, DAY_MIN)) {
        val s = Math.floorMod(start - nowMinutes, DAY_MIN) + dayOffset
        val a = s.coerceAtLeast(0); val b = (s + len).coerceAtMost(DAY_MIN)
        if (b > a) out += a.toFloat() / DAY_MIN to b.toFloat() / DAY_MIN
    }
    return out.distinct().sortedBy { it.first }
}

/** "12시간 뒤 시작" / "지금 진행 중 · 3시간 남음". */
fun eventStatus(startHour: Int, endHour: Int, nowMinutes: Int): String {
    val start = startHour * 60
    val len = Math.floorMod(endHour * 60 - start, DAY_MIN).let { if (it == 0) DAY_MIN else it }
    val since = Math.floorMod(nowMinutes - start, DAY_MIN)
    return if (since < len) "지금 진행 중 · ${duration(len - since)} 남음" else "${duration(DAY_MIN - since)} 뒤 시작"
}

private fun duration(min: Int): String = when {
    min < 60 -> "${min}분"
    min >= 180 -> "${(min + 30) / 60}시간"
    min % 60 == 0 -> "${min / 60}시간"
    else -> "${min / 60}시간 ${min % 60}분"
}
