package com.chargecompass.app.vehicle

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MockVehicleRepositoryTest {
    @Test
    fun `SoC를 바꾸면 energy에 바로 반영된다`() {
        val repo = MockVehicleRepository()
        repo.setSoc(15)
        assertEquals(15, repo.energy.value.socPct)
    }

    @Test
    fun `충전을 켜면 충전 샘플이 하나 나온다`() = runTest(UnconfinedTestDispatcher()) {
        val repo = MockVehicleRepository()
        repo.setLocation(37.4840, 127.0350)
        val received = mutableListOf<Boolean>()
        val job = launch { repo.charging.collect { received += it.charging } }
        repo.setCharging(true)
        job.cancel()
        assertEquals(listOf(true), received)
    }

    @Test
    fun `트립 종료를 흘려보낸다`() = runTest(UnconfinedTestDispatcher()) {
        val repo = MockVehicleRepository()
        val trip = TripSummary(12.0, 5.5, java.time.LocalDateTime.of(2026, 11, 3, 9, 0))
        val got = async { repo.tripEnded.first() }
        repo.endTrip(trip)
        assertEquals(trip, got.await())
    }

    @Test
    fun `기어를 바꾸면 parked가 바뀐다`() {
        val repo = MockVehicleRepository()
        assertTrue(repo.parked.value == true)
        repo.setParked(false)
        assertEquals(false, repo.parked.value)
    }
}
