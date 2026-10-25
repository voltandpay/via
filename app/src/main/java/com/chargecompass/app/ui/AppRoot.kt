package com.chargecompass.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.chargecompass.app.AppContainer
import com.chargecompass.app.BuildConfig
import com.chargecompass.app.DataMode
import com.chargecompass.app.drivestate.DriveState

/**
 * 화면 노출 규칙(설계 6-1절): 주행 중에는 터치할 것이 없는 상태 카드만, 정차 중에는 전체 화면, 충전 중에는 충전 화면.
 * 디버그 빌드의 시연 패널만 예외다.
 */
@Composable
fun AppRoot(c: AppContainer, onSwitchMode: (DataMode) -> Unit) {
    val drive by c.drive.state.collectAsState()
    val vehicleId by c.membership.vehicleModelId.collectAsState()
    val narrow by c.narrowPreview.collectAsState()
    ViaTheme {
        Box(Modifier.fillMaxSize()) {
            Box(if (narrow) Modifier.width(840.dp).fillMaxHeight() else Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalWindowLayout provides if (narrow) WindowLayout.NARROW else LocalWindowLayout.current) {
                    when {
                        drive == DriveState.DRIVING -> DriveCard(c)
                        vehicleId == null -> Onboarding(c)
                        drive == DriveState.CHARGING -> ChargingScreen(c)
                        else -> ParkedHome(c)
                    }
                }
            }
            if (BuildConfig.DEBUG) DebugPanel(c, onSwitchMode)
        }
    }
}
