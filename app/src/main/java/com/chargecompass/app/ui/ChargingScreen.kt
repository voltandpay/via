package com.chargecompass.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chargecompass.app.AppContainer
import com.chargecompass.core.pricing.StationRanker
import com.chargecompass.core.rewards.RewardKind

/** 충전 중 화면: 적용 요금, 비회원가 대비 예상 절약액, 이번 충전으로 받은 포인트. */
@Composable
fun ChargingScreen(c: AppContainer) {
    val session by c.rewards.activeSession.collectAsState()
    val energy by c.vehicle.energy.collectAsState()
    val owned by c.membership.owned.collectAsState()
    val entries by c.ledger.entries.collectAsState()
    val s = session ?: return
    val station = c.data.stations.firstOrNull { it.id == s.stationId }
    val car = c.vehicleModel()
    val ranked = if (station != null && car != null) {
        StationRanker.rank(listOf(station.copy(alongRouteKm = 0.0, detourKm = 0.0, status = com.chargecompass.core.model.ChargerStatus.AVAILABLE)), c.data.tariffs, owned, energy, car).firstOrNull()
    } else null
    val earned = entries.filter { !it.at.isBefore(s.startedAt) && it.stationId == s.stationId && s.stationId != null }

    Column(Modifier.fillMaxSize().padding(40.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Caption("충전 중 · 배터리 ${energy.socPct ?: "-"}%")
        Card {
            if (station == null || ranked == null) {
                Title("충전소 확인 불가", Warn)
                Caption("위치로 충전소를 찾지 못해 요금과 포인트를 계산하지 않았어요")
            } else {
                Title(station.name)
                Body("${ranked.price.wonPerKwh}원/kWh (${ranked.price.basis.label()})")
                Body("80%까지 약 ${won(ranked.totalCostWon)} · 비회원가보다 ${won(ranked.savingWon)} 절약 예상", Accent)
            }
        }
        if (earned.isNotEmpty()) Card {
            Caption("이번 충전으로 받은 포인트")
            Title("+${earned.sumOf { it.points }}P", Accent)
            if (earned.any { it.kind == RewardKind.NEW_STAMP }) Body("새 스탬프 획득!")
            if (earned.any { it.kind == RewardKind.EVENT_BONUS }) Body("이벤트 충전소 보너스!")
        }
    }
}
