package com.chargecompass.app.usual

import com.chargecompass.core.energy.EnergyState
import com.chargecompass.core.model.Connector
import com.chargecompass.core.model.VehicleModel
import com.chargecompass.core.usual.UsualRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UsualRouteAdvisorTest {
    private val car = VehicleModel("ioniq5", "아이오닉 5", 77.4, 5.0, setOf(Connector.DC_COMBO), emptyList())

    @Test
    fun `평소 경로로 가면 모자라면 목적지 이름과 도착 배터리를 말한다`() {
        // 23km / 5km/kWh = 4.6kWh = 5.9%p → 15% - 5.9 = 9%
        assertEquals(
            "평소처럼 서울역에 가시면 도착 배터리가 9%예요. 내비에 목적지를 넣으면 경로 위 가장 싼 충전소를 찾아 드릴게요.",
            UsualRouteAdvisor.speech(UsualRoute(23.0, 4, "서울역"), EnergyState(15, null), car),
        )
    }

    @Test
    fun `목적지 이름을 모르면 거리로, 바닥나면 그렇게 말한다`() {
        assertEquals(
            "평소 이 시간에 가시는 곳(60km)까지 가시면 가는 도중 배터리가 바닥나요. 내비에 목적지를 넣으면 경로 위 가장 싼 충전소를 찾아 드릴게요.",
            UsualRouteAdvisor.speech(UsualRoute(60.0, 3, null), EnergyState(5, null), car),
        )
    }

    @Test
    fun `충분하면 말하지 않는다`() {
        assertNull(UsualRouteAdvisor.speech(UsualRoute(23.0, 4, "서울역"), EnergyState(60, null), car))
    }
}
