package com.chargecompass.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chargecompass.app.AppContainer
import com.chargecompass.core.rewards.RewardKind
import java.time.LocalDateTime

/** 충전소 스탬프 도감: 처음 충전한 충전소 목록. */
@Composable
fun StampBook(c: AppContainer) {
    val entries by c.ledger.entries.collectAsState()
    val stamps = entries.filter { it.kind == RewardKind.NEW_STAMP }
    T("충전소 도감 ${stamps.size} / ${c.data.stations.size}", ViaType.titleM)
    if (stamps.isEmpty()) T("처음 충전한 충전소마다 스탬프가 찍혀요", ViaType.bodyL, colors.text2.c())
    stamps.forEach { e ->
        ViaCard(padding = 32.dp) {
            T(c.data.stations.firstOrNull { it.id == e.stationId }?.name ?: e.stationId.orEmpty(), ViaType.titleS)
            T("첫 방문 ${e.at.toLocalDate()} · +${e.points}P", ViaType.labelM, colors.text3.c())
        }
    }
}

/** 레이드형 이벤트 충전소 목록. */
@Composable
fun EventsScreen(c: AppContainer) {
    T("이벤트 충전소", ViaType.titleM)
    T("한산한 시간·장소에서 충전하면 포인트를 더 드려요", ViaType.bodyM, colors.text2.c())
    c.data.events.forEach { e ->
        ViaCard(padding = 32.dp) {
            T(e.title, ViaType.titleS)
            T(c.data.stations.firstOrNull { it.id == e.stationId }?.name.orEmpty(), ViaType.bodyM, colors.text2.c())
            T("${e.startHour}시 ~ ${e.endHour}시 충전 시작 · 포인트 ${e.multiplier}배 · 하루 1회", ViaType.labelM, colors.text3.c())
        }
    }
}

/** 포인트 → 제휴 충전 할인 쿠폰. 1차 MVP는 화면만(현금성 포인트 아님). */
@Composable
fun PointsExchange(c: AppContainer) {
    val col = colors
    T("보유 ${c.ledger.balance(LocalDateTime.now())}P", ViaType.headline, col.activeText.c())
    T("포인트는 제휴 충전 할인 쿠폰으로만 바꿀 수 있어요 · 유효기간 12개월", ViaType.bodyM, col.text2.c())
    listOf("채비 충전 2,000원 할인" to 200, "한전 EV 충전 1,000원 할인" to 100, "워터 초급속 3,000원 할인" to 300).forEach { (name, cost) ->
        ViaCard(padding = 32.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                T(name, ViaType.titleS, modifier = Modifier.weight(1f))
                Chip("${cost}P · 제휴 준비 중", fg = col.text2.c())
            }
        }
    }
}
