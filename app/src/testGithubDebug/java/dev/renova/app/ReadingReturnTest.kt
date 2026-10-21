package dev.renova.app

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.renova.app.domain.UnlockOption
import dev.renova.app.ui.EXTRA_RETURN_TO
import dev.renova.app.ui.ReadingActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Leitura aberta pela tela de bloqueio: concluída, o X do topo volta ao app liberado, como o "Fechar". */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ReadingReturnTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val blocked = "com.example.video"

    @Test fun topCloseAfterUnlockReturnsToTheBlockedApp() {
        val launcher = ComponentName(blocked, "$blocked.Main")
        shadowOf(app.packageManager).apply {
            addActivityIfNotPresent(launcher)
            addIntentFilterForActivity(launcher, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
        }
        val intent = Intent(app, ReadingActivity::class.java)
            .putExtra(ReadingActivity.EXTRA_OPTION, UnlockOption.VERSE.name)
            .putExtra(ReadingActivity.EXTRA_RULE_IDS, arrayOf("a"))
            .putExtra(EXTRA_RETURN_TO, blocked)
        ActivityScenario.launch<ReadingActivity>(intent).use {
            compose.waitUntil(20_000) { compose.onAllNodes(hasText("Concluir")).fetchSemanticsNodes().isNotEmpty() }
            compose.mainClock.advanceTimeBy(120_000)
            compose.onNodeWithText("Concluir").performClick()
            compose.waitUntil(20_000) { compose.onAllNodes(hasText("Livre", substring = true)).fetchSemanticsNodes().isNotEmpty() }
            while (shadowOf(app).nextStartedActivity != null) Unit
            compose.onNodeWithContentDescription("Fechar").performClick()
            assertEquals(blocked, shadowOf(app).nextStartedActivity?.component?.packageName)
        }
    }
}
