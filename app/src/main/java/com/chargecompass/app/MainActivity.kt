package com.chargecompass.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.chargecompass.app.ui.AppRoot
import com.chargecompass.app.vehicle.PleosVehicleRepository

class MainActivity : ComponentActivity() {
    private val app get() = application as ChargeCompassApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestMissingPermissions()
        setContent {
            AppRoot(app.container, onSwitchMode = { mode ->
                app.switchMode(mode)
                recreate()
            })
        }
    }

    /** 위치(충전소 매칭)와 마이크(음성 응답)는 실행 중 승인이 필요하다. 거절해도 나머지 기능은 동작한다. */
    private fun requestMissingPermissions() {
        val missing = RUNTIME_PERMISSIONS.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        (app.container.vehicle as? PleosVehicleRepository)?.retryLocation()
        // 마이크를 지금 허용했으면 음성 인식도 다시 붙인다(앱을 다시 켜지 않아도 음성으로 대답할 수 있게)
        val mic = permissions.indexOf(Manifest.permission.RECORD_AUDIO)
        if (mic >= 0 && grantResults.getOrNull(mic) == PackageManager.PERMISSION_GRANTED) app.container.voice.retrySpeechToText()
    }

    private companion object {
        val RUNTIME_PERMISSIONS = listOf(
            "pleos.car.permission.FUSED_LOCATION",
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
        )
    }
}
