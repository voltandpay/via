package com.chargecompass.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chargecompass.app.AppContainer
import com.chargecompass.app.navi.RouteSnapshot
import com.chargecompass.app.navi.StopResult
import com.chargecompass.app.recommend.RecoState
import com.chargecompass.core.pricing.RankedStation
import kotlin.math.roundToInt

/** 주행 중 화면. 클릭할 수 있는 요소를 두지 않는다 — 안내와 확인은 음성으로만. */
@Composable
fun DriveCard(c: AppContainer) {
    val reco by c.reco.state.collectAsState()
    val route by c.navi.route.collectAsState()
    val energy by c.vehicle.energy.collectAsState()
    val col = colors
    Column(Modifier.fillMaxSize().padding(48.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ViaMark()
            T("주행 중 · 안내는 음성으로 드려요 · \"제일 싼 충전소 어디야?\"", ViaType.labelM, col.text2.c(), Modifier.weight(1f))
            route?.let { Chip("${it.destinationName}까지 ${it.remainingKm.roundToInt()}km · ${it.remainingSec / 60}분", fg = col.text2.c()) }
            // 주행 중 화면의 유일한 터치 요소: 큰 말하기 버튼(누른 뒤에는 음성으로만 주고받는다)
            VoiceAskButton(c)
        }
        val arrival = when (val s = reco) {
            is RecoState.Enough -> s.arrivalSoc
            is RecoState.Suggested -> s.arrivalSoc
            is RecoState.AwaitingTouch -> s.arrivalSoc
            else -> null
        }
        val best = when (val s = reco) {
            is RecoState.Suggested -> s.top.first()
            is RecoState.AwaitingTouch -> s.choice
            is RecoState.Adding -> s.station
            is RecoState.Added -> s.station
            else -> null
        }
        Panels(Modifier.weight(1f)) { mod ->
            ViaCard(mod, padding = 48.dp) { BatteryPanel(arrival, energy.socPct, route) }
            ViaCard(if (isWide) Modifier.weight(1.35f).fillMaxHeight() else Modifier, padding = 48.dp) { RecommendPanel(reco, best, route) }
        }
        VoiceBar(reco)
    }
}

/** 넓은 창은 나란히, 좁은 창은 위아래로. 첫 패널에 줄 Modifier를 넘긴다. */
@Composable
private fun Panels(modifier: Modifier, content: @Composable (Modifier) -> Unit) {
    if (isWide) {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp)) { content(Modifier.weight(1f).fillMaxHeight()) }
    } else {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) { content(Modifier) }
    }
}

@Composable
private fun ColumnScope.BatteryPanel(arrival: Double?, socNow: Int?, route: RouteSnapshot?) {
    val col = colors
    T(if (arrival == null) "지금 배터리" else "도착 예상 배터리", ViaType.bodyM, col.text2.c())
    if (arrival == null) {
        T(socNow?.let { "$it%" } ?: "-", ViaType.display)
        T(if (route == null) "목적지를 정하면 충전이 필요한지 알려드려요" else "계산 중이에요", ViaType.bodyM, col.text2.c())
        return
    }
    val low = arrival < 15
    val tint = if (low) col.neg.c() else col.activeText.c()
    // 한눈에: 넓은 창에서는 숫자를 더 크게, 막대·설명은 패널 아래로
    T("${arrival.roundToInt().coerceAtLeast(0)}%", if (isWide) ViaType.display.copy(fontSize = ViaType.display.fontSize * 1.5f, lineHeight = ViaType.display.lineHeight * 1.5f) else ViaType.display, tint)
    T(if (low) "충전이 필요해요" else "충전 없이 도착해요", ViaType.titleM, tint)
    if (isWide) Spacer(Modifier.weight(1f))
    Bar((arrival / 100).toFloat(), if (low) col.neg.c() else col.active.c(), height = 36.dp, marker = 0.15f)
    Row(Modifier.fillMaxWidth()) {
        T("0%", ViaType.labelM, col.text3.c(), Modifier.weight(0.15f))
        T("충전 권장 15%", ViaType.labelM, col.text3.c(), Modifier.weight(0.7f))
        T("100%", ViaType.labelM, col.text3.c())
    }
    T("지금 ${socNow ?: "-"}% · 남은 거리 ${route?.remainingKm?.roundToInt() ?: "-"}km", ViaType.bodyL, col.text2.c())
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.RecommendPanel(reco: RecoState, best: RankedStation?, route: RouteSnapshot?) {
    val col = colors
    if (best == null) {
        T(
            when (reco) {
                RecoState.NoneAvailable -> "경로 위에 쓸 수 있는 충전소가 없어요"
                is RecoState.Enough -> "이번 경로는 충전 없이 갈 수 있어요"
                else -> "충전이 필요하면 경로 위 가장 싼 충전소를 알려드려요"
            },
            ViaType.titleS, if (reco == RecoState.NoneAvailable) col.neg.c() else col.text.c(),
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        T(if (reco is RecoState.Added) "경유지로 추가한 충전소" else "추천 충전소 · 내 멤버십 기준 최저가", ViaType.bodyM, col.text2.c())
        T(best.station.name, ViaType.headline, maxLines = 1)
    }
    if (best.savingWon > 0) T("${won(best.savingWon)} 절약", ViaType.titleL, col.activeText.c())
    if (isWide) Spacer(Modifier.weight(1f))
    RouteStrip(best.station.alongRouteKm, route?.remainingKm, route?.destinationName ?: "목적지")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Chip("${best.price.wonPerKwh}원/kWh · ${best.price.basis.label()}", bold = true)
        Chip("${best.station.maxKw}kW · 사용 가능")
    }
}

/** 지금 —(km)— 충전소 —(km)— 목적지. */
@Composable
private fun RouteStrip(toStationKm: Double, totalKm: Double?, destination: String) {
    val col = colors
    val rest = totalKm?.let { (it - toStationKm).coerceAtLeast(0.0) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Stop("지금") { Box(Modifier.size(28.dp).clip(CircleShape).background(col.text.c())) }
        Leg("${toStationKm.roundToInt()}km", col.pos.c(), (toStationKm.coerceAtLeast(1.0)).toFloat())
        Stop("충전소", bold = true) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(col.active.c()), contentAlignment = Alignment.Center) {
                BoltIcon()
            }
        }
        Leg(rest?.let { "${it.roundToInt()}km" } ?: "", col.track.c(), (rest ?: 1.0).coerceAtLeast(1.0).toFloat())
        Stop(destination) { Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(col.text.c())) }
    }
}

@Composable
private fun Stop(label: String, bold: Boolean = false, dot: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        dot()
        T(label, ViaType.labelM.copy(fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal), if (bold) colors.text.c() else colors.text2.c())
    }
}

@Composable
private fun RowScope.Leg(label: String, color: Color, weight: Float) {
    Column(Modifier.weight(weight).padding(bottom = 36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        T(label, ViaType.labelM, colors.text3.c())
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
    }
}

/** 지금 음성으로 무엇을 하고 있는지. 누르는 곳이 아니다. */
@Composable
private fun VoiceBar(reco: RecoState) {
    val col = colors
    val (title, sub) = when (reco) {
        is RecoState.Suggested -> "경유지로 추가할까요?" to "\"응\"이라고 말하면 내비에 추가돼요 · 화면은 만지지 않아도 돼요"
        is RecoState.AwaitingTouch -> "대답을 듣지 못했어요" to "정차하면 화면에서 한 번에 추가할 수 있어요"
        is RecoState.Adding -> "경유지로 추가하는 중" to "내비에 반영하고 있어요"
        is RecoState.Added -> if (reco.result == StopResult.FAILED) "경유지 추가에 실패했어요" to "정차 후 다시 시도해 주세요" else "경유지로 추가했어요" to "내비 안내를 따라가세요"
        else -> return
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(col.posSoft.c()).padding(horizontal = 36.dp, vertical = 28.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(col.pos.c()), contentAlignment = Alignment.Center) {
            MicIcon()
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            T(title, ViaType.bodyL.copy(fontWeight = FontWeight.Bold))
            T(sub, ViaType.labelM, col.text2.c())
        }
    }
}
