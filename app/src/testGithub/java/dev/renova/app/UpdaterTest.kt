package dev.renova.app

import dev.renova.app.data.AvailableUpdate
import dev.renova.app.data.Updater
import dev.renova.app.ui.updateCheckMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
    @Test fun versionComparison() {
        assertTrue(Updater.isNewer("1.1", "1.0"))
        assertTrue(Updater.isNewer("1.10", "1.9"))
        assertTrue(Updater.isNewer("2", "1.9.9"))
        assertFalse(Updater.isNewer("1.0", "1.0"))
        assertFalse(Updater.isNewer("1.0", "1.0.1"))
    }

    @Test fun offlineCheckDoesNotClaimLatest() {
        assertEquals("Não foi possível verificar. Confira a internet.", updateCheckMessage(reached = false, update = null))
        assertEquals("Você já tem a versão mais recente.", updateCheckMessage(reached = true, update = null))
        assertEquals(
            "Nova versão: v2.0. Atualize pela tela inicial.",
            updateCheckMessage(reached = true, update = AvailableUpdate("2.0", "https://x/renova.apk", "")),
        )
    }

    @Test fun downloadedApkMustBeThisAppInTheAnnouncedVersion() {
        val update = AvailableUpdate("1.13", "https://x/renova.apk")
        assertTrue(Updater.archiveMatches("dev.renova.app", "1.13", "dev.renova.app", update))
        assertFalse(Updater.archiveMatches("dev.renova.app", "1.12", "dev.renova.app", update))
        assertFalse(Updater.archiveMatches("com.outro.app", "1.13", "dev.renova.app", update))
        // Arquivo cortado: o Android não consegue ler o pacote.
        assertFalse(Updater.archiveMatches(null, null, "dev.renova.app", update))
    }
}
