package com.chargecompass.app.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VoiceCommandsTest {
    @Test
    fun `말한 문장을 명령으로 알아듣는다`() {
        assertEquals(VoiceCommand.CHEAPEST, VoiceCommands.parse("제일 싼 충전소 어디야"))
        assertEquals(VoiceCommand.CHEAPEST, VoiceCommands.parse("가까운 충전소 찾아줘"))
        assertEquals(VoiceCommand.SAVINGS, VoiceCommands.parse("이번 달 얼마 아꼈어?"))
        assertEquals(VoiceCommand.ARRIVAL, VoiceCommands.parse("도착하면 배터리 몇 퍼센트야"))
        assertEquals(VoiceCommand.RANGE, VoiceCommands.parse("얼마나 갈 수 있어"))
        assertEquals(VoiceCommand.POINTS, VoiceCommands.parse("내 포인트 얼마야"))
        assertEquals(VoiceCommand.UNKNOWN, VoiceCommands.parse("노래 틀어줘"))
        assertEquals(VoiceCommand.UNKNOWN, VoiceCommands.parse(null))
    }

    private val ctx = AnswerContext(
        destination = "서울역", arrivalSoc = 9, best = BestStation("양재 채비 충전소", 11, 299, 11_632),
        monthSavingWon = 42_300, balance = 780, rangeKm = 58, socPct = 15,
    )

    @Test
    fun `명령마다 짧게 답한다`() {
        assertEquals("11km 앞 양재 채비 충전소가 가장 싸요. kWh당 299원, 비회원가보다 1만 1천 6백 원 아껴요.", VoiceCommands.answer(VoiceCommand.CHEAPEST, ctx))
        assertEquals("이번 달 비회원가보다 4만 2천 3백 원 아꼈어요.", VoiceCommands.answer(VoiceCommand.SAVINGS, ctx))
        assertEquals("서울역에 도착하면 배터리가 9%예요.", VoiceCommands.answer(VoiceCommand.ARRIVAL, ctx))
        assertEquals("지금 배터리 15%로 약 58km 갈 수 있어요.", VoiceCommands.answer(VoiceCommand.RANGE, ctx))
        assertEquals("포인트는 780포인트예요.", VoiceCommands.answer(VoiceCommand.POINTS, ctx))
        assertTrue(VoiceCommands.answer(VoiceCommand.UNKNOWN, ctx).startsWith("잘 못 들었어요"))
    }

    @Test
    fun `충전소 이름 끝에 받침이 있으면 '이'를 붙인다`() {
        val c = ctx.copy(best = BestStation("용산 워터 초급속", 20, 330, 5000))
        assertTrue(VoiceCommands.answer(VoiceCommand.CHEAPEST, c).startsWith("20km 앞 용산 워터 초급속이 가장 싸요"))
    }

    @Test
    fun `경로나 추천이 없으면 그렇게 말한다`() {
        val none = ctx.copy(destination = null, arrivalSoc = null, best = null)
        assertEquals("목적지를 정하면 경로 위에서 가장 싼 충전소를 찾아드려요.", VoiceCommands.answer(VoiceCommand.CHEAPEST, none))
        assertEquals("목적지를 정하면 도착할 때 배터리를 알려드려요.", VoiceCommands.answer(VoiceCommand.ARRIVAL, none))
    }

    @Test
    fun `가장 싼 충전소를 답한 뒤 아직 넣지 않았으면 경유지로 넣을지 묻고, 응이면 넣는다`() = kotlinx.coroutines.test.runTest {
        val voice = MockVoiceGateway(answers = listOf(Answer.YES)).apply { heard += "제일 싼 충전소 어디야" }
        var added: String? = null
        VoiceAssistant(voice, { ctx }, offerStation = { "st-yangjae-chaevi" }, addStation = { added = it }).handle()
        assertEquals(listOf("무엇을 도와드릴까요?"), voice.listened)
        assertTrue(voice.spoken.first().contains("양재 채비 충전소가 가장 싸요"))
        assertEquals(listOf("경유지로 추가할까요?"), voice.asked)
        assertEquals("st-yangjae-chaevi", added)
    }

    @Test
    fun `음성 인식을 못 쓰면 화면 안내로 대신한다`() = kotlinx.coroutines.test.runTest {
        val voice = MockVoiceGateway(sttAvailable = false)
        VoiceAssistant(voice, { ctx }, offerStation = { null }, addStation = {}).handle()
        assertEquals(listOf("지금은 음성으로 대답을 들을 수 없어요. 정차하면 화면에서 확인해 주세요."), voice.spoken)
    }
}
