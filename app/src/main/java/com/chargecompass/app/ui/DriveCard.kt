package com.chargecompass.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chargecompass.app.AppContainer
import com.chargecompass.app.navi.StopResult
import com.chargecompass.app.recommend.RecoState
import kotlin.math.roundToInt

/** 주행 중 화면. 클릭할 수 있는 요소를 두지 않는다 — 안내와 확인은 음성으로만. */
@Composable
fun DriveCard(c: AppContainer) {
    val reco by c.reco.state.collectAsState()
    Column(Modifier.fillMaxSize().padding(48.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Caption("주행 중 · 안내는 음성으로 드려요")
        Card {
            when (val s = reco) {
                RecoState.Idle -> Body("목적지를 정하면 충전이 필요한지 알려드려요")
                is RecoState.Enough -> {
                    Caption("도착 예상 배터리")
                    BigSoc(s.arrivalSoc)
                    Body("충전 없이 도착할 수 있어요", Accent)
                }
                is RecoState.Suggested -> Suggestion(s.arrivalSoc, s.top.first())
                is RecoState.AwaitingTouch -> {
                    Suggestion(null, s.choice)
                    Caption("정차하면 화면에서 경유지로 추가할 수 있어요")
                }
                is RecoState.Added -> {
                    Caption(if (s.result == StopResult.FAILED) "경유지 추가 실패" else "경유지로 추가했어요")
                    Title(s.station.station.name, if (s.result == StopResult.FAILED) Warn else Accent)
                    Body("${s.station.price.wonPerKwh}원/kWh · ${won(s.station.savingWon)} 절약 예상")
                }
                is RecoState.Adding -> { Caption("경유지로 추가하는 중"); Title(s.station.station.name) }
                RecoState.NoneAvailable -> Body("경로 위에 쓸 수 있는 충전소가 없어요", Warn)
            }
        }
    }
}

@Composable
private fun BigSoc(soc: Double) =
    Text("${soc.roundToInt().coerceAtLeast(0)}%", fontSize = 150.sp, fontWeight = FontWeight.Bold, color = if (soc < 15) Warn else Accent)

@Composable
private fun Suggestion(arrival: Double?, best: com.chargecompass.core.pricing.RankedStation) {
    if (arrival != null) {
        Caption("도착 예상 배터리")
        BigSoc(arrival)
    }
    Caption("추천 충전소")
    Title(best.station.name)
    Body("${best.station.alongRouteKm.roundToInt()}km 앞 · ${best.price.wonPerKwh}원/kWh · ${won(best.savingWon)} 절약 예상")
}
