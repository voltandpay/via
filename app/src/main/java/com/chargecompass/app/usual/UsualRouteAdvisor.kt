package com.chargecompass.app.usual

import com.chargecompass.core.energy.ArrivalCalculator
import com.chargecompass.core.energy.EnergyState
import com.chargecompass.core.model.VehicleModel
import com.chargecompass.core.usual.UsualRoute
import kotlin.math.roundToInt

/**
 * 내비 경로 없이 출발했을 때, 평소 이 시간 경로로 가면 배터리가 모자라는지 알리는 말.
 * 충분하면 null — 익숙한 길에서 매번 말을 걸지 않는다.
 */
object UsualRouteAdvisor {
    fun speech(route: UsualRoute, energy: EnergyState, car: VehicleModel): String? {
        val arrival = ArrivalCalculator.socAfter(energy, car, route.distanceKm) ?: return null
        if (!ArrivalCalculator.needsCharge(arrival)) return null
        val where = route.destination?.let { "평소처럼 ${it}에 가시면" } ?: "평소 이 시간에 가시는 곳(${route.distanceKm.roundToInt()}km)까지 가시면"
        val result = if (arrival <= 0) "가는 도중 배터리가 바닥나요" else "도착 배터리가 ${arrival.roundToInt()}%예요"
        return "$where $result. 내비에 목적지를 넣으면 경로 위 가장 싼 충전소를 찾아 드릴게요."
    }
}
