package dev.renova.app

import dev.renova.app.domain.Enforcement
import dev.renova.app.domain.OffscreenAction
import dev.renova.app.domain.OwnScreen
import org.junit.Assert.assertEquals
import org.junit.Test

class EnforcementTest {

    @Test fun blockedVideoOffscreenIsPausedDuringReadingAndPulledElsewhere() {
        assertEquals(OffscreenAction.PAUSE, Enforcement.offscreenAction(blockedOutside = true, ownScreen = OwnScreen.READING))
        assertEquals(OffscreenAction.PULL, Enforcement.offscreenAction(blockedOutside = true, ownScreen = OwnScreen.OTHER))
        assertEquals(OffscreenAction.PULL, Enforcement.offscreenAction(blockedOutside = true, ownScreen = null))
    }

    @Test fun nothingBlockedOffscreenDoesNothing() {
        for (screen in listOf(null, OwnScreen.READING, OwnScreen.OTHER)) {
            assertEquals(OffscreenAction.NONE, Enforcement.offscreenAction(blockedOutside = false, ownScreen = screen))
        }
    }
}
