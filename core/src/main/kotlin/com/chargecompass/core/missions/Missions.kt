package com.chargecompass.core.missions

import com.chargecompass.core.Defaults
import com.chargecompass.core.rewards.RewardEntry
import com.chargecompass.core.rewards.RewardKind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/** 주간 미션(포켓몬 고의 필드 리서치). 모두 절약으로 이어지는 행동이고, 주행 거리에는 보상하지 않는다. */
enum class Mission(val title: String, val target: Int) {
    DISCOUNT_CHARGE("회원가·로밍가로 2번 충전", 2),
    EFFICIENT_TRIP("효율 운전 2번", 2),
    NEW_STATION("새 충전소 1곳 등록", 1),
}

data class MissionProgress(val mission: Mission, val count: Int) {
    val done: Boolean get() = count >= mission.target
}

object Missions {
    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** [date]가 속한 주의 진행. [discountCharges]는 이번 주 비회원가보다 싸게 낸 충전 횟수(충전 요약에서 센다). */
    fun progress(date: LocalDate, entries: List<RewardEntry>, discountCharges: Int): List<MissionProgress> {
        val start = weekStart(date)
        val week = entries.filter { !it.at.toLocalDate().isBefore(start) && it.at.toLocalDate().isBefore(start.plusDays(7)) }
        return Mission.entries.map { m ->
            MissionProgress(m, when (m) {
                Mission.DISCOUNT_CHARGE -> discountCharges
                Mission.EFFICIENT_TRIP -> week.count { it.kind == RewardKind.EFFICIENT_TRIP }
                Mission.NEW_STATION -> week.count { it.kind == RewardKind.NEW_STAMP }
            })
        }
    }

    /** 이번 주 새로 달성한 미션 보상과, 미션 스탬프가 [Defaults.MISSIONS_PER_BREAKTHROUGH]개 찰 때마다 리서치 돌파 보너스. */
    fun rewards(at: LocalDateTime, entries: List<RewardEntry>, discountCharges: Int): List<RewardEntry> {
        val start = weekStart(at.toLocalDate())
        val rewarded = entries.filter { it.kind == RewardKind.MISSION && it.eventDate == start }.mapNotNull { it.eventId }.toSet()
        // 시연 시계로 미래 시각에 쌓인 기록은 아직 일어나지 않은 일이다
        val fresh = progress(at.toLocalDate(), entries.filter { !it.at.isAfter(at) }, discountCharges)
            .filter { it.done && it.mission.name !in rewarded }
            .map { RewardEntry(RewardKind.MISSION, Defaults.POINTS_MISSION, at, eventId = it.mission.name, eventDate = start) }
        val before = entries.count { it.kind == RewardKind.MISSION }
        val per = Defaults.MISSIONS_PER_BREAKTHROUGH
        val breakthroughs = (before + fresh.size) / per - before / per
        return fresh + List(breakthroughs) { RewardEntry(RewardKind.BREAKTHROUGH, Defaults.POINTS_BREAKTHROUGH, at) }
    }

    /** 다음 리서치 돌파까지 모은 미션 스탬프 수(0~6). */
    fun breakthroughStamps(entries: List<RewardEntry>): Int = entries.count { it.kind == RewardKind.MISSION } % Defaults.MISSIONS_PER_BREAKTHROUGH
}
