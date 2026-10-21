package dev.renova.app

import dev.renova.app.domain.AppWindow
import dev.renova.app.domain.Enforcement
import dev.renova.app.domain.OffscreenAction
import dev.renova.app.domain.OwnScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    private val launcher = "com.android.launcher"
    private val youtube = "com.google.android.youtube"

    private fun window(pkg: String?, pip: Boolean = false, active: Boolean = false, area: Long = 100) =
        AppWindow(pkg, isPip = pip, isActive = active, isFocused = false, area = area)

    @Test fun pipWindowIsNeverTheFrontApp() {
        // O PiP vem antes na lista (fica por cima), mas a frente é a tela inicial ativa.
        assertEquals(launcher, Enforcement.frontApp(listOf(window(youtube, pip = true, area = 10), window(launcher, active = true))))
        // Sem janela ativa (o aviso por cima tem o foco), vale a maior.
        assertEquals(launcher, Enforcement.frontApp(listOf(window(youtube, pip = true, area = 10), window(launcher, area = 1000))))
        assertNull(Enforcement.frontApp(listOf(window(youtube, pip = true))))
    }

    @Test fun splitScreenFrontIsTheActiveHalf() {
        val renova = "dev.renova.app"
        assertEquals(renova, Enforcement.frontApp(listOf(window(youtube, area = 500), window(renova, active = true, area = 500))))
    }

    @Test fun frontOverlayClosesWhenAnotherAppComesToFront() {
        assertTrue(Enforcement.keepOverlay(youtube, followsFront = true, blockScreenShowing = false, front = youtube, visible = listOf(youtube)))
        // Home com o app bloqueado indo para PiP: a frente passa a ser a tela inicial.
        assertFalse(Enforcement.keepOverlay(youtube, followsFront = true, blockScreenShowing = false, front = launcher, visible = listOf(youtube, launcher)))
        assertFalse(Enforcement.keepOverlay(youtube, followsFront = true, blockScreenShowing = true, front = youtube, visible = listOf(youtube)))
    }

    @Test fun pipOverlayStaysWhileThePipIsOnScreen() {
        assertTrue(Enforcement.keepOverlay(youtube, followsFront = false, blockScreenShowing = false, front = launcher, visible = listOf(youtube, launcher)))
        assertFalse(Enforcement.keepOverlay(youtube, followsFront = false, blockScreenShowing = false, front = launcher, visible = listOf(launcher)))
    }
}
