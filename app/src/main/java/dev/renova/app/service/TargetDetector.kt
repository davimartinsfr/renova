package dev.renova.app.service

import android.view.accessibility.AccessibilityNodeInfo
import dev.renova.app.domain.ActiveTarget

/**
 * Descobre o que está na tela a partir da árvore de acessibilidade.
 * Os IDs abaixo vêm dos apps de terceiros e podem mudar com atualizações:
 * se o Shorts ou algum navegador parar de ser detectado, ajuste aqui.
 */
object TargetDetector {

    const val YOUTUBE = "com.google.android.youtube"

    private val SHORTS_VIEW_IDS = listOf(
        "$YOUTUBE:id/reel_recycler",
        "$YOUTUBE:id/reel_player_page_container",
        "$YOUTUBE:id/reel_watch_player",
    )

    const val INSTAGRAM = "com.instagram.android"

    /** Visualizador de Reels (aba Reels ou um Reel aberto em tela cheia); Reels no meio do feed não contam. */
    private val REELS_VIEW_IDS = listOf(
        "$INSTAGRAM:id/clips_viewer_view_pager",
        "$INSTAGRAM:id/root_clips_layout",
    )

    private val URL_BAR_IDS = mapOf(
        "com.android.chrome" to "url_bar",
        "com.chrome.beta" to "url_bar",
        "com.chrome.dev" to "url_bar",
        "com.chrome.canary" to "url_bar",
        "com.brave.browser" to "url_bar",
        "com.microsoft.emmx" to "url_bar",
        "com.kiwibrowser.browser" to "url_bar",
        "com.vivaldi.browser" to "url_bar",
        "org.chromium.chrome" to "url_bar",
        "com.opera.browser" to "url_field",
        "com.opera.mini.native" to "url_field",
        "org.mozilla.firefox" to "mozac_browser_toolbar_url_view",
        "org.mozilla.firefox_beta" to "mozac_browser_toolbar_url_view",
        "org.mozilla.focus" to "display_url",
        "com.sec.android.app.sbrowser" to "location_bar_edit_text",
        "com.duckduckgo.mobile.android" to "omnibarTextInput",
    )

    fun detect(root: AccessibilityNodeInfo): ActiveTarget? {
        val pkg = root.packageName?.toString() ?: return null
        return when {
            pkg == YOUTUBE -> ActiveTarget(pkg, isShorts = isShorts(root))
            pkg == INSTAGRAM -> ActiveTarget(pkg, isReels = isReels(root))
            pkg in URL_BAR_IDS -> ActiveTarget(pkg, url = readUrl(root, pkg))
            else -> ActiveTarget(pkg)
        }
    }

    private fun isShorts(root: AccessibilityNodeInfo): Boolean = anyVisible(root, SHORTS_VIEW_IDS)

    private fun isReels(root: AccessibilityNodeInfo): Boolean = anyVisible(root, REELS_VIEW_IDS)

    private fun anyVisible(root: AccessibilityNodeInfo, ids: List<String>): Boolean =
        ids.any { id -> root.findAccessibilityNodeInfosByViewId(id).any { it.isVisibleToUser } }

    private fun readUrl(root: AccessibilityNodeInfo, pkg: String): String? {
        val id = "$pkg:id/${URL_BAR_IDS.getValue(pkg)}"
        return root.findAccessibilityNodeInfosByViewId(id)
            .firstNotNullOfOrNull { it.text?.toString()?.takeIf(String::isNotBlank) }
    }
}
