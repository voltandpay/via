package com.chargecompass.core.dex

import com.chargecompass.core.model.ChargerStatus.AVAILABLE
import com.chargecompass.core.model.Connector
import com.chargecompass.core.model.Station
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DexTest {
    private fun st(id: String, region: String, kw: Int = 100) =
        Station(id, id, "op", 37.5, 127.0, kw, setOf(Connector.DC_COMBO), AVAILABLE, 0.0, 0.0, region)

    @Test
    fun `출력으로 타입을 정한다 - 200kW 이상 초급속, 50kW 이상 급속, 그 아래 완속`() {
        assertEquals(PowerType.ULTRA, PowerType.of(200))
        assertEquals(PowerType.ULTRA, PowerType.of(350))
        assertEquals(PowerType.FAST, PowerType.of(100))
        assertEquals(PowerType.FAST, PowerType.of(50))
        assertEquals(PowerType.SLOW, PowerType.of(7))
    }

    @Test
    fun `충전 횟수로 메달 단계 - 1번 동, 3번 은, 5번 금`() {
        assertNull(MedalTier.of(0))
        assertEquals(MedalTier.BRONZE, MedalTier.of(1))
        assertEquals(MedalTier.BRONZE, MedalTier.of(2))
        assertEquals(MedalTier.SILVER, MedalTier.of(3))
        assertEquals(MedalTier.GOLD, MedalTier.of(5))
        assertEquals(MedalTier.GOLD, MedalTier.of(9))
    }

    @Test
    fun `다음 단계까지 남은 충전 횟수(금이면 null)`() {
        assertEquals(1, MedalTier.visitsToNext(0))
        assertEquals(2, MedalTier.visitsToNext(1))
        assertEquals(1, MedalTier.visitsToNext(4))
        assertNull(MedalTier.visitsToNext(5))
    }

    @Test
    fun `도감은 지역 순서대로 번호를 매기고 충전했으면 등록, 추천에 나왔으면 발견`() {
        val stations = listOf(st("b1", "gangnam"), st("d1", "downtown"), st("g1", "gyeonggi"), st("d2", "downtown"))
        val dex = Dex.build(stations, seen = setOf("d2", "g1"), visits = mapOf("g1" to 3))
        assertEquals(listOf("d1", "d2", "b1", "g1"), dex.map { it.station.id })
        assertEquals(listOf(1, 2, 3, 4), dex.map { it.no })
        assertEquals(listOf(DexState.UNSEEN, DexState.SEEN, DexState.UNSEEN, DexState.REGISTERED), dex.map { it.state })
        assertEquals(MedalTier.SILVER, dex.last().tier)
    }

    @Test
    fun `충전한 곳은 추천 기록이 없어도 등록`() {
        val dex = Dex.build(listOf(st("d1", "downtown")), seen = emptySet(), visits = mapOf("d1" to 1))
        assertEquals(DexState.REGISTERED, dex.single().state)
    }

    @Test
    fun `지역별 진행률`() {
        val stations = listOf(st("d1", "downtown"), st("d2", "downtown"), st("b1", "gangnam"))
        val dex = Dex.build(stations, seen = emptySet(), visits = mapOf("d1" to 1, "b1" to 2))
        assertEquals(1 to 2, Dex.progress(dex, "downtown"))
        assertEquals(1 to 1, Dex.progress(dex, "gangnam"))
    }

    @Test
    fun `도감 번호 표기는 세 자리`() {
        assertEquals("No.007", Dex.label(7))
    }

    @Test
    fun `지역 진행률의 전체 수에서 고장·점검 중인 충전소는 뺀다(이미 등록했으면 센다)`() {
        val broken = st("b1", "gangnam").copy(status = com.chargecompass.core.model.ChargerStatus.BROKEN)
        val dex = Dex.build(listOf(st("a1", "gangnam"), broken), seen = emptySet(), visits = mapOf("a1" to 1))
        assertEquals(1 to 1, Dex.progress(dex, "gangnam"))
        val dex2 = Dex.build(listOf(st("a1", "gangnam"), broken), seen = emptySet(), visits = mapOf("a1" to 1, "b1" to 1))
        assertEquals(2 to 2, Dex.progress(dex2, "gangnam"))
    }
}
