package com.chargecompass.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.chargecompass.app.recommend.RecoState
import com.chargecompass.core.model.ChargerStatus
import com.chargecompass.core.pricing.PriceBasis
import kotlin.math.roundToInt

fun PriceBasis.label() = when (this) {
    PriceBasis.MEMBER -> "회원가"
    PriceBasis.ROAMING -> "로밍가"
    PriceBasis.NON_MEMBER -> "비회원가"
}

/** 정차 중 후보 비교: 총비용 순 최대 3곳, 1위 강조, 추천에서 뺀 곳과 이유. */
@Composable
fun CompareScreen(c: AppContainer) {
    val reco by c.reco.state.collectAsState()
    val route by c.navi.route.collectAsState()
    val owned by c.membership.owned.collectAsState()
    val col = colors
    val (top, arrival) = when (val s = reco) {
        is RecoState.Suggested -> s.top to s.arrivalSoc
        is RecoState.AwaitingTouch -> s.top to s.arrivalSoc
        else -> emptyList<com.chargecompass.core.pricing.RankedStation>() to null
    }
    val addingId = (reco as? RecoState.Adding)?.station?.station?.id

    if (top.isEmpty() && addingId == null) {
        EmptyState({ PinIcon(color = col.pos.c()) }, "아직 비교할 충전소가 없어요", "내비에서 목적지를 정하면, 충전이 필요할 때 내 멤버십 기준 후보 3곳을 보여드려요")
        return
    }
    Adaptive(Arrangement.spacedBy(12.dp)) { first ->
        T("${route?.destinationName ?: "목적지"}까지 ${route?.remainingKm?.roundToInt() ?: "-"}km · 도착 예상 ${arrival?.roundToInt()?.coerceAtLeast(0) ?: "-"}%", ViaType.bodyL.copy(fontWeight = FontWeight.Bold), modifier = first)
        val names = c.data.memberships.filter { it.id in owned }.joinToString { it.name }.ifEmpty { "없음(비회원가)" }
        T("내 멤버십: $names · 80%까지 충전 기준 총비용", ViaType.labelM, col.text3.c())
    }
    top.forEachIndexed { i, r ->
        val first = i == 0
        val nonMember = c.data.tariffs[r.station.operatorId]?.nonMemberWonPerKwh ?: r.price.wonPerKwh
        ViaCard(padding = 32.dp, highlight = if (first) col.pos.c() else null) {
            Adaptive(Arrangement.spacedBy(28.dp)) { grow ->
                Row(grow, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(if (first) col.pos.c() else col.track.c()), contentAlignment = Alignment.Center) {
                        T("${i + 1}", ViaType.bodyM.copy(fontWeight = FontWeight.ExtraBold), if (first) Color.White else col.text.c())
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        T(r.station.name, ViaType.titleS)
                        T("${r.station.alongRouteKm.roundToInt()}km 앞 · 우회 ${r.station.detourKm}km · ${r.station.maxKw}kW · 사용 가능", ViaType.labelM, col.text3.c())
                    }
                }
                Column(if (isWide) Modifier.weight(0.8f) else Modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    T("${r.price.wonPerKwh}원/kWh · ${r.price.basis.label()}", ViaType.bodyM.copy(fontWeight = FontWeight.Bold))
                    Bar(r.price.wonPerKwh / nonMember.toFloat(), if (first) col.active.c() else col.muted.c(), height = 14.dp)
                    T("비회원가 ${nonMember}원/kWh", ViaType.labelS, col.text3.c())
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    T(won(r.totalCostWon), ViaType.titleS)
                    if (r.savingWon > 0) T("${won(r.savingWon)} 절약", ViaType.labelM.copy(fontWeight = FontWeight.Bold), col.activeText.c())
                }
                ViaButton(
                    "경유지 추가", onClick = { c.reco.confirmByTouch(r.station.id) },
                    primary = first, loading = addingId == r.station.id, enabled = addingId == null || addingId == r.station.id, height = 88.dp,
                )
            }
        }
    }
    // 추천에서 뺀 곳: 고장·사용 중·커넥터 불일치
    val car = c.vehicleModel()
    val broken = c.data.stations.count { it.status == ChargerStatus.BROKEN || it.status == ChargerStatus.MAINTENANCE }
    val inUse = c.data.stations.count { it.status == ChargerStatus.IN_USE }
    val mismatch = car?.let { v -> c.data.stations.count { it.status == ChargerStatus.AVAILABLE && it.connectors.none { k -> k in v.connectors } } } ?: 0
    if (broken + inUse + mismatch > 0) {
        T(
            "추천에서 뺀 곳 ${broken + inUse + mismatch}곳 · 고장 $broken · 사용 중 $inUse · 커넥터 불일치 $mismatch",
            ViaType.labelM, col.text2.c(),
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(col.chip.c()).padding(horizontal = 28.dp, vertical = 20.dp),
        )
    }
}
