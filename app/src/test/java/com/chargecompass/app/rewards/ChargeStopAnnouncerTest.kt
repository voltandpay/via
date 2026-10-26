package com.chargecompass.app.rewards

import com.chargecompass.core.charge.ChargeStopAdvice
import com.chargecompass.core.session.ChargeSession
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChargeStopAnnouncerTest {
    private val t0 = LocalDateTime.of(2026, 10, 26, 18, 0)
    private val advice = ChargeStopAdvice(enoughSoc = 55, minutesToEnough = 9, arrivalSoc = 15, extraMinutes = 12, extraWon = 3_750, winter = false)
    private fun session(at: LocalDateTime = t0, startSoc: Int? = 30) = ChargeSession("st", at, null, startSoc, null)

    @Test
    fun `목적지까지 충분히 차면 충전 1회에 한 번만 말한다`() {
        val a = ChargeStopAnnouncer()
        assertNull(a.onUpdate(session(), 54, "서울역", advice))
        assertEquals(
            "지금 충전을 끝내도 서울역에 15%로 도착해요. 80%까지 채우면 12분, 약 3,800원 더 들어요.",
            a.onUpdate(session(), 55, "서울역", advice),
        )
        assertNull(a.onUpdate(session(), 60, "서울역", advice))
        // 다음 충전에서는 다시 말한다
        assertEquals(true, a.onUpdate(session(t0.plusHours(3)), 56, "서울역", advice) != null)
    }

    @Test
    fun `이미 충분한 상태로 꽂았거나 알려 줄 것이 없으면 말하지 않는다`() {
        val a = ChargeStopAnnouncer()
        assertNull(a.onUpdate(session(startSoc = 60), 61, "서울역", advice))
        assertNull(a.onUpdate(session(), 70, "서울역", null))
    }
}
