package com.chargecompass.core.energy

import com.chargecompass.core.model.Connector
import com.chargecompass.core.model.VehicleModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArrivalCalculatorTest {
    private val car = VehicleModel("ioniq5", "아이오닉 5", 77.4, 5.0, setOf(Connector.DC_COMBO), emptyList())

    @Test
    fun `40퍼센트로 100km 가면 약 14_16퍼센트가 남아 충전이 필요하다`() {
        val soc = ArrivalCalculator.socAfter(EnergyState(40, 5.0), car, 100.0)
        assertNotNull(soc)
        assertEquals(14.16, soc, 0.01)
        assertTrue(ArrivalCalculator.needsCharge(soc))
    }

    @Test
    fun `80퍼센트로 23km 가면 약 74_06퍼센트라 충전이 필요 없다`() {
        val soc = ArrivalCalculator.socAfter(EnergyState(80, 5.0), car, 23.0)!!
        assertEquals(74.06, soc, 0.01)
        assertFalse(ArrivalCalculator.needsCharge(soc))
    }

    @Test
    fun `전비가 없거나 0 이하면 차종 기본 전비를 쓴다`() {
        val base = ArrivalCalculator.socAfter(EnergyState(40, 5.0), car, 100.0)!!
        assertEquals(base, ArrivalCalculator.socAfter(EnergyState(40, null), car, 100.0)!!, 1e-9)
        assertEquals(base, ArrivalCalculator.socAfter(EnergyState(40, 0.0), car, 100.0)!!, 1e-9)
        assertEquals(base, ArrivalCalculator.socAfter(EnergyState(40, -1.0), car, 100.0)!!, 1e-9)
    }

    @Test
    fun `SoC를 모르면 계산하지 않고 충전 필요로 보지 않는다`() {
        assertNull(ArrivalCalculator.socAfter(EnergyState(null, 5.0), car, 100.0))
        assertFalse(ArrivalCalculator.needsCharge(null))
    }

    @Test
    fun `도착 전에 방전되면 음수이고 충전이 필요하다`() {
        val soc = ArrivalCalculator.socAfter(EnergyState(5, 5.0), car, 300.0)!!
        assertTrue(soc < 0)
        assertTrue(ArrivalCalculator.needsCharge(soc))
    }
}
