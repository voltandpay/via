package com.chargecompass.app.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * TTS는 기본 모드(서버 자격 증명이 없으면 On-device로 자동 전환), STT는 앱 등록이 필요 없는 ON_DEVICE 모드(스파이크 2차).
 */
class PleosVoiceGateway(context: Context) : VoiceGateway {
    private val tts = TextToSpeech(context.applicationContext)
    private val stt = SpeechToText(context.applicationContext, Mode.ON_DEVICE)

    @Volatile override var sttAvailable: Boolean = false
        private set
    @Volatile private var speaking: CompletableDeferred<Unit>? = null
    @Volatile private var listening: CompletableDeferred<String?>? = null

    private val ttsListener = object : EventListener {
        override fun onReady() = Unit
        override fun onStart() = Unit
        override fun onDone() { speaking?.complete(Unit) }
        override fun onStop() { speaking?.complete(Unit) }
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onError(errorType: ai.pleos.playground.tts.constant.ErrorType) {
            Log.w(TAG, "TTS 오류: $errorType")
            speaking?.complete(Unit)
        }
    }
    private val sttListener = object : ResultListener {
        override fun onReady() = Unit
        override fun onStartedRecognition() = Unit
        override fun onUpdated(stt: String, completed: Boolean) {
            if (completed) listening?.complete(stt)
        }
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onEndedRecognition() { listening?.complete(null) }
        override fun onError(errorType: ai.pleos.playground.stt.constant.ErrorType) {
            Log.w(TAG, "STT 오류: $errorType")
            listening?.complete(null)
        }
    }

    override fun start() {
        tts.initialize()
        tts.addEventListener(ttsListener)
        stt.initialize(object : ai.pleos.playground.stt.listener.OnInitializeListener {
            override fun onSuccess() {
                stt.addListener(sttListener)
                stt.setLocale(Locale.KOREA)
                sttAvailable = true
            }
            override fun onFailure(errorType: ai.pleos.playground.stt.constant.ErrorType) {
                Log.w(TAG, "STT 초기화 실패: $errorType")
            }
        })
    }

    override fun stop() {
        tts.removeEventListener(ttsListener)
        tts.release()
        stt.removeListener(sttListener)
        stt.release()
    }

    override suspend fun speak(text: String) {
        val done = CompletableDeferred<Unit>().also { speaking = it }
        tts.speak(text)
        withTimeoutOrNull(SPEAK_TIMEOUT_MS) { done.await() }
        speaking = null
    }

    override suspend fun ask(question: String, timeoutMs: Long): Answer {
        speak(question)
        if (!sttAvailable) return Answer.UNKNOWN
        val heard = CompletableDeferred<String?>().also { listening = it }
        stt.request()
        val text = withTimeoutOrNull(timeoutMs) { heard.await() }
        listening = null
        if (text == null && !heard.isCompleted) {
            stt.stop()
            return Answer.TIMEOUT
        }
        return YesNoParser.parse(text)
    }

    companion object {
        private const val TAG = "PleosVoice"
        private const val SPEAK_TIMEOUT_MS = 15_000L
    }
}
