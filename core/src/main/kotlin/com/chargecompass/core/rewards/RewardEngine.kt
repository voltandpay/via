package com.chargecompass.core.rewards

import com.chargecompass.core.Defaults
import com.chargecompass.core.model.RewardEvent
import com.chargecompass.core.model.VehicleModel
import com.chargecompass.core.session.ChargeSession
import java.time.LocalDate
import java.time.LocalDateTime

enum class RewardKind { EFFICIENT_TRIP, CHARGE, EVENT_BONUS, NEW_STAMP }

data class RewardEntry(
    val kind: RewardKind,
    val points: Int,
    val at: LocalDateTime,
    val stationId: String? = null,
    val eventId: String? = null,
)

/** 이미 받은 보상. 영속 구현은 앱의 포인트 원장이 맡는다. */
interface RewardHistory {
    fun stampedStationIds(): Set<String>
    fun eventBonusDates(eventId: String): Set<LocalDate>
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

    fun onChargeStarted(session: ChargeSession, events: List<RewardEvent>, history: RewardHistory): List<RewardEntry> {
        val stationId = session.stationId ?: return emptyList()
        val at = session.startedAt
        val entries = mutableListOf(RewardEntry(RewardKind.CHARGE, Defaults.POINTS_CHARGE, at, stationId))
        if (stationId !in history.stampedStationIds()) {
            entries += RewardEntry(RewardKind.NEW_STAMP, Defaults.POINTS_NEW_STAMP, at, stationId)
        }
        for (event in events.filter { it.stationId == stationId }) {
            val occurrence = eventOccurrenceDate(event, at) ?: continue
            if (occurrence in history.eventBonusDates(event.id)) continue
            entries += RewardEntry(RewardKind.EVENT_BONUS, Defaults.POINTS_CHARGE * (event.multiplier - 1), at, stationId, event.id)
        }
        return entries
    }

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
