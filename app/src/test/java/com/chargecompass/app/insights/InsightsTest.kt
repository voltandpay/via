package com.chargecompass.app.insights

import com.chargecompass.app.rewards.ChargeSummary
import com.chargecompass.core.data.MockDataLoader
import com.chargecompass.core.rewards.RewardEntry
import com.chargecompass.core.rewards.RewardKind
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InsightsTest {
    private val data = MockDataLoader.load { javaClass.classLoader.getResource(it)!!.readText() }
    private val now = LocalDateTime.of(2026, 11, 20, 12, 0)
    private fun s(daysAgo: Long, id: String?, kwh: Double, saving: Int = 1000) =
        ChargeSummary(id ?: "?", kwh, 10_000, saving, 10, now.minusDays(daysAgo), id)

    @Test
    fun `코치는 최근 30일 충전만 사업자로 바꿔 계산한다(충전소를 모르는 기록은 뺀다)`() {
        val summaries = listOf(s(3, "st-yangjae-chaevi", 40.0), s(10, "st-banpo-kepco", 20.0), s(45, "st-yangjae-chaevi", 40.0), s(2, null, 30.0))
        val charges = coachCharges(summaries, data.stations, now)
        assertEquals(listOf("chaevi", "kepco"), charges.map { it.operatorId })
    }

    @Test
    fun `월간 리포트는 그 달의 충전 횟수·kWh·절약액·포인트·새 충전소를 모은다`() {
        val oct = YearMonth.of(2026, 10)
        val summaries = listOf(
            ChargeSummary("a", 30.0, 9000, 4000, 10, LocalDateTime.of(2026, 10, 3, 9, 0), "st-yangjae-chaevi"),
            ChargeSummary("b", 20.0, 7000, 2000, 10, LocalDateTime.of(2026, 10, 30, 9, 0), "st-banpo-kepco"),
            ChargeSummary("c", 99.0, 1, 9999, 10, LocalDateTime.of(2026, 11, 1, 9, 0), "st-banpo-kepco"),
        )
        val entries = listOf(
            RewardEntry(RewardKind.CHARGE, 10, LocalDateTime.of(2026, 10, 3, 9, 0), "st-yangjae-chaevi"),
            RewardEntry(RewardKind.NEW_STAMP, 20, LocalDateTime.of(2026, 10, 3, 9, 0), "st-yangjae-chaevi"),
            RewardEntry(RewardKind.EFFICIENT_TRIP, 10, LocalDateTime.of(2026, 10, 12, 9, 0)),
            RewardEntry(RewardKind.CHARGE, 10, LocalDateTime.of(2026, 11, 1, 9, 0), "st-banpo-kepco"),
        )
        val r = monthlyReport(oct, summaries, entries)!!
        assertEquals(2, r.charges)
        assertEquals(50.0, r.kwh)
        assertEquals(6000, r.savingWon)
        assertEquals(40, r.points)
        assertEquals(1, r.newStations)
        assertNull(monthlyReport(YearMonth.of(2026, 9), summaries, entries))
    }

    @Test
    fun `월간 리포트 음성은 달이 바뀐 뒤 처음 한 번만, 지난달 기록이 있을 때만`() {
        val nov1 = LocalDateTime.of(2026, 11, 1, 8, 0)
        assertEquals(YearMonth.of(2026, 10), reportDue(nov1, lastAnnounced = null, hasReport = { it == YearMonth.of(2026, 10) }))
        assertNull(reportDue(nov1, lastAnnounced = YearMonth.of(2026, 10), hasReport = { true }))
        assertNull(reportDue(nov1, lastAnnounced = null, hasReport = { false }))
    }

    @Test
    fun `리포트 카드는 매월 1~7일에 지난달 것을 보여 준다`() {
        assertTrue(reportCardVisible(LocalDateTime.of(2026, 11, 7, 23, 0)))
        assertFalse(reportCardVisible(LocalDateTime.of(2026, 11, 8, 0, 0)))
    }

    @Test
    fun `음성용 금액은 만·천·백 단위로 읽는다`() {
        assertEquals("4만 2천 3백 원", spokenWon(42_300))
        assertEquals("9천 원", spokenWon(9_000))
        assertEquals("1만 원", spokenWon(10_000))
        assertEquals("0원", spokenWon(0))
        assertEquals("10월에 충전 2번, 비회원가보다 6천 원 아꼈어요",
            reportSpeech(MonthlyReport(YearMonth.of(2026, 10), 2, 50.0, 6000, 40, 1)))
    }
}
