package com.chargecompass.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class WindowLayoutTest {
    @Test
    fun `가로 앱 창은 넓은 배치, 분할 화면 세로 창은 좁은 배치`() {
        assertEquals(WindowLayout.WIDE, WindowLayout.of(1700))   // Connect-S 17 가로
        assertEquals(WindowLayout.WIDE, WindowLayout.of(1256))   // Connect-WF 가로
        assertEquals(WindowLayout.NARROW, WindowLayout.of(840))  // Connect-S 17 세로
        assertEquals(WindowLayout.NARROW, WindowLayout.of(472))  // Connect-S 14.6 세로
    }
}
