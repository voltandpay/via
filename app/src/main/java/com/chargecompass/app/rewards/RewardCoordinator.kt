package com.chargecompass.app.rewards

import com.chargecompass.app.store.RewardLedger
import com.chargecompass.app.store.SummaryLog
import com.chargecompass.app.vehicle.VehicleRepository
import com.chargecompass.app.voice.VoiceGateway
import com.chargecompass.core.Defaults
import com.chargecompass.core.data.MockData
import com.chargecompass.core.data.nearestStation
import com.chargecompass.core.model.VehicleModel
import com.chargecompass.core.pricing.StationRanker
import com.chargecompass.core.rewards.RewardEngine
import com.chargecompass.core.rewards.RewardEntry
import com.chargecompass.core.rewards.RewardKind
import com.chargecompass.core.dex.Dex
import com.chargecompass.core.missions.Mission
import com.chargecompass.core.missions.Missions
import com.chargecompass.core.session.ChargeSession
import com.chargecompass.core.session.ChargeSessionDetector
import com.chargecompass.core.session.SessionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 차량 데이터만으로 충전·스탬프·이벤트·효율 운전 포인트를 쌓는다. 사용자가 누를 것은 없다. */
class RewardCoordinator(
    private val vehicle: VehicleRepository,
    private val ledger: RewardLedger,
    private val data: MockData,
    private val vehicleModel: () -> VehicleModel?,
    private val voice: VoiceGateway,
    scope: CoroutineScope,
    private val ownedMemberships: () -> Set<String> = { emptySet() },
    private val summaryLog: SummaryLog? = null,
) {
    private val _active = MutableStateFlow<ChargeSession?>(null)
    /** 진행 중인 충전 세션. 주행 상태 CHARGING 판단에도 쓴다. */
    val activeSession: StateFlow<ChargeSession?> = _active

    private val _sessionEarned = MutableStateFlow<List<RewardEntry>>(emptyList())
    /** 지금 충전 세션에서 적립한 것만. 충전 화면의 "이번 충전으로 받은 포인트". */
    val sessionEarned: StateFlow<List<RewardEntry>> = _sessionEarned

    private val _lastSummary = MutableStateFlow<ChargeSummary?>(null)
    /** 방금 끝난 충전의 요약. 홈에서 닫을 때까지 보인다. */
    val lastSummary: StateFlow<ChargeSummary?> = _lastSummary

    fun dismissSummary() { _lastSummary.value = null }

    private val detector = ChargeSessionDetector({ lat, lng ->
        nearestStation(data.stations, lat, lng, Defaults.STATION_MATCH_RADIUS_M)
    })

    init {
        scope.launch {
            vehicle.charging.collect { sample ->
                when (val event = detector.onSample(sample)) {
                    is SessionEvent.Started -> onStarted(event.session)
                    is SessionEvent.Ended -> {
                        val summary = summarize(event.session, _sessionEarned.value)
                        _lastSummary.value = summary
                        summary?.let { summaryLog?.append(it); checkMissions(it.endedAt) }
                        _active.value = null
                        _sessionEarned.value = emptyList()
                    }
                    null -> Unit
                }
            }
        }
        scope.launch {
            vehicle.tripEnded.collect { trip ->
                val car = vehicleModel() ?: return@collect
                ledger.append(RewardEngine.onTripEnded(trip.distanceKm, trip.efficiencyKmPerKwh, car, trip.at))
                checkMissions(trip.at)
            }
        }
    }

    private suspend fun onStarted(session: ChargeSession) {
        _active.value = session
        val charged = RewardEngine.onChargeStarted(session, data.events, ledger, data.stations, data.operatorDays)
        ledger.append(charged)
        val earned = charged + checkMissions(session.startedAt, speak = false)
        // 충전소를 늦게 찾아 Started가 다시 오면, 처음 Started(충전소 없음)의 빈 적립분을 이것으로 바꾼다
        _sessionEarned.value = earned
        val region = earned.firstOrNull { it.kind == RewardKind.REGION_COMPLETE }
        when {
            region != null -> voice.speak("${Dex.regions.firstOrNull { it.id == region.regionId }?.label ?: "지역"} 도감 완성! 보너스 ${region.points}포인트")
            earned.any { it.kind == RewardKind.NEW_STAMP } -> voice.speak(NEW_STAMP)
        }
        speakMissions(earned)
    }

    /** 이번 주 새로 달성한 미션과 리서치 돌파를 적립하고 돌려준다. */
    private suspend fun checkMissions(at: java.time.LocalDateTime, speak: Boolean = true): List<RewardEntry> {
        val fresh = Missions.rewards(at, ledger.entries.value, discountCharges(summaryLog?.summaries?.value.orEmpty(), at.toLocalDate()))
        if (fresh.isEmpty()) return fresh
        ledger.append(fresh)
        if (speak) speakMissions(fresh)
        return fresh
    }

    /** 미션 달성은 짧게 한 번만 말한다(주행 중일 수 있다). */
    private suspend fun speakMissions(earned: List<RewardEntry>) {
        val missions = earned.filter { it.kind == RewardKind.MISSION }
        when {
            earned.any { it.kind == RewardKind.BREAKTHROUGH } -> voice.speak("리서치 돌파! 보너스 ${Defaults.POINTS_BREAKTHROUGH}포인트")
            missions.isNotEmpty() -> voice.speak("주간 미션 달성! " + missions.joinToString(", ") { m -> Mission.valueOf(m.eventId!!).title })
        }
    }

    /** 실제 충전량(시작·종료 SoC × 배터리 용량)으로 낸 돈과 비회원가 대비 절약액을 계산한다. */
    private fun summarize(session: ChargeSession, earned: List<RewardEntry>): ChargeSummary? {
        val station = data.stations.firstOrNull { it.id == session.stationId } ?: return null
        val car = vehicleModel() ?: return null
        val start = session.startSoc ?: return null
        val end = session.endSoc ?: return null
        val tariff = data.tariffs[station.operatorId] ?: return null
        val kwh = (end - start).coerceAtLeast(0) / 100.0 * car.batteryKwh
        val price = StationRanker.effectivePrice(tariff, ownedMemberships())
        val cost = (kwh * price.wonPerKwh).roundToInt()
        val nonMember = (kwh * tariff.nonMemberWonPerKwh).roundToInt()
        return ChargeSummary(station.name, kwh, cost, nonMember - cost, earned.sumOf { it.points }, session.endedAt ?: session.startedAt)
    }

    companion object {
        const val NEW_STAMP = "새 스탬프 획득!"
    }
}

/** [date]가 속한 주에 비회원가보다 싸게 낸 충전 횟수(주간 미션 "회원가·로밍가로 충전"). */
fun discountCharges(summaries: List<ChargeSummary>, date: java.time.LocalDate): Int {
    val start = Missions.weekStart(date)
    return summaries.count { Missions.weekStart(it.endedAt.toLocalDate()) == start && it.savingWon > 0 }
}

data class ChargeSummary(
    val stationName: String, val chargedKwh: Double, val costWon: Int, val savingWon: Int, val points: Int,
    val endedAt: java.time.LocalDateTime,
)
