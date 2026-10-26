package com.chargecompass.core.season

import com.chargecompass.core.rewards.RewardEntry
import com.chargecompass.core.rewards.RewardKind
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** 충전 혜택 시즌(포켓몬 고의 시즌·타임드 리서치). 4주마다 새로 시작한다. */
data class Season(val number: Int, val start: LocalDate) {
    val end: LocalDate get() = start.plusDays(Seasons.DAYS - 1L)
    val id: String get() = "s$number"
}

enum class SeasonReward { COIN, CHEST, MEDAL }

/** [xp] 시즌 경험치를 넘으면 [bonus] 포인트와 [reward] 모양의 보상. */
data class SeasonStep(val xp: Int, val reward: SeasonReward, val bonus: Int)

object Seasons {
    const val DAYS = 28
    /** 시즌 1 시작일(월요일). 2차 PT(12/4)가 시즌 3 안에 들도록 정했다. */
    val EPOCH: LocalDate = LocalDate.of(2026, 10, 5)

    val steps = listOf(
        SeasonStep(50, SeasonReward.COIN, 10),
        SeasonStep(100, SeasonReward.COIN, 10),
        SeasonStep(160, SeasonReward.CHEST, 30),
        SeasonStep(240, SeasonReward.COIN, 20),
        SeasonStep(340, SeasonReward.COIN, 30),
        SeasonStep(460, SeasonReward.MEDAL, 50),
    )

    fun of(date: LocalDate): Season {
        val n = Math.floorDiv(ChronoUnit.DAYS.between(EPOCH, date), DAYS.toLong()).toInt()
        return Season(n + 1, EPOCH.plusDays(n * DAYS.toLong()))
    }

    /** 시즌 안에, [at]까지 받은 포인트. 시즌 보상 자체는 넣지 않는다(보상이 보상을 부르지 않게). */
    fun xp(season: Season, entries: List<RewardEntry>, at: LocalDateTime): Int = entries
        .filter { it.kind != RewardKind.SEASON_STEP && !it.at.isAfter(at) }
        .filter { val d = it.at.toLocalDate(); !d.isBefore(season.start) && !d.isAfter(season.end) }
        .sumOf { it.points }

    /** 이번 시즌에 새로 넘은 단계의 보상. */
    fun rewards(at: LocalDateTime, entries: List<RewardEntry>): List<RewardEntry> {
        val season = of(at.toLocalDate())
        val xp = xp(season, entries, at)
        val got = entries.filter { it.kind == RewardKind.SEASON_STEP }.mapNotNull { it.eventId }.toSet()
        return steps.withIndex()
            .filter { (i, s) -> xp >= s.xp && "${season.id}-${i + 1}" !in got }
            .map { (i, s) -> RewardEntry(RewardKind.SEASON_STEP, s.bonus, at, eventId = "${season.id}-${i + 1}", eventDate = season.start) }
    }
}
