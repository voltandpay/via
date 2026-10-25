package com.chargecompass.app.rewards

import com.chargecompass.core.data.MockDataLoader
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EventAnnouncerTest {
    private val data = MockDataLoader.load { javaClass.classLoader.getResource(it)!!.readText() }

    @Test
    fun `출발할 때 오늘의 이벤트를 하루 한 번만 알린다`() {
        val a = EventAnnouncer(data.events, data.stations)
        val morning = LocalDateTime.of(2026, 11, 3, 8, 0)
        assertEquals("오늘 밤 22시부터 2시까지 양재 채비 충전소에서 충전하면 포인트 2배예요", a.onDepart(morning))
        assertNull(a.onDepart(morning.plusHours(3)))
        assertEquals("오늘 밤 22시부터 2시까지 양재 채비 충전소에서 충전하면 포인트 2배예요", a.onDepart(morning.plusDays(1)))
    }

    @Test
    fun `이벤트 시간 안에 출발하면 지금 진행 중이라고 알린다`() {
        val a = EventAnnouncer(data.events, data.stations)
        assertEquals("지금 양재 채비 충전소에서 충전하면 포인트 2배예요 · 2시까지", a.onDepart(LocalDateTime.of(2026, 11, 3, 23, 10)))
        val b = EventAnnouncer(data.events, data.stations)
        assertEquals("지금 양재 채비 충전소에서 충전하면 포인트 2배예요 · 2시까지", b.onDepart(LocalDateTime.of(2026, 11, 4, 1, 0)))
    }

    @Test
    fun `이벤트가 없으면 말하지 않는다`() {
        assertNull(EventAnnouncer(emptyList(), data.stations).onDepart(LocalDateTime.of(2026, 11, 3, 8, 0)))
    }
}
