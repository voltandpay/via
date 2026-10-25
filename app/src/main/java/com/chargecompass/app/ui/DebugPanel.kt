package com.chargecompass.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chargecompass.app.AppContainer
import com.chargecompass.app.DataMode
import com.chargecompass.app.drivestate.DriveState
import com.chargecompass.app.navi.PleosNaviRepository
import com.chargecompass.app.navi.RouteSnapshot
import com.chargecompass.app.vehicle.TripSummary
import java.time.LocalDate
import java.time.LocalDateTime

/** 디버그 빌드 전용 시연 패널. Mock 모드에서 차량·경로 상태를 직접 바꾼다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DebugPanel(c: AppContainer, onSwitchMode: (DataMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val energy by c.vehicle.energy.collectAsState()
    val drive by c.drive.state.collectAsState()
    val route by c.navi.route.collectAsState()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
        if (!open) {
            TextButton(onClick = { open = true }) { Text("DEV", color = Muted, fontSize = 18.sp) }
            return@Box
        }
        Column(
            Modifier.width(560.dp).padding(16.dp).background(Color(0xEE000000), RoundedCornerShape(16.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("시연 패널 · ${c.mode} · $drive", color = Color.White, fontSize = 20.sp)
            Text("SoC ${energy.socPct} · 경로 ${route?.destinationName ?: "-"} ${route?.remainingKm ?: ""}km", color = Muted, fontSize = 18.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val other = if (c.mode == DataMode.MOCK) DataMode.PLEOS else DataMode.MOCK
                OutlinedButton(onClick = { onSwitchMode(other) }) { Text("$other 모드로") }
                OutlinedButton(onClick = { open = false }) { Text("닫기") }
                OutlinedButton(onClick = { c.demoClock.jumpTo(LocalDate.now().atTime(23, 30)) }) { Text("밤 11:30으로") }
                OutlinedButton(onClick = { c.demoClock.reset() }) { Text("실제 시각") }
                (c.navi as? PleosNaviRepository)?.let { navi ->
                    OutlinedButton(onClick = { navi.requestDemoRoute() }) { Text("서울역 경로 요청") }
                }
            }
            if (c.mode == DataMode.MOCK) {
                val v = c.mockVehicle
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { c.toggleDrive.set(DriveState.DRIVING) }) { Text("주행") }
                    OutlinedButton(onClick = { c.toggleDrive.set(DriveState.PARKED) }) { Text("정차") }
                    OutlinedButton(onClick = { v.setSoc(15) }) { Text("SoC 15") }
                    OutlinedButton(onClick = { v.setSoc(80) }) { Text("SoC 80") }
                    OutlinedButton(onClick = { c.mockNavi.setRoute(RouteSnapshot(1, "서울역", 23.0, 2040)) }) { Text("서울역 안내") }
                    OutlinedButton(onClick = { c.mockNavi.setRoute(null) }) { Text("안내 종료") }
                    OutlinedButton(onClick = {
                        c.data.stations.first { it.id == "st-yangjae-chaevi" }.let { v.setLocation(it.lat, it.lng) }
                    }) { Text("양재 채비 도착") }
                    OutlinedButton(onClick = { v.setCharging(true) }) { Text("충전 시작") }
                    OutlinedButton(onClick = { v.endCharging() }) { Text("충전 끝") }
                    OutlinedButton(onClick = { v.endTrip(TripSummary(12.0, 5.6, LocalDateTime.now())) }) { Text("효율 트립 종료") }
                }
            }
        }
    }
}
