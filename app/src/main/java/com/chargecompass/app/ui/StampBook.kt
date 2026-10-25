package com.chargecompass.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.chargecompass.app.AppContainer
import com.chargecompass.core.rewards.RewardKind

/** 충전소 스탬프 도감: 처음 충전한 충전소 목록. */
@Composable
fun StampBook(c: AppContainer) {
    val entries by c.ledger.entries.collectAsState()
    val stamps = entries.filter { it.kind == RewardKind.NEW_STAMP }
    Title("충전소 도감 ${stamps.size}/${c.data.stations.size}")
    if (stamps.isEmpty()) Body("처음 충전한 충전소마다 스탬프가 찍혀요", Muted)
    stamps.forEach { e ->
        Card {
            Title(c.data.stations.firstOrNull { it.id == e.stationId }?.name ?: e.stationId.orEmpty())
            Caption("첫 방문 ${e.at.toLocalDate()}")
        }
    }
}

/** 레이드형 이벤트 충전소 목록. */
@Composable
fun EventsScreen(c: AppContainer) {
    Title("이벤트 충전소")
    Caption("한산한 시간·장소에서 충전하면 포인트를 더 드려요")
    c.data.events.forEach { e ->
        Card {
            Title(e.title)
            Body(c.data.stations.firstOrNull { it.id == e.stationId }?.name.orEmpty())
            Caption("${e.startHour}시~${e.endHour}시 충전 시작 · 포인트 ${e.multiplier}배 · 하루 1회")
        }
    }
}

/** 포인트 → 제휴 충전 할인 쿠폰. 1차 MVP는 화면만(현금성 포인트 아님). */
@Composable
fun PointsExchange(c: AppContainer) {
    Title("보유 ${c.ledger.balance(java.time.LocalDateTime.now())}P", Accent)
    Caption("포인트는 제휴 충전 할인 쿠폰으로만 바꿀 수 있어요 · 유효기간 12개월")
    listOf("채비 충전 2,000원 할인" to 200, "한전 EV 충전 1,000원 할인" to 100, "워터 초급속 3,000원 할인" to 300).forEach { (name, cost) ->
        Card {
            Title(name)
            Caption("${cost}P · 제휴 준비 중")
        }
    }
}
