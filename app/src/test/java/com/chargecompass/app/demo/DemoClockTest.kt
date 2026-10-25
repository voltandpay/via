package com.chargecompass.app.demo

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class DemoClockTest {
    @Test
    fun `기본은 실제 시각을 따른다`() {
        var real = LocalDateTime.of(2026, 12, 3, 14, 0)
        val clock = DemoClock { real }
        assertEquals(real, clock.now())
        real = real.plusMinutes(5)
        assertEquals(real, clock.now())
    }

    @Test
    fun `시각을 정하면 그때부터 흐른 만큼 더해진다`() {
        var real = LocalDateTime.of(2026, 12, 3, 14, 0)
        val clock = DemoClock { real }
        clock.jumpTo(LocalDateTime.of(2026, 12, 3, 23, 30))
        real = real.plusMinutes(3)
        assertEquals(LocalDateTime.of(2026, 12, 3, 23, 33), clock.now())
        clock.reset()
        assertEquals(real, clock.now())
    }
}
