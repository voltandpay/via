package com.chargecompass.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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

/**
 * 장거리 충전 계획: 한 번 충전으로 목적지까지 못 갈 때 정류지 2~3곳을 순서대로 보여 준다.
 * 각 정류지는 도착 배터리 → 충전 목표, 예상 요금. 경유지로 넣은 정류지는 강조한다.
 */
@Composable
fun TripPlanCard(c: AppContainer) {
    val plan by c.reco.tripPlan.collectAsState()
    val reco by c.reco.state.collectAsState()
    val route by c.navi.route.collectAsState()
    val p = plan ?: return
    val col = colors
    val added = (reco as? RecoState.Added)?.station?.station?.id
    ViaCard(padding = 36.dp) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                T("${route?.destinationName ?: "목적지"}까지 충전 계획", ViaType.labelM, col.text3.c())
                T("충전 ${p.stops.size}번 · 예상 ${won(p.totalCostWon)}", ViaType.titleM)
            }
            T("도착 ${p.arrivalSoc}%", ViaType.labelM, col.text3.c())
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            p.stops.forEachIndexed { i, s ->
                val now = s.station.id == added
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(if (now) col.active.c() else col.text3.c().copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                        T("${i + 1}", ViaType.labelM.copy(fontWeight = FontWeight.Bold), if (now) Color.White else col.text2.c())
                    }
                    Column(Modifier.weight(1f)) {
                        T(s.station.name, ViaType.titleS, if (now) col.activeText.c() else col.text.c(), maxLines = 1)
                        T("${s.km.toInt()}km · ${s.arrivalSoc}% → ${s.chargeToSoc}% · ${"%.0f".format(s.kwh)}kWh", ViaType.bodyM, col.text2.c(), maxLines = 1)
                    }
                    T(won(s.costWon), ViaType.bodyM, col.text2.c())
                }
            }
        }
    }
}
