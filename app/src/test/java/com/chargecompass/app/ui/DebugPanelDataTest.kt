package com.chargecompass.app.ui

import com.chargecompass.core.rewards.RewardKind
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class DebugPanelDataTest {
    private val now = LocalDateTime.of(2026, 11, 4, 12, 0)

    @Test
    fun `지난 미션 예시 기록을 두 번 넣어도 같은 주 같은 미션은 한 번만`() {
        val first = demoPastMissions(now, emptyList())
        assertEquals(6, first.size)
        assertEquals(0, demoPastMissions(now, first).size)
        assertEquals(RewardKind.MISSION, first.first().kind)
    }
}
