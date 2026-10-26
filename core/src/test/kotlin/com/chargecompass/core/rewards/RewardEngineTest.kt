package com.chargecompass.core.rewards

import com.chargecompass.core.model.Connector
import com.chargecompass.core.model.RewardEvent
import com.chargecompass.core.model.VehicleModel
import com.chargecompass.core.session.ChargeSession
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RewardEngineTest {
    private val car = VehicleModel("ioniq5", "아이오닉 5", 77.4, 5.0, setOf(Connector.DC_COMBO), emptyList())
    private val now = LocalDateTime.of(2026, 11, 3, 23, 30)
    private val event = RewardEvent("night2x", "st-event", "22시~02시 포인트 2배", 22, 2, 2)

    private class History(
        val stamps: Set<String> = emptySet(),
        val bonusDates: Map<String, Set<LocalDate>> = emptyMap(),
    ) : RewardHistory {
        override fun stampedStationIds() = stamps
        override fun eventBonusDates(eventId: String) = bonusDates[eventId].orEmpty()
    }

    private fun session(stationId: String?, at: LocalDateTime) = ChargeSession(stationId, at, null, 30, null)

    @Test
    fun `10km 이상 기본 전비 이상이면 효율 운전 10점`() {
        val r = RewardEngine.onTripEnded(12.0, 5.5, car, now)
        assertEquals(listOf(RewardEntry(RewardKind.EFFICIENT_TRIP, 10, now)), r)
    }

    @Test
    fun `짧거나 전비가 낮거나 전비를 모르면 효율 운전 보상 없음`() {
        assertTrue(RewardEngine.onTripEnded(9.0, 5.5, car, now).isEmpty())
        assertTrue(RewardEngine.onTripEnded(12.0, 4.9, car, now).isEmpty())
        assertTrue(RewardEngine.onTripEnded(12.0, null, car, now).isEmpty())
    }

    @Test
    fun `처음 충전한 충전소면 충전 10점과 스탬프 20점`() {
        val day = now.withHour(14) // 경부하 시간대 밖
        val r = RewardEngine.onChargeStarted(session("st-1", day), emptyList(), History())
        assertEquals(
            listOf(RewardEntry(RewardKind.CHARGE, 10, day, "st-1"), RewardEntry(RewardKind.NEW_STAMP, 20, day, "st-1")),
            r,
        )
    }

    @Test
    fun `이미 스탬프가 있으면 충전 점수만`() {
        val r = RewardEngine.onChargeStarted(session("st-1", now.withHour(14)), emptyList(), History(stamps = setOf("st-1")))
        assertEquals(listOf(RewardKind.CHARGE), r.map { it.kind })
    }

    @Test
    fun `이벤트 시간대에 충전하면 보너스 10점`() {
        val r = RewardEngine.onChargeStarted(session("st-event", now), listOf(event), History(stamps = setOf("st-event")))
        assertEquals(RewardEntry(RewardKind.EVENT_BONUS, 10, now, "st-event", "night2x", LocalDate.of(2026, 11, 3)), r.single { it.kind == RewardKind.EVENT_BONUS })
    }

    @Test
    fun `자정을 넘긴 01시 충전도 보너스이고 전날 회차로 센다`() {
        val oneAm = LocalDateTime.of(2026, 11, 4, 1, 0)
        val r = RewardEngine.onChargeStarted(session("st-event", oneAm), listOf(event), History(stamps = setOf("st-event")))
        assertEquals(LocalDate.of(2026, 11, 3), r.single { it.kind == RewardKind.EVENT_BONUS }.eventDate)
        // 같은 회차(11/3)에 이미 받았으면 없음
        val again = RewardEngine.onChargeStarted(
            session("st-event", oneAm), listOf(event),
            History(stamps = setOf("st-event"), bonusDates = mapOf("night2x" to setOf(LocalDate.of(2026, 11, 3)))),
        )
        assertEquals(0, again.count { it.kind == RewardKind.EVENT_BONUS })
    }

    @Test
    fun `이벤트 시간 밖이나 다른 충전소면 보너스 없음`() {
        val before = LocalDateTime.of(2026, 11, 3, 21, 59)
        val twoAm = LocalDateTime.of(2026, 11, 4, 2, 0)
        assertEquals(0, RewardEngine.onChargeStarted(session("st-event", before), listOf(event), History()).count { it.kind == RewardKind.EVENT_BONUS })
        assertEquals(0, RewardEngine.onChargeStarted(session("st-event", twoAm), listOf(event), History()).count { it.kind == RewardKind.EVENT_BONUS })
        assertEquals(0, RewardEngine.onChargeStarted(session("st-1", now), listOf(event), History()).count { it.kind == RewardKind.EVENT_BONUS })
    }

    @Test
    fun `같은 회차에 이미 보너스를 받았으면 다시 주지 않는다`() {
        val h = History(stamps = setOf("st-event"), bonusDates = mapOf("night2x" to setOf(LocalDate.of(2026, 11, 3))))
        assertEquals(0, RewardEngine.onChargeStarted(session("st-event", now), listOf(event), h).count { it.kind == RewardKind.EVENT_BONUS })
    }

    @Test
    fun `충전소를 못 찾았으면 아무것도 주지 않는다`() {
        assertTrue(RewardEngine.onChargeStarted(session(null, now), listOf(event), History()).isEmpty())
    }

    @Test
    fun `이벤트 회차 날짜 계산`() {
        assertEquals(LocalDate.of(2026, 11, 3), RewardEngine.eventOccurrenceDate(event, LocalDateTime.of(2026, 11, 4, 1, 59)))
        assertEquals(LocalDate.of(2026, 11, 3), RewardEngine.eventOccurrenceDate(event, LocalDateTime.of(2026, 11, 3, 22, 0)))
        assertEquals(null, RewardEngine.eventOccurrenceDate(event, LocalDateTime.of(2026, 11, 3, 12, 0)))
        val day = RewardEvent("day", "s", "낮", 10, 14, 2)
        assertEquals(LocalDate.of(2026, 11, 3), RewardEngine.eventOccurrenceDate(day, LocalDateTime.of(2026, 11, 3, 13, 59)))
        assertEquals(null, RewardEngine.eventOccurrenceDate(day, LocalDateTime.of(2026, 11, 3, 14, 0)))
    }

    private fun station(id: String, region: String) = com.chargecompass.core.model.Station(
        id, id, "op", 37.5, 127.0, 100, setOf(Connector.DC_COMBO), com.chargecompass.core.model.ChargerStatus.AVAILABLE, 0.0, 0.0, region)

    @Test
    fun `지역의 마지막 충전소에 처음 충전하면 지역 완성 보너스`() {
        val stations = listOf(station("a", "downtown"), station("b", "downtown"), station("c", "gangnam"))
        val r = RewardEngine.onChargeStarted(session("b", now.withHour(12)), emptyList(), History(stamps = setOf("a")), stations)
        val bonus = r.single { it.kind == RewardKind.REGION_COMPLETE }
        assertEquals(50, bonus.points)
        assertEquals("downtown", bonus.regionId)
    }

    @Test
    fun `지역이 덜 찼거나 이미 스탬프가 있으면 지역 완성 보너스 없음`() {
        val stations = listOf(station("a", "downtown"), station("b", "downtown"), station("c", "downtown"))
        val notYet = RewardEngine.onChargeStarted(session("b", now.withHour(12)), emptyList(), History(stamps = setOf("a")), stations)
        assertTrue(notYet.none { it.kind == RewardKind.REGION_COMPLETE })
        // 다 모은 지역에서 다시 충전해도 또 주지 않는다
        val again = RewardEngine.onChargeStarted(session("b", now.withHour(12)), emptyList(), History(stamps = setOf("a", "b", "c")), stations)
        assertTrue(again.none { it.kind == RewardKind.REGION_COMPLETE })
    }

    @Test
    fun `경부하 시간대(23~9시)에 충전을 시작하면 부스트 보너스`() {
        val stations = listOf(station("a", "downtown"))
        val night = RewardEngine.onChargeStarted(session("a", now.withHour(23).withMinute(10)), emptyList(), History(stamps = setOf("a")), stations)
        assertEquals(10, night.single { it.kind == RewardKind.OFF_PEAK_BOOST }.points)
        val morning = RewardEngine.onChargeStarted(session("a", now.withHour(8).withMinute(59)), emptyList(), History(stamps = setOf("a")), stations)
        assertTrue(morning.any { it.kind == RewardKind.OFF_PEAK_BOOST })
        val noon = RewardEngine.onChargeStarted(session("a", now.withHour(9)), emptyList(), History(stamps = setOf("a")), stations)
        assertTrue(noon.none { it.kind == RewardKind.OFF_PEAK_BOOST })
    }

    @Test
    fun `사업자 데이에 그 사업자에서 충전하면 포인트 3배(보너스 20)와 한정 메달 기록`() {
        val st = station("a", "downtown").copy(operatorId = "chaevi")
        val day = com.chargecompass.core.model.OperatorDay("chaevi-day-1114", "chaevi", LocalDate.of(2026, 11, 14), 3, "채비 데이")
        val r = RewardEngine.onChargeStarted(session("a", LocalDateTime.of(2026, 11, 14, 12, 0)), emptyList(), History(stamps = setOf("a")), listOf(st), listOf(day))
        val bonus = r.single { it.kind == RewardKind.OPERATOR_DAY }
        assertEquals(20, bonus.points)
        assertEquals("chaevi-day-1114", bonus.eventId)
        // 다른 날이나 다른 사업자는 없음
        val other = RewardEngine.onChargeStarted(session("a", LocalDateTime.of(2026, 11, 15, 12, 0)), emptyList(), History(stamps = setOf("a")), listOf(st), listOf(day))
        assertTrue(other.none { it.kind == RewardKind.OPERATOR_DAY })
    }
}
