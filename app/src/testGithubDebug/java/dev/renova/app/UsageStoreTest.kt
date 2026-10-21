package dev.renova.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.renova.app.data.AppStore
import dev.renova.app.data.DailyUsage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Uso guardado por data: mexer na data não zera o uso de hoje. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class UsageStoreTest {
    private val store get() = AppStore.get(ApplicationProvider.getApplicationContext())
    private val today = LocalDate.now()

    @Test fun legacySingleDayIsMigrated() = runBlocking {
        store.setLegacyUsage(DailyUsage(today.toString(), mapOf("cal" to 300L)))
        assertEquals(mapOf("cal" to 300L), store.usage.first().today())
    }

    @Test fun dateForwardAndBackKeepsTheUsage() = runBlocking {
        store.setLegacyUsage(DailyUsage(today.toString(), mapOf("cal" to 300L)))
        val tomorrow = today.plusDays(1).toString()
        assertTrue(store.rollOverUsage(DailyUsage(today.toString(), mapOf("cal" to 300L)), tomorrow).isEmpty())
        val back = store.rollOverUsage(DailyUsage(tomorrow, mapOf("cal" to 10L)), today.toString())
        assertEquals(mapOf("cal" to 300L), back)
        assertEquals(mapOf("cal" to 300L), store.usage.first().today())
    }
}
