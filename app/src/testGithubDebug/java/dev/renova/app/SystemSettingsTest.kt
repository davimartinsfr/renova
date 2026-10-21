package dev.renova.app

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.renova.app.service.BlockerAccessibilityService
import dev.renova.app.ui.openAccessibilitySettings
import dev.renova.app.ui.notificationsOk
import dev.renova.app.ui.requestNotifications
import dev.renova.app.ui.startFirstAvailable
import android.content.Intent
import org.robolectric.shadows.ShadowToast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SystemSettingsTest {
    private val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

    private class FakeLauncher : ActivityResultLauncher<String>() {
        var launched = 0
        override fun launch(input: String, options: ActivityOptionsCompat?) { launched++ }
        override fun unregister() {}
        override val contract: ActivityResultContract<String, *> get() = throw UnsupportedOperationException()
    }

    @Test fun accessibilityHighlightsRenova() {
        openAccessibilitySettings(activity)
        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, intent.action)
        val me = ComponentName(activity, BlockerAccessibilityService::class.java).flattenToString()
        assertEquals(me, intent.getStringExtra(":settings:fragment_args_key"))
        assertEquals(me, intent.getBundleExtra(":settings:show_fragment_args")?.getString(":settings:fragment_args_key"))
    }

    @Test fun notificationsAskFirstThenOpenSettings() {
        activity.getSharedPreferences("permissions", Context.MODE_PRIVATE).edit().clear().commit()
        val launcher = FakeLauncher()
        requestNotifications(activity, launcher)
        assertEquals(1, launcher.launched)
        assertNull(shadowOf(activity).nextStartedActivity)
        // Recusado sem o sistema oferecer de novo o pedido: vai para as notificações do Renova.
        requestNotifications(activity, launcher)
        assertEquals(1, launcher.launched)
        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(activity.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }

    @Test fun missingScreenShowsAToastInsteadOfCrashing() {
        shadowOf(activity.application).checkActivities(true)
        activity.startFirstAvailable(Intent("dev.renova.nao.existe"), Intent("dev.renova.tambem.nao"))
        assertEquals("Não foi possível abrir.", ShadowToast.getTextOfLatestToast())
    }

    @Test fun notificationsTurnedOffInSettingsAreNotOkOnOldAndroid() {
        assertFalse(notificationsOk(sdk = 30, enabled = false, permissionGranted = false))
        assertTrue(notificationsOk(sdk = 30, enabled = true, permissionGranted = false))
        assertFalse(notificationsOk(sdk = 34, enabled = true, permissionGranted = false))
        assertTrue(notificationsOk(sdk = 34, enabled = true, permissionGranted = true))
    }
}
