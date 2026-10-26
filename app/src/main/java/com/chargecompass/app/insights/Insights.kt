package com.chargecompass.app.insights

import com.chargecompass.app.rewards.ChargeSummary
import com.chargecompass.core.coach.Charge
import com.chargecompass.core.model.Station
import com.chargecompass.core.rewards.RewardEntry
import com.chargecompass.core.rewards.RewardKind
import java.time.LocalDateTime
import java.time.YearMonth

/** 멤버십 코치 입력: 최근 30일 충전을 (사업자, kWh)로. 충전소를 모르는 예전 기록은 뺀다. */
fun coachCharges(summaries: List<ChargeSummary>, stations: List<Station>, now: LocalDateTime, days: Long = 30): List<Charge> {
    val byId = stations.associateBy { it.id }
    return summaries.filter { !it.endedAt.isBefore(now.minusDays(days)) && !it.endedAt.isAfter(now) }
        .mapNotNull { s -> s.stationId?.let(byId::get)?.let { Charge(it.operatorId, s.chargedKwh) } }
}

/** 한 달 요약: 충전 횟수·충전량·비회원가 대비 절약액·받은 포인트·처음 간 충전소 수. */
data class MonthlyReport(val month: YearMonth, val charges: Int, val kwh: Double, val savingWon: Int, val points: Int, val newStations: Int)

fun monthlyReport(month: YearMonth, summaries: List<ChargeSummary>, entries: List<RewardEntry>): MonthlyReport? {
    val ms = summaries.filter { YearMonth.from(it.endedAt) == month }
    if (ms.isEmpty()) return null
    val me = entries.filter { YearMonth.from(it.at) == month }
    return MonthlyReport(month, ms.size, ms.sumOf { it.chargedKwh }, ms.sumOf { it.savingWon }, me.sumOf { it.points }, me.count { it.kind == RewardKind.NEW_STAMP })
}

/** 달이 바뀐 뒤 처음 한 번만 지난달 리포트를 말한다. 말할 달(지난달)이나 null. */
fun reportDue(now: LocalDateTime, lastAnnounced: YearMonth?, hasReport: (YearMonth) -> Boolean): YearMonth? {
    val prev = YearMonth.from(now).minusMonths(1)
    return prev.takeIf { it != lastAnnounced && hasReport(it) }
}

/** 리포트 카드는 매월 1~7일에 지난달 것을 보여 준다. */
fun reportCardVisible(now: LocalDateTime): Boolean = now.dayOfMonth <= 7

/** "10월에 충전 9번, 비회원가보다 4만 2천 원 아꼈어요" */
fun reportSpeech(r: MonthlyReport): String = "${r.month.monthValue}월에 충전 ${r.charges}번, 비회원가보다 ${spokenWon(r.savingWon)} 아꼈어요"

/** 음성용 금액: 42,300 → "4만 2천 3백 원", 9,000 → "9천 원". */
fun spokenWon(won: Int): String {
    if (won <= 0) return "0원"
    val man = won / 10_000; val rest = won % 10_000
    val parts = mutableListOf<String>()
    if (man > 0) parts += "${man}만"
    if (rest / 1000 > 0) parts += "${rest / 1000}천"
    if (rest % 1000 / 100 > 0) parts += "${rest % 1000 / 100}백"
    return parts.joinToString(" ").ifEmpty { "${won}" } + " 원"
}
