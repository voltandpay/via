package com.chargecompass.app.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val voiceLock = Mutex()
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
        initSpeechToText()
    }

    /** 권한이 없어 처음 초기화에 실패했으면, 허용 뒤 다시 초기화한다(이미 쓸 수 있으면 그대로). */
    override fun retrySpeechToText() {
        if (!sttAvailable) initSpeechToText()
    }

    private fun initSpeechToText() {
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

    /** 한 번에 한 문장씩만 말한다(스탬프 알림과 추천 질문이 겹쳐도 완료 신호가 섞이지 않게). */
    override suspend fun speak(text: String) = voiceLock.withLock { speakLocked(text) }

    /** 질문과 듣기를 한 덩어리로 묶는다. 목적지가 바뀌어 취소되면 말하던 것과 듣던 것을 바로 멈춘다. */
    override suspend fun ask(question: String, timeoutMs: Long): Answer = voiceLock.withLock {
        speakLocked(question)
        if (!sttAvailable) return@withLock Answer.UNKNOWN
        val heard = CompletableDeferred<String?>().also { listening = it }
        try {
            stt.request()
            val text = withTimeoutOrNull(timeoutMs) { heard.await() }
            if (text == null && !heard.isCompleted) {
                stt.stop()
                return@withLock Answer.TIMEOUT
            }
            YesNoParser.parse(text)
        } catch (e: CancellationException) {
            stt.stop()
            throw e
        } finally {
            listening = null
        }
    }

    private suspend fun speakLocked(text: String) {
        val done = CompletableDeferred<Unit>().also { speaking = it }
        try {
            tts.speak(text)
            withTimeoutOrNull(SPEAK_TIMEOUT_MS) { done.await() }
        } catch (e: CancellationException) {
            tts.stop()
            throw e
        } finally {
            speaking = null
        }
    }

    companion object {
        private const val TAG = "PleosVoice"
        private const val SPEAK_TIMEOUT_MS = 15_000L
    }
}
