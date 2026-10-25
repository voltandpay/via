package com.chargecompass.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chargecompass.app.AppContainer
import com.chargecompass.app.recommend.RecoState
import com.chargecompass.core.pricing.PriceBasis

fun PriceBasis.label() = when (this) {
    PriceBasis.MEMBER -> "회원가"
    PriceBasis.ROAMING -> "로밍가"
    PriceBasis.NON_MEMBER -> "비회원가"
}

/** 정차 중 후보 비교: 최대 3곳. */
@Composable
fun CompareScreen(c: AppContainer) {
    val reco by c.reco.state.collectAsState()
    val top = when (val s = reco) {
        is RecoState.Suggested -> s.top
        is RecoState.AwaitingTouch -> s.top
        else -> emptyList()
    }
    Title("경로 위 충전소 비교")
    if (top.isEmpty()) {
        Body("경로 안내 중 충전이 필요하면 내 멤버십 기준 후보 3곳을 보여드려요", Muted)
        return
    }
    top.forEachIndexed { i, r ->
        Card {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(1f)) {
                    Caption("${i + 1}위 · ${r.station.maxKw}kW · 우회 ${r.station.detourKm}km")
                    Title(r.station.name)
                    Body("${r.price.wonPerKwh}원/kWh (${r.price.basis.label()}) · 총 ${won(r.totalCostWon)}")
                    Body("비회원가보다 ${won(r.savingWon)} 절약", Accent)
                }
                Button(onClick = { c.reco.confirmByTouch(r.station.id) }, modifier = Modifier.height(88.dp)) {
                    Text("경유지 추가", fontSize = 32.sp)
                }
            }
        }
    }
}
