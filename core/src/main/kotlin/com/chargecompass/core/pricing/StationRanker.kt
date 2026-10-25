package com.chargecompass.core.pricing

import com.chargecompass.core.Defaults
import com.chargecompass.core.energy.ArrivalCalculator
import com.chargecompass.core.energy.EnergyState
import com.chargecompass.core.model.ChargerStatus
import com.chargecompass.core.model.Station
import com.chargecompass.core.model.Tariff
import com.chargecompass.core.model.VehicleModel
import kotlin.math.roundToInt

enum class PriceBasis { MEMBER, ROAMING, NON_MEMBER }

data class EffectivePrice(val wonPerKwh: Int, val basis: PriceBasis, val membershipId: String?)

data class RankedStation(
    val station: Station,
    val price: EffectivePrice,
    val chargeKwh: Double,
    val totalCostWon: Int,
    val nonMemberCostWon: Int,
    val savingWon: Int,
    val arrivalSocPct: Double,
)

/** 설계 문서 5-2절: 내 멤버십 기준 실효 단가로 경로상 충전소의 총비용을 비교한다. */
object StationRanker {
    fun effectivePrice(tariff: Tariff, owned: Set<String>): EffectivePrice {
        val membership = tariff.membershipIds.filter { it in owned }.minOrNull()
        if (membership != null) return EffectivePrice(tariff.memberWonPerKwh, PriceBasis.MEMBER, membership)
        // 로밍가는 다른 사업자 카드라도 하나는 있어야 받을 수 있다
        val roaming = tariff.roamingWonPerKwh?.takeIf { owned.isNotEmpty() }
        return if (roaming != null && roaming < tariff.nonMemberWonPerKwh) {
            EffectivePrice(roaming, PriceBasis.ROAMING, null)
        } else {
            EffectivePrice(tariff.nonMemberWonPerKwh, PriceBasis.NON_MEMBER, null)
        }
    }

    fun rank(
        candidates: List<Station>,
        tariffs: Map<String, Tariff>,
        owned: Set<String>,
        state: EnergyState,
        vehicle: VehicleModel,
        targetSocPct: Double = Defaults.TARGET_SOC,
        minArrivalSocPct: Double = 0.0,
    ): List<RankedStation> = candidates.mapNotNull { station ->
        if (station.status != ChargerStatus.AVAILABLE) return@mapNotNull null
        if (station.connectors.none { it in vehicle.connectors }) return@mapNotNull null
        val tariff = tariffs[station.operatorId] ?: return@mapNotNull null
        val arrival = ArrivalCalculator.socAfter(state, vehicle, station.alongRouteKm + station.detourKm)
            ?: return@mapNotNull null
        if (arrival < minArrivalSocPct) return@mapNotNull null

        val price = effectivePrice(tariff, owned)
        val chargeKwh = ((targetSocPct - arrival) * vehicle.batteryKwh / 100).coerceAtLeast(0.0)
        val efficiency = state.efficiencyKmPerKwh?.takeIf { it > 0 } ?: vehicle.baseEfficiencyKmPerKwh
        val kwhNeeded = chargeKwh + station.detourKm / efficiency
        val total = (kwhNeeded * price.wonPerKwh).roundToInt()
        val nonMember = (kwhNeeded * tariff.nonMemberWonPerKwh).roundToInt()
        RankedStation(station, price, chargeKwh, total, nonMember, nonMember - total, arrival)
    }.sortedWith(compareBy<RankedStation> { it.totalCostWon }.thenByDescending { it.station.maxKw })
}
