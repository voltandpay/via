package com.chargecompass.core.charge

import com.chargecompass.core.energy.EnergyState
import com.chargecompass.core.model.Connector
import com.chargecompass.core.model.VehicleModel
import java.time.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChargeStopTest {
    // 50kWh, 5km/kWh → 1km에 0.4%p
    private val car = VehicleModel("t", "테스트", 50.0, 5.0, setOf(Connector.DC_COMBO), emptyList())

    @Test
    fun `80%까지는 출력의 85%, 그 위는 35%로 충전 시간을 잡는다`() {
        // 20→80%: 30kWh / (100kW × 0.85) = 21.2분 → 22분
        assertEquals(22, ChargeCurve.minutes(20, 80, 50.0, 100, Month.OCTOBER))
        // 80→90%: 5kWh / (100kW × 0.35) = 8.6분 → 9분
        assertEquals(9, ChargeCurve.minutes(80, 90, 50.0, 100, Month.OCTOBER))
        assertEquals(0, ChargeCurve.minutes(80, 80, 50.0, 100, Month.OCTOBER))
    }

    @Test
    fun `겨울에는 출력이 떨어져 더 오래 걸리고, 완속은 80% 위에서도 느려지지 않는다`() {
        // 30kWh / (100kW × 0.8 × 0.85) = 26.5분 → 27분
        assertEquals(27, ChargeCurve.minutes(20, 80, 50.0, 100, Month.JANUARY))
        // 완속 7kW: 80→90% 5kWh / (7 × 0.85) = 50.4분 → 51분
        assertEquals(51, ChargeCurve.minutes(80, 90, 50.0, 7, Month.OCTOBER))
    }

    @Test
    fun `목적지에 15%로 닿을 만큼만 채우면 언제 끝내도 되는지, 80%까지 더 채우면 얼마나 더 드는지`() {
        // 남은 100km = 40%p → 55% 필요
        val a = ChargeStopAdvisor.advise(EnergyState(30, null), car, kmLeft = 100.0, maxKw = 100, wonPerKwh = 300, month = Month.OCTOBER)!!
        assertEquals(55, a.enoughSoc)
        assertEquals(15, a.arrivalSoc)
        // 30→55%: 12.5kWh / 85kW = 8.8분 → 9분
        assertEquals(9, a.minutesToEnough)
        // 55→80%: 12.5kWh × 300원 = 3,750원
        assertEquals(3_750, a.extraWon)
        assertFalse(a.reached(54))
        assertTrue(a.reached(55))
    }

    @Test
    fun `이미 충분히 찼으면 지금 끝낼 때 도착 배터리를 알려 준다`() {
        val a = ChargeStopAdvisor.advise(EnergyState(65, null), car, kmLeft = 100.0, maxKw = 100, wonPerKwh = 300, month = Month.OCTOBER)!!
        assertEquals(0, a.minutesToEnough)
        assertEquals(25, a.arrivalSoc)
        // 65→80%: 7.5kWh × 300원
        assertEquals(2_250, a.extraWon)
    }

    @Test
    fun `남은 거리를 모르거나 80%까지 채워야 닿는 구간이면 알려 줄 것이 없다`() {
        assertNull(ChargeStopAdvisor.advise(EnergyState(30, null), car, kmLeft = null, maxKw = 100, wonPerKwh = 300, month = Month.OCTOBER))
        // 200km = 80%p + 15% → 95% 필요
        assertNull(ChargeStopAdvisor.advise(EnergyState(30, null), car, kmLeft = 200.0, maxKw = 100, wonPerKwh = 300, month = Month.OCTOBER))
        assertNull(ChargeStopAdvisor.advise(EnergyState(85, null), car, kmLeft = 10.0, maxKw = 100, wonPerKwh = 300, month = Month.OCTOBER))
    }

    @Test
    fun `차가 주는 실시간 전비가 낮으면(겨울 히터 등) 더 많이 채워야 한다`() {
        // 4km/kWh → 1km에 0.5%p, 100km = 50%p + 15% → 65%
        assertEquals(65, ChargeStopAdvisor.enoughSoc(100.0, EnergyState(30, 4.0), car))
    }
}
