package com.chargecompass.app.rewards

import com.chargecompass.core.model.RewardEvent
import com.chargecompass.core.model.Station
import com.chargecompass.core.rewards.RewardEngine
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 장면 C: 출발할 때 오늘의 이벤트 충전소를 음성으로 한 번 알린다(하루 한 번).
 * 이벤트 시간 안이면 "지금", 아니면 "오늘 밤 …시부터".
 */
class EventAnnouncer(private val events: List<RewardEvent>, private val stations: List<Station>) {
    private var announcedOn: LocalDate? = null

    fun onDepart(now: LocalDateTime): String? {
        val event = events.firstOrNull() ?: return null
        val today = now.toLocalDate()
        if (announcedOn == today) return null
        announcedOn = today
        val name = stations.firstOrNull { it.id == event.stationId }?.name ?: return null
        return if (RewardEngine.eventOccurrenceDate(event, now) != null) {
            "지금 ${name}에서 충전하면 포인트 ${event.multiplier}배예요 · ${event.endHour}시까지"
        } else {
            "오늘 밤 ${event.startHour}시부터 ${event.endHour}시까지 ${name}에서 충전하면 포인트 ${event.multiplier}배예요"
        }
    }
}
