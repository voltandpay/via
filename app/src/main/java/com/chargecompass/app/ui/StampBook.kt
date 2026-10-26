package com.chargecompass.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import com.chargecompass.app.R
import com.chargecompass.core.Defaults
import com.chargecompass.core.dex.Dex
import com.chargecompass.core.dex.DexEntry
import com.chargecompass.core.dex.DexState
import com.chargecompass.core.dex.MedalTier
import com.chargecompass.core.dex.PowerType
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

/**
 * 충전소 도감(포켓몬 도감처럼): 지역 탭과 지역별 진행 링, 도감 번호, 출력 타입,
 * 못 본 곳(???) → 추천에 나와 발견(실루엣) → 충전해 등록(컬러 메달), 다시 충전하면 동 → 은 → 금.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StampBook(c: AppContainer) {
    val entries by c.ledger.entries.collectAsState()
    val seen by c.seen.seen.collectAsState()
    val flatBadges by c.flatBadges.collectAsState()
    var region by rememberSaveable { mutableStateOf<String?>(null) }
    val col = colors
    val now = c.demoClock.now()
    val visits = entries.filter { it.kind == RewardKind.CHARGE }.mapNotNull { it.stationId }.groupingBy { it }.eachCount()
    val limited = entries.filter { it.kind == RewardKind.OPERATOR_DAY }.mapNotNull { it.stationId }.toSet()
    val stampedAt = entries.filter { it.kind == RewardKind.NEW_STAMP }.associate { it.stationId to it.at }
    val dex = Dex.build(c.data.stations, seen, visits)
    val registered = dex.count { it.state == DexState.REGISTERED }

    ViaCard(padding = 36.dp) {
        Adaptive(Arrangement.spacedBy(36.dp)) { first ->
            Row(first, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                RingGauge(registered / dex.size.toFloat(), col.active.c(), size = 160.dp, stroke = 16.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        T("$registered/${dex.size}", ViaType.titleM)
                        T("등록", ViaType.labelS, col.text3.c())
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    T("충전소 도감", ViaType.titleM)
                    T("발견 ${dex.count { it.state != DexState.UNSEEN }}곳 · 등록 ${registered}곳", ViaType.bodyM, col.text2.c())
                    T("지역을 다 모으면 +${Defaults.POINTS_REGION_COMPLETE}P", ViaType.labelM, col.text3.c())
                }
            }
            // 지역별 진행: 작은 링 3개
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Dex.regions.forEach { r ->
                    val (got, total) = Dex.progress(dex, r.id)
                    val done = got == total
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        // 지역 엠블럼: 다 모으면 금 방패, 아니면 회색 방패와 진행 수
                        regionEmblemRes(r.id, done)?.let { Prop(it, 96.dp, alpha = if (done) 1f else 0.75f) }
                        T("$got/$total", ViaType.labelM.copy(fontWeight = FontWeight.Bold))
                        T(if (done) "${r.label} 완성" else r.label, ViaType.labelS.copy(fontWeight = if (done) FontWeight.Bold else FontWeight.Normal), if (done) col.text.c() else col.text3.c())
                    }
                }
            }
        }
    }

    // 지역 탭
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChoiceChip("전체 ${dex.size}", region == null) { region = null }
        Dex.regions.forEach { r -> ChoiceChip("${r.label} ${Dex.progress(dex, r.id).let { (g, t) -> "$g/$t" }}", region == r.id) { region = r.id } }
    }

    // 창 폭에 맞춰 열 수를 정하고 카드가 한 줄을 꽉 채우게(넓은 창에서 오른쪽이 비지 않도록)
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 20.dp
        val cols = ((maxWidth + gap) / (230.dp + gap)).toInt().coerceAtLeast(1)
        // 소수점 반올림으로 한 줄 폭을 넘으면 마지막 카드가 다음 줄로 밀리므로 1dp 여유
        val cardW = (maxWidth - gap * (cols - 1)) / cols - 1.dp
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap)) {
            dex.filter { region == null || it.station.region == region }.forEach { e ->
                val at = stampedAt[e.station.id]
                // 방금(2분 안에) 등록한 곳은 톡 커지며 나타난다
                val fresh = at != null && Duration.between(at, now).abs() < Duration.ofMinutes(2)
                DexCard(c, e, fresh, flatBadges, Modifier.width(cardW), limited = e.station.id in limited)
            }
        }
    }
}

@Composable
private fun DexCard(c: AppContainer, e: DexEntry, fresh: Boolean, flat: Boolean, modifier: Modifier, limited: Boolean = false) {
    val col = colors
    val st = e.station
    val op = c.data.memberships.firstOrNull { it.operatorId == st.operatorId }
    val initial = op?.name?.take(1) ?: "?"
    ViaCard(modifier, padding = 24.dp, highlight = if (e.tier == MedalTier.GOLD) Color(0xFFF2C24A) else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(Dex.label(e.no), ViaType.labelM.copy(fontWeight = FontWeight.Bold), col.text3.c(), Modifier.weight(1f))
            if (e.state != DexState.UNSEEN) TypeChip(e.type)
        }
        Box(Modifier.fillMaxWidth().height(132.dp), contentAlignment = Alignment.Center) {
            when {
                flat -> StationBadge(initial, badgeColor(st.operatorId), e.state == DexState.REGISTERED, fresh, size = 112.dp)
                // 사업자 데이에 충전했으면 한정 메달(보라 테두리)을 보여 준다
                e.state == DexState.REGISTERED -> StationBadge(initial, badgeColor(st.operatorId), true, fresh, size = 120.dp,
                    medal = (if (limited) eventMedalRes(st.operatorId) else null) ?: medalRes(st.operatorId, e.tier ?: MedalTier.BRONZE))
                // 발견: 흑백으로 어둡게 바랜 메달(포켓몬 도감의 그림자처럼 모양만 보인다)
                e.state == DexState.SEEN -> medalRes(st.operatorId, MedalTier.BRONZE)?.let {
                    // 다크 모드에서는 어두운 카드에 묻히지 않게 더 밝은 실루엣
                    Image(painterResource(it), "발견한 충전소", Modifier.size(120.dp), alpha = if (col.isDark) 0.95f else 0.7f, colorFilter = if (col.isDark) silhouetteDark else silhouette)
                } ?: StationBadge(initial, badgeColor(st.operatorId), false, size = 112.dp)
                else -> Image(painterResource(R.drawable.medal_locked), "못 본 충전소", Modifier.size(120.dp), alpha = 0.6f)
            }
        }
        T(if (e.state == DexState.UNSEEN) "???" else st.name, ViaType.bodyM.copy(fontWeight = FontWeight.Bold), if (e.state == DexState.UNSEEN) col.text3.c() else col.text.c(), maxLines = 1)
        val line = when (e.state) {
            DexState.UNSEEN -> "추천에 나오면 발견"
            DexState.SEEN -> "발견 · 충전하면 등록돼요"
            DexState.REGISTERED -> {
                val next = MedalTier.visitsToNext(e.visits)
                (if (limited) "한정 메달 · " else "") + "${e.tier?.label ?: ""} 메달 · ${e.visits}회 충전" + (next?.let { " · 다음까지 ${it}번" } ?: "")
            }
        }
        T(line, ViaType.labelS, col.text2.c(), maxLines = 2)
    }
}

/** 다크 모드용: 채도만 빼고 밝기는 거의 그대로. */
private val silhouetteDark = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f); timesAssign(ColorMatrix().apply { setToScale(0.9f, 0.9f, 0.95f, 1f) }) })

/** 채도를 빼고 밝기를 낮춘 색 필터(발견만 한 충전소의 메달). */
private val silhouette = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f); timesAssign(ColorMatrix().apply { setToScale(0.55f, 0.55f, 0.6f, 1f) }) })

/** 출력 타입 칩: 초급속은 진한 파랑, 급속은 옅은 파랑, 완속은 회색. */
@Composable
private fun TypeChip(type: PowerType) {
    val col = colors
    val (bg, fg) = when (type) {
        PowerType.ULTRA -> col.pos.c() to Color.White
        PowerType.FAST -> col.posSoft.c() to col.pos.c()
        PowerType.SLOW -> col.chip.c() to col.text2.c()
    }
    Row(Modifier.clip(RoundedCornerShape(999.dp)).background(bg).padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (type == PowerType.ULTRA) BoltIcon(18.dp, fg)
        T(type.label, ViaType.labelS.copy(fontWeight = FontWeight.Bold), fg)
    }
}

/** 레이드형 이벤트 충전소: 24시간 띠에 이벤트 시간대와 지금 시각. */
@Composable
fun EventsScreen(c: AppContainer) {
    val col = colors
    T("이벤트", ViaType.titleM)
    T("한산한 시간·장소에서 충전하면 포인트를 더 드려요", ViaType.bodyM, col.text2.c())
    val now = c.demoClock.now()
    OffPeakCard(c)
    T("사업자 데이", ViaType.titleS)
    c.data.operatorDays.filter { !it.date.isBefore(now.toLocalDate()) }.sortedBy { it.date }.forEach { OperatorDayCard(c, it) }
    T("이벤트 충전소", ViaType.titleS)
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
        CoinAmount("${balance}P", ViaType.headline, col.activeText.c(), coin = 88.dp)
        nextCoupon(balance, partnerCoupons)?.let { next ->
            Bar(balance / next.coupon.cost.toFloat(), col.active.c(), height = 16.dp)
            T("다음 쿠폰(${next.coupon.name})까지 ${next.remaining}P", ViaType.bodyM, col.text2.c())
        } ?: T("모든 쿠폰을 받을 수 있어요", ViaType.bodyM, col.text2.c())
    }
    T("유효기간 12개월 · 제휴 충전 할인 쿠폰으로만 바꿀 수 있어요", ViaType.labelM, col.text3.c())
    // 제휴 전이라 실제로 바꿔 주지는 않는다: 받기를 누르면 언제 받을 수 있는지 알려 준다
    var asked by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(setOf<String>()) }
    partnerCoupons.forEach { coupon ->
        val ready = balance >= coupon.cost
        TicketCard(accent = if (ready) col.active.c() else col.line.c()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                // 티켓 이미지는 가로로 긴 비율 그대로(폭만 정한다)
                couponTicketRes(coupon.operatorId)?.let { Image(painterResource(it), null, Modifier.width(150.dp), alpha = if (ready) 1f else 0.5f) }
                Column(Modifier.weight(0.72f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    T(coupon.name, ViaType.titleS, maxLines = 1)
                    T(
                        when {
                            coupon.name in asked -> "제휴가 열리면 알림으로 알려 드릴게요"
                            ready -> "${coupon.cost}P로 받을 수 있어요 · 제휴 준비 중"
                            else -> "${coupon.cost - balance}P 더 모으면 받을 수 있어요"
                        },
                        ViaType.labelM, if (coupon.name in asked) col.pos.c() else col.text3.c(), maxLines = 1,
                    )
                }
                ViaButton(
                    if (coupon.name in asked) "신청됨" else "${coupon.cost}P 받기", onClick = { asked = asked + coupon.name },
                    primary = ready && coupon.name !in asked, enabled = ready, height = 80.dp,
                )
            }
        }
    }
}
