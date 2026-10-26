package com.chargecompass.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chargecompass.app.AppContainer
import com.chargecompass.core.rewards.RewardKind
import java.time.Duration
import java.time.LocalDateTime

/** 충전소 스탬프 도감: 진행 링 + 배지 판(모은 곳은 사업자 색, 못 모은 곳은 자물쇠). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StampBook(c: AppContainer) {
    val entries by c.ledger.entries.collectAsState()
    val medalPreview by c.medalPreview.collectAsState()
    val col = colors
    val stamps = entries.filter { it.kind == RewardKind.NEW_STAMP }.associateBy { it.stationId }
    val total = c.data.stations.size
    val now = c.demoClock.now()
    ViaCard(padding = 40.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            RingGauge(stamps.size / total.toFloat(), col.active.c(), size = 180.dp, stroke = 18.dp) {
                T("${stamps.size}/$total", ViaType.titleM)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                T("충전소 도감", ViaType.titleM)
                T("처음 충전한 충전소마다 스탬프 +20P", ViaType.bodyM, col.text2.c())
                T(if (stamps.size == total) "시연 경로의 충전소를 모두 모았어요!" else "${total - stamps.size}곳 남았어요", ViaType.labelM, col.text3.c())
            }
        }
    }
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
        c.data.stations.forEach { st ->
            val stamp = stamps[st.id]
            val op = c.data.memberships.firstOrNull { it.operatorId == st.operatorId }
            // 방금(2분 안에) 받은 스탬프는 톡 커지며 나타난다
            val fresh = stamp != null && Duration.between(stamp.at, now).abs() < Duration.ofMinutes(2)
            Column(Modifier.width(if (isWide) 180.dp else 150.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StationBadge(op?.name?.take(1) ?: "?", badgeColor(st.operatorId), stamp != null, fresh, size = if (isWide) 112.dp else 96.dp,
                    medal = if (medalPreview) medalRes(st.operatorId, stamp != null) else null)
                T(st.name, ViaType.labelM.copy(fontWeight = if (stamp != null) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center),
                    if (stamp != null) col.text.c() else col.text3.c())
                T(stamp?.let { "${it.at.toLocalDate().monthValue}/${it.at.toLocalDate().dayOfMonth} 첫 충전" } ?: "아직 안 가봤어요", ViaType.labelS, col.text3.c())
            }
        }
    }
}

/** 레이드형 이벤트 충전소: 24시간 띠에 이벤트 시간대와 지금 시각. */
@Composable
fun EventsScreen(c: AppContainer) {
    val col = colors
    T("이벤트 충전소", ViaType.titleM)
    T("한산한 시간·장소에서 충전하면 포인트를 더 드려요", ViaType.bodyM, col.text2.c())
    val now = c.demoClock.now()
    c.data.events.forEach { e ->
        val live = com.chargecompass.core.rewards.RewardEngine.eventOccurrenceDate(e, now) != null
        ViaCard(padding = 36.dp, highlight = if (live) col.event.c() else null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                T(e.title, ViaType.titleS, modifier = Modifier.weight(1f), )
                Chip(eventStatus(e.startHour, e.endHour, now.hour * 60 + now.minute), bg = if (live) col.event.c() else col.chip.c(), fg = if (live) androidx.compose.ui.graphics.Color.White else col.text2.c(), bold = live)
            }
            T(c.data.stations.firstOrNull { it.id == e.stationId }?.name.orEmpty() + " · 포인트 ${e.multiplier}배 · 하루 1회", ViaType.bodyM, col.text2.c())
            UpcomingTimeline(e.startHour, e.endHour, now.hour * 60 + now.minute, col.event.c())
        }
    }
}

/** 포인트 → 제휴 충전 할인 쿠폰(티켓). 1차 MVP는 화면만(현금성 포인트 아님). */
@Composable
fun PointsExchange(c: AppContainer) {
    val col = colors
    val balance = c.ledger.balance(LocalDateTime.now())
    if (balance == 0) {
        EmptyState({ TicketIcon(color = col.pos.c()) }, "아직 포인트가 없어요", "충전·새 충전소 스탬프·이벤트 충전소·효율 운전으로 포인트가 저절로 쌓여요")
    } else ViaCard(padding = 40.dp) {
        T("보유 포인트", ViaType.labelM, col.text3.c())
        T("${balance}P", ViaType.headline, col.activeText.c())
        nextCoupon(balance, partnerCoupons)?.let { next ->
            Bar(balance / next.coupon.cost.toFloat(), col.active.c(), height = 16.dp)
            T("다음 쿠폰(${next.coupon.name})까지 ${next.remaining}P", ViaType.bodyM, col.text2.c())
        } ?: T("모든 쿠폰을 받을 수 있어요", ViaType.bodyM, col.text2.c())
    }
    T("유효기간 12개월 · 제휴 충전 할인 쿠폰으로만 바꿀 수 있어요", ViaType.labelM, col.text3.c())
    partnerCoupons.forEach { coupon ->
        val ready = balance >= coupon.cost
        TicketCard(accent = if (ready) col.active.c() else col.line.c()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(0.72f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    T(coupon.name, ViaType.titleS, maxLines = 1)
                    T(if (ready) "포인트로 받을 수 있어요 · 제휴 준비 중" else "${coupon.cost - balance}P 더 모으면 받을 수 있어요", ViaType.labelM, col.text3.c())
                }
                T("${coupon.cost}P", ViaType.titleM, if (ready) col.activeText.c() else col.text3.c(), Modifier.weight(0.28f), align = TextAlign.Center)
            }
        }
    }
}
