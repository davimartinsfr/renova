package dev.renova.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.renova.app.data.AppStore
import dev.renova.app.domain.Rule
import dev.renova.app.domain.TargetType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Dados de uma versão mais nova (ou corrompidos) não derrubam o app e não se perdem ao regravar. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class StoreToleranceTest {
    private val store get() = AppStore.get(ApplicationProvider.getApplicationContext())

    private val withUnknownType = """[
        {"id":"a","type":"APP","target":"com.reddit.frontpage","label":"Reddit","dailyLimitMin":0},
        {"id":"t","type":"TIKTOK","label":"TikTok","dailyLimitMin":5}
    ]"""

    @Test fun unknownRuleTypeIsSkippedOnRead() = runBlocking {
        store.setRaw("rules", withUnknownType)
        assertEquals(listOf("a"), store.rules.first().map { it.id })
    }

    @Test fun unknownRuleIsKeptWhenTheListIsSaved() = runBlocking {
        store.setRaw("rules", withUnknownType)
        store.saveRule(Rule("b", TargetType.APP, "com.netflix.mediaclient", "Netflix", dailyLimitMin = 0))
        assertTrue(store.raw("rules")!!.contains("TIKTOK"))
        assertEquals(listOf("a", "b"), store.rules.first().map { it.id })
        store.deleteRule("a")
        assertTrue(store.raw("rules")!!.contains("TIKTOK"))
        assertEquals(listOf("b"), store.rules.first().map { it.id })
    }

    @Test fun corruptedMapsReadAsEmpty() = runBlocking {
        store.setRaw("unlocks", "{nada")
        store.setRaw("usage_days", "[1,2")
        store.setRaw("rules", "isto não é json")
        assertTrue(store.unlocks.first().isEmpty())
        assertTrue(store.usage.first().seconds.isEmpty())
        assertTrue(store.rules.first().isEmpty())
    }

    @Test fun defaultValuesAreWrittenToDisk() = runBlocking {
        store.setRaw("rules", "[]")
        store.saveRule(Rule("d", TargetType.YT_SHORTS, label = "YouTube Shorts", dailyLimitMin = 10))
        val raw = store.raw("rules")!!
        assertTrue(raw, "\"enabled\":true" in raw && "\"schedules\":[]" in raw)
    }
}
