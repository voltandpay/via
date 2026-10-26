package com.chargecompass.core.trip

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

class TripPlannerTest {
    // 50kWh, 5km/kWh → 100%로 250km, 1km에 0.4%p
    private val car = VehicleModel("t", "테스트", 50.0, 5.0, setOf(Connector.DC_COMBO), emptyList())
    private fun st(id: String, km: Double, op: String = "a", status: ChargerStatus = ChargerStatus.AVAILABLE) =
        Station(id, id, op, 0.0, 0.0, 200, setOf(Connector.DC_COMBO), status, km, 0.0)
    private val tariffs = mapOf("a" to Tariff("a", 300, null, 400, setOf("m-a")), "b" to Tariff("b", 250, null, 300, setOf("m-b")))

    @Test
    fun `한 번 충전으로 안 되면 멀리 갈 수 있는 곳부터 골라 여러 번 나눠 충전한다`() {
        val stations = listOf(st("s50", 50.0), st("s100", 100.0), st("s140", 140.0), st("s200", 200.0), st("s250", 250.0), st("s300", 300.0), st("s350", 350.0))
        val plan = TripPlanner.plan(400.0, EnergyState(60, null), car, stations, tariffs, owned = setOf("m-a"))!!
        assertEquals(listOf("s100", "s250"), plan.stops.map { it.station.id })
        assertEquals(20, plan.stops[0].arrivalSoc)
        assertEquals(80, plan.stops[0].chargeToSoc)
        // 마지막 충전은 목적지에 15%로 도착할 만큼만(250km 지점 → 150km 남음 = 60%p + 15%)
        assertEquals(75, plan.stops[1].chargeToSoc)
        assertEquals(15, plan.arrivalSoc)
        // 충전량·비용: 첫 번째 60%p × 50kWh = 30kWh × 300원
        assertEquals(30.0, plan.stops[0].kwh, 1e-9)
        assertEquals(9_000, plan.stops[0].costWon)
    }

    @Test
    fun `갈 수 있는 먼 구간 안에서는 더 싼 곳을 고른다`() {
        val stations = listOf(st("s100", 100.0), st("s230", 230.0, op = "b"), st("s250", 250.0))
        val plan = TripPlanner.plan(400.0, EnergyState(60, null), car, stations, tariffs, owned = setOf("m-a", "m-b"))!!
        assertEquals("s230", plan.stops[1].station.id)
    }

    @Test
    fun `고장·사용 중인 곳은 빼고, 닿을 수 있는 곳이 없으면 계획을 못 세운다`() {
        val plan = TripPlanner.plan(400.0, EnergyState(60, null), car, listOf(st("s100", 100.0, status = ChargerStatus.BROKEN), st("s120", 120.0)), tariffs, setOf("m-a"))
        assertNull(plan)   // 120km에서 80%로 충전해도 280km 남아 목적지까지 못 간다
    }

    @Test
    fun `충전 없이 갈 수 있으면 정류 0번`() {
        val plan = TripPlanner.plan(100.0, EnergyState(80, null), car, listOf(st("s50", 50.0)), tariffs, setOf("m-a"))!!
        assertTrue(plan.stops.isEmpty())
        assertEquals(40, plan.arrivalSoc)
    }
}
