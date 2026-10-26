package com.chargecompass.core.trip

import com.chargecompass.core.Defaults
import com.chargecompass.core.energy.EnergyState
import com.chargecompass.core.model.ChargerStatus
import com.chargecompass.core.model.Station
import com.chargecompass.core.model.Tariff
import com.chargecompass.core.model.VehicleModel
import com.chargecompass.core.pricing.StationRanker
import kotlin.math.ceil
import kotlin.math.roundToInt

/** 장거리 충전 한 곳: [km] 지점에 [arrivalSoc]%로 도착해 [chargeToSoc]%까지 충전([kwh], [costWon]). */
data class TripStop(val station: Station, val km: Double, val arrivalSoc: Int, val chargeToSoc: Int, val kwh: Double, val costWon: Int)

data class TripPlan(val stops: List<TripStop>, val arrivalSoc: Int) {
    val totalCostWon: Int get() = stops.sumOf { it.costWon }
}

/**
 * 장거리 다중 충전 계획(서울→부산처럼 한 번 충전으로 안 되는 경로).
 * 지금 위치에서 닿을 수 있는 충전소 중 멀리 있는 구간(뒤쪽 40%)에서 내 멤버십 기준 가장 싼 곳을 골라 [targetSoc]까지 충전하고,
 * 마지막 충전은 목적지에 [arriveSoc]로 도착할 만큼만 한다. 정류 횟수를 줄이면서 비싼 곳을 피하는 단순한 탐욕 방식이다.
 */
object TripPlanner {
    fun plan(
        routeKm: Double, energy: EnergyState, car: VehicleModel, stations: List<Station>, tariffs: Map<String, Tariff>, owned: Set<String>,
        minStopArrival: Double = Defaults.MIN_ARRIVAL_SOC, arriveSoc: Double = Defaults.NEED_CHARGE_SOC, targetSoc: Double = Defaults.TARGET_SOC,
    ): TripPlan? {
        val eff = energy.efficiencyKmPerKwh?.takeIf { it > 0 } ?: car.baseEfficiencyKmPerKwh
        val pctPerKm = 100.0 / (car.batteryKwh * eff)
        var pos = 0.0
        var soc = (energy.socPct ?: return null).toDouble()
        val stops = mutableListOf<TripStop>()
        val usable = stations.filter { it.status == ChargerStatus.AVAILABLE && it.connectors.any { c -> c in car.connectors } && tariffs.containsKey(it.operatorId) }
            .sortedBy { it.alongRouteKm }
        while (soc - (routeKm - pos) * pctPerKm < arriveSoc) {
            val reach = pos + (soc - minStopArrival) / pctPerKm
            val reachable = usable.filter { it.alongRouteKm > pos && it.alongRouteKm <= reach && it.alongRouteKm < routeKm }
            if (reachable.isEmpty()) return null
            val windowStart = pos + 0.6 * (minOf(reach, routeKm) - pos)
            val window = reachable.filter { it.alongRouteKm >= windowStart }.ifEmpty { listOf(reachable.last()) }
            val next = window.minWith(compareBy<Station> { price(it, tariffs, owned) }.thenByDescending { it.alongRouteKm })
            val arrival = soc - (next.alongRouteKm - pos) * pctPerKm
            val needToFinish = (routeKm - next.alongRouteKm) * pctPerKm + arriveSoc
            val chargeTo = if (needToFinish <= targetSoc) ceil(needToFinish) else targetSoc
            if (chargeTo <= arrival) return null   // 충전해도 나아가지 못한다
            val kwh = (chargeTo - arrival) / 100.0 * car.batteryKwh
            stops += TripStop(next, next.alongRouteKm, arrival.roundToInt(), chargeTo.roundToInt(), kwh, (kwh * price(next, tariffs, owned)).roundToInt())
            pos = next.alongRouteKm
            soc = chargeTo
            if (stops.size > 10) return null
        }
        return TripPlan(stops, (soc - (routeKm - pos) * pctPerKm).roundToInt())
    }

    private fun price(s: Station, tariffs: Map<String, Tariff>, owned: Set<String>): Int = StationRanker.effectivePrice(tariffs.getValue(s.operatorId), owned).wonPerKwh
}
