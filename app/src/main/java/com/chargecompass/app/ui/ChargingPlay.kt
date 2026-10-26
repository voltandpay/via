package com.chargecompass.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.chargecompass.app.AppContainer
import com.chargecompass.app.R
import com.chargecompass.core.dex.Dex
import com.chargecompass.core.dex.DexState
import com.chargecompass.core.dex.MedalTier
import com.chargecompass.core.rewards.RewardKind
import com.chargecompass.core.season.SeasonReward
import com.chargecompass.core.season.Seasons
import java.time.temporal.ChronoUnit

private val grayscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/**
 * 충전 중 메달 진열장: 도감 9곳의 메달을 3D 선반 위에 놓는다(지금 충전소는 살짝 크게).
 * 등록은 단계 메달(사업자 데이면 한정 메달), 발견은 흑백, 못 본 곳은 흐린 잠긴 메달.
 */
@Composable
fun MedalShelfCard(c: AppContainer, currentStationId: String) {
    val entries by c.ledger.entries.collectAsState()
    val seen by c.seen.seen.collectAsState()
    val col = colors
    val visits = entries.filter { it.kind == RewardKind.CHARGE }.mapNotNull { it.stationId }.groupingBy { it }.eachCount()
    val limited = entries.filter { it.kind == RewardKind.OPERATOR_DAY }.mapNotNull { it.stationId }.toSet()
    val dex = Dex.build(c.data.stations, seen, visits)
    val registered = dex.filter { it.state == DexState.REGISTERED }
    ViaCard(padding = 32.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T("메달 진열장", ViaType.labelM, col.text3.c())
                T("${registered.size} / ${dex.size}곳 등록", ViaType.titleS)
            }
            T(MedalTier.entries.reversed().joinToString(" · ") { t -> "${t.label} ${registered.count { it.tier == t }}" }, ViaType.labelM, col.text2.c())
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val shelfH = maxWidth * (125f / 1400f)
            val slot = maxWidth * 0.86f / dex.size
            val medal = min(slot * 0.92f, 120.dp)
            val side = maxWidth * 0.07f
            Box(Modifier.fillMaxWidth().height(medal * 1.15f + shelfH)) {
                Image(painterResource(R.drawable.prop_shelf), null, Modifier.fillMaxWidth().align(Alignment.BottomCenter))
                // 선반 판 윗면(그림 맨 위에서 약 5%)에 메달 아래쪽이 닿게
                Row(
                    Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = shelfH * 0.95f, start = side, end = side),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom,
                ) {
                    dex.forEach { e ->
                        val here = e.station.id == currentStationId
                        val size = if (here) medal * 1.15f else medal
                        val res = when (e.state) {
                            DexState.REGISTERED -> (if (e.station.id in limited) eventMedalRes(e.station.operatorId) else null) ?: medalRes(e.station.operatorId, e.tier ?: MedalTier.BRONZE)
                            DexState.SEEN -> medalRes(e.station.operatorId, MedalTier.BRONZE)
                            DexState.UNSEEN -> R.drawable.medal_locked
                        } ?: R.drawable.medal_locked
                        Image(
                            painterResource(res), e.station.name, Modifier.size(size),
                            alpha = when (e.state) { DexState.REGISTERED -> 1f; DexState.SEEN -> if (col.isDark) 0.85f else 0.6f; DexState.UNSEEN -> if (col.isDark) 0.5f else 0.35f },
                            colorFilter = if (e.state == DexState.SEEN) grayscale else null,
                        )
                    }
                }
            }
        }
    }
}

/** 충전 혜택 시즌 트랙: 시즌 경험치(이번 시즌에 받은 포인트)로 단계를 넘으면 코인·상자·시즌 메달 보상. */
@Composable
fun SeasonTrackCard(c: AppContainer) {
    val entries by c.ledger.entries.collectAsState()
    val col = colors
    val now = c.demoClock.now()
    val season = Seasons.of(now.toLocalDate())
    val xp = Seasons.xp(season, entries, now)
    val got = entries.filter { it.kind == RewardKind.SEASON_STEP }.mapNotNull { it.eventId }.toSet()
    val left = ChronoUnit.DAYS.between(now.toLocalDate(), season.end) + 1
    ViaCard(padding = 32.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Prop(R.drawable.medal_season, 72.dp)
            Column(Modifier.weight(1f)) {
                T("충전 혜택 시즌 ${season.number}", ViaType.titleS)
                T("${season.start.monthValue}/${season.start.dayOfMonth} ~ ${season.end.monthValue}/${season.end.dayOfMonth} · ${left}일 남음", ViaType.labelM, col.text3.c())
            }
            Column(horizontalAlignment = Alignment.End) {
                T("시즌 경험치", ViaType.labelS, col.text3.c())
                T("$xp", ViaType.titleM, col.event.c())
            }
        }
        val fraction = seasonTrackFraction(xp, Seasons.steps.map { it.xp })
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val w = maxWidth
            val node = 64.dp
            Column {
                // 보상 아이콘 줄: 단계 위치(오른쪽 끝 정렬)에 놓는다
                Box(Modifier.fillMaxWidth().height(node)) {
                    Seasons.steps.forEachIndexed { i, step ->
                        val reached = xp >= step.xp
                        val res = when (step.reward) {
                            SeasonReward.COIN -> R.drawable.prop_coin
                            SeasonReward.CHEST -> if (reached) R.drawable.prop_chest_f5 else R.drawable.prop_chest_f0
                            SeasonReward.MEDAL -> R.drawable.medal_season
                        }
                        val x = w * ((i + 1f) / Seasons.steps.size) - node
                        Box(Modifier.offset(x = x)) {
                            Image(painterResource(res), null, Modifier.size(node), alpha = if (reached) 1f else 0.4f)
                            if ("${season.id}-${i + 1}" in got) Box(Modifier.size(24.dp).clip(CircleShape).background(col.active.c()).align(Alignment.TopEnd), contentAlignment = Alignment.Center) {
                                T("✓", ViaType.labelS.copy(fontWeight = FontWeight.Bold), Color.White)
                            }
                        }
                    }
                }
                // 진행 막대
                Box(Modifier.fillMaxWidth().padding(vertical = 10.dp).height(14.dp).clip(RoundedCornerShape(7.dp)).background(col.track.c())) {
                    Box(Modifier.width(w * fraction).height(14.dp).clip(RoundedCornerShape(7.dp)).background(col.event.c()))
                }
                Box(Modifier.fillMaxWidth().height(48.dp)) {
                    Seasons.steps.forEachIndexed { i, step ->
                        Column(Modifier.offset(x = w * ((i + 1f) / Seasons.steps.size) - node).width(node), horizontalAlignment = Alignment.CenterHorizontally) {
                            T("${step.xp}", ViaType.labelS.copy(fontWeight = FontWeight.Bold), if (xp >= step.xp) col.text.c() else col.text3.c())
                            T("+${step.bonus}P", ViaType.labelS, col.text3.c())
                        }
                    }
                }
            }
        }
        T("충전·미션·이벤트로 받은 포인트가 시즌 경험치가 돼요 · 시즌은 4주마다 새로 시작", ViaType.labelS, col.text3.c())
    }
}
