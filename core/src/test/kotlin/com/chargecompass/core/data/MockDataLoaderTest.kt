package com.chargecompass.core.data

import com.chargecompass.core.model.ChargerStatus
import com.chargecompass.core.model.ChargerStatus.AVAILABLE
import com.chargecompass.core.model.Connector
import com.chargecompass.core.model.Station
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MockDataLoaderTest {
    private val data = MockDataLoader.load { path ->
        checkNotNull(javaClass.classLoader.getResource(path)) { "리소스 없음: $path" }.readText()
    }

    @Test
    fun `모든 충전소의 사업자 요금표가 있다`() {
        assertTrue(data.stations.isNotEmpty())
        assertTrue(data.stations.all { it.operatorId in data.tariffs })
        assertTrue(data.tariffs.size >= 10)
    }

    @Test
    fun `시연 경로에 고장과 사용 중 충전소가 하나 이상씩 있다`() {
        assertTrue(data.stations.size >= 8)
        assertTrue(data.stations.any { it.status == ChargerStatus.BROKEN })
        assertTrue(data.stations.any { it.status == ChargerStatus.IN_USE })
        val ioniq5 = data.vehicles.first { it.id == "ioniq5" }
        assertTrue(data.stations.any { it.status == AVAILABLE && it.connectors.none { c -> c in ioniq5.connectors } })
    }

    @Test
    fun `이벤트 충전소는 실제 충전소다`() {
        assertTrue(data.events.isNotEmpty())
        val ids = data.stations.map { it.id }.toSet()
        assertTrue(data.events.all { it.stationId in ids })
    }

    @Test
    fun `멤버십과 차종을 읽는다`() {
        assertTrue(data.vehicles.size >= 4)
        assertTrue(data.memberships.all { m -> data.tariffs.values.any { m.id in it.membershipIds } })
    }

    @Test
    fun `반경 100m 안의 가장 가까운 충전소를 찾는다`() {
        val st = Station("a", "a", "op", 37.5000, 127.0000, 100, setOf(Connector.DC_COMBO), AVAILABLE, 0.0, 0.0)
        val far = st.copy(id = "b", lat = 37.5100)
        // 위도 0.00045도 ≈ 50m, 0.00135도 ≈ 150m
        assertEquals("a", nearestStation(listOf(far, st), 37.50045, 127.0))
        assertNull(nearestStation(listOf(st), 37.50135, 127.0))
    }
}
