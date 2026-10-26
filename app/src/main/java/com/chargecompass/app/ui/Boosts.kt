package com.chargecompass.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chargecompass.app.AppContainer
import com.chargecompass.app.rewards.discountCharges
import com.chargecompass.core.Defaults
import com.chargecompass.core.missions.Missions
import com.chargecompass.core.model.OperatorDay
import com.chargecompass.core.rewards.RewardEngine
import java.time.temporal.ChronoUnit

/** 이번 주 미션 3개(필드 리서치)와 리서치 돌파 스탬프 7칸. */
@Composable
fun WeeklyMissionsCard(c: AppContainer) {
    val entries by c.ledger.entries.collectAsState()
    val summaries by c.summaries.summaries.collectAsState()
    val col = colors
    val today = c.demoClock.now().toLocalDate()
    val progress = Missions.progress(today, entries.filter { !it.at.toLocalDate().isAfter(today) }, discountCharges(summaries, today))
    val stamps = Missions.breakthroughStamps(entries)
    val left = 7 - ChronoUnit.DAYS.between(Missions.weekStart(today), today).toInt()
    ViaCard(padding = 36.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T("이번 주 미션", ViaType.labelM, col.text3.c())
                T("${progress.count { it.done }} / ${progress.size} 달성", ViaType.titleS)
            }
            T("${left}일 남음 · 미션마다 +${Defaults.POINTS_MISSION}P", ViaType.labelM, col.text3.c())
        }
        Adaptive(Arrangement.spacedBy(28.dp)) { first ->
            progress.forEach { p ->
                Column(if (isWide) Modifier.weight(1f) else Modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(if (p.done) col.active.c() else col.track.c()), contentAlignment = Alignment.Center) {
                            if (p.done) CheckMark(color = Color.White)
                        }
                        T(p.mission.title, ViaType.bodyM.copy(fontWeight = if (p.done) FontWeight.Bold else FontWeight.Normal), maxLines = 1)
                    }
                    Bar(p.count.coerceAtMost(p.mission.target) / p.mission.target.toFloat(), if (p.done) col.active.c() else col.pos.c(), height = 12.dp)
                    T("${p.count.coerceAtMost(p.mission.target)} / ${p.mission.target}", ViaType.labelS, col.text3.c())
                }
            }
        }
        DashedDivider()
        // 리서치 돌파: 미션 스탬프 7개를 모으면 큰 보상
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Prop(com.chargecompass.app.R.drawable.prop_chest_closed, 64.dp)
            T("리서치 돌파", ViaType.labelM.copy(fontWeight = FontWeight.Bold), modifier = Modifier.width(120.dp))
            repeat(Defaults.MISSIONS_PER_BREAKTHROUGH) { i ->
                Box(Modifier.size(36.dp).clip(CircleShape).background(if (i < stamps) col.event.c() else col.track.c()), contentAlignment = Alignment.Center) {
                    if (i < stamps) StampIcon(22.dp, Color.White)
                }
            }
            T("${Defaults.MISSIONS_PER_BREAKTHROUGH - stamps}개 더 모으면 +${Defaults.POINTS_BREAKTHROUGH}P", ViaType.labelM, col.text2.c(), Modifier.padding(start = 8.dp))
        }
    }
}

/** 경부하 부스트: 전기가 싼 시간대에 충전하면 충전 포인트 2배(시연용 예시 시간대). */
@Composable
fun OffPeakCard(c: AppContainer) {
    val col = colors
    val now = c.demoClock.now()
    val nowMin = now.hour * 60 + now.minute
    val live = RewardEngine.isOffPeak(now.hour)
    ViaCard(padding = 36.dp, highlight = if (live) col.pos.c() else null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Prop(com.chargecompass.app.R.drawable.prop_moon, 64.dp)
            T("경부하 부스트", ViaType.titleS, modifier = Modifier.weight(1f))
            Chip(eventStatus(Defaults.OFF_PEAK_START_HOUR, Defaults.OFF_PEAK_END_HOUR, nowMin), bg = if (live) col.pos.c() else col.chip.c(), fg = if (live) Color.White else col.text2.c(), bold = live)
        }
        T("${Defaults.OFF_PEAK_START_HOUR}시 ~ ${Defaults.OFF_PEAK_END_HOUR}시 충전 시작 시 충전 포인트 2배 · 전기가 싸고 전력망도 한가한 시간", ViaType.bodyM, col.text2.c())
        UpcomingTimeline(Defaults.OFF_PEAK_START_HOUR, Defaults.OFF_PEAK_END_HOUR, nowMin, col.pos.c())
        T("시간대는 시연용 예시예요. 실제 서비스에서는 사업자별 시간대 요금에 맞춥니다.", ViaType.labelS, col.text3.c())
    }
}

/** 오늘이거나 다가오는 사업자 데이(가장 가까운 것). */
fun upcomingOperatorDay(days: List<OperatorDay>, today: java.time.LocalDate): OperatorDay? =
    days.filter { !it.date.isBefore(today) }.minByOrNull { it.date }

/** 사업자 데이(커뮤니티 데이): 그날 그 사업자 충전소 포인트 3배 + 한정 메달. */
@Composable
fun OperatorDayCard(c: AppContainer, day: OperatorDay) {
    val col = colors
    val today = c.demoClock.now().toLocalDate()
    val dDay = ChronoUnit.DAYS.between(today, day.date)
    val live = dDay == 0L
    val stations = c.data.stations.filter { it.operatorId == day.operatorId }
    ViaCard(padding = 36.dp, highlight = if (live) col.event.c() else null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            StationBadge(day.title.take(1), badgeColor(day.operatorId), true, size = 72.dp, medal = eventMedalRes(day.operatorId))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                T(day.title, ViaType.titleS)
                T("${day.date.monthValue}/${day.date.dayOfMonth}(${day.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.KOREAN)}) · 포인트 ${day.multiplier}배 + 한정 메달", ViaType.bodyM, col.text2.c())
                if (stations.isNotEmpty()) T("시연 경로: " + stations.joinToString { it.name }, ViaType.labelS, col.text3.c())
            }
            Chip(if (live) "오늘!" else "D-$dDay", bg = if (live) col.event.c() else col.chip.c(), fg = if (live) Color.White else col.text2.c(), bold = true)
        }
    }
}

@Composable
private fun CheckMark(color: Color) = LineIcon(20.dp, color) { s ->
    listOf(androidx.compose.ui.graphics.Path().apply { moveTo(5 * s, 12.5f * s); lineTo(10 * s, 17 * s); lineTo(19 * s, 7 * s) })
}

/** 리서치 돌파 직후(10분 안): 열린 상자와 +100P. 정차 중 화면이라 상자가 톡 커지고 짧은 축하 효과. */
@Composable
fun BreakthroughCard(c: AppContainer) {
    val entries by c.ledger.entries.collectAsState()
    val now = c.demoClock.now()
    val last = entries.lastOrNull { it.kind == com.chargecompass.core.rewards.RewardKind.BREAKTHROUGH } ?: return
    var dismissed by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    if (dismissed == last.at.toString() || java.time.Duration.between(last.at, now).abs() > java.time.Duration.ofMinutes(10)) return
    val col = colors
    val pop = androidx.compose.runtime.remember(last.at) { androidx.compose.animation.core.Animatable(0.5f) }
    androidx.compose.runtime.LaunchedEffect(last.at) {
        pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessLow))
    }
    Box {
        ViaCard(padding = 36.dp, highlight = col.event.c()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                Prop(com.chargecompass.app.R.drawable.prop_chest_open, 200.dp, Modifier.scale(pop.value))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    T("리서치 돌파!", ViaType.titleL, col.event.c())
                    T("주간 미션 스탬프 ${Defaults.MISSIONS_PER_BREAKTHROUGH}개를 모았어요", ViaType.bodyM, col.text2.c())
                    CoinAmount("+${last.points}P", ViaType.headline, col.activeText.c(), coin = 64.dp)
                }
                Box(Modifier.size(64.dp).clip(CircleShape).pressable { dismissed = last.at.toString() }, contentAlignment = Alignment.Center) {
                    CloseIcon(color = col.text3.c())
                }
            }
        }
        ConfettiBurst(last.at, Modifier.matchParentSize())
    }
}
