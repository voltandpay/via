package com.chargecompass.core.rewards

import com.chargecompass.core.Defaults
import com.chargecompass.core.model.RewardEvent
import com.chargecompass.core.model.OperatorDay
import com.chargecompass.core.model.Station
import com.chargecompass.core.dex.Dex
import com.chargecompass.core.model.VehicleModel
import com.chargecompass.core.session.ChargeSession
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

enum class RewardKind { EFFICIENT_TRIP, CHARGE, EVENT_BONUS, NEW_STAMP, REGION_COMPLETE, OFF_PEAK_BOOST, OPERATOR_DAY, MISSION, BREAKTHROUGH, SEASON_STEP }

data class RewardEntry(
    val kind: RewardKind,
    val points: Int,
    val at: LocalDateTime,
    val stationId: String? = null,
    val eventId: String? = null,
    /** EVENT_BONUS의 이벤트 회차 시작일. 자정을 넘긴 충전은 전날 회차다. */
    val eventDate: LocalDate? = null,
    /** REGION_COMPLETE의 도감 지역. */
    val regionId: String? = null,
)

/** 이미 받은 보상. 영속 구현은 앱의 포인트 원장이 맡는다. */
interface RewardHistory {
    fun stampedStationIds(): Set<String>
    fun eventBonusDates(eventId: String): Set<LocalDate>
    /** 이 충전소에서 [notAfter] 이전(같은 시각 포함)에 충전 보상을 마지막으로 받은 시각. 시연 시계로 미래에 쌓인 기록은 보지 않는다. */
    fun lastChargeAt(stationId: String, notAfter: LocalDateTime): LocalDateTime? = null
}

/**
 * 설계 문서 5-3절. 사용자가 아무것도 누르지 않아도 차량 데이터로 확인된 일에만 보상한다.
 * 주행거리 자체에는 보상하지 않는다(불필요한 주행 유도 방지).
 */
object RewardEngine {
    fun onTripEnded(distanceKm: Double, tripEfficiency: Double?, vehicle: VehicleModel, at: LocalDateTime): List<RewardEntry> {
        if (distanceKm < Defaults.EFFICIENT_TRIP_MIN_KM || tripEfficiency == null) return emptyList()
        if (tripEfficiency < vehicle.baseEfficiencyKmPerKwh) return emptyList()
        return listOf(RewardEntry(RewardKind.EFFICIENT_TRIP, Defaults.POINTS_EFFICIENT_TRIP, at))
    }

    /** [stations]를 주면 지역의 마지막 충전소를 처음 충전할 때 지역 완성 보너스를 더한다. */
    fun onChargeStarted(
        session: ChargeSession, events: List<RewardEvent>, history: RewardHistory,
        stations: List<Station> = emptyList(), operatorDays: List<OperatorDay> = emptyList(),
    ): List<RewardEntry> {
        val stationId = session.stationId ?: return emptyList()
        val at = session.startedAt
        // 같은 충전소에서 쿨다운 안에 다시 시작한 충전은 같은 방문으로 본다(앱 재시작·플러그 다시 꽂기로 포인트가 늘지 않게)
        history.lastChargeAt(stationId, at)?.let { last ->
            if (Duration.between(last, at) < Duration.ofMinutes(Defaults.CHARGE_REWARD_COOLDOWN_MIN)) return emptyList()
        }
        val entries = mutableListOf(RewardEntry(RewardKind.CHARGE, Defaults.POINTS_CHARGE, at, stationId))
        val stamped = history.stampedStationIds()
        if (stationId !in stamped) {
            entries += RewardEntry(RewardKind.NEW_STAMP, Defaults.POINTS_NEW_STAMP, at, stationId)
            val region = stations.firstOrNull { it.id == stationId }?.region?.takeIf { it.isNotEmpty() }
            // 고장·점검 중인 충전소는 충전할 수 없으니 지역 완성 조건에서 뺀다
            val required = stations.filter { it.region == region && Dex.countsForRegion(it, it.id in stamped || it.id == stationId) }
            if (region != null && required.all { it.id == stationId || it.id in stamped }) {
                entries += RewardEntry(RewardKind.REGION_COMPLETE, Defaults.POINTS_REGION_COMPLETE, at, stationId, regionId = region)
            }
        }
        if (isOffPeak(at.hour)) entries += RewardEntry(RewardKind.OFF_PEAK_BOOST, Defaults.POINTS_CHARGE, at, stationId)
        val operatorId = stations.firstOrNull { it.id == stationId }?.operatorId
        operatorDays.firstOrNull { it.operatorId == operatorId && it.date == at.toLocalDate() }?.let { day ->
            entries += RewardEntry(RewardKind.OPERATOR_DAY, Defaults.POINTS_CHARGE * (day.multiplier - 1), at, stationId, day.id, day.date)
        }
        for (event in events.filter { it.stationId == stationId }) {
            val occurrence = eventOccurrenceDate(event, at) ?: continue
            if (occurrence in history.eventBonusDates(event.id)) continue
            entries += RewardEntry(RewardKind.EVENT_BONUS, Defaults.POINTS_CHARGE * (event.multiplier - 1), at, stationId, event.id, occurrence)
        }
        return entries
    }

    /** 경부하 시간대(자정을 넘는다) 안이면 true. */
    fun isOffPeak(hour: Int): Boolean = hour >= Defaults.OFF_PEAK_START_HOUR || hour < Defaults.OFF_PEAK_END_HOUR

    /** [at]이 이벤트 시간대 안이면 그 회차의 시작일, 밖이면 null. 끝 시각은 포함하지 않는다. */
    fun eventOccurrenceDate(event: RewardEvent, at: LocalDateTime): LocalDate? {
        val hour = at.hour
        return if (event.startHour < event.endHour) {
            at.toLocalDate().takeIf { hour >= event.startHour && hour < event.endHour }
        } else when {
            hour >= event.startHour -> at.toLocalDate()
            hour < event.endHour -> at.toLocalDate().minusDays(1)
            else -> null
        }
    }
}
