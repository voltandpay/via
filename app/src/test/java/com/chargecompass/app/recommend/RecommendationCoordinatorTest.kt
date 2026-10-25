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
}
