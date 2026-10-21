package dev.renova.app

import dev.renova.app.service.TargetDetector
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reserva para achar a barra de endereço de um navegador que não está na lista. */
class TargetDetectorTest {

    @Test fun urlFieldIds() {
        assertTrue(TargetDetector.isUrlFieldId("com.ecosia.android:id/url_bar"))
        assertTrue(TargetDetector.isUrlFieldId("com.yandex.browser:id/bro_omnibar_address_title_text"))
        assertTrue(TargetDetector.isUrlFieldId("org.example.browser:id/Omnibox"))
        assertFalse(TargetDetector.isUrlFieldId("org.example.browser:id/search_suggestions"))
        assertFalse(TargetDetector.isUrlFieldId(null))
    }

    @Test fun textThatLooksLikeAnAddress() {
        assertTrue(TargetDetector.looksLikeUrl("m.youtube.com/shorts/abc"))
        assertTrue(TargetDetector.looksLikeUrl("https://www.instagram.com/reels/"))
        assertTrue(TargetDetector.looksLikeUrl("youtube.com:443/watch"))
        assertFalse(TargetDetector.looksLikeUrl("Pesquisar ou digitar endereço"))
        assertFalse(TargetDetector.looksLikeUrl("pessoa@gmail.com"))
        assertFalse(TargetDetector.looksLikeUrl("3.14"))
        assertFalse(TargetDetector.looksLikeUrl(""))
    }
}
