package com.chargecompass.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chargecompass.app.AppContainer
import com.chargecompass.app.navi.StopResult
import com.chargecompass.app.recommend.RecoState
import com.chargecompass.core.pricing.RankedStation
import com.chargecompass.core.rewards.RewardKind
import com.chargecompass.core.energy.ArrivalCalculator
import java.time.LocalDateTime
import kotlin.math.roundToInt

private val tabs = listOf("홈", "충전소 비교", "도감", "이벤트", "포인트")

/** 정차 중 화면: 위쪽 Page Navigation + 탭. */
@Composable
fun ParkedHome(c: AppContainer) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var settings by rememberSaveable { mutableStateOf(false) }
    if (settings) {
        SettingsScreen(c) { settings = false }
        return
    }
    Column(Modifier.fillMaxSize()) {
        PageNavigation(
            "정차 중", tabs, tab, onSelect = { tab = it }, onSettings = { settings = true },
            icons = listOf({ t -> HomeIcon(color = t) }, { t -> ListIcon(color = t) }, { t -> StampIcon(32.dp, t) }, { t -> CalendarIcon(color = t) }, { t -> TicketIcon(32.dp, t) }),
        )
        // 정차 중 화면이라 탭 전환에 짧은 페이드·이동(주행 중 화면에는 애니메이션 없음)
        AnimatedContent(
            targetState = tab, modifier = Modifier.weight(1f).fillMaxWidth(), label = "tab",
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 12 * dir }) togetherWith fadeOut(tween(120))
            },
        ) { t ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                when (t) {
                    0 -> HomeTab(c)
                    1 -> CompareScreen(c)
                    2 -> StampBook(c)
                    3 -> EventsScreen(c)
                    else -> PointsExchange(c)
                }
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

    BatteryHero(c)

    // 오늘이 사업자 데이면 맨 위에 알린다(포켓몬 고의 커뮤니티 데이)
    upcomingOperatorDay(c.data.operatorDays, c.demoClock.now().toLocalDate())?.takeIf { it.date == c.demoClock.now().toLocalDate() }?.let { OperatorDayCard(c, it) }

    // 방금 끝난 충전: 영수증 카드(보상 고리의 마무리). 정차 중 화면이라 처음 나타날 때 한 번 축하 효과
    val summary by c.rewards.lastSummary.collectAsState()
    summary?.let { sum ->
        Box {
            ReceiptCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        T("충전 영수증", ViaType.labelM, col.text3.c())
                        T(sum.stationName, ViaType.titleS)
                    }
                    Box(Modifier.size(64.dp).clip(CircleShape).pressable(onClick = c.rewards::dismissSummary), contentAlignment = Alignment.Center) {
                        CloseIcon(color = col.text3.c())
                    }
                }
                DashedDivider()
                ReceiptRow("충전량", "${"%.1f".format(sum.chargedKwh)}kWh")
                ReceiptRow("결제 예상", won(sum.costWon))
                ReceiptRow("비회원가 대비", if (sum.savingWon > 0) "-${won(sum.savingWon)}" else won(0), col.activeText.c(), bold = true)
                if (sum.points > 0) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    T("받은 포인트", ViaType.bodyM, col.text2.c(), Modifier.weight(1f))
                    CoinAmount("+${sum.points}P", ViaType.bodyM.copy(fontWeight = FontWeight.Bold), col.activeText.c(), coin = 36.dp)
                }
                DashedDivider()
                Row(verticalAlignment = Alignment.Bottom) {
                    T("이번 충전으로 아낀 돈", ViaType.bodyM, col.text2.c(), Modifier.weight(1f))
                    CountUpWon(sum.savingWon, ViaType.headline, col.activeText.c())
                }
            }
            ConfettiBurst(sum.endedAt, Modifier.matchParentSize())
        }
    }

    // 경로 위 추천을 아직 넣지 않았거나(정차 중 바로 추천·주행 중 대답 없음), 넣는 중이거나, 넣다가 실패한 경우
    val pending: Triple<RankedStation, Boolean, Boolean>? = when (val s = reco) {
        is RecoState.AwaitingTouch -> Triple(s.choice, false, false)
        is RecoState.Adding -> Triple(s.station, true, false)
        is RecoState.Added -> if (s.result == StopResult.FAILED) Triple(s.station, false, true) else null
        else -> null
    }
    pending?.let { (station, loading, failed) ->
        val top = (reco as? RecoState.AwaitingTouch)?.top ?: listOf(station)
        ViaCard(padding = 44.dp, highlight = if (failed) col.neg.c() else null) {
            RouteBandFor(c, top)
            Adaptive(Arrangement.spacedBy(32.dp)) { firstWeight ->
                Column(firstWeight, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (failed) T("경유지를 넣지 못했어요 · 내비가 준비되면 다시 시도해 주세요", ViaType.labelM.copy(fontWeight = FontWeight.Bold), col.neg.c())
                    else T("경로 위 추천 충전소 · 아직 경로에 없어요", ViaType.labelM, col.text3.c())
                    T(station.station.name, ViaType.titleL)
                    T("${station.station.alongRouteKm.toInt()}km 앞 · ${station.price.wonPerKwh}원/kWh ${station.price.basis.label()} · ${won(station.savingWon)} 절약", ViaType.bodyM, col.text2.c())
                }
                ViaButton(if (failed) "다시 시도" else "경유지로 추가", onClick = { c.reco.confirmByTouch(station.station.id) }, loading = loading, modifier = if (isWide) Modifier else Modifier.fillMaxWidth())
            }
        }
    }

    Grid3 {
        it(Modifier) {
            T("이번 주 포인트", ViaType.labelM, col.text3.c())
            CoinAmount("+${weekPoints}P", ViaType.headline, col.activeText.c(), coin = 64.dp)
            Bar(balance / 200f, col.active.c(), height = 16.dp)
            val ready = partnerCoupons.count { it.cost <= balance }
            T("보유 ${balance}P · " + (nextCoupon(balance, partnerCoupons)?.let { n -> if (ready > 0) "쿠폰 ${ready}장 받을 수 있어요" else "다음 쿠폰까지 ${n.remaining}P" } ?: "쿠폰 ${ready}장 모두 받을 수 있어요"),
                ViaType.labelM, col.text2.c(), maxLines = 1)
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
                val now = c.demoClock.now()
                val nowMin = now.hour * 60 + now.minute
                val status = eventStatus(event.startHour, event.endHour, nowMin)
                T("${event.startHour}시 ~ ${event.endHour}시 · $status", ViaType.bodyM.copy(fontWeight = if (status.startsWith("지금")) FontWeight.Bold else FontWeight.Normal),
                    if (status.startsWith("지금")) col.event.c() else col.text2.c())
                UpcomingTimeline(event.startHour, event.endHour, nowMin, col.event.c())
            }
        }
        it(Modifier) {
            val stamped = entries.filter { e -> e.kind == RewardKind.NEW_STAMP }.mapNotNull { e -> e.stationId }.toSet()
            T("충전소 도감", ViaType.labelM, col.text3.c())
            T("${stamped.size} / ${c.data.stations.size}곳", ViaType.headline)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                c.data.stations.forEach { st ->
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        val op = c.data.memberships.firstOrNull { it.operatorId == st.operatorId }
                        StationBadge(op?.name?.take(1) ?: "?", badgeColor(st.operatorId), st.id in stamped, size = 40.dp)
                    }
                }
            }
            T("처음 충전한 곳마다 스탬프 +20P", ViaType.labelM, col.text2.c())
        }
    }

    WeeklyMissionsCard(c)

    // 최근 7일 절약(충전 종료 요약 기록) — Pleos Widget처럼 제목·정보 영역
    val summaries by c.summaries.summaries.collectAsState()
    val today = c.demoClock.now().toLocalDate()
    val week = dailySavings(summaries, today)
    ViaCard(padding = 36.dp) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                T("최근 7일 절약", ViaType.labelM, col.text3.c())
                T(won(week.sum()), ViaType.titleM, col.activeText.c())
            }
            T(if (week.count { it > 0 } <= 1) "충전이 끝나면 자동으로 쌓여요" else "하루 평균 ${won(week.sum() / week.count { it > 0 })}", ViaType.labelM, col.text3.c())
        }
        WeekBars(week, (6 downTo 0).map { back -> if (back == 0) "오늘" else today.minusDays(back.toLong()).dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.KOREAN) }, height = if (isWide) 140.dp else 110.dp)
    }

    when (val s = reco) {
        is RecoState.Added -> if (s.result != StopResult.FAILED) ViaCard { T("경유지로 추가한 충전소", ViaType.labelM, col.text3.c()); T(s.station.station.name, ViaType.titleM, col.activeText.c()) }
        is RecoState.Enough -> ViaCard { T("지금 경로는 충전 없이 도착할 수 있어요", ViaType.bodyL) }
        RecoState.NoneAvailable -> ViaCard { T("경로 위에 쓸 수 있는 충전소가 없어요", ViaType.bodyL, col.neg.c()) }
        RecoState.Idle -> IdleGuide()
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

/** 홈 맨 위: 배터리 링(현재 %, 목적지가 있으면 도착 예상 눈금)과 주행 가능 거리. */
@Composable
private fun BatteryHero(c: AppContainer) {
    val energy by c.vehicle.energy.collectAsState()
    val route by c.navi.route.collectAsState()
    val col = colors
    val car = c.vehicleModel()
    val soc = energy.socPct
    val eff = energy.efficiencyKmPerKwh?.takeIf { it > 0 } ?: car?.baseEfficiencyKmPerKwh
    val range = if (car != null && eff != null) rangeKm(soc, car.batteryKwh, eff) else null
    val arrival = route?.let { r -> car?.let { ArrivalCalculator.socAfter(energy, it, r.remainingKm) } }?.roundToInt()
    val low = (soc ?: 100) < 20
    ViaCard(padding = 36.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (isWide) 48.dp else 28.dp)) {
            RingGauge((soc ?: 0) / 100f, if (low) col.neg.c() else col.active.c(), size = if (isWide) 220.dp else 180.dp, stroke = 22.dp,
                marker = arrival?.let { (it.coerceIn(0, 100)) / 100f }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    T(soc?.let { "$it%" } ?: "-", ViaType.titleL.copy(fontWeight = FontWeight.ExtraBold))
                    T("배터리", ViaType.labelS, col.text3.c())
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    T(car?.name ?: "차종을 골라 주세요", ViaType.labelM, col.text3.c())
                    val now = c.demoClock.now()
                    if (com.chargecompass.core.rewards.RewardEngine.isOffPeak(now.hour)) T("경부하 부스트 중 · 충전 포인트 2배", ViaType.labelS.copy(fontWeight = FontWeight.Bold), Color.White,
                        Modifier.clip(RoundedCornerShape(999.dp)).background(col.pos.c()).padding(horizontal = 14.dp, vertical = 4.dp))
                }
                T(range?.let { "약 ${it}km 갈 수 있어요" } ?: "배터리 정보를 기다리는 중", ViaType.titleM)
                val r = route
                if (r != null && arrival != null) {
                    val need = ArrivalCalculator.needsCharge(arrival.toDouble())
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        T("${r.destinationName}까지 ${r.remainingKm.roundToInt()}km · 도착 예상 ${arrival.coerceAtLeast(0)}%", ViaType.bodyM, col.text2.c())
                        Chip(if (need) "충전 필요" else "충분해요", if (need) col.neg.c() else col.activeSoft.c(), if (need) Color.White else col.activeText.c(), bold = true)
                    }
                } else {
                    T("목적지를 정하면 도착할 때 남는 배터리를 계산해요", ViaType.bodyM, col.text2.c())
                }
            }
        }
    }
}

/** 추천 후보를 경로 띠로: 출발 배터리, 후보 위치·그 지점 배터리, 목적지 도착 예상. */
@Composable
fun RouteBandFor(c: AppContainer, top: List<RankedStation>) {
    val route by c.navi.route.collectAsState()
    val energy by c.vehicle.energy.collectAsState()
    val r = route ?: return
    val car = c.vehicleModel()
    val stops = routeStops(r.remainingKm, top.map { it.station.id to it.station.alongRouteKm }) { km -> car?.let { ArrivalCalculator.socAfter(energy, it, km) } }
    RouteBand(
        stops, energy.socPct, r.destinationName,
        car?.let { ArrivalCalculator.socAfter(energy, it, r.remainingKm) }?.roundToInt()?.coerceAtLeast(0),
        r.remainingKm.roundToInt(), top.firstOrNull()?.station?.alongRouteKm?.roundToInt(),
    )
}

/** 경로가 없을 때: 그림과 세 단계 안내. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun IdleGuide() {
    val col = colors
    ViaCard(padding = 36.dp) {
        Adaptive(Arrangement.spacedBy(40.dp)) { first ->
            Prop(com.chargecompass.app.R.drawable.prop_symbol, 150.dp)
            Column(first, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                T("목적지를 정하면 경로 위 가장 싼 충전소를 찾아드려요", ViaType.bodyL.copy(fontWeight = FontWeight.Bold))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("내비에서 목적지 설정", "내 멤버십 기준 최저가 추천", "경유지로 한 번에 추가").forEachIndexed { i, step ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(36.dp).clip(CircleShape).background(col.posSoft.c()), contentAlignment = Alignment.Center) {
                                T("${i + 1}", ViaType.labelS.copy(fontWeight = FontWeight.Bold), col.pos.c())
                            }
                            T(step, ViaType.labelM, col.text2.c())
                        }
                    }
                }
            }
        }
    }
}
