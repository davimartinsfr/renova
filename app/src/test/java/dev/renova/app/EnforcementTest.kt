package dev.renova.app

import dev.renova.app.domain.ActiveTarget
import dev.renova.app.domain.AppWindow
import dev.renova.app.domain.BlockReason
import dev.renova.app.domain.Enforcement
import dev.renova.app.domain.LastUrl
import dev.renova.app.domain.OffscreenAction
import dev.renova.app.domain.OwnScreen
import dev.renova.app.domain.Rule
import dev.renova.app.domain.TargetType
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

    @Test fun appUnderTheOverlayIsNeitherCountedNorPulled() {
        val targets = listOf(ActiveTarget(youtube), ActiveTarget("com.netflix.mediaclient"))
        assertEquals(listOf(ActiveTarget("com.netflix.mediaclient")), Enforcement.uncovered(targets, overlayPackage = youtube))
        assertEquals(targets, Enforcement.uncovered(targets, overlayPackage = null))
    }

    @Test fun overlayOnlyWhenTheBlockScreenNeverOpened() {
        // Abriu (e a pessoa já saiu pelo Voltar): nada de aviso.
        assertTrue(Enforcement.blockScreenOpened(lastResumedAt = 1_500, requestedAt = 1_000))
        // A última vez que abriu foi num bloqueio anterior: o sistema barrou este.
        assertFalse(Enforcement.blockScreenOpened(lastResumedAt = 500, requestedAt = 1_000))
        assertFalse(Enforcement.blockScreenOpened(lastResumedAt = 0, requestedAt = 1_000))
    }

    @Test fun appRuleMatchedByItsSiteBlocksOnlyTheSite() {
        val app = Rule("y", TargetType.APP, youtube, "YouTube", dailyLimitMin = 0)
        assertFalse(Enforcement.blocksOnlySection(app, ActiveTarget(youtube)))
        assertTrue(Enforcement.blocksOnlySection(app, ActiveTarget("com.android.chrome", url = "m.youtube.com")))
        val shorts = Rule("s", TargetType.YT_SHORTS, label = "YouTube Shorts", dailyLimitMin = 0)
        assertTrue(Enforcement.blocksOnlySection(shorts, ActiveTarget(youtube, isShorts = true)))
    }

    @Test fun clockJumpIsTheWallClockMinusRealTime() {
        // Um tique normal de 1 s, com folga do laço: nada.
        assertEquals(0L, Enforcement.clockJump(prevWallMs = 0, prevElapsedMs = 0, wallMs = 1_300, elapsedMs = 1_000))
        // Hora atrasada 1 h durante um tique.
        assertEquals(-3_600_000L, Enforcement.clockJump(0, 0, wallMs = 1_000 - 3_600_000, elapsedMs = 1_000))
        // Hora adiantada 1 dia.
        assertEquals(86_400_000L, Enforcement.clockJump(0, 0, wallMs = 1_000 + 86_400_000, elapsedMs = 1_000))
    }

    @Test fun browserKeepsTheLastUrlWhileTheBarIsHidden() {
        val chrome = "com.android.chrome"
        val (read, last) = Enforcement.withLastUrl(ActiveTarget(chrome, url = "m.youtube.com/watch"), isBrowser = true, last = null)
        assertEquals("m.youtube.com/watch", read.url)
        assertEquals(LastUrl(chrome, "m.youtube.com/watch"), last)
        // Tela cheia: sem barra, vale a guardada.
        val (fullscreen, kept) = Enforcement.withLastUrl(ActiveTarget(chrome), isBrowser = true, last = last)
        assertEquals("m.youtube.com/watch", fullscreen.url)
        assertEquals(last, kept)
        // Outro app na frente esquece; voltar ao navegador sem barra não reaproveita.
        val (other, forgotten) = Enforcement.withLastUrl(ActiveTarget("com.whatsapp"), isBrowser = false, last = kept)
        assertNull(other.url)
        assertNull(forgotten)
        assertNull(Enforcement.withLastUrl(ActiveTarget(chrome), isBrowser = true, last = forgotten).first.url)
        // Outro navegador não herda a URL do primeiro.
        assertNull(Enforcement.withLastUrl(ActiveTarget("org.mozilla.firefox"), isBrowser = true, last = last).first.url)
    }

    @Test fun tickCountsRealTimeUpToTwoSeconds() {
        assertEquals(1_000L, Enforcement.tickMs(prevTickAt = 0, now = 50_000))
        assertEquals(1_027L, Enforcement.tickMs(prevTickAt = 10_000, now = 11_027))
        // Laço segurado (ou o sistema dormiu): não conta o buraco.
        assertEquals(Enforcement.MAX_TICK_MS, Enforcement.tickMs(prevTickAt = 10_000, now = 70_000))
        // 300 voltas de 1,027 s somam os mesmos ~308 s reais, e não 300.
        var total = 0L
        var at = 1L
        repeat(300) { val next = at + 1_027; total += Enforcement.tickMs(at, next); at = next }
        assertEquals(308_100L, total)
    }

    @Test fun unreadFrontWindowDoesNotCount() {
        val yt = ActiveTarget(youtube)
        assertEquals(yt, Enforcement.frontToCount(yt, frontRead = true))
        assertNull(Enforcement.frontToCount(yt, frontRead = false))
    }

    @Test fun wholeAppRuleLeadsOverSection() {
        val shorts = Rule("s", TargetType.YT_SHORTS, label = "YouTube Shorts", dailyLimitMin = 0)
        val app = Rule("y", TargetType.APP, youtube, "YouTube", dailyLimitMin = 0)
        val led = Enforcement.leadingFirst(listOf(shorts to BlockReason.ALWAYS, app to BlockReason.LIMIT))
        assertEquals(listOf("y", "s"), led.map { it.first.id })
    }
}
