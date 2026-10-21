package dev.renova.app

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.renova.app.domain.UnlockOption
import dev.renova.app.ui.EXTRA_RETURN_TO
import dev.renova.app.ui.ReadingActivity
import dev.renova.app.ui.startDevotion
import dev.renova.app.ui.startReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Leitura e 7 minutos abertos pela tela de bloqueio vão para a task do Renova, não para a dela. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BlockScreenTaskTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun Intent.newTask() = flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0

    @Test fun readingFromBlockScreenOpensInTheAppTask() {
        app.startReading(UnlockOption.CHAPTER, listOf("a"), "YouTube", returnTo = "com.google.android.youtube")
        val started = shadowOf(app).nextStartedActivity
        assertEquals(ReadingActivity::class.java.name, started.component?.className)
        assertTrue(started.newTask())
        assertEquals("com.google.android.youtube", started.getStringExtra(EXTRA_RETURN_TO))
    }

    @Test fun devotionFromBlockScreenOpensInTheAppTask() {
        app.startDevotion(returnTo = "com.instagram.android")
        val started = shadowOf(app).nextStartedActivity
        assertTrue(started.newTask())
        assertEquals("com.instagram.android", started.getStringExtra(EXTRA_RETURN_TO))
    }
}
