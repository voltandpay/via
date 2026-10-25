package com.chargecompass.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chargecompass.app.AppContainer
import com.chargecompass.app.recommend.RecoState
import com.chargecompass.core.pricing.RankedStation
import com.chargecompass.core.rewards.RewardKind
import java.time.LocalDateTime

private val tabs = listOf("홈", "충전소 비교", "도감", "이벤트", "포인트")

/** 정차 중 화면: 위쪽 Page Navigation + 탭. */
@Composable
fun ParkedHome(c: AppContainer) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        PageNavigation("정차 중", tabs, tab) { tab = it }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            when (tab) {
                0 -> HomeTab(c)
                1 -> CompareScreen(c)
                2 -> StampBook(c)
                3 -> EventsScreen(c)
                else -> PointsExchange(c)
            }
        }
    }
}

@Composable
private fun HomeTab(c: AppContainer) {
    val reco by c.reco.state.collectAsState()
    val entries by c.ledger.entries.collectAsState()
    val col = colors
    val now = LocalDateTime.now()
    val weekPoints = entries.filter { it.at.isAfter(now.minusDays(7)) }.sumOf { it.points }
    val balance = c.ledger.balance(now)

    // 주행 중 음성으로 확인하지 못한 추천 — 정차 중 터치 한 번으로 추가. 추가하는 동안은 로딩 버튼.
    val pending: Pair<RankedStation, Boolean>? = when (val s = reco) {
        is RecoState.AwaitingTouch -> s.choice to false
        is RecoState.Adding -> s.station to true
        else -> null
    }
    pending?.let { (station, loading) ->
        ViaCard(padding = 44.dp) {
            Adaptive(Arrangement.spacedBy(32.dp)) { firstWeight ->
                Column(firstWeight, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    T("주행 중 추천한 충전소 · 아직 경로에 없어요", ViaType.labelM, col.text3.c())
                    T(station.station.name, ViaType.titleL)
                    T("${station.station.alongRouteKm.toInt()}km 앞 · ${station.price.wonPerKwh}원/kWh ${station.price.basis.label()} · ${won(station.savingWon)} 절약", ViaType.bodyM, col.text2.c())
                }
                ViaButton("경유지로 추가", onClick = { c.reco.confirmByTouch(station.station.id) }, loading = loading, modifier = if (isWide) Modifier else Modifier.fillMaxWidth())
            }
        }
    }

    Grid3 {
        it(Modifier) {
            T("이번 주 포인트", ViaType.labelM, col.text3.c())
            T("+${weekPoints}P", ViaType.headline, col.activeText.c())
            Bar(balance / 200f, col.active.c(), height = 16.dp)
            T("보유 ${balance}P · 200P면 충전 2,000원 할인 쿠폰", ViaType.labelM, col.text2.c())
        }
        it(Modifier) {
            val event = c.data.events.firstOrNull()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                T("오늘의 이벤트 충전소", ViaType.labelM, col.text3.c())
                if (event != null) T("${event.multiplier}배", ViaType.labelS.copy(fontWeight = FontWeight.Bold), Color.White,
                    Modifier.clip(RoundedCornerShape(999.dp)).background(col.event.c()).padding(horizontal = 12.dp, vertical = 4.dp))
            }
            if (event == null) T("진행 중인 이벤트가 없어요", ViaType.bodyM, col.text2.c()) else {
                T(c.data.stations.firstOrNull { it.id == event.stationId }?.name.orEmpty(), ViaType.titleS)
                T("${event.startHour}시 ~ ${event.endHour}시 충전 시작 시 포인트 ${event.multiplier}배", ViaType.bodyM, col.text2.c())
                T("한산한 시간에 충전하면 더 드려요", ViaType.labelM, col.text3.c())
            }
        }
        it(Modifier) {
            val stamped = entries.filter { e -> e.kind == RewardKind.NEW_STAMP }.mapNotNull { e -> e.stationId }.toSet()
            T("충전소 도감", ViaType.labelM, col.text3.c())
            T("${stamped.size} / ${c.data.stations.size}곳", ViaType.headline)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                c.data.stations.forEach { st ->
                    val got = st.id in stamped
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).clip(CircleShape)
                            .then(if (got) Modifier.background(col.active.c()) else Modifier.border(3.dp, col.line.c(), CircleShape)),
                    )
                }
            }
            T("처음 충전한 곳마다 스탬프 +20P", ViaType.labelM, col.text2.c())
        }
    }

    when (val s = reco) {
        is RecoState.Added -> ViaCard { T("경유지로 추가한 충전소", ViaType.labelM, col.text3.c()); T(s.station.station.name, ViaType.titleM, col.activeText.c()) }
        is RecoState.Enough -> ViaCard { T("지금 경로는 충전 없이 도착할 수 있어요", ViaType.bodyL) }
        RecoState.NoneAvailable -> ViaCard { T("경로 위에 쓸 수 있는 충전소가 없어요", ViaType.bodyL, col.neg.c()) }
        RecoState.Idle -> ViaCard { T("목적지를 정하면 내 멤버십 기준으로 가장 싼 충전소를 찾아드려요", ViaType.bodyL, col.text2.c()) }
        else -> Unit
    }
}

/** 넓은 창: 가로로 나란히(첫 칸이 남는 폭을 차지), 좁은 창: 세로로 쌓기. */
@Composable
fun Adaptive(spacing: Arrangement.HorizontalOrVertical, content: @Composable (firstWeight: Modifier) -> Unit) {
    if (isWide) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = spacing) { content(Modifier.weight(1f)) }
    else Column(verticalArrangement = spacing) { content(Modifier) }
}

/** 카드 3개: 넓은 창은 한 줄, 좁은 창은 세로로. */
@Composable
private fun Grid3(content: (card: (Modifier, @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) -> Unit) -> Unit) {
    val cards = mutableListOf<@Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit>()
    content { _, body -> cards += body }
    if (isWide) {
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) { cards.forEach { body -> ViaCard(Modifier.weight(1f), padding = 36.dp, content = body) } }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(28.dp)) { cards.forEach { body -> ViaCard(padding = 36.dp, content = body) } }
    }
}
