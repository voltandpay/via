package com.chargecompass.app.recommend

import com.chargecompass.app.navi.MockNaviRepository
import com.chargecompass.app.navi.RouteSnapshot
import com.chargecompass.app.navi.StopResult
import com.chargecompass.app.vehicle.MockVehicleRepository
import com.chargecompass.app.voice.Answer
import com.chargecompass.app.voice.MockVoiceGateway
import com.chargecompass.app.voice.VoiceGateway
import com.chargecompass.core.data.MockData
import com.chargecompass.core.data.MockDataLoader
import com.chargecompass.core.model.ChargerStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RecommendationCoordinatorTest {
    private val data: MockData = MockDataLoader.load { javaClass.classLoader.getResource(it)!!.readText() }
    private val car = data.vehicles.first { it.id == "ioniq5" }
    private val route = RouteSnapshot(1, "서울역", 23.0, 2040)

    private class Env(
        val vehicle: MockVehicleRepository = MockVehicleRepository(),
        val navi: MockNaviRepository = MockNaviRepository(),
        val voice: VoiceGateway = MockVoiceGateway(),
    )

    private fun TestScope.start(env: Env, data: MockData = this@RecommendationCoordinatorTest.data, scope: CoroutineScope = backgroundScope) =
        RecommendationCoordinator(env.vehicle, env.navi, env.voice, data, { car }, { setOf("m-chaevi") }, scope)

    @Test
    fun `배터리가 넉넉하면 Enough이고 말하지 않는다`() = runTest(UnconfinedTestDispatcher()) {
        val env = Env()
        val c = start(env)
        env.vehicle.setSoc(80)
        env.navi.setRoute(route)
        assertIs<RecoState.Enough>(c.state.value)
        assertTrue((env.voice as MockVoiceGateway).asked.isEmpty())
    }

    @Test
    fun `부족하면 1위를 한 번 묻고 답이 없으면 터치 확인으로 넘긴다`() = runTest(UnconfinedTestDispatcher()) {
        val voice = MockVoiceGateway()   // 준비된 답 없음 → TIMEOUT
        val env = Env(voice = voice)
        val c = start(env)
        env.vehicle.setSoc(15)
        env.navi.setRoute(route)
        assertEquals(1, voice.asked.size)
        val s = c.state.value
        assertIs<RecoState.AwaitingTouch>(s)
        assertEquals("st-yangjae-chaevi", s.choice.station.id)
        assertTrue(s.top.size in 1..3)
    }

    @Test
    fun `예라고 하면 1위 충전소를 경유지로 넣는다`() = runTest(UnconfinedTestDispatcher()) {
        val voice = MockVoiceGateway(answers = listOf(Answer.YES))
        val env = Env(voice = voice)
        val c = start(env)
        env.vehicle.setSoc(15)
        env.navi.setRoute(route)
        val s = c.state.value
        assertIs<RecoState.Added>(s)
        assertEquals(StopResult.WAYPOINT_ADDED, s.result)
        assertEquals(listOf(s.station.station), env.navi.addedStops)
        assertEquals("st-yangjae-chaevi", s.station.station.id)
        assertEquals(1, voice.asked.size)
        assertTrue(voice.asked.single().contains("양재 채비 충전소"))
        assertTrue(voice.asked.single().startsWith("도착 시 배터리가 9%예요."))
    }

    @Test
    fun `아니라고 하면 같은 경로에서는 다시 묻지 않는다`() = runTest(UnconfinedTestDispatcher()) {
        val voice = MockVoiceGateway(answers = listOf(Answer.NO))
        val env = Env(voice = voice)
        val c = start(env)
        env.vehicle.setSoc(15)
        env.navi.setRoute(route)
        env.vehicle.setSoc(14)
        env.vehicle.setSoc(13)
        assertEquals(1, voice.asked.size)
        assertTrue(env.navi.addedStops.isEmpty())
        val s = c.state.value
        assertIs<RecoState.Suggested>(s)
        assertTrue(s.top.size in 1..3)
    }

    @Test
    fun `음성 인식을 못 쓰면 정차 후 터치로 확인한다`() = runTest(UnconfinedTestDispatcher()) {
        val voice = MockVoiceGateway(sttAvailable = false)
        val env = Env(voice = voice)
        val c = start(env)
        env.vehicle.setSoc(15)
        env.navi.setRoute(route)
        val s = c.state.value
        assertIs<RecoState.AwaitingTouch>(s)
        assertTrue(voice.asked.isEmpty())
        assertEquals(1, voice.spoken.size)
        c.confirmByTouch(s.choice.station.id)
        assertIs<RecoState.Added>(c.state.value)
        assertEquals(1, env.navi.addedStops.size)
    }

    @Test
    fun `답을 못 들으면 정차 후 터치로 확인한다`() = runTest(UnconfinedTestDispatcher()) {
        val env = Env(voice = MockVoiceGateway(answers = listOf(Answer.UNKNOWN)))
        val c = start(env)
        env.vehicle.setSoc(15)
        env.navi.setRoute(route)
        assertIs<RecoState.AwaitingTouch>(c.state.value)
    }

    @Test
    fun `쓸 수 있는 충전소가 없으면 한 번 알린다`() = runTest(UnconfinedTestDispatcher()) {
        val voice = MockVoiceGateway()
        val env = Env(voice = voice)
        val broken = data.copy(stations = data.stations.map { it.copy(status = ChargerStatus.BROKEN) })
        val c = start(env, broken)
        env.vehicle.setSoc(15)
        env.navi.setRoute(route)
        env.vehicle.setSoc(14)
        assertIs<RecoState.NoneAvailable>(c.state.value)
        assertEquals(listOf("경로 위에 쓸 수 있는 충전소가 없어요"), voice.spoken)
    }

    @Test
    fun `질문 중에 목적지가 바뀌면 이전 답으로 경유지를 넣지 않는다`() = runTest(UnconfinedTestDispatcher()) {
        val pending = CompletableDeferred<Answer>()
        val voice = object : VoiceGateway {
            override val sttAvailable = true
            val asked = mutableListOf<String>()
            override suspend fun speak(text: String) = Unit
            override suspend fun ask(question: String, timeoutMs: Long): Answer {
                asked += question
                return if (asked.size == 1) pending.await() else Answer.NO
            }
            override fun start() = Unit
            override fun stop() = Unit
        }
        val env = Env(voice = voice)
        val c = start(env)
        env.vehicle.setSoc(15)
        env.navi.setRoute(route)
        env.navi.setRoute(RouteSnapshot(2, "강남역", 23.0, 2000))
        pending.complete(Answer.YES)
        assertTrue(env.navi.addedStops.isEmpty())
        assertEquals(2, voice.asked.size)
        assertIs<RecoState.Suggested>(c.state.value)
    }

    @Test
    fun `안내가 끝나면 Idle`() = runTest(UnconfinedTestDispatcher()) {
        val env = Env()
        val c = start(env)
        env.vehicle.setSoc(80)
        env.navi.setRoute(route)
        env.navi.setRoute(null)
        assertIs<RecoState.Idle>(c.state.value)
    }

    @Test
    fun `한 경로에서 처음 넉넉하다고 판단했으면 주행 중 SoC가 줄어도 다시 계산해 묻지 않는다`() = runTest(UnconfinedTestDispatcher()) {
        // 경로 거리는 출발 때 값 그대로라, 주행 중 SoC로 다시 계산하면 이미 지나친 충전소를 추천하게 된다
        val voice = MockVoiceGateway()
        val env = Env(voice = voice)
        val c = start(env)
        env.vehicle.setSoc(45)
        env.navi.setRoute(RouteSnapshot(7, "대전", 100.0, 4000))
        assertIs<RecoState.Enough>(c.state.value)
        env.vehicle.setSoc(33)
        assertIs<RecoState.Enough>(c.state.value)
        assertTrue(voice.asked.isEmpty())
    }

    @Test
    fun `경유지 추가를 두 번 눌러도 한 번만 추가한다`() = runTest(UnconfinedTestDispatcher()) {
        val gate = CompletableDeferred<Unit>()
        val navi = object : com.chargecompass.app.navi.NaviRepository {
            val calls = mutableListOf<String>()
            override val route = kotlinx.coroutines.flow.MutableStateFlow<RouteSnapshot?>(null)
            override suspend fun addChargingStop(station: com.chargecompass.core.model.Station): StopResult {
                calls += station.id; gate.await(); return StopResult.WAYPOINT_ADDED
            }
            override fun start() = Unit
            override fun stop() = Unit
        }
        val vehicle = MockVehicleRepository()
        val c = RecommendationCoordinator(vehicle, navi, MockVoiceGateway(sttAvailable = false), data, { car }, { setOf("m-chaevi") }, backgroundScope)
        vehicle.setSoc(15)
        navi.route.value = route
        val s = c.state.value
        assertIs<RecoState.AwaitingTouch>(s)
        c.confirmByTouch(s.choice.station.id)
        assertIs<RecoState.Adding>(c.state.value)
        c.confirmByTouch(s.choice.station.id)
        gate.complete(Unit)
        assertEquals(1, navi.calls.size)
        assertIs<RecoState.Added>(c.state.value)
    }

    @Test
    fun `로밍가로 고른 곳은 로밍 요금 기준이라고 말한다`() = runTest(UnconfinedTestDispatcher()) {
        val voice = MockVoiceGateway(answers = listOf(Answer.NO))
        val env = Env(voice = voice)
        // 에버온 충전소는 커넥터가 안 맞아, 1위는 다른 사업자의 로밍가가 된다
        RecommendationCoordinator(env.vehicle, env.navi, voice, data, { car }, { setOf("m-everon") }, backgroundScope)
        env.vehicle.setSoc(15)
        env.navi.setRoute(route)
        val q = voice.asked.single()
        assertTrue(q.contains("로밍 요금 기준"), q)
        assertTrue(!q.contains("내 멤버십 기준"), q)
    }

    @Test
    fun `새 경로에서 계산할 수 없으면 이전 경로 추천을 지우고 이전 후보 터치는 무시한다`() = runTest(UnconfinedTestDispatcher()) {
        val env = Env(voice = MockVoiceGateway(sttAvailable = false))
        var model: com.chargecompass.core.model.VehicleModel? = car
        val c = RecommendationCoordinator(env.vehicle, env.navi, env.voice, data, { model }, { setOf("m-chaevi") }, backgroundScope)
        env.vehicle.setSoc(15)
        env.navi.setRoute(route)
        val old = c.state.value
        assertIs<RecoState.AwaitingTouch>(old)
        model = null
        env.navi.setRoute(RouteSnapshot(2, "강남역", 23.0, 2000))
        assertIs<RecoState.Idle>(c.state.value)
        c.confirmByTouch(old.choice.station.id)
        assertTrue(env.navi.addedStops.isEmpty())
    }

    @Test
    fun `도착 잔량 5퍼센트 미만으로만 닿는 충전소는 추천하지 않는다`() = runTest(UnconfinedTestDispatcher()) {
        val voice = MockVoiceGateway()
        val env = Env(voice = voice)
        // SoC 15%, 기본 전비 5.0, 77.4kWh: 46km 앞이면 도착 잔량 약 3.1%
        val far = data.stations.first { it.id == "st-yangjae-chaevi" }.copy(alongRouteKm = 46.0, detourKm = 0.0)
        val c = start(env, data.copy(stations = listOf(far)))
        env.vehicle.setSoc(15)
        env.navi.setRoute(RouteSnapshot(3, "평택", 60.0, 3000))
        assertIs<RecoState.NoneAvailable>(c.state.value)
    }

    @Test
    fun `정차 중이면 음성으로 묻지 않고 화면에 바로 추천한다`() = runTest(UnconfinedTestDispatcher()) {
        val voice = MockVoiceGateway(answers = listOf(Answer.YES))
        val env = Env(voice = voice)
        val driving = kotlinx.coroutines.flow.MutableStateFlow(false)
        val c = RecommendationCoordinator(env.vehicle, env.navi, voice, data, { car }, { setOf("m-chaevi") }, backgroundScope, driving)
        env.vehicle.setSoc(15)
        env.navi.setRoute(route)
        val s = c.state.value
        assertIs<RecoState.AwaitingTouch>(s)
        assertEquals("st-yangjae-chaevi", s.choice.station.id)
        assertTrue(voice.asked.isEmpty())
        assertTrue(voice.spoken.isEmpty())
    }

    @Test
    fun `정차 중 추천을 그대로 두고 출발하면 그때 한 번 음성으로 묻는다`() = runTest(UnconfinedTestDispatcher()) {
        val voice = MockVoiceGateway(answers = listOf(Answer.YES))
        val env = Env(voice = voice)
        val driving = kotlinx.coroutines.flow.MutableStateFlow(false)
        val c = RecommendationCoordinator(env.vehicle, env.navi, voice, data, { car }, { setOf("m-chaevi") }, backgroundScope, driving)
        env.vehicle.setSoc(15)
        env.navi.setRoute(route)
        driving.value = true
        assertEquals(1, voice.asked.size)
        assertIs<RecoState.Added>(c.state.value)
        driving.value = false
        driving.value = true
        assertEquals(1, voice.asked.size)
    }

    @Test
    fun `경유지를 넣으면 음성으로 확인해 준다`() = runTest(UnconfinedTestDispatcher()) {
        val voice = MockVoiceGateway(answers = listOf(Answer.YES))
        val env = Env(voice = voice)
        start(env)
        env.vehicle.setSoc(15)
        env.navi.setRoute(route)
        assertEquals("양재 채비 충전소를 경유지로 넣었어요", voice.spoken.last())
    }

    @Test
    fun `경유지 추가에 실패하면 알리고 다시 시도할 수 있다`() = runTest(UnconfinedTestDispatcher()) {
        val voice = MockVoiceGateway(answers = listOf(Answer.YES))
        val env = Env(voice = voice)
        env.navi.nextResult = StopResult.FAILED
        val c = start(env)
        env.vehicle.setSoc(15)
        env.navi.setRoute(route)
        val failed = c.state.value
        assertIs<RecoState.Added>(failed)
        assertEquals(StopResult.FAILED, failed.result)
        assertEquals("경유지를 넣지 못했어요. 정차 후 화면에서 다시 시도해 주세요", voice.spoken.last())
        env.navi.nextResult = StopResult.WAYPOINT_ADDED
        c.confirmByTouch(failed.station.station.id)
        val retried = c.state.value
        assertIs<RecoState.Added>(retried)
        assertEquals(StopResult.WAYPOINT_ADDED, retried.result)
        assertEquals(2, env.navi.addedStops.size)
    }

    private fun ranked(id: String) = com.chargecompass.core.pricing.RankedStation(
        data.stations.first { it.id == id }, com.chargecompass.core.pricing.EffectivePrice(300, com.chargecompass.core.pricing.PriceBasis.MEMBER, null), 30.0, 9000, 15000, 6000, 20.0)

    @Test
    fun `추천으로 보여 준 충전소는 도감 발견 대상`() {
        val top = listOf(ranked("st-yangjae-chaevi"), ranked("st-banpo-kepco"))
        assertEquals(listOf("st-yangjae-chaevi", "st-banpo-kepco"), shownStationIds(RecoState.Suggested(1, 10.0, top)))
        assertEquals(listOf("st-yangjae-chaevi", "st-banpo-kepco"), shownStationIds(RecoState.AwaitingTouch(1, top[0], top, 10.0)))
        assertEquals(null, shownStationIds(RecoState.Idle))
    }
}
