package com.chargecompass.app.voice

import com.chargecompass.app.insights.spokenWon

/** 주행 중에도 화면을 보지 않고 물을 수 있는 것. */
enum class VoiceCommand { CHEAPEST, SAVINGS, ARRIVAL, RANGE, POINTS, UNKNOWN }

data class BestStation(val name: String, val km: Int, val wonPerKwh: Int, val savingWon: Int)

/** 답에 필요한 지금 상태(앱이 매번 새로 만든다). */
data class AnswerContext(
    val destination: String?,
    val arrivalSoc: Int?,
    val best: BestStation?,
    val monthSavingWon: Int,
    val balance: Int,
    val rangeKm: Int?,
    val socPct: Int?,
)

/** 정해진 몇 가지 질문만 알아듣는다(기기 안 STT 결과를 낱말로 판단). */
object VoiceCommands {
    private val rules = listOf(
        VoiceCommand.SAVINGS to listOf("아꼈", "아낀", "절약"),
        VoiceCommand.ARRIVAL to listOf("도착"),
        VoiceCommand.RANGE to listOf("갈 수", "주행 가능", "얼마나 가"),
        VoiceCommand.POINTS to listOf("포인트"),
        VoiceCommand.CHEAPEST to listOf("싼", "충전소", "충전할"),
    )

    fun parse(text: String?): VoiceCommand {
        val t = text?.replace(" ", "") ?: return VoiceCommand.UNKNOWN
        return rules.firstOrNull { (_, words) -> words.any { t.contains(it.replace(" ", "")) } }?.first ?: VoiceCommand.UNKNOWN
    }

    fun answer(cmd: VoiceCommand, c: AnswerContext): String = when (cmd) {
        VoiceCommand.CHEAPEST -> c.best?.let { "${it.km}km 앞 ${withSubjectParticle(it.name)} 가장 싸요. kWh당 ${it.wonPerKwh}원, 비회원가보다 ${spokenWon(it.savingWon)} 아껴요." }
            ?: if (c.destination == null) "목적지를 정하면 경로 위에서 가장 싼 충전소를 찾아드려요." else "지금 경로는 충전 없이 갈 수 있어요."
        VoiceCommand.SAVINGS -> "이번 달 비회원가보다 ${spokenWon(c.monthSavingWon)} 아꼈어요."
        VoiceCommand.ARRIVAL -> if (c.destination != null && c.arrivalSoc != null) "${c.destination}에 도착하면 배터리가 ${c.arrivalSoc}%예요." else "목적지를 정하면 도착할 때 배터리를 알려드려요."
        VoiceCommand.RANGE -> if (c.socPct != null && c.rangeKm != null) "지금 배터리 ${c.socPct}%로 약 ${c.rangeKm}km 갈 수 있어요." else "배터리 정보를 아직 받지 못했어요."
        VoiceCommand.POINTS -> "포인트는 ${c.balance}포인트예요."
        VoiceCommand.UNKNOWN -> "잘 못 들었어요. 가장 싼 충전소, 이번 달 아낀 돈, 도착 배터리를 물어보세요."
    }
}

/**
 * 말하기 버튼을 누르면: "무엇을 도와드릴까요?" → 들은 말을 명령으로 → 짧게 답한다.
 * 가장 싼 충전소를 물었고 아직 경로에 넣지 않았다면 이어서 "경유지로 추가할까요?"를 묻는다.
 */
class VoiceAssistant(
    private val voice: VoiceGateway,
    private val context: () -> AnswerContext,
    private val offerStation: () -> String?,
    private val addStation: (String) -> Unit,
) {
    suspend fun handle() {
        if (!voice.sttAvailable) {
            voice.speak("지금은 음성으로 대답을 들을 수 없어요. 정차하면 화면에서 확인해 주세요.")
            return
        }
        val cmd = VoiceCommands.parse(voice.listen("무엇을 도와드릴까요?"))
        voice.speak(VoiceCommands.answer(cmd, context()))
        if (cmd != VoiceCommand.CHEAPEST) return
        val id = offerStation() ?: return
        if (voice.ask("경유지로 추가할까요?") == Answer.YES) addStation(id)
    }
}

/** 받침이 있으면 "이", 없으면 "가"(한글이 아니면 "가"). */
fun withSubjectParticle(word: String): String {
    val last = word.lastOrNull() ?: return word
    val hasFinal = last in '가'..'힣' && (last - '가') % 28 != 0
    return word + if (hasFinal) "이" else "가"
}
