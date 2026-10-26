package com.chargecompass.core.coach

import com.chargecompass.core.model.Membership
import com.chargecompass.core.model.Tariff
import com.chargecompass.core.pricing.StationRanker
import kotlin.math.roundToInt

/** 지난 충전 한 번: 어느 사업자에서 몇 kWh. */
data class Charge(val operatorId: String, val kwh: Double)

/** [membership]이 있었다면 지난 충전에서 [savingWon]원을 덜 냈다. [charges]는 그 카드로 회원가를 받았을 충전 횟수. */
data class CoachAdvice(val membership: Membership, val savingWon: Int, val charges: Int)

/**
 * 멤버십 코치: 실제 충전 기록을 다시 계산해 "이 카드가 있었다면 얼마 덜 냈을지"를 알려 준다.
 * 회원가를 받으려면 카드가 여러 장 필요하다는 불편(가입 장벽)을, 내 충전 습관에 맞는 카드 하나로 줄인다.
 */
object MembershipCoach {
    fun advise(charges: List<Charge>, tariffs: Map<String, Tariff>, memberships: List<Membership>, owned: Set<String>): List<CoachAdvice> {
        if (charges.isEmpty()) return emptyList()
        val now = cost(charges, tariffs, owned)
        return memberships.filter { it.id !in owned }
            .map { m -> CoachAdvice(m, now - cost(charges, tariffs, owned + m.id), charges.count { c -> tariffs[c.operatorId]?.membershipIds?.contains(m.id) == true }) }
            .filter { it.savingWon > 0 }
            .sortedByDescending { it.savingWon }
    }

    /** 지금 가진 카드로 비회원가보다 덜 낸 돈. */
    fun savedWithOwned(charges: List<Charge>, tariffs: Map<String, Tariff>, owned: Set<String>): Int =
        cost(charges, tariffs, emptySet()) - cost(charges, tariffs, owned)

    private fun cost(charges: List<Charge>, tariffs: Map<String, Tariff>, owned: Set<String>): Int = charges.sumOf { c ->
        val t = tariffs[c.operatorId] ?: return@sumOf 0
        (c.kwh * StationRanker.effectivePrice(t, owned).wonPerKwh).roundToInt()
    }
}
