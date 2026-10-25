package com.chargecompass.app.voice

import kotlin.test.Test
import kotlin.test.assertEquals

class YesNoParserTest {
    @Test
    fun `긍정 표현은 YES`() {
        listOf("응", "네", "예", "그래", "좋아", "추가해", "응 추가해줘", "네.", " 좋아! ").forEach {
            assertEquals(Answer.YES, YesNoParser.parse(it), it)
        }
    }

    @Test
    fun `부정 표현은 NO`() {
        listOf("아니", "아니요", "됐어", "싫어", "취소", "아니 됐어").forEach {
            assertEquals(Answer.NO, YesNoParser.parse(it), it)
        }
    }

    @Test
    fun `긍정과 부정이 섞이면 NO`() {
        assertEquals(Answer.NO, YesNoParser.parse("응 아니"))
        assertEquals(Answer.NO, YesNoParser.parse("네 아니요 취소해"))
    }

    @Test
    fun `알아듣지 못하면 UNKNOWN`() {
        assertEquals(Answer.UNKNOWN, YesNoParser.parse(null))
        assertEquals(Answer.UNKNOWN, YesNoParser.parse(""))
        assertEquals(Answer.UNKNOWN, YesNoParser.parse("날씨 어때"))
    }
}
