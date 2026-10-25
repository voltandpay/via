package com.chargecompass.app.drivestate

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DriveStateProviderTest {
    @Test
    fun `기어로 주행과 정차를 판단하고 충전 중이 우선한다`() = runTest(UnconfinedTestDispatcher()) {
        val parked = MutableStateFlow<Boolean?>(true)
        val charging = MutableStateFlow(false)
        val p = GearDriveStateProvider(parked, charging, backgroundScope)
        assertEquals(DriveState.PARKED, p.state.value)
        parked.value = false
        assertEquals(DriveState.DRIVING, p.state.value)
        parked.value = true
        charging.value = true
        assertEquals(DriveState.CHARGING, p.state.value)
    }

    @Test
    fun `기어를 모르면 안전하게 주행으로 본다`() = runTest(UnconfinedTestDispatcher()) {
        val p = GearDriveStateProvider(MutableStateFlow(null), MutableStateFlow(false), backgroundScope)
        assertEquals(DriveState.DRIVING, p.state.value)
    }

    @Test
    fun `시연 토글은 정한 상태를 따르고 충전 중이 우선한다`() = runTest(UnconfinedTestDispatcher()) {
        val charging = MutableStateFlow(false)
        val p = ToggleDriveStateProvider(charging, backgroundScope)
        p.set(DriveState.DRIVING)
        assertEquals(DriveState.DRIVING, p.state.value)
        charging.value = true
        assertEquals(DriveState.CHARGING, p.state.value)
    }
}
