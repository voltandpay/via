package com.chargecompass.core.missions

import com.chargecompass.core.rewards.RewardEntry
import com.chargecompass.core.rewards.RewardKind
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MissionsTest {
    // 2026-11-04는 수요일 → 그 주 월요일은 11-02
    private val wed = LocalDateTime.of(2026, 11, 4, 12, 0)
    private fun e(kind: RewardKind, at: LocalDateTime, eventId: String? = null, eventDate: LocalDate? = null) =
        RewardEntry(kind, 10, at, "st", eventId, eventDate)

    @Test
    fun `주는 월요일에 시작한다`() {
        assertEquals(LocalDate.of(2026, 11, 2), Missions.weekStart(LocalDate.of(2026, 11, 4)))
        assertEquals(LocalDate.of(2026, 11, 2), Missions.weekStart(LocalDate.of(2026, 11, 2)))
        assertEquals(LocalDate.of(2026, 11, 2), Missions.weekStart(LocalDate.of(2026, 11, 8)))
    }

    @Test
    fun `이번 주 기록만 센다 - 할인 충전, 효율 운전, 새 충전소`() {
        val entries = listOf(
            e(RewardKind.EFFICIENT_TRIP, wed), e(RewardKind.EFFICIENT_TRIP, wed.minusDays(3)), // 지난주 일요일은 빠진다
            e(RewardKind.NEW_STAMP, wed.minusDays(1)),
        )
        val p = Missions.progress(wed.toLocalDate(), entries, discountCharges = 1).associate { it.mission to it.count }
        assertEquals(1, p[Mission.DISCOUNT_CHARGE])
        assertEquals(1, p[Mission.EFFICIENT_TRIP])
        assertEquals(1, p[Mission.NEW_STATION])
    }

    @Test
    fun `새로 달성한 미션만 한 번 보상한다`() {
        val entries = listOf(e(RewardKind.NEW_STAMP, wed))
        val first = Missions.rewards(wed, entries, discountCharges = 2)
        assertEquals(setOf("DISCOUNT_CHARGE", "NEW_STATION"), first.filter { it.kind == RewardKind.MISSION }.map { it.eventId }.toSet())
        assertTrue(first.all { it.kind != RewardKind.MISSION || (it.points == 15 && it.eventDate == LocalDate.of(2026, 11, 2)) })
        // 이미 받은 미션은 다시 주지 않는다
        assertTrue(Missions.rewards(wed.plusHours(1), entries + first, discountCharges = 2).isEmpty())
    }

    @Test
    fun `미션 스탬프 7개마다 리서치 돌파 보너스`() {
        // 지난 주들에 미션 6개 달성
        val past = (1..6).map { i -> e(RewardKind.MISSION, wed.minusWeeks(i.toLong()), "M$i", LocalDate.of(2026, 1, i)) }
        val r = Missions.rewards(wed, past, discountCharges = 2)
        assertEquals(1, r.count { it.kind == RewardKind.MISSION })
        assertEquals(100, r.single { it.kind == RewardKind.BREAKTHROUGH }.points)
        assertEquals(0, Missions.breakthroughStamps(past + r))
        assertEquals(6, Missions.breakthroughStamps(past))
    }

    @Test
    fun `지금보다 미래 시각의 기록(시연 시계)은 미션에 세지 않는다`() {
        val future = listOf(e(RewardKind.NEW_STAMP, wed.plusHours(9)))
        assertTrue(Missions.rewards(wed, future, discountCharges = 0).isEmpty())
    }
}
