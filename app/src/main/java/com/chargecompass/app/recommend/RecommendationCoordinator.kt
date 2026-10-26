package com.chargecompass.app.recommend

import com.chargecompass.app.navi.NaviRepository
import com.chargecompass.app.navi.RouteSnapshot
import com.chargecompass.app.navi.StopResult
import com.chargecompass.app.navi.StationStatusBoard
import com.chargecompass.app.voice.withSubjectParticle
import com.chargecompass.core.model.ChargerStatus
import com.chargecompass.core.trip.TripPlan
import com.chargecompass.core.trip.TripPlanner
import com.chargecompass.app.vehicle.VehicleRepository
import com.chargecompass.app.voice.Answer
import com.chargecompass.app.voice.VoiceGateway
import com.chargecompass.core.Defaults
import com.chargecompass.core.data.MockData
import com.chargecompass.core.energy.ArrivalCalculator
import com.chargecompass.core.energy.EnergyState
import com.chargecompass.core.model.VehicleModel
import com.chargecompass.core.pricing.PriceBasis
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
    data class AwaitingTouch(val routeId: Long, val choice: RankedStation, val top: List<RankedStation>, val arrivalSoc: Double) : RecoState
    /** 경유지 추가 요청 중. 이 동안 들어오는 터치는 무시한다(중복 추가 방지). */
    data class Adding(val station: RankedStation) : RecoState
    data class Added(val station: RankedStation, val result: StopResult) : RecoState
    /**
     * 추천(또는 경유지로 넣은) 충전소가 가는 도중 사용 중·고장이 됐다 → [to]로 바꿀지 묻는다.
     * [wasAdded]면 이미 경유지에 있어 바꿀 때 경유지를 교체한다.
     */
    data class Switch(val routeId: Long, val from: RankedStation, val to: RankedStation, val top: List<RankedStation>, val arrivalSoc: Double, val wasAdded: Boolean) : RecoState
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
    /** 주행 중이면 true. 정차 중에는 음성 대신 화면으로 추천한다. */
    private val driving: StateFlow<Boolean> = MutableStateFlow(true),
    /** 충전기 실시간 상태(1차는 시연 패널). 바뀌면 추천·경유지를 다시 본다. */
    private val statusBoard: StationStatusBoard = StationStatusBoard(),
) {
    private val _state = MutableStateFlow<RecoState>(RecoState.Idle)
    val state: StateFlow<RecoState> = _state

    private val _tripPlan = MutableStateFlow<TripPlan?>(null)
    /** 한 번 충전으로 목적지까지 못 가는 장거리 경로의 다중 충전 계획(2곳 이상일 때만). */
    val tripPlan: StateFlow<TripPlan?> = _tripPlan
    /** 계획에서 지금 추천 중인 정류지 순번. */
    private var planIndex = 0

    private var handledRouteId: Long? = null
    private var question: Job? = null
    /** 음성으로 이미 물어본 경로. 같은 경로에서는 한 번만 묻는다. */
    private val voiceAskedRoutes = mutableSetOf<Long>()

    init {
        scope.launch {
            combine(navi.route, vehicle.energy, ::Pair).collect { (route, energy) -> onUpdate(route, energy) }
        }
        scope.launch { driving.collect { if (it) onDrivingStarted() } }
        scope.launch { statusBoard.overrides.collect { onStatusChanged() } }
    }

    fun confirmByTouch(stationId: String) {
        val currentRoute = navi.route.value?.routeId
        val choice = when (val s = _state.value) {
            is RecoState.AwaitingTouch -> s.top.takeIf { s.routeId == currentRoute }?.firstOrNull { it.station.id == stationId }
            is RecoState.Suggested -> s.top.takeIf { s.routeId == currentRoute }?.firstOrNull { it.station.id == stationId }
            // 추가에 실패했으면 같은 충전소로 다시 시도할 수 있다
            is RecoState.Added -> s.station.takeIf { s.result == StopResult.FAILED && currentRoute != null && it.station.id == stationId }
            is RecoState.Switch -> s.to.takeIf { s.routeId == currentRoute && it.station.id == stationId }
            else -> null
        } ?: return
        val replace = (_state.value as? RecoState.Switch)?.wasAdded == true
        // 음성 질문이 진행 중이면 거둔다(화면에서 넣었는데 "응"이 와서 한 번 더 넣거나, 시간 초과로 상태가 되돌아가지 않게)
        cancelQuestion()
        _state.value = RecoState.Adding(choice)
        // 터치로 시작한 추가도 question으로 추적해, 목적지가 바뀌면 함께 취소된다
        question = scope.launch { add(choice, currentRoute!!, replace) }
    }

    private fun onUpdate(route: RouteSnapshot?, energy: EnergyState) {
        if (route == null) {
            cancelQuestion()
            handledRouteId = null
            _tripPlan.value = null
            _state.value = RecoState.Idle
            return
        }
        if (route.routeId == handledRouteId) {
            if (!advancePlan(route, energy)) dropIfCharged(route, energy)
            return
        }
        _tripPlan.value = null
        planIndex = 0
        if (question != null) cancelQuestion()
        // 이전 경로의 추천이 화면에 남지 않게, 새 경로를 계산하기 전에 비운다
        _state.value = RecoState.Idle

        val car = vehicleModel() ?: return
        val arrival = ArrivalCalculator.socAfter(energy, car, route.remainingKm) ?: return
        // 경로 거리는 출발 때 값이라, 경로마다 처음 한 번만 판단한다(주행 중 다시 계산하면 지나친 충전소를 추천한다)
        handledRouteId = route.routeId
        if (!ArrivalCalculator.needsCharge(arrival)) {
            _state.value = RecoState.Enough(arrival)
            return
        }
        var ranked = rankNow(route, energy, car)
        // 한 번 충전으로 목적지까지 못 가면 장거리 계획을 세우고, 계획의 첫 정류지를 1순위로 추천한다
        val plan = TripPlanner.plan(route.remainingKm, energy, car, statusBoard.apply(data.stationsFor(route.destinationName)), data.tariffs, ownedMemberships())
            ?.takeIf { it.stops.size >= 2 }
        _tripPlan.value = plan
        if (plan != null) {
            val first = ranked.firstOrNull { it.station.id == plan.stops[0].station.id }
            if (first != null) ranked = (listOf(first) + ranked.filter { it !== first }).take(Defaults.COMPARE_SUGGESTIONS)
        }
        if (ranked.isEmpty()) {
            _state.value = RecoState.NoneAvailable
            question = scope.launch { voice.speak(NO_STATION) }
            return
        }
        if (!driving.value) {
            // 정차 중이면 화면을 쓸 수 있다: 바로 추천 카드와 추가 버튼. 음성은 출발할 때 한 번.
            _state.value = RecoState.AwaitingTouch(route.routeId, ranked.first(), ranked, arrival)
            return
        }
        askByVoice(route.routeId, arrival, ranked)
    }

    /**
     * 같은 경로에서 배터리가 늘어(충전) 이제 충분하면, 아직 넣지 않은 추천을 내린다.
     * 주행 중 배터리가 줄어드는 쪽으로는 다시 판단하지 않는다(지나친 충전소를 추천하지 않도록).
     */
    private fun dropIfCharged(route: RouteSnapshot, energy: EnergyState) {
        val s = _state.value
        if (s !is RecoState.AwaitingTouch && s !is RecoState.Suggested && s !is RecoState.NoneAvailable) return
        val car = vehicleModel() ?: return
        val arrival = ArrivalCalculator.socAfter(energy, car, route.remainingKm) ?: return
        if (ArrivalCalculator.needsCharge(arrival)) return
        cancelQuestion()
        _state.value = RecoState.Enough(arrival)
    }

    private fun askByVoice(routeId: Long, arrival: Double, top: List<RankedStation>, intro: String = planIntro()) {
        voiceAskedRoutes += routeId
        _state.value = RecoState.Suggested(routeId, arrival, top)
        question = scope.launch { ask(routeId, arrival, top, intro) }
    }

    private fun planIntro(): String {
        val plan = _tripPlan.value ?: return ""
        return "${navi.route.value?.destinationName ?: "목적지"}까지 충전이 ${plan.stops.size}번 필요해요. "
    }

    /**
     * 장거리 계획에서 지금 정류지 충전을 마쳤으면(계획한 충전량에 거의 닿으면) 다음 정류지를 추천한다.
     * 다음 정류지까지의 거리는 지금 정류지 기준으로 다시 잡는다. 다음을 추천했으면 true.
     */
    private fun advancePlan(route: RouteSnapshot, energy: EnergyState): Boolean {
        val plan = _tripPlan.value ?: return false
        val s = _state.value
        if (s !is RecoState.Added || s.result == StopResult.FAILED) return false
        if (planIndex + 1 >= plan.stops.size) return false
        val done = plan.stops[planIndex]
        if ((energy.socPct ?: return false) < done.chargeToSoc - 3) return false
        val car = vehicleModel() ?: return false
        planIndex++
        val next = plan.stops[planIndex]
        val ranked = StationRanker.rank(statusBoard.apply(listOf(next.station.copy(alongRouteKm = next.km - done.km))), data.tariffs, ownedMemberships(), energy, car, minArrivalSocPct = Defaults.MIN_ARRIVAL_SOC)
        val choice = ranked.firstOrNull() ?: return false
        val arrival = ArrivalCalculator.socAfter(energy, car, route.remainingKm - done.km) ?: choice.arrivalSocPct
        val intro = "다음 충전은 ${choice.station.alongRouteKm.roundToInt()}km 앞 ${choice.station.name}예요. "
        cancelQuestion()
        if (driving.value) askByVoice(route.routeId, arrival, ranked, intro)
        else _state.value = RecoState.AwaitingTouch(route.routeId, choice, ranked, arrival)
        return true
    }

    /** 정차 중에 띄운 추천을 그대로 두고 출발하면, 그때 음성으로 한 번 묻는다. */
    private fun onDrivingStarted() {
        val s = _state.value as? RecoState.AwaitingTouch ?: return
        if (s.routeId != handledRouteId || s.routeId in voiceAskedRoutes || question?.isActive == true) return
        askByVoice(s.routeId, s.arrivalSoc, s.top)
    }

    private suspend fun ask(routeId: Long, arrival: Double, top: List<RankedStation>, intro: String = "") {
        val best = top.first()
        val info = intro + describe(arrival, best)
        if (!voice.sttAvailable) {
            voice.speak("$info 정차하면 화면에서 추가할 수 있어요.")
            _state.value = RecoState.AwaitingTouch(routeId, best, top, arrival)
            return
        }
        when (voice.ask("$info 경유지로 추가할까요?")) {
            Answer.YES -> add(best, routeId)
            Answer.NO -> Unit
            Answer.UNKNOWN, Answer.TIMEOUT -> _state.value = RecoState.AwaitingTouch(routeId, best, top, arrival)
        }
    }

    private fun rankNow(route: RouteSnapshot, energy: EnergyState, car: VehicleModel): List<RankedStation> =
        StationRanker.rank(statusBoard.apply(data.stationsFor(route.destinationName)), data.tariffs, ownedMemberships(), energy, car, minArrivalSocPct = Defaults.MIN_ARRIVAL_SOC)
            .take(Defaults.COMPARE_SUGGESTIONS)

    /**
     * 충전기 상태가 바뀌었다: 지금 추천하거나 경유지로 넣은 충전소가 쓸 수 없게 됐으면 다음 곳을 고른다.
     * 아직 넣지 않은 추천은 조용히 바꾸고, 이미 넣었으면 주행 중엔 음성으로 묻고 정차 중엔 바꾸기 카드를 띄운다.
     */
    private fun onStatusChanged() {
        val route = navi.route.value ?: return
        val car = vehicleModel() ?: return
        val s = _state.value
        val (current, wasAdded, arrival) = when (s) {
            is RecoState.AwaitingTouch -> Triple(s.choice, false, s.arrivalSoc)
            is RecoState.Suggested -> Triple(s.top.first(), false, s.arrivalSoc)
            is RecoState.Added -> if (s.result != StopResult.FAILED) Triple(s.station, true, s.station.arrivalSocPct) else return
            is RecoState.Switch -> Triple(s.from, s.wasAdded, s.arrivalSoc)
            else -> return
        }
        val status = statusBoard.apply(listOf(current.station)).first().status
        if (status == ChargerStatus.AVAILABLE) return
        val ranked = rankNow(route, vehicle.energy.value, car).filter { it.station.id != current.station.id }
        if (ranked.isEmpty()) {
            cancelQuestion()
            _state.value = RecoState.NoneAvailable
            question = scope.launch { voice.speak(NO_STATION) }
            return
        }
        if (!wasAdded) {
            // 아직 경로에 넣지 않았으면 추천만 바꾼다
            cancelQuestion()
            _state.value = RecoState.AwaitingTouch(route.routeId, ranked.first(), ranked, arrival)
            return
        }
        cancelQuestion()
        val next = ranked.first()
        _state.value = RecoState.Switch(route.routeId, current, next, ranked, arrival, wasAdded = true)
        if (!driving.value) return
        question = scope.launch {
            val q = "${withSubjectParticle(current.station.name)} 지금 ${statusPhrase(status)}. ${next.station.alongRouteKm.roundToInt()}km 앞 ${next.station.name}로 바꿀까요?"
            if (voice.sttAvailable && voice.ask(q) == Answer.YES) add(next, route.routeId, replace = true)
            else if (!voice.sttAvailable) voice.speak("${withSubjectParticle(current.station.name)} 지금 ${statusPhrase(status)}. 정차하면 화면에서 바꿀 수 있어요.")
        }
    }

    private suspend fun add(choice: RankedStation, routeId: Long, replace: Boolean = false) {
        _state.value = RecoState.Adding(choice)
        val result = if (replace) navi.replaceChargingStop(choice.station) else navi.addChargingStop(choice.station)
        // 넣는 사이 경로가 바뀌었으면 새 경로의 상태를 이전 경로 결과로 덮지 않는다
        if (navi.route.value?.routeId != routeId) return
        _state.value = RecoState.Added(choice, result)
        // 추가되면 내비 앱이 앞으로 나와 우리 화면이 가려지므로, 결과는 음성으로 알린다
        voice.speak(
            if (result == StopResult.FAILED) ADD_FAILED else "${withObjectParticle(choice.station.name)} 경유지로 넣었어요",
        )
    }

    private fun describe(arrival: Double, best: RankedStation): String {
        val soc = arrival.roundToInt().coerceAtLeast(0)
        val km = best.station.alongRouteKm.roundToInt()
        val nonMember = data.tariffs[best.station.operatorId]?.nonMemberWonPerKwh ?: best.price.wonPerKwh
        val diff = nonMember - best.price.wonPerKwh
        val basis = when (best.price.basis) {
            PriceBasis.MEMBER -> "내 멤버십 기준"
            PriceBasis.ROAMING -> "로밍 요금 기준"
            PriceBasis.NON_MEMBER -> null
        }
        val saving = if (diff > 0 && basis != null) "$basis kWh당 ${diff}원 더 저렴해요." else "경로 위에서 가장 저렴해요."
        return "도착 시 배터리가 $soc%예요. ${km}km 앞 ${best.station.name}에 들르면 $saving"
    }

    private fun cancelQuestion() {
        question?.cancel()
        question = null
    }

    companion object {
        const val NO_STATION = "경로 위에 쓸 수 있는 충전소가 없어요"
        const val ADD_FAILED = "경유지를 넣지 못했어요. 정차 후 화면에서 다시 시도해 주세요"
    }
}

/** 받침이 있으면 "을", 없으면 "를" (한글이 아니면 "를"). */
fun withObjectParticle(word: String): String {
    val last = word.lastOrNull() ?: return word
    val hasFinal = last in '가'..'힣' && (last - '가') % 28 != 0
    return word + if (hasFinal) "을" else "를"
}

/** 화면이나 음성으로 보여 준 추천 충전소(도감 "발견"). 추천 중이 아니면 null. */
fun shownStationIds(state: RecoState): List<String>? = when (state) {
    is RecoState.Suggested -> state.top.map { it.station.id }
    is RecoState.AwaitingTouch -> state.top.map { it.station.id }
    else -> null
}

private fun statusPhrase(status: ChargerStatus): String = when (status) {
    ChargerStatus.IN_USE -> "사용 중이에요"
    ChargerStatus.BROKEN -> "고장 났어요"
    ChargerStatus.MAINTENANCE -> "점검 중이에요"
    else -> "쓸 수 없어요"
}
