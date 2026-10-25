package com.chargecompass.app.voice

enum class Answer { YES, NO, UNKNOWN, TIMEOUT }

/** 주행 중 안내는 음성으로만 한다. 확인 질문은 예/아니오로만 받는다. */
interface VoiceGateway {
    val sttAvailable: Boolean
    suspend fun speak(text: String)
    /** 질문을 읽고 답을 듣는다. STT를 못 쓰면 바로 UNKNOWN. */
    suspend fun ask(question: String, timeoutMs: Long = 8_000): Answer
    fun start()
    fun stop()
}

object YesNoParser {
    private val yes = listOf("응", "네", "예", "그래", "좋아", "추가")
    private val no = listOf("아니", "됐어", "싫어", "취소", "하지마", "말아", "안해", "안돼")

    fun parse(utterance: String?): Answer {
        val text = utterance?.filter { it.isLetterOrDigit() }.orEmpty()
        if (text.isEmpty()) return Answer.UNKNOWN
        return when {
            no.any { it in text } -> Answer.NO
            yes.any { it in text } -> Answer.YES
            else -> Answer.UNKNOWN
        }
    }
}
