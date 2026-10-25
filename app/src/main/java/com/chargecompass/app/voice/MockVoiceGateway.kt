package com.chargecompass.app.voice

/** 테스트·시연용. 말한 문장을 기록하고, 질문에는 [answers]를 차례로 돌려준다. */
class MockVoiceGateway(
    override var sttAvailable: Boolean = true,
    answers: List<Answer> = emptyList(),
) : VoiceGateway {
    val spoken = mutableListOf<String>()
    val asked = mutableListOf<String>()
    private val queue = ArrayDeque(answers)

    fun enqueue(answer: Answer) = queue.addLast(answer)

    override suspend fun speak(text: String) { spoken += text }

    override suspend fun ask(question: String, timeoutMs: Long): Answer {
        asked += question
        if (!sttAvailable) return Answer.UNKNOWN
        return queue.removeFirstOrNull() ?: Answer.TIMEOUT
    }

    override fun start() = Unit
    override fun stop() = Unit
}
