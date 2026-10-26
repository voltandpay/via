package com.chargecompass.core.usual

import com.chargecompass.core.data.distanceM
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.abs

/** 끝난 주행 1회(D → P). [distanceKm]는 차가 잰 주행거리, [destination]은 그때 내비에 넣었던 목적지(없으면 null). */
data class TripRecord(
    val startLat: Double,
    val startLng: Double,
    val endLat: Double,
    val endLng: Double,
    val distanceKm: Double,
    val startedAt: LocalDateTime,
    val destination: String?,
)

/** 평소 경로: 같은 곳에서 비슷한 시간에 출발해 같은 곳으로 간 주행이 [trips]번. 거리는 그 주행들의 중앙값. */
data class UsualRoute(val distanceKm: Double, val trips: Int, val destination: String?)

/**
 * 출퇴근처럼 익숙한 길은 내비를 켜지 않아 경로를 모른다. 차 안에 쌓인 주행 기록으로 "평소 이 시간 이곳에서 출발하면
 * 어디로 가는지"를 추정한다. 평일·주말을 나누고, 출발 시각 ±[WINDOW_MIN]분, 같은 장소는 반경 [SAME_PLACE_M]m.
 * 같은 목적지가 [MIN_TRIPS]번 이상이고 후보의 [MIN_SHARE] 이상일 때만 평소 경로로 본다(애매하면 말하지 않는다).
 */
object UsualRoutes {
    const val SAME_PLACE_M = 300.0
    const val WINDOW_MIN = 60
    const val MIN_TRIPS = 3
    const val MIN_SHARE = 0.6
    const val LOOKBACK_DAYS = 60L

    fun predict(history: List<TripRecord>, lat: Double, lng: Double, now: LocalDateTime): UsualRoute? {
        val since = now.minusDays(LOOKBACK_DAYS)
        val candidates = history.filter {
            it.startedAt.isAfter(since) && weekend(it.startedAt) == weekend(now) &&
                minutesApart(it.startedAt.toLocalTime(), now.toLocalTime()) <= WINDOW_MIN &&
                distanceM(lat, lng, it.startLat, it.startLng) <= SAME_PLACE_M
        }
        if (candidates.size < MIN_TRIPS) return null
        val groups = mutableListOf<MutableList<TripRecord>>()
        for (t in candidates) {
            groups.firstOrNull { distanceM(it[0].endLat, it[0].endLng, t.endLat, t.endLng) <= SAME_PLACE_M }?.add(t)
                ?: groups.add(mutableListOf(t))
        }
        val best = groups.maxBy { it.size }
        if (best.size < MIN_TRIPS || best.size < candidates.size * MIN_SHARE) return null
        val km = best.map { it.distanceKm }.sorted()
        val median = if (km.size % 2 == 1) km[km.size / 2] else (km[km.size / 2 - 1] + km[km.size / 2]) / 2
        val destination = best.mapNotNull { it.destination }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        return UsualRoute(median, best.size, destination)
    }

    private fun weekend(t: LocalDateTime) = t.dayOfWeek == DayOfWeek.SATURDAY || t.dayOfWeek == DayOfWeek.SUNDAY

    /** 자정을 넘는 차이도 짧은 쪽으로(23:30과 00:10은 40분). */
    private fun minutesApart(a: LocalTime, b: LocalTime): Int {
        val d = abs(a.toSecondOfDay() - b.toSecondOfDay()) / 60
        return minOf(d, 24 * 60 - d)
    }
}
