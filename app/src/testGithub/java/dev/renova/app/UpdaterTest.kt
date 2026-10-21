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
}
