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
import kotlin.test.assertTrue

class RewardCoordinatorTest {
    private val data = MockDataLoader.load { javaClass.classLoader.getResource(it)!!.readText() }
    private val car = data.vehicles.first { it.id == "ioniq5" }
    private val yangjae = data.stations.first { it.id == "st-yangjae-chaevi" }
    private var now = LocalDateTime.of(2026, 11, 3, 14, 0)

    private fun env() = Triple(MockVehicleRepository { now }, InMemoryRewardLedger(), MockVoiceGateway())

    @Test
    fun `새 충전소에서 충전하면 30점과 스탬프 음성, 이번 주 첫 등록이면 미션 15점`() = runTest(UnconfinedTestDispatcher()) {
        val (vehicle, ledger, voice) = env()
        val c = RewardCoordinator(vehicle, ledger, data, { car }, voice, backgroundScope)
        vehicle.setLocation(yangjae.lat, yangjae.lng)
        vehicle.setCharging(true)
        assertEquals(45, ledger.balance(now))
        assertEquals(listOf("새 스탬프 획득!", "주간 미션 달성! 새 충전소 1곳 등록"), voice.spoken)
        // 충전 화면의 "이번 충전으로 받은 포인트"에도 미션이 들어간다
        assertTrue(c.sessionEarned.value.any { it.kind == RewardKind.MISSION })
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
        // 45 + 10 = 55 → 시즌 1단계(50) 보상 +10
        assertEquals(65, ledger.balance(now))
        assertEquals(2, voice.spoken.size)
    }

    @Test
    fun `충전이 30초 끊겼다 이어지면 추가 적립 없음`() = runTest(UnconfinedTestDispatcher()) {
        val (vehicle, ledger, voice) = env()
        val c = RewardCoordinator(vehicle, ledger, data, { car }, voice, backgroundScope)
        vehicle.setLocation(yangjae.lat, yangjae.lng)
        vehicle.setCharging(true)
        now = now.plusSeconds(5); vehicle.setCharging(false)
        now = now.plusSeconds(30); vehicle.setCharging(true)
        assertEquals(45, // 충전 10 + 스탬프 20 + 이번 주 새 충전소 미션 15
            ledger.balance(now))
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
        assertEquals(45, // 충전 10 + 스탬프 20 + 이번 주 새 충전소 미션 15
            ledger.balance(now))
    }

    @Test
    fun `이번 충전으로 받은 포인트는 현재 세션 적립분만이다`() = runTest(UnconfinedTestDispatcher()) {
        val (vehicle, _, voice) = env()
        // 시연 시계로 미래 시각에 쌓인 같은 충전소의 기록이 이미 있어도 섞이면 안 된다
        val ledger = InMemoryRewardLedger(listOf(
            RewardEntry(RewardKind.CHARGE, 10, now.plusHours(9), yangjae.id),
            RewardEntry(RewardKind.NEW_STAMP, 20, now.plusHours(9), yangjae.id),
        ))
        val c = RewardCoordinator(vehicle, ledger, data, { car }, voice, backgroundScope)
        vehicle.setLocation(yangjae.lat, yangjae.lng)
        vehicle.setCharging(true)
        assertEquals(listOf(RewardKind.CHARGE), c.sessionEarned.value.map { it.kind })
        assertEquals(10, c.sessionEarned.value.sumOf { it.points })
    }

    @Test
    fun `충전이 끝나면 세션 적립분을 비우고 위치가 늦게 잡히면 그때 적립분을 보여준다`() = runTest(UnconfinedTestDispatcher()) {
        val (vehicle, ledger, voice) = env()
        val c = RewardCoordinator(vehicle, ledger, data, { car }, voice, backgroundScope)
        vehicle.clearLocation()
        vehicle.setCharging(true)
        assertTrue(c.sessionEarned.value.isEmpty())
        vehicle.setLocation(yangjae.lat, yangjae.lng)
        now = now.plusSeconds(5); vehicle.setCharging(true)
        assertEquals(45, // 충전 10 + 스탬프 20 + 이번 주 새 충전소 미션 15
            c.sessionEarned.value.sumOf { it.points })
        now = now.plusMinutes(10); vehicle.endCharging()
        assertTrue(c.sessionEarned.value.isEmpty())
    }

    @Test
    fun `충전이 끝나면 실제 충전량으로 절약액과 받은 포인트를 요약한다`() = runTest(UnconfinedTestDispatcher()) {
        val (vehicle, ledger, voice) = env()
        val c = RewardCoordinator(vehicle, ledger, data, { car }, voice, backgroundScope, { setOf("m-chaevi") })
        vehicle.setLocation(yangjae.lat, yangjae.lng)
        vehicle.setSoc(30)
        vehicle.setCharging(true)
        assertNull(c.lastSummary.value)
        vehicle.setSoc(55)
        now = now.plusMinutes(20); vehicle.setCharging(true)
        now = now.plusMinutes(1); vehicle.endCharging()
        val sum = c.lastSummary.value
        assertNotNull(sum)
        // (55−30)% × 77.4kWh = 19.35kWh · 회원가 299원 = 5,786원, 비회원가 520원 = 10,062원 → 4,276원 절약
        assertEquals("양재 채비 충전소", sum.stationName)
        assertEquals(19.35, sum.chargedKwh, 0.001)
        assertEquals(5786, sum.costWon)
        assertEquals(4276, sum.savingWon)
        assertEquals(45, // 충전 10 + 스탬프 20 + 이번 주 새 충전소 미션 15
            sum.points)
        c.dismissSummary()
        assertNull(c.lastSummary.value)
    }

    @Test
    fun `충전소를 못 찾았거나 충전량을 모르면 요약하지 않는다`() = runTest(UnconfinedTestDispatcher()) {
        val (vehicle, ledger, voice) = env()
        val c = RewardCoordinator(vehicle, ledger, data, { car }, voice, backgroundScope, { setOf("m-chaevi") })
        vehicle.clearLocation()
        vehicle.setCharging(true)
        now = now.plusMinutes(20); vehicle.endCharging()
        assertNull(c.lastSummary.value)
    }

    @Test
    fun `충전 요약은 기록에 남아 앱을 다시 켜도 7일 차트에 쓸 수 있다`() = runTest(UnconfinedTestDispatcher()) {
        val (vehicle, ledger, voice) = env()
        val log = com.chargecompass.app.store.InMemorySummaryLog()
        RewardCoordinator(vehicle, ledger, data, { car }, voice, backgroundScope, { setOf("m-chaevi") }, log)
        vehicle.setLocation(yangjae.lat, yangjae.lng)
        vehicle.setSoc(30); vehicle.setCharging(true)
        vehicle.setSoc(55); now = now.plusMinutes(20); vehicle.setCharging(true)
        now = now.plusMinutes(1); vehicle.endCharging()
        assertEquals(1, log.summaries.value.size)
        assertEquals(4276, log.summaries.value.single().savingWon)
        assertEquals(now, log.summaries.value.single().endedAt)
    }

    @Test
    fun `지역의 마지막 충전소를 처음 충전하면 지역 완성 보너스와 음성`() = runTest(UnconfinedTestDispatcher()) {
        val (vehicle, ledger, voice) = env()
        // 강남·서초의 나머지 세 곳은 이미 스탬프
        ledger.append(data.stations.filter { it.region == "gangnam" && it.id != yangjae.id }.map { RewardEntry(RewardKind.NEW_STAMP, 20, now.minusDays(1), it.id) })
        RewardCoordinator(vehicle, ledger, data, { car }, voice, backgroundScope)
        vehicle.setLocation(yangjae.lat, yangjae.lng)
        vehicle.setCharging(true)
        assertTrue(ledger.entries.value.any { it.kind == RewardKind.REGION_COMPLETE && it.regionId == "gangnam" })
        assertEquals("강남·서초 도감 완성! 보너스 50포인트", voice.spoken.first())
    }

    @Test
    fun `효율 운전 두 번째 트립에서 주간 미션 달성`() = runTest(UnconfinedTestDispatcher()) {
        val (vehicle, ledger, voice) = env()
        RewardCoordinator(vehicle, ledger, data, { car }, voice, backgroundScope)
        vehicle.endTrip(TripSummary(12.0, 5.6, now))
        assertTrue(ledger.entries.value.none { it.kind == RewardKind.MISSION })
        vehicle.endTrip(TripSummary(12.0, 5.6, now.plusHours(1)))
        assertEquals(listOf("EFFICIENT_TRIP"), ledger.entries.value.filter { it.kind == RewardKind.MISSION }.map { it.eventId })
        assertEquals(listOf("주간 미션 달성! 효율 운전 2번"), voice.spoken)
    }

    @Test
    fun `이번 주 비회원가보다 싸게 두 번 충전했으면 할인 충전 미션`() = runTest(UnconfinedTestDispatcher()) {
        val (vehicle, ledger, voice) = env()
        val log = com.chargecompass.app.store.InMemorySummaryLog(List(2) { ChargeSummary("양재 채비 충전소", 30.0, 9000, 6000, 10, now.minusHours(2)) })
        RewardCoordinator(vehicle, ledger, data, { car }, voice, backgroundScope, summaryLog = log)
        vehicle.endTrip(TripSummary(3.0, 4.0, now)) // 보상 없는 트립도 미션 확인의 계기
        assertEquals(listOf("DISCOUNT_CHARGE"), ledger.entries.value.filter { it.kind == RewardKind.MISSION }.map { it.eventId })
    }

    @Test
    fun `시즌 경험치가 단계를 넘으면 시즌 보상이 쌓이고 충전 화면 적립분에도 보인다`() = runTest(UnconfinedTestDispatcher()) {
        val (vehicle, ledger, voice) = env()
        ledger.append(listOf(RewardEntry(RewardKind.EFFICIENT_TRIP, 10, now.minusDays(1))))   // 이번 시즌 10P
        val c = RewardCoordinator(vehicle, ledger, data, { car }, voice, backgroundScope)
        vehicle.setLocation(yangjae.lat, yangjae.lng)
        vehicle.setCharging(true)   // +45P → 시즌 경험치 55 → 1단계(50)
        val step = ledger.entries.value.single { it.kind == RewardKind.SEASON_STEP }
        assertEquals(10, step.points)
        assertTrue(c.sessionEarned.value.any { it.kind == RewardKind.SEASON_STEP })
    }

    @Test
    fun `충전 중 앱이 다시 시작돼도 포인트가 다시 쌓이지 않고 이번 충전 적립분은 다시 보인다`() = runTest(UnconfinedTestDispatcher()) {
        val (vehicle, ledger, voice) = env()
        RewardCoordinator(vehicle, ledger, data, { car }, voice, backgroundScope)
        vehicle.setLocation(yangjae.lat, yangjae.lng)
        vehicle.setCharging(true)
        val before = ledger.balance(now)
        // 재시작: 새 차량 저장소·새 코디네이터, 같은 원장
        val vehicle2 = com.chargecompass.app.vehicle.MockVehicleRepository { now }
        now = now.plusMinutes(3)
        val c2 = RewardCoordinator(vehicle2, ledger, data, { car }, voice, backgroundScope)
        vehicle2.setLocation(yangjae.lat, yangjae.lng)
        vehicle2.setCharging(true)
        assertEquals(before, ledger.balance(now))
        assertTrue(c2.sessionEarned.value.any { it.kind == RewardKind.CHARGE })
    }

    @Test
    fun `플러그를 1분 넘게 뺐다가 다시 꽂아도 같은 방문이라 보상은 한 번`() = runTest(UnconfinedTestDispatcher()) {
        val (vehicle, ledger, voice) = env()
        RewardCoordinator(vehicle, ledger, data, { car }, voice, backgroundScope)
        vehicle.setLocation(yangjae.lat, yangjae.lng)
        vehicle.setCharging(true)
        val before = ledger.balance(now)
        repeat(3) {
            now = now.plusMinutes(1); vehicle.setCharging(false)
            now = now.plusMinutes(2); vehicle.setCharging(false)   // 세션 종료
            now = now.plusMinutes(1); vehicle.setCharging(true)    // 다시 꽂음
        }
        assertEquals(before, ledger.balance(now))
    }

    @Test
    fun `위치가 늦게 잡혀도 처음 Started에서 받은 미션 적립분이 이번 충전 화면에 남는다`() = runTest(UnconfinedTestDispatcher()) {
        val (vehicle, ledger, voice) = env()
        // 이번 주 효율 운전 2번 → 다음 확인 때 미션 달성
        ledger.append(listOf(RewardEntry(RewardKind.EFFICIENT_TRIP, 10, now.minusHours(2)), RewardEntry(RewardKind.EFFICIENT_TRIP, 10, now.minusHours(1))))
        val c = RewardCoordinator(vehicle, ledger, data, { car }, voice, backgroundScope)
        vehicle.clearLocation()
        vehicle.setCharging(true)   // 충전소 없이 시작 → 미션만 적립
        assertTrue(c.sessionEarned.value.any { it.kind == RewardKind.MISSION })
        vehicle.setLocation(yangjae.lat, yangjae.lng)
        now = now.plusSeconds(5); vehicle.setCharging(true)
        val earned = c.sessionEarned.value
        // 처음 Started에서 받은 '효율 운전 2번' 미션이 두 번째 Started 뒤에도 남아 있어야 한다
        assertTrue(earned.any { it.kind == RewardKind.MISSION && it.eventId == "EFFICIENT_TRIP" }, earned.toString())
        assertTrue(earned.any { it.kind == RewardKind.CHARGE }, earned.toString())
    }

    @Test
    fun `시연 시계로 미래에 쌓인 충전 기록이 있어도 최근 충전 기준으로 다시 꽂기를 막는다`() = runTest(UnconfinedTestDispatcher()) {
        val (vehicle, ledger, voice) = env()
        ledger.append(listOf(RewardEntry(RewardKind.CHARGE, 10, now.plusDays(30), yangjae.id), RewardEntry(RewardKind.NEW_STAMP, 20, now.plusDays(30), yangjae.id)))
        RewardCoordinator(vehicle, ledger, data, { car }, voice, backgroundScope)
        vehicle.setLocation(yangjae.lat, yangjae.lng)
        vehicle.setCharging(true)
        val after = ledger.entries.value.count { it.kind == RewardKind.CHARGE }
        now = now.plusMinutes(1); vehicle.setCharging(false)
        now = now.plusMinutes(2); vehicle.setCharging(false)
        now = now.plusMinutes(1); vehicle.setCharging(true)
        assertEquals(after, ledger.entries.value.count { it.kind == RewardKind.CHARGE })
    }
}
