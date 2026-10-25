package com.chargecompass.core.energy

import com.chargecompass.core.Defaults
import com.chargecompass.core.model.VehicleModel

data class EnergyState(val socPct: Int?, val efficiencyKmPerKwh: Double?)

/** 내비가 주는 "도착 시 배터리값"은 소모를 반영하지 않아(스파이크 2-1) 앱이 직접 계산한다. */
object ArrivalCalculator {
    /** 전비가 null이거나 0 이하면 차종 기본 전비를 쓴다. SoC를 모르면 null. 결과는 음수일 수 있다. */
    fun socAfter(state: EnergyState, vehicle: VehicleModel, distanceKm: Double): Double? {
        val soc = state.socPct ?: return null
        val efficiency = state.efficiencyKmPerKwh?.takeIf { it > 0 } ?: vehicle.baseEfficiencyKmPerKwh
        val usedKwh = distanceKm / efficiency
        return soc - usedKwh / vehicle.batteryKwh * 100
    }

    fun needsCharge(arrivalSoc: Double?, thresholdPct: Double = Defaults.NEED_CHARGE_SOC): Boolean =
        arrivalSoc != null && arrivalSoc < thresholdPct
}
