package com.chargecompass.app.recommend

import com.chargecompass.app.navi.NaviRepository
import com.chargecompass.app.navi.RouteSnapshot
import com.chargecompass.app.navi.StopResult
import com.chargecompass.app.vehicle.VehicleRepository
import com.chargecompass.app.voice.Answer
import com.chargecompass.app.voice.VoiceGateway
import com.chargecompass.core.Defaults
import com.chargecompass.core.data.MockData
import com.chargecompass.core.energy.ArrivalCalculator
import com.chargecompass.core.energy.EnergyState
import com.chargecompass.core.model.VehicleModel
import com.chargecompass.core.pricing.RankedStation
import com.chargecompass.core.pricing.StationRanker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

sealed interface RecoState {
    data object Idle : RecoState
    data class Enough(val arrivalSoc: Double) : RecoState
    data class Suggested(val routeId: Long, val arrivalSoc: Double, val top: List<RankedStation>) : RecoState
    /** 음성으로 확인하지 못했다. 정차 화면에서 터치 한 번으로 추가한다. */
    data class AwaitingTouch(val routeId: Long, val choice: RankedStation, val top: List<RankedStation>) : RecoState
    /** 경유지 추가 요청 중. 이 동안 들어오는 터치는 무시한다(중복 추가 방지). */
    data class Adding(val station: RankedStation) : RecoState
    data class Added(val station: RankedStation, val result: StopResult) : RecoState
    data object NoneAvailable : RecoState
}

/**
 * 장면 A: 도착 잔량이 부족하면 경로상 최저가 충전소 1곳을 음성으로 묻고, 예라고 하면 경유지로 넣는다.
 * 같은 경로(routeId)에서는 한 번만 묻는다. 목적지가 바뀌면 진행 중인 질문을 버리고 다시 계산한다.
 */
class RecommendationCoordinator(
    private val vehicle: VehicleRepository,
    private val navi: NaviRepository,
    private val voice: VoiceGateway,
    private val data: MockData,
    private val vehicleModel: () -> VehicleModel?,
    private val ownedMemberships: () -> Set<String>,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<RecoState>(RecoState.Idle)
    val state: StateFlow<RecoState> = _state

    private var handledRouteId: Long? = null
    private var question: Job? = null

    init {
        scope.launch {
            combine(navi.route, vehicle.energy, ::Pair).collect { (route, energy) -> onUpdate(route, energy) }
        }
    }

    fun confirmByTouch(stationId: String) {
        val choice = when (val s = _state.value) {
            is RecoState.AwaitingTouch -> s.top.firstOrNull { it.station.id == stationId }
            is RecoState.Suggested -> s.top.firstOrNull { it.station.id == stationId }
            else -> null
        } ?: return
        _state.value = RecoState.Adding(choice)
        scope.launch { add(choice) }
    }

    private fun onUpdate(route: RouteSnapshot?, energy: EnergyState) {
        if (route == null) {
            cancelQuestion()
            handledRouteId = null
            _state.value = RecoState.Idle
            return
        }
        if (route.routeId != handledRouteId && question != null) cancelQuestion()
        if (route.routeId == handledRouteId) return

        val car = vehicleModel() ?: return
        val arrival = ArrivalCalculator.socAfter(energy, car, route.remainingKm) ?: return
        // 경로 거리는 출발 때 값이라, 경로마다 처음 한 번만 판단한다(주행 중 다시 계산하면 지나친 충전소를 추천한다)
        handledRouteId = route.routeId
        if (!ArrivalCalculator.needsCharge(arrival)) {
            _state.value = RecoState.Enough(arrival)
            return
        }
        val ranked = StationRanker.rank(data.stations, data.tariffs, ownedMemberships(), energy, car)
            .take(Defaults.COMPARE_SUGGESTIONS)
        if (ranked.isEmpty()) {
            _state.value = RecoState.NoneAvailable
            question = scope.launch { voice.speak(NO_STATION) }
            return
        }
        _state.value = RecoState.Suggested(route.routeId, arrival, ranked)
        question = scope.launch { ask(route.routeId, arrival, ranked) }
    }

    private suspend fun ask(routeId: Long, arrival: Double, top: List<RankedStation>) {
        val best = top.first()
        val info = describe(arrival, best)
        if (!voice.sttAvailable) {
            voice.speak("$info 정차하면 화면에서 추가할 수 있어요.")
            _state.value = RecoState.AwaitingTouch(routeId, best, top)
            return
        }
        when (voice.ask("$info 경유지로 추가할까요?")) {
            Answer.YES -> add(best)
            Answer.NO -> Unit
            Answer.UNKNOWN, Answer.TIMEOUT -> _state.value = RecoState.AwaitingTouch(routeId, best, top)
        }
    }

    private suspend fun add(choice: RankedStation) {
        _state.value = RecoState.Adding(choice)
        _state.value = RecoState.Added(choice, navi.addChargingStop(choice.station))
    }

    private fun describe(arrival: Double, best: RankedStation): String {
        val soc = arrival.roundToInt().coerceAtLeast(0)
        val km = best.station.alongRouteKm.roundToInt()
        val nonMember = data.tariffs[best.station.operatorId]?.nonMemberWonPerKwh ?: best.price.wonPerKwh
        val diff = nonMember - best.price.wonPerKwh
        val saving = if (diff > 0) "내 멤버십 기준 kWh당 ${diff}원 더 저렴해요." else "경로 위에서 가장 저렴해요."
        return "도착 시 배터리가 $soc%예요. ${km}km 앞 ${best.station.name}에 들르면 $saving"
    }

    private fun cancelQuestion() {
        question?.cancel()
        question = null
    }

    companion object {
        const val NO_STATION = "경로 위에 쓸 수 있는 충전소가 없어요"
    }
}
