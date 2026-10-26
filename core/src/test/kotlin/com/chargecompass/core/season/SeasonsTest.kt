package com.chargecompass.core.season

import com.chargecompass.core.rewards.RewardEntry
import com.chargecompass.core.rewards.RewardKind
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SeasonsTest {
    private fun e(kind: RewardKind, points: Int, at: LocalDateTime, eventId: String? = null) = RewardEntry(kind, points, at, "st", eventId)

    @Test
    fun `시즌은 2026-10-05부터 4주씩 이어진다`() {
        val s1 = Seasons.of(LocalDate.of(2026, 10, 26))
        assertEquals(1, s1.number)
        assertEquals(LocalDate.of(2026, 10, 5), s1.start)
        assertEquals(LocalDate.of(2026, 11, 1), s1.end)
        // 2차 PT(12/4)는 시즌 3
        val s3 = Seasons.of(LocalDate.of(2026, 12, 4))
        assertEquals(3, s3.number)
        assertEquals(LocalDate.of(2026, 11, 30), s3.start)
    }

    @Test
    fun `시즌 경험치는 시즌 안에 받은 포인트(시즌 보상 자체는 빼고)`() {
        val at = LocalDateTime.of(2026, 10, 20, 12, 0)
        val entries = listOf(
            e(RewardKind.CHARGE, 10, at), e(RewardKind.NEW_STAMP, 20, at),
            e(RewardKind.CHARGE, 10, LocalDateTime.of(2026, 10, 2, 12, 0)), // 지난 시즌
            e(RewardKind.SEASON_STEP, 10, at, "s1-1"),                          // 시즌 보상은 경험치에 넣지 않는다
        )
        assertEquals(30, Seasons.xp(Seasons.of(at.toLocalDate()), entries, at))
    }

    @Test
    fun `새로 넘은 단계만 한 번 보상한다`() {
        val at = LocalDateTime.of(2026, 10, 20, 12, 0)
        val earned = listOf(e(RewardKind.CHARGE, 10, at), e(RewardKind.NEW_STAMP, 20, at), e(RewardKind.MISSION, 15, at), e(RewardKind.EVENT_BONUS, 10, at))
        // 경험치 55 → 1단계(50) 달성
        val first = Seasons.rewards(at, earned)
        assertEquals(listOf("s1-1"), first.map { it.eventId })
        assertEquals(Seasons.steps[0].bonus, first.single().points)
        assertTrue(Seasons.rewards(at, earned + first).isEmpty())
    }

    @Test
    fun `시즌 단계는 경험치 순서대로 커지고 마지막은 시즌 메달`() {
        val t = Seasons.steps.map { it.xp }
        assertEquals(t.sorted(), t)
        assertEquals(SeasonReward.MEDAL, Seasons.steps.last().reward)
    }
}
