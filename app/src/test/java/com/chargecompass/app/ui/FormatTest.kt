package com.chargecompass.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class FormatTest {
    @Test
    fun `금액은 천 단위 쉼표와 원으로 표시한다`() {
        assertEquals("0원", won(0))
        assertEquals("1,234원", won(1234))
        assertEquals("1,234,567원", won(1234567))
        assertEquals("-500원", won(-500))
        assertEquals("-1,234원", won(-1234))
    }
}
