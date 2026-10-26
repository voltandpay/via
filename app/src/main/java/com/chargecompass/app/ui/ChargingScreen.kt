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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chargecompass.app.AppContainer
import com.chargecompass.core.Defaults
import com.chargecompass.core.charge.ChargeCurve
import com.chargecompass.core.charge.ChargeStopAdvice
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
    val station = c.data.allStations.firstOrNull { it.id == s.stationId }
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

        // 처음 온 충전소: 메달이 돌며 나타나는 "새 스탬프 획득" 카드(충전 중 = 정차 중이라 연출 허용)
        val stamp = earned.firstOrNull { it.kind == RewardKind.NEW_STAMP }
        if (station != null && stamp != null) NewStampCard(c, station.operatorId, station.name, stamp.at)

        if (station == null || ranked == null) {
            ViaCard(padding = 48.dp) {
                T("충전소를 확인하지 못했어요", ViaType.titleM, col.neg.c())
                T("위치가 잡히면 요금과 포인트를 바로 계산해요", ViaType.bodyM, col.text2.c())
            }
            return@Column
        }
        val nonMemberTotal = ranked.nonMemberCostWon
        val eta = car?.let { m -> energy.socPct?.let { ChargeCurve.minutes(it, Defaults.TARGET_SOC.toInt(), m.batteryKwh, station.maxKw, c.demoClock.now().month) } }
        Adaptive(Arrangement.spacedBy(28.dp), Alignment.Top) { first ->
            ViaCard(first, padding = 48.dp) {
                Adaptive(Arrangement.spacedBy(48.dp)) { textSide ->
                    // 배터리: 현재 → 목표 80% 원형 게이지(충전 중이라 차오르는 애니메이션 허용)
                    RingGauge(
                        (energy.socPct ?: 0) / 100f, col.pos.c(), size = if (isWide) 300.dp else 260.dp,
                        marker = (Defaults.TARGET_SOC / 100).toFloat(), targetColor = col.pos.c().copy(alpha = 0.3f),
                        modifier = if (isWide) Modifier else Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            T("${energy.socPct ?: "-"}%", ViaType.headline)
                            // 좁은 링 안에서도 넘치지 않게 두 줄로
                            T("목표 ${Defaults.TARGET_SOC.toInt()}%", ViaType.labelM, col.text3.c())
                            eta?.let { T("약 ${it}분", ViaType.labelM, col.text3.c()) }
                        }
                    }
                    Column(textSide, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        T("이 충전소 비회원가보다 절약 예상", ViaType.bodyM, col.text2.c())
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
                                RewardKind.REGION_COMPLETE -> Chip("지역 도감 완성 +${e.points}P", bg = col.activeSoft.c(), fg = col.activeText.c(), bold = true)
                                RewardKind.OFF_PEAK_BOOST -> Chip("경부하 부스트 +${e.points}P", bg = col.posSoft.c(), fg = col.pos.c(), bold = true)
                                RewardKind.OPERATOR_DAY -> Chip("사업자 데이 +${e.points}P", bg = col.event.c(), fg = androidx.compose.ui.graphics.Color.White, bold = true)
                                RewardKind.MISSION -> Chip("주간 미션 +${e.points}P", bg = col.activeSoft.c(), fg = col.activeText.c(), bold = true)
                                RewardKind.BREAKTHROUGH -> Chip("미션 상자 +${e.points}P", bg = col.activeSoft.c(), fg = col.activeText.c(), bold = true)
                                RewardKind.SEASON_STEP -> Chip("시즌 보상 +${e.points}P", bg = col.event.c(), fg = androidx.compose.ui.graphics.Color.White, bold = true)
                                RewardKind.EFFICIENT_TRIP -> Unit
                            }
                        }
                    }
                    T("차가 충전 위치와 상태를 확인해 자동으로 쌓였어요", ViaType.labelM, col.text3.c())
                }
                StationDexCard(c, station.id, station.operatorId)
            }
        }
        c.chargeStopAdvice(s, energy)?.let { (destination, advice) -> ChargeStopCard(destination, advice, energy.socPct) }
        // 충전 중(차를 세워 둔 20~40분)에만 보여 주는 수집·시즌 화면. 홈·주행 화면에는 넣지 않는다
        MedalShelfCard(c, station.id)
        SeasonTrackCard(c)
        WeeklyMissionsCard(c)
    }
}

/** 언제 끝낼까: 목적지까지 충분한 지점과, 목표까지 더 채울 때 드는 시간·돈. 다 차기를 기다리지 않아도 되게. */
@Composable
private fun ChargeStopCard(destination: String, a: ChargeStopAdvice, socPct: Int?) {
    val col = colors
    val reached = socPct != null && a.reached(socPct)
    val target = Defaults.TARGET_SOC.toInt()
    ViaCard(padding = 36.dp, highlight = if (reached) col.pos.c() else null) {
        T("언제 끝낼까요", ViaType.labelM, col.text3.c())
        T(
            if (reached) "지금 끝내도 ${destination}에 ${a.arrivalSoc}%로 도착해요"
            else "${a.enoughSoc}%면 ${destination}에 ${a.arrivalSoc}%로 도착 · 약 ${a.minutesToEnough}분 뒤",
            ViaType.titleM,
        )
        T("${target}%까지 채우면 ${a.extraMinutes}분 · ${won(a.extraWon)} 더" + if (a.winter) " · 겨울이라 충전이 느려요" else "", ViaType.bodyM, col.text2.c())
    }
}

/** 이 충전소의 도감 메달: 지금 단계와 다음 단계까지 남은 충전 횟수. */
@Composable
private fun StationDexCard(c: AppContainer, stationId: String, operatorId: String) {
    val entries by c.ledger.entries.collectAsState()
    val col = colors
    val visits = entries.count { it.kind == RewardKind.CHARGE && it.stationId == stationId }
    val tier = com.chargecompass.core.dex.MedalTier.of(visits) ?: return
    val next = com.chargecompass.core.dex.MedalTier.visitsToNext(visits)
    val no = com.chargecompass.core.dex.Dex.build(c.data.stations, emptySet(), emptyMap()).firstOrNull { it.station.id == stationId }?.no
    ViaCard(padding = 32.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            medalRes(operatorId, tier)?.let { Prop(it, 104.dp) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                T("도감" + (no?.let { " · ${com.chargecompass.core.dex.Dex.label(it)}" } ?: ""), ViaType.labelM, col.text3.c())
                T("${tier.label} 메달 · ${visits}회 충전", ViaType.titleS)
                if (next != null) {
                    val nextTier = com.chargecompass.core.dex.MedalTier.entries[tier.ordinal + 1]
                    Bar((visits - tier.minVisits).toFloat() / (nextTier.minVisits - tier.minVisits), col.active.c(), height = 12.dp)
                    T("${nextTier.label} 메달까지 ${next}번", ViaType.labelM, col.text2.c())
                } else T("가장 높은 단계예요", ViaType.labelM, col.text2.c())
            }
        }
    }
}

@Composable
private fun NewStampCard(c: AppContainer, operatorId: String, stationName: String, key: java.time.LocalDateTime) {
    val col = colors
    val frames = stampSpinFrames(operatorId)
    ViaCard(padding = 32.dp, highlight = col.active.c()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            if (frames != null) {
                // 두 바퀴 돌고 정면에서 멈춘다(약 1초)
                var i by androidx.compose.runtime.remember(key) { androidx.compose.runtime.mutableIntStateOf(1) }
                androidx.compose.runtime.LaunchedEffect(key) {
                    repeat(frames.size * 2) { step -> kotlinx.coroutines.delay(60); i = (step + 2) % frames.size }
                    i = 0
                }
                Prop(frames[i], 150.dp)
            } else {
                val dex = c.data.memberships.firstOrNull { it.operatorId == operatorId }
                StationBadge(operatorId, dex?.name ?: stationName, true, fresh = true, size = 120.dp,
                    medal = medalRes(operatorId, com.chargecompass.core.dex.MedalTier.BRONZE))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                T("새 스탬프 획득!", ViaType.titleL, col.activeText.c())
                T("$stationName · 도감에 동 메달로 등록됐어요", ViaType.bodyM, col.text2.c())
            }
        }
    }
}
