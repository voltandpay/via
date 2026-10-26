package com.chargecompass.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chargecompass.app.AppContainer
import com.chargecompass.app.insights.coachCharges
import com.chargecompass.app.insights.monthlyReport
import com.chargecompass.app.insights.reportCardVisible
import com.chargecompass.core.coach.MembershipCoach
import kotlinx.coroutines.launch
import java.time.YearMonth

/**
 * 멤버십 코치: 최근 30일 실제 충전을 다시 계산해 "이 카드가 있었다면 얼마 덜 냈을지"를 보여 준다.
 * 카드를 이미 가지고 있다면 한 번에 내 멤버십에 넣을 수 있다(다음 추천부터 회원가로 계산).
 */
@Composable
fun MembershipCoachCard(c: AppContainer) {
    val summaries by c.summaries.summaries.collectAsState()
    val owned by c.membership.owned.collectAsState()
    val scope = rememberCoroutineScope()
    val col = colors
    val charges = coachCharges(summaries, c.data.allStations, c.demoClock.now())
    if (charges.isEmpty()) return
    val advice = MembershipCoach.advise(charges, c.data.tariffs, c.data.memberships, owned)
    val saved = MembershipCoach.savedWithOwned(charges, c.data.tariffs, owned)
    ViaCard(padding = 36.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T("멤버십 코치", ViaType.labelM, col.text3.c())
                T("최근 30일 충전 ${charges.size}번 기준", ViaType.labelS, col.text3.c())
            }
            T("지금 카드로 아낀 돈 ${won(saved)}", ViaType.labelM.copy(fontWeight = FontWeight.Bold), col.activeText.c())
        }
        val best = advice.firstOrNull()
        if (best == null) {
            T("지금 가진 카드 조합이 가장 좋아요", ViaType.titleS)
            return@ViaCard
        }
        Adaptive(Arrangement.spacedBy(28.dp)) { first ->
            Row(first, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                membershipCardRes(best.membership.operatorId)?.let { Prop(it, 120.dp) }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    T("${best.membership.name} 카드가 있었다면", ViaType.bodyM, col.text2.c())
                    T("${won(best.savingWon)} 덜 냈어요", ViaType.titleL, col.activeText.c())
                    T("그 카드로 회원가를 받았을 충전 ${best.charges}번" + (advice.getOrNull(1)?.let { " · 다음은 ${it.membership.name} ${won(it.savingWon)}" } ?: ""),
                        ViaType.labelM, col.text3.c(), maxLines = 1)
                }
            }
            // 카드는 사업자 앱에서 만든다. 이미 있다면 여기서 한 번에 내 멤버십에 넣는다
            ViaButton("이미 있어요 · 추가", onClick = { scope.launch { c.membership.set(owned + best.membership.id) } }, primary = false)
        }
    }
}

/** 매월 1~7일에 지난달 절약 리포트. */
@Composable
fun MonthlyReportCard(c: AppContainer) {
    val summaries by c.summaries.summaries.collectAsState()
    val entries by c.ledger.entries.collectAsState()
    val now = c.demoClock.now()
    if (!reportCardVisible(now)) return
    val r = monthlyReport(YearMonth.from(now).minusMonths(1), summaries, entries) ?: return
    val col = colors
    ViaCard(padding = 36.dp, highlight = col.active.c()) {
        Adaptive(Arrangement.spacedBy(28.dp)) { first ->
            Column(first, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                T("${r.month.monthValue}월 절약 리포트", ViaType.labelM, col.text3.c())
                T("비회원가보다 ${won(r.savingWon)} 아꼈어요", ViaType.titleL, col.activeText.c())
                T("충전 ${r.charges}번 · ${"%.0f".format(r.kwh)}kWh · 처음 간 충전소 ${r.newStations}곳", ViaType.bodyM, col.text2.c())
            }
            CoinAmount("+${r.points}P", ViaType.titleM, col.activeText.c(), coin = 48.dp)
        }
    }
}
