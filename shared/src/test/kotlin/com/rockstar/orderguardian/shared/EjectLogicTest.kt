package com.rockstar.orderguardian.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EjectLogicTest {

    @Test
    fun `first eject is always back`() {
        assertEquals(EjectAction.BACK, EjectLogic.nextAction(1, EjectStyle.BACK_ONLY))
        assertEquals(EjectAction.BACK, EjectLogic.nextAction(1, EjectStyle.BACK_THEN_CLOSE_APP))
    }

    @Test
    fun `second consecutive checkout scan closes the app in close-app style`() {
        assertEquals(EjectAction.HOME, EjectLogic.nextAction(2, EjectStyle.BACK_THEN_CLOSE_APP))
        assertEquals(EjectAction.HOME, EjectLogic.nextAction(3, EjectStyle.BACK_THEN_CLOSE_APP))
    }

    @Test
    fun `second consecutive checkout scan still goes back in back-only style`() {
        assertEquals(EjectAction.BACK, EjectLogic.nextAction(2, EjectStyle.BACK_ONLY))
    }
}
