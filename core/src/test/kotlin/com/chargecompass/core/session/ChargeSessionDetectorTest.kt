package com.chargecompass.core.session

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ChargeSessionDetectorTest {
    private val t0 = LocalDateTime.of(2026, 11, 3, 23, 0)
    private fun s(sec: Long, charging: Boolean, soc: Int? = 30, lat: Double? = 37.48, lng: Double? = 127.03) =
        ChargingSample(t0.plusSeconds(sec), charging, soc, lat, lng)

    private fun detector() = ChargeSessionDetector({ lat, _ -> if (lat > 37.0) "st-1" else null })

    @Test
    fun `충전이 시작되면 Started 한 번`() {
        val d = detector()
        assertNull(d.onSample(s(0, false)))
        val e = d.onSample(s(5, true, soc = 30))
        assertIs<SessionEvent.Started>(e)
        assertEquals(ChargeSession("st-1", t0.plusSeconds(5), null, 30, null), e.session)
        assertNull(d.onSample(s(10, true, soc = 31)))
    }

    @Test
    fun `60초 안에 다시 충전되면 같은 세션`() {
        val d = detector()
        d.onSample(s(0, true))
        assertNull(d.onSample(s(5, false)))
        assertNull(d.onSample(s(35, false)))
        assertNull(d.onSample(s(36, true)))
        assertNull(d.onSample(s(40, true)))
    }

    @Test
    fun `60초 넘게 끊기면 Ended 한 번`() {
        val d = detector()
        d.onSample(s(0, true, soc = 30))
        d.onSample(s(100, true, soc = 55))
        assertNull(d.onSample(s(105, false, soc = 55)))
        val e = d.onSample(s(170, false, soc = 55))
        assertIs<SessionEvent.Ended>(e)
        assertEquals(ChargeSession("st-1", t0, t0.plusSeconds(105), 30, 55), e.session)
        assertNull(d.onSample(s(200, false)))
        assertIs<SessionEvent.Started>(d.onSample(s(300, true)))
    }

    @Test
    fun `좌표가 없으면 충전소 없이 시작`() {
        val e = detector().onSample(s(0, true, lat = null, lng = null))
        assertIs<SessionEvent.Started>(e)
        assertNull(e.session.stationId)
    }

    @Test
    fun `시작 때 위치가 없었으면 위치가 잡히는 샘플에서 충전소를 붙여 다시 Started`() {
        val d = detector()
        val first = d.onSample(s(0, true, lat = null, lng = null))
        assertIs<SessionEvent.Started>(first)
        assertNull(first.session.stationId)
        assertNull(d.onSample(s(5, true, lat = null, lng = null)))
        val matched = d.onSample(s(10, true))
        assertIs<SessionEvent.Started>(matched)
        assertEquals(ChargeSession("st-1", t0, null, 30, null), matched.session)
        assertNull(d.onSample(s(15, true)))
    }
}
