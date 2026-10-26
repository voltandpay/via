package com.chargecompass.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chargecompass.app.AppContainer
import com.chargecompass.core.Defaults
import com.chargecompass.core.model.ChargerStatus
import com.chargecompass.core.pricing.StationRanker
import com.chargecompass.core.rewards.RewardKind

/** 충전 중 화면: 절약액이 주인공. 적용 요금, 비회원가 대비 막대, 배터리 진행, 이번 충전 포인트. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChargingScreen(c: AppContainer) {
    val session by c.rewards.activeSession.collectAsState()
    val energy by c.vehicle.energy.collectAsState()
    val owned by c.membership.owned.collectAsState()
    val earned by c.rewards.sessionEarned.collectAsState()
    val col = colors
    val s = session ?: return
    val station = c.data.stations.firstOrNull { it.id == s.stationId }
    val car = c.vehicleModel()
    val ranked = if (station != null && car != null) {
        StationRanker.rank(listOf(station.copy(alongRouteKm = 0.0, detourKm = 0.0, status = ChargerStatus.AVAILABLE)), c.data.tariffs, owned, energy, car).firstOrNull()
    } else null

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(48.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ViaMark()
            Column(Modifier.weight(1f)) {
                T("충전 중", ViaType.labelM, col.text3.c())
                T(station?.name ?: "충전소 확인 중", ViaType.titleM)
            }
            station?.let { Chip("● ${it.maxKw}kW 충전 중", bg = col.activeSoft.c(), fg = col.activeText.c(), bold = true) }
        }

        if (station == null || ranked == null) {
            ViaCard(padding = 48.dp) {
                T("충전소를 확인하지 못했어요", ViaType.titleM, col.neg.c())
                T("위치가 잡히면 요금과 포인트를 바로 계산해요", ViaType.bodyM, col.text2.c())
            }
            return@Column
        }
        val nonMemberTotal = ranked.nonMemberCostWon
        val eta = car?.let { chargeEtaMinutes(energy.socPct, Defaults.TARGET_SOC.toInt(), it.batteryKwh, station.maxKw) }
        Adaptive(Arrangement.spacedBy(28.dp)) { first ->
            ViaCard(first, padding = 48.dp) {
                Adaptive(Arrangement.spacedBy(48.dp)) { textSide ->
                    // 배터리: 현재 → 목표 80% 원형 게이지(충전 중이라 차오르는 애니메이션 허용)
                    RingGauge(
                        (energy.socPct ?: 0) / 100f, col.pos.c(), size = if (isWide) 300.dp else 260.dp,
                        marker = (Defaults.TARGET_SOC / 100).toFloat(),
                        modifier = if (isWide) Modifier else Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            T("${energy.socPct ?: "-"}%", ViaType.headline)
                            T("목표 ${Defaults.TARGET_SOC.toInt()}%" + (eta?.let { " · 약 ${it}분" } ?: ""), ViaType.labelM, col.text3.c())
                        }
                    }
                    Column(textSide, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        T("비회원가보다 절약 예상", ViaType.bodyM, col.text2.c())
                        CountUpWon(ranked.savingWon, if (isWide) ViaType.display else ViaType.display.copy(fontSize = ViaType.headline.fontSize * 1.4f), col.activeText.c())
                        T("${Defaults.TARGET_SOC.toInt()}%까지 · ${ranked.price.wonPerKwh}원/kWh ${ranked.price.basis.label()}", ViaType.labelM, col.text3.c())
                        SavingBar(ranked.totalCostWon, nonMemberTotal, col.active.c(), height = 24.dp)
                        Row(Modifier.fillMaxWidth()) {
                            T("내 요금 ${won(ranked.totalCostWon)}", ViaType.bodyM.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                            T("비회원가 ${won(nonMemberTotal)}", ViaType.bodyM, col.text3.c())
                        }
                    }
                }
            }
            Column(if (isWide) Modifier.weight(0.6f) else Modifier, verticalArrangement = Arrangement.spacedBy(28.dp)) {
                if (earned.isNotEmpty()) ViaCard(padding = 36.dp) {
                    T("이번 충전으로 받은 포인트", ViaType.labelM, col.text3.c())
                    T("+${earned.sumOf { it.points }}P", ViaType.headline, col.activeText.c())
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        earned.forEach { e ->
                            when (e.kind) {
                                RewardKind.CHARGE -> Chip("충전 +${e.points}P")
                                RewardKind.NEW_STAMP -> Chip("새 스탬프 +${e.points}P", bg = col.activeSoft.c(), fg = col.activeText.c(), bold = true)
                                RewardKind.EVENT_BONUS -> Chip("이벤트 보너스 +${e.points}P", bg = col.activeSoft.c(), fg = col.activeText.c(), bold = true)
                                RewardKind.EFFICIENT_TRIP -> Unit
                            }
                        }
                    }
                    T("차가 충전 위치와 상태를 확인해 자동으로 쌓였어요", ViaType.labelM, col.text3.c())
                }
            }
        }
    }
}
