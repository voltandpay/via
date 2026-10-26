package com.chargecompass.core.coach

import com.chargecompass.core.model.Membership
import com.chargecompass.core.model.Tariff
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MembershipCoachTest {
    private val tariffs = mapOf(
        "chaevi" to Tariff("chaevi", 299, 410, 520, setOf("m-chaevi")),
        "kepco" to Tariff("kepco", 324, 400, 470, setOf("m-kepco")),
        "me" to Tariff("me", 347, null, 486, setOf("m-me")),
    )
    private val memberships = listOf(Membership("m-chaevi", "chaevi", "채비"), Membership("m-kepco", "kepco", "한전 EV충전"), Membership("m-me", "me", "환경부 충전카드"))

    @Test
    fun `지난 충전을 그 카드가 있었다면 얼마 덜 냈을지로 카드를 추천한다`() {
        // 채비에서 40kWh 두 번(비회원 520 → 회원 299), 한전에서 20kWh 한 번
        val charges = listOf(Charge("chaevi", 40.0), Charge("chaevi", 40.0), Charge("kepco", 20.0))
        val advice = MembershipCoach.advise(charges, tariffs, memberships, owned = emptySet())
        assertEquals("m-chaevi", advice.first().membership.id)
        // 채비 카드가 있으면: 채비 80kWh × (520-299) = 17,680원, 한전은 로밍가 400(카드 하나라도 있으면) → 20 × 70 = 1,400원 → 19,080원
        assertEquals(19_080, advice.first().savingWon)
        assertEquals(2, advice.first().charges)   // 그 카드로 회원가를 받았을 충전 횟수(채비 2번)
    }

    @Test
    fun `이미 가진 카드는 추천하지 않고, 아끼는 돈이 없으면 빈 목록`() {
        val charges = listOf(Charge("chaevi", 40.0))
        assertTrue(MembershipCoach.advise(charges, tariffs, memberships, owned = setOf("m-chaevi")).none { it.membership.id == "m-chaevi" })
        assertTrue(MembershipCoach.advise(charges, tariffs, memberships, owned = setOf("m-chaevi")).all { it.savingWon > 0 })
        assertTrue(MembershipCoach.advise(emptyList(), tariffs, memberships, owned = emptySet()).isEmpty())
    }

    @Test
    fun `지금 카드로 이미 아낀 돈(비회원가 대비)도 계산한다`() {
        val charges = listOf(Charge("chaevi", 40.0), Charge("me", 10.0))
        // 채비 회원: 40 × 221 = 8,840원, 환경부는 카드 없음·로밍 없음 → 0
        assertEquals(8_840, MembershipCoach.savedWithOwned(charges, tariffs, setOf("m-chaevi")))
    }
}
