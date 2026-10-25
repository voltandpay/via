package com.chargecompass.app.drivestate

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class DriveState { DRIVING, PARKED, CHARGING }

/** 화면 노출 규칙의 기준. 충전 중이면 기어와 상관없이 CHARGING. */
interface DriveStateProvider {
    val state: StateFlow<DriveState>
}

/**
 * Vehicle SDK 기어로 판단한다(스파이크 2차: registerGearSelection 동작 확인).
 * 기어를 모르면 주행 중으로 본다 — 주행 중에 터치 화면이 뜨는 쪽이 더 위험하다.
 */
class GearDriveStateProvider(
    parked: StateFlow<Boolean?>,
    chargingActive: StateFlow<Boolean>,
    scope: CoroutineScope,
) : DriveStateProvider {
    override val state: StateFlow<DriveState> = combine(parked, chargingActive, ::resolve)
        .stateIn(scope, SharingStarted.Eagerly, resolve(parked.value, chargingActive.value))

    private fun resolve(parked: Boolean?, charging: Boolean) = when {
        charging -> DriveState.CHARGING
        parked == true -> DriveState.PARKED
        else -> DriveState.DRIVING
    }
}

/** 시연용: 디버그 패널에서 상태를 직접 고른다. */
class ToggleDriveStateProvider(
    chargingActive: StateFlow<Boolean>,
    scope: CoroutineScope,
) : DriveStateProvider {
    private val selected = MutableStateFlow(DriveState.PARKED)

    override val state: StateFlow<DriveState> = combine(selected, chargingActive) { s, charging ->
        if (charging) DriveState.CHARGING else s
    }.stateIn(scope, SharingStarted.Eagerly, selected.value)

    fun set(state: DriveState) { selected.value = state }
}
