package com.chargecompass.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.chargecompass.app.ui.AppRoot

class MainActivity : ComponentActivity() {
    private lateinit var container: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestMissingPermissions()
        container = AppContainer(this, AppContainer.savedMode(this), lifecycleScope)
        setContent {
            AppRoot(container, onSwitchMode = { mode ->
                AppContainer.saveMode(this, mode)
                recreate()
            })
        }
    }

    /** 위치(충전소 매칭)와 마이크(음성 응답)는 실행 중 승인이 필요하다. 거절해도 나머지 기능은 동작한다. */
    private fun requestMissingPermissions() {
        val missing = RUNTIME_PERMISSIONS.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1)
    }

    override fun onDestroy() {
        container.stop()
        super.onDestroy()
    }

    private companion object {
        val RUNTIME_PERMISSIONS = listOf(
            "pleos.car.permission.FUSED_LOCATION",
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
        )
    }
}
