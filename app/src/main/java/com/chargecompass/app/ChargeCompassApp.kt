package com.chargecompass.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * 의존성은 Activity가 아니라 앱 프로세스에 둔다. 화면이 다시 만들어져도(주야간 전환 등)
 * 충전 세션 감지·경로 상태가 처음부터 다시 시작되지 않아야 포인트가 두 번 쌓이지 않는다.
 */
class ChargeCompassApp : Application() {
    private var scope = newScope()
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, AppContainer.savedMode(this), scope)
    }

    /** 시연 패널의 모드 전환. 기존 연결을 모두 닫고 새로 만든다. */
    fun switchMode(mode: DataMode) {
        AppContainer.saveMode(this, mode)
        // 먼저 작업을 취소하고(말하던 코루틴이 tts.stop()을 부른다) 그다음 SDK를 해제한다
        scope.cancel()
        container.stop()
        scope = newScope()
        container = AppContainer(this, mode, scope)
    }

    private fun newScope() = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}
