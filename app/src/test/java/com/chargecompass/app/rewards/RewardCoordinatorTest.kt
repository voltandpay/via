package com.chargecompass.app.rewards

import com.chargecompass.app.store.InMemoryRewardLedger
import com.chargecompass.app.vehicle.MockVehicleRepository
import com.chargecompass.app.vehicle.TripSummary
import com.chargecompass.app.voice.MockVoiceGateway
import com.chargecompass.core.data.MockDataLoader
import com.chargecompass.core.rewards.RewardEntry
import com.chargecompass.core.rewards.RewardKind
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RewardCoordinatorTest {
    private val data = MockDataLoader.load { javaClass.classLoader.getResource(it)!!.readText() }
    private val car = data.vehicles.first { it.id == "ioniq5" }
    private val yangjae = data.stations.first { it.id == "st-yangjae-chaevi" }
    private var now = LocalDateTime.of(2026, 11, 3, 14, 0)

    private fun env() = Triple(MockVehicleRepository { now }, InMemoryRewardLedger(), MockVoiceGateway())

    @Test
    fun `새 충전소에서 충전하면 30점과 스탬프 음성`() = runTest(UnconfinedTestDispatcher()) {
        val (vehicle, ledger, voice) = env()
        val c = RewardCoordinator(vehicle, ledger, data, { car }, voice, backgroundScope)
        vehicle.setLocation(yangjae.lat, yangjae.lng)
        vehicle.setCharging(true)
        assertEquals(30, ledger.balance(now))
        assertEquals(listOf("새 스탬프 획득!"), voice.spoken)
        assertNotNull(c.activeSession.value)
    }

    @Test
    fun `같은 충전소 두 번째 세션은 10점만이고 음성 없음`() = runTest(UnconfinedTestDispatcher()) {
        val (vehicle, ledger, voice) = env()
        RewardCoordinator(vehicle, ledger, data, { car }, voice, backgroundScope)
        vehicle.setLocation(yangjae.lat, yangjae.lng)
        vehicle.setCharging(true)
        now = now.plusMinutes(30); vehicle.setCharging(false)
        now = now.plusMinutes(2); vehicle.setCharging(false)
        now = now.plusHours(3); vehicle.setCharging(true)
        assertEquals(40, ledger.balance(now))
        assertEquals(1, voice.spoken.size)
    }

    @Test
    fun `충전이 30초 끊겼다 이어지면 추가 적립 없음`() = runTest(UnconfinedTestDispatcher()) {
        val (vehicle, ledger, voice) = env()
        val c = RewardCoordinator(vehicle, ledger, data, { car }, voice, backgroundScope)
        vehicle.setLocation(yangjae.lat, yangjae.lng)
        vehicle.setCharging(true)
        now = now.plusSeconds(5); vehicle.setCharging(false)
        now = now.plusSeconds(30); vehicle.setCharging(true)
        assertEquals(30, ledger.balance(now))
        assertNotNull(c.activeSession.value)
    }

    @Test
    fun `충전이 끝나면 진행 중 세션이 사라진다`() = runTest(UnconfinedTestDispatcher()) {
        val (vehicle, ledger, voice) = env()
        val c = RewardCoordinator(vehicle, ledger, data, { car }, voice, backgroundScope)
        vehicle.setCharging(true)
        now = now.plusMinutes(20); vehicle.setCharging(false)
        now = now.plusMinutes(2); vehicle.setCharging(false)
        assertNull(c.activeSession.value)
    }

    @Test
    fun `효율 운전 트립이 끝나면 10점`() = runTest(UnconfinedTestDispatcher()) {
        val (vehicle, ledger, voice) = env()
        RewardCoordinator(vehicle, ledger, data, { car }, voice, backgroundScope)
        vehicle.endTrip(TripSummary(12.0, 5.6, now))
        assertEquals(listOf(RewardKind.EFFICIENT_TRIP), ledger.entries.value.map { it.kind })
    }

    @Test
    fun `12개월 지난 포인트는 잔액에서 빠진다`() {
        val ledger = InMemoryRewardLedger()
        kotlinx.coroutines.runBlocking {
            ledger.append(listOf(RewardEntry(RewardKind.CHARGE, 10, now.minusMonths(13)), RewardEntry(RewardKind.CHARGE, 10, now.minusMonths(11))))
        }
        assertEquals(10, ledger.balance(now))
    }

    @Test
    fun `시연용 충전 종료는 한 번으로 세션을 끝낸다`() = runTest(UnconfinedTestDispatcher()) {
        val (vehicle, ledger, voice) = env()
        val c = RewardCoordinator(vehicle, ledger, data, { car }, voice, backgroundScope)
        vehicle.setCharging(true)
        vehicle.endCharging()
        assertNull(c.activeSession.value)
    }

    @Test
    fun `충전 시작 뒤 위치가 잡히면 그때 충전소를 찾아 적립한다`() = runTest(UnconfinedTestDispatcher()) {
        val (vehicle, ledger, voice) = env()
        RewardCoordinator(vehicle, ledger, data, { car }, voice, backgroundScope)
        vehicle.clearLocation()
        vehicle.setCharging(true)
        assertEquals(0, ledger.balance(now))
        vehicle.setLocation(yangjae.lat, yangjae.lng)
        now = now.plusSeconds(5); vehicle.setCharging(true)
        now = now.plusSeconds(5); vehicle.setCharging(true)
        assertEquals(30, ledger.balance(now))
    }
}
