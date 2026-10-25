package com.chargecompass.app.rewards

import com.chargecompass.app.store.RewardLedger
import com.chargecompass.app.vehicle.VehicleRepository
import com.chargecompass.app.voice.VoiceGateway
import com.chargecompass.core.Defaults
import com.chargecompass.core.data.MockData
import com.chargecompass.core.data.nearestStation
import com.chargecompass.core.model.VehicleModel
import com.chargecompass.core.rewards.RewardEngine
import com.chargecompass.core.rewards.RewardEntry
import com.chargecompass.core.rewards.RewardKind
import com.chargecompass.core.session.ChargeSession
import com.chargecompass.core.session.ChargeSessionDetector
import com.chargecompass.core.session.SessionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** 차량 데이터만으로 충전·스탬프·이벤트·효율 운전 포인트를 쌓는다. 사용자가 누를 것은 없다. */
class RewardCoordinator(
    private val vehicle: VehicleRepository,
    private val ledger: RewardLedger,
    private val data: MockData,
    private val vehicleModel: () -> VehicleModel?,
    private val voice: VoiceGateway,
    scope: CoroutineScope,
) {
    private val _active = MutableStateFlow<ChargeSession?>(null)
    /** 진행 중인 충전 세션. 주행 상태 CHARGING 판단에도 쓴다. */
    val activeSession: StateFlow<ChargeSession?> = _active

    private val _sessionEarned = MutableStateFlow<List<RewardEntry>>(emptyList())
    /** 지금 충전 세션에서 적립한 것만. 충전 화면의 "이번 충전으로 받은 포인트". */
    val sessionEarned: StateFlow<List<RewardEntry>> = _sessionEarned

    private val detector = ChargeSessionDetector({ lat, lng ->
        nearestStation(data.stations, lat, lng, Defaults.STATION_MATCH_RADIUS_M)
    })

    init {
        scope.launch {
            vehicle.charging.collect { sample ->
                when (val event = detector.onSample(sample)) {
                    is SessionEvent.Started -> onStarted(event.session)
                    is SessionEvent.Ended -> {
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
            }
        }
    }

    private suspend fun onStarted(session: ChargeSession) {
        _active.value = session
        val earned = RewardEngine.onChargeStarted(session, data.events, ledger)
        ledger.append(earned)
        // 충전소를 늦게 찾아 Started가 다시 오면, 처음 Started(충전소 없음)의 빈 적립분을 이것으로 바꾼다
        _sessionEarned.value = earned
        if (earned.any { it.kind == RewardKind.NEW_STAMP }) voice.speak(NEW_STAMP)
    }

    companion object {
        const val NEW_STAMP = "새 스탬프 획득!"
    }
}
