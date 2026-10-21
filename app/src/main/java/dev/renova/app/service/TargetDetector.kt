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
        // O Firefox novo está trocando a barra por uma em Compose, que pode não expor este id;
        // aí vale a busca de reserva (findUrlField). Conferir em aparelho a cada versão grande.
        "org.mozilla.firefox" to "mozac_browser_toolbar_url_view",
        "org.mozilla.firefox_beta" to "mozac_browser_toolbar_url_view",
        "org.mozilla.fenix" to "mozac_browser_toolbar_url_view",
        "org.mozilla.focus" to "display_url",
        "com.sec.android.app.sbrowser" to "location_bar_edit_text",
        "com.sec.android.app.sbrowser.beta" to "location_bar_edit_text",
        "com.duckduckgo.mobile.android" to "omnibarTextInput",
        // Ids abaixo ainda não conferidos em aparelho; se não baterem, a busca de reserva acha a barra.
        "com.opera.gx" to "url_field",
        "com.ecosia.android" to "url_bar",
        "com.mi.globalbrowser" to "url",
        "com.android.browser" to "url",
        "com.yandex.browser" to "bro_omnibar_address_title_text",
        "com.UCMobile.intl" to "url",
    )

    /** Pedaços de id que indicam a barra de endereço num navegador fora da lista. */
    private val URL_FIELD_HINTS = listOf("url", "address", "omnibox")

    /** Limite de nós visitados na busca de reserva: a árvore inteira de uma página pesaria a cada evento. */
    private const val MAX_SCANNED_NODES = 300

    /** Intervalo mínimo entre duas buscas de reserva no mesmo navegador. */
    const val URL_SCAN_INTERVAL_MS = 5_000L

    /** Se [pkg] é um navegador: está na lista ou entre os [browsers] instalados. */
    fun isBrowser(pkg: String, browsers: Set<String>): Boolean = pkg in URL_BAR_IDS || pkg in browsers

    /**
     * [browsers]: navegadores instalados (abrem links https), para buscar a barra nos que não estão na lista.
     * [urlBars] guarda, entre as leituras, o que já se sabe da barra de cada navegador; [now] é o
     * [elapsedRealtime][android.os.SystemClock.elapsedRealtime].
     */
    fun detect(root: AccessibilityNodeInfo, browsers: Set<String>, urlBars: UrlBarMemory, now: Long): ActiveTarget? {
        val pkg = root.packageName?.toString() ?: return null
        return when {
            pkg == YOUTUBE -> ActiveTarget(pkg, isShorts = isShorts(root))
            pkg == INSTAGRAM -> ActiveTarget(pkg, isReels = isReels(root))
            pkg in URL_BAR_IDS || pkg in browsers -> ActiveTarget(pkg, url = readUrl(root, pkg, urlBars, now))
            else -> ActiveTarget(pkg)
        }
    }

    private fun isShorts(root: AccessibilityNodeInfo): Boolean = anyVisible(root, SHORTS_VIEW_IDS)

    private fun isReels(root: AccessibilityNodeInfo): Boolean = anyVisible(root, REELS_VIEW_IDS)

    private fun anyVisible(root: AccessibilityNodeInfo, ids: List<String>): Boolean =
        ids.any { id -> root.findAccessibilityNodeInfosByViewId(id).any { it.isVisibleToUser } }

    /**
     * Reserva para navegador sem id conhecido: um campo de texto cujo id lembre a barra de endereço
     * ([isUrlFieldId]) e cujo texto pareça um endereço ([looksLikeUrl]). Busca em largura, de cima para baixo,
     * que é onde a barra costuma ficar.
     */
    private fun findUrlField(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque(listOf(root))
        var visited = 0
        while (queue.isNotEmpty() && visited++ < MAX_SCANNED_NODES) {
            val node = queue.removeFirst()
            val text = node.text?.toString()
            val isText = node.className?.let { it.endsWith("EditText") || it.endsWith("TextView") } == true
            if (isText && text != null && isUrlFieldId(node.viewIdResourceName) && looksLikeUrl(text)) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::addLast)
        }
        return null
    }

    /** "com.navegador:id/url_bar" e afins (o id do Compose com testTag também serve). */
    internal fun isUrlFieldId(viewId: String?): Boolean {
        val id = viewId?.substringAfter(":id/")?.lowercase() ?: return false
        return URL_FIELD_HINTS.any { it in id }
    }

    /** "m.youtube.com/shorts/x", "https://instagram.com": sem espaço nem @, com um domínio de verdade no começo. */
    internal fun looksLikeUrl(text: String): Boolean {
        val s = text.trim().lowercase()
        if (s.isEmpty() || ' ' in s || '@' in s) return false
        val host = s.substringAfter("://").substringBefore('/').substringBefore(':')
        val tld = host.substringAfterLast('.', "")
        return '.' in host && tld.length >= 2 && tld.all { it.isLetter() }
    }

    /**
     * Lê a barra pelo id da lista ou pelo que a busca de reserva já achou, que é barato. Sem ela, a busca de
     * reserva roda só se [shouldScanForUrlBar] deixar: a barra some a cada rolagem, e buscar a cada evento
     * gastava CPU à toa (aí vale a última URL lida, ver [Enforcement.withLastUrl][dev.renova.app.domain.Enforcement.withLastUrl]).
     */
    private fun readUrl(root: AccessibilityNodeInfo, pkg: String, urlBars: UrlBarMemory, now: Long): String? {
        val ids = listOfNotNull(URL_BAR_IDS[pkg]?.let { "$pkg:id/$it" }, urlBars.learnedIds[pkg]).distinct()
        for (id in ids) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (nodes.isEmpty()) continue
            urlBars.seen += pkg
            return nodes.firstNotNullOfOrNull { it.text?.toString()?.takeIf(String::isNotBlank) }
        }
        // O id não bateu (o app mudou, ou o id ainda não foi conferido): procura a barra pelo jeito dela.
        if (!shouldScanForUrlBar(pkg in urlBars.seen, urlBars.lastScanAt[pkg], now)) return null
        urlBars.lastScanAt[pkg] = now
        val field = findUrlField(root) ?: return null
        field.viewIdResourceName?.let { urlBars.learnedIds[pkg] = it }
        return field.text?.toString()
    }

    /**
     * Se vale a busca de reserva da barra. Depois que a barra apareceu pelo id ([idSeenBefore]), sumir é só
     * ela escondida na rolagem, e a busca não acharia nada. Antes disso (id que mudou numa versão nova, ou
     * navegador fora da lista), busca no máximo a cada [URL_SCAN_INTERVAL_MS]; [lastScanAt] null = nunca buscou.
     */
    internal fun shouldScanForUrlBar(idSeenBefore: Boolean, lastScanAt: Long?, now: Long): Boolean =
        !idSeenBefore && (lastScanAt == null || now - lastScanAt >= URL_SCAN_INTERVAL_MS)
}

/** O que o serviço já sabe da barra de endereço de cada navegador, nesta sessão (ver [TargetDetector.detect]). */
class UrlBarMemory {
    /** Navegadores em que a barra já foi achada pelo id. */
    val seen = mutableSetOf<String>()
    /** Id da barra que a busca de reserva achou, por navegador. */
    val learnedIds = mutableMapOf<String, String>()
    /** Quando cada navegador foi buscado pela última vez. */
    val lastScanAt = mutableMapOf<String, Long>()
}
