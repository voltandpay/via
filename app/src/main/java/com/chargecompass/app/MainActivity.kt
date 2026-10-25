package com.chargecompass.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.chargecompass.app.drivestate.GearDriveStateProvider
import com.chargecompass.app.navi.PleosNaviRepository
import com.chargecompass.app.vehicle.PleosVehicleRepository
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private lateinit var vehicle: PleosVehicleRepository
    private lateinit var navi: PleosNaviRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vehicle = PleosVehicleRepository(this, lifecycleScope).also { it.start() }
        navi = PleosNaviRepository(this).also { it.start() }
        val drive = GearDriveStateProvider(vehicle.parked, MutableStateFlow(false), lifecycleScope)
        setContent {
            val energy by vehicle.energy.collectAsState()
            val state by drive.state.collectAsState()
            val route by navi.route.collectAsState()
            val charging = remember { mutableStateOf<Boolean?>(null) }
            LaunchedEffect(Unit) { vehicle.charging.collect { charging.value = it.charging } }
            Column(Modifier.padding(48.dp)) {
                listOf("SoC ${energy.socPct}", "전비 ${energy.efficiencyKmPerKwh}", "상태 $state", "충전 ${charging.value}", "경로 $route")
                    .forEach { Text(it, fontSize = 28.sp) }
            }
        }
    }

    override fun onDestroy() {
        vehicle.stop()
        navi.stop()
        super.onDestroy()
    }
}
