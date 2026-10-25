package com.chargecompass.core.pricing

import com.chargecompass.core.energy.EnergyState
import com.chargecompass.core.model.ChargerStatus
import com.chargecompass.core.model.Connector
import com.chargecompass.core.model.Station
import com.chargecompass.core.model.Tariff
import com.chargecompass.core.model.VehicleModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StationRankerTest {
    private val car = VehicleModel("ioniq5", "아이오닉 5", 77.4, 5.0, setOf(Connector.DC_COMBO), emptyList())
    private val state = EnergyState(30, 5.0)

    private val tariffA = Tariff("opA", memberWonPerKwh = 300, roamingWonPerKwh = 400, nonMemberWonPerKwh = 450, membershipIds = setOf("memA"))
    private val tariffB = Tariff("opB", memberWonPerKwh = 320, roamingWonPerKwh = null, nonMemberWonPerKwh = 450, membershipIds = setOf("memB"))
    private val tariffs = mapOf("opA" to tariffA, "opB" to tariffB)

    private fun station(
        id: String, op: String, kw: Int = 100, status: ChargerStatus = ChargerStatus.AVAILABLE,
        connectors: Set<Connector> = setOf(Connector.DC_COMBO), along: Double = 10.0, detour: Double = 0.5,
    ) = Station(id, id, op, 37.4, 127.1, kw, connectors, status, along, detour)

    @Test
    fun `멤버십이 있으면 회원가`() {
        val p = StationRanker.effectivePrice(tariffA, setOf("memA"))
        assertEquals(EffectivePrice(300, PriceBasis.MEMBER, "memA"), p)
    }

    @Test
    fun `이 사업자 멤버십은 없지만 다른 카드가 있고 로밍가가 더 싸면 로밍가`() {
        val p = StationRanker.effectivePrice(tariffA, setOf("memB"))
        assertEquals(400, p.wonPerKwh)
        assertEquals(PriceBasis.ROAMING, p.basis)
        assertNull(p.membershipId)
    }

    @Test
    fun `카드가 하나도 없으면 로밍가를 못 받으니 비회원가`() {
        assertEquals(EffectivePrice(450, PriceBasis.NON_MEMBER, null), StationRanker.effectivePrice(tariffA, emptySet()))
    }

    @Test
    fun `멤버십도 로밍가도 없으면 비회원가`() {
        val p = StationRanker.effectivePrice(tariffB, setOf("memA"))
        assertEquals(EffectivePrice(450, PriceBasis.NON_MEMBER, null), p)
    }

    @Test
    fun `내 멤버십 기준으로 더 싼 충전소가 1위이고 절약액이 양수`() {
        val ranked = StationRanker.rank(listOf(station("B", "opB"), station("A", "opA")), tariffs, setOf("memA"), state, car)
        assertEquals("A", ranked.first().station.id)
        assertTrue(ranked.first().savingWon > 0)
    }

    @Test
    fun `충전량 비용 우회 비용 절약액을 원 단위로 계산한다`() {
        val r = StationRanker.rank(listOf(station("A", "opA")), tariffs, setOf("memA"), state, car).single()
        // 도착 SoC = 30 − (10.5/5)/77.4×100 = 27.287%, 충전량 = (80 − 27.287)×0.774 = 40.80 kWh
        assertEquals(27.287, r.arrivalSocPct, 0.001)
        assertEquals(40.80, r.chargeKwh, 0.01)
        // 총비용 = 40.80×300 + 0.5/5×300 = 12,240 + 30 = 12,270원, 비회원 = 40.80×450 + 0.5/5×450 = 18,405원
        assertEquals(12270, r.totalCostWon)
        assertEquals(18405, r.nonMemberCostWon)
        assertEquals(18405 - 12270, r.savingWon)
    }

    @Test
    fun `사용 가능하지 않은 충전소는 뺀다`() {
        val stations = listOf(ChargerStatus.IN_USE, ChargerStatus.BROKEN, ChargerStatus.MAINTENANCE, ChargerStatus.UNKNOWN)
            .map { station(it.name, "opA", status = it) } + station("ok", "opA")
        assertEquals(listOf("ok"), StationRanker.rank(stations, tariffs, emptySet(), state, car).map { it.station.id })
    }

    @Test
    fun `내 차 커넥터와 안 맞으면 뺀다`() {
        val ranked = StationRanker.rank(listOf(station("chademo", "opA", connectors = setOf(Connector.CHADEMO))), tariffs, emptySet(), state, car)
        assertTrue(ranked.isEmpty())
    }

    @Test
    fun `요금표가 없거나 도착 전에 방전되면 뺀다`() {
        val far = station("far", "opA", along = 200.0)          // 30% − 200/5/77.4 → 음수
        val noTariff = station("x", "opX")
        assertTrue(StationRanker.rank(listOf(far, noTariff), tariffs, emptySet(), state, car).isEmpty())
    }

    @Test
    fun `총비용이 같으면 충전 속도가 빠른 곳이 앞`() {
        val ranked = StationRanker.rank(listOf(station("slow", "opA", kw = 100), station("fast", "opA", kw = 200)), tariffs, emptySet(), state, car)
        assertEquals(listOf("fast", "slow"), ranked.map { it.station.id })
    }

    @Test
    fun `후보가 모두 빠지면 빈 리스트`() {
        val ranked = StationRanker.rank(listOf(station("x", "opA", status = ChargerStatus.BROKEN)), tariffs, emptySet(), state, car)
        assertTrue(ranked.isEmpty())
    }
}
