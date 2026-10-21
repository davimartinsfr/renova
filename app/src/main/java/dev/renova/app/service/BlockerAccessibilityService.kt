package dev.renova.app.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.media.AudioManager
import android.net.Uri
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import dev.renova.app.data.AppStore
import dev.renova.app.data.DailyUsage
import dev.renova.app.data.Updater
import dev.renova.app.domain.ActiveTarget
import dev.renova.app.domain.AppWindow
import dev.renova.app.domain.BlockReason
import dev.renova.app.domain.Devotion
import dev.renova.app.domain.Enforcement
import dev.renova.app.domain.LastUrl
import dev.renova.app.domain.OffscreenAction
import dev.renova.app.domain.OwnScreen
import dev.renova.app.domain.Rule
import dev.renova.app.domain.RuleEngine
import dev.renova.app.domain.TargetType
import dev.renova.app.ui.BlockedActivity
import dev.renova.app.ui.DevotionActivity
import dev.renova.app.ui.Notifications
import dev.renova.app.ui.ReadingActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Observa a tela, contabiliza o tempo gasto em cada alvo e expulsa o usuário
 * quando uma regra está em horário de bloqueio ou estourou o limite diário.
 */
class BlockerAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var store: AppStore
    private lateinit var power: PowerManager
    private lateinit var overlay: BlockOverlay

    private var rules: List<Rule> = emptyList()
    private var devotionDoneDay: String? = null
    private var devotionEnabled = false
    private var unlocks: Map<String, Long> = emptyMap()
    /** Navegadores instalados, para achar a barra de endereço nos que o TargetDetector não conhece. */
    private var browsers: Set<String> = emptySet()
    private var browserTicks = 0
    private var noticeTicks = 0
    /** Última URL do navegador da frente, para quando a barra some (ver [Enforcement.withLastUrl]). */
    private var lastUrl: LastUrl? = null
    private var usageDate = LocalDate.now().toString()
    private val usage = mutableMapOf<String, Long>()
    private var usageLoaded = false
    private var dirtyTicks = 0
    /** Tempo real do último tique com a tela acesa, e os milissegundos de uso ainda não somados (ver [Enforcement.tickMs]). */
    private var lastTickAt = 0L
    private var usageCarryMs = 0L
    private var updateTicks = UPDATE_CHECK_EVERY_TICKS - 30

    private var active: ActiveTarget? = null
    /** O Renova está em primeiro plano (lendo para liberar, nos 7 minutos, nas regras). */
    private var ownAppInFront = false
    // Intervalos em elapsedRealtime: com o relógio do aparelho, atrasar a hora travava o bloqueio pelo tamanho do atraso.
    private var lastEvalAt = 0L
    private var lastBlockAt = 0L
    private var lastExpandAt = 0L
    /** Última amostra do relógio e do tempo real, para notar quando a hora do aparelho muda. */
    private var lastWallMs = 0L
    private var lastElapsedMs = 0L
    /** Alvo em PiP cujo aviso por cima a pessoa fechou: a mídia segue pausada, mas o aviso não volta. */
    private var pipNoticeDismissed: String? = null

    override fun onServiceConnected() {
        store = AppStore.get(this)
        power = getSystemService(PowerManager::class.java)
        overlay = BlockOverlay(this)
        browsers = installedBrowsers()
        scope.launch { store.rules.collect { rules = it } }
        scope.launch { store.unlocks.collect { unlocks = it } }
        scope.launch { store.devotionDoneDay.collect { devotionDoneDay = it } }
        scope.launch { store.devotionEnabled.collect { devotionEnabled = it } }
        scope.launch {
            usage.putAll(store.usage.first().today())
            usageLoaded = true
            tickLoop()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val now = SystemClock.elapsedRealtime()
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && now - lastEvalAt < EVAL_THROTTLE_MS) return
        lastEvalAt = now
        evaluate()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (usageLoaded) runBlocking { flushUsage() }
        if (::overlay.isInitialized) overlay.hide()
        scope.cancel()
        super.onDestroy()
    }

    /** Devolve se conseguiu ler a janela ativa; sem ela, [active] é o da última leitura. */
    private fun evaluate(): Boolean {
        syncOverlay()
        val root = rootInActiveWindow ?: return false
        val detected = TargetDetector.detect(root, browsers) ?: return false
        val (target, last) = Enforcement.withLastUrl(
            detected, TargetDetector.isBrowser(detected.packageName, browsers), lastUrl, keepFor = IGNORED_PACKAGES + packageName,
        )
        lastUrl = last
        ownAppInFront = target.packageName == packageName
        active = target.takeIf(::isCounted)
        checkBlock()
        return true
    }

    private fun isCounted(target: ActiveTarget) = target.packageName != packageName && target.packageName !in IGNORED_PACKAGES

    /**
     * Apps visíveis fora da janela ativa: um vídeo em picture-in-picture (YouTube, Netflix…) ou a outra
     * metade da tela dividida. Eles não aparecem em [rootInActiveWindow], mas o tempo deles conta igual.
     */
    private fun otherVisibleTargets(): List<ActiveTarget> = windows
        .filter { (it.type == AccessibilityWindowInfo.TYPE_APPLICATION || it.isInPictureInPictureMode) && !it.isActive }
        .mapNotNull { window -> window.root?.let { TargetDetector.detect(it, browsers) } }
        .filter(::isCounted)

    /** Tira a contagem regressiva das regras que voltaram a bloquear antes do fim ou foram apagadas. */
    private fun cancelStaleUnlockNotices() {
        val tags = runCatching { Notifications.unlockTags(this) }.getOrDefault(emptyList())
        if (tags.isEmpty()) return
        val devotionPending = devotionEnabled && !Devotion.isDone(devotionDoneDay, LocalDateTime.now())
        Enforcement.staleUnlockNotices(tags, rules, unlocks, System.currentTimeMillis(), devotionPending)
            .forEach { Notifications.cancelUnlock(this, it) }
    }

    /** Apps que abrem um link https qualquer: os navegadores (os apps com links próprios só abrem os seus). */
    private fun installedBrowsers(): Set<String> {
        val link = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)
        return runCatching {
            packageManager.queryIntentActivities(link, PackageManager.MATCH_ALL).map { it.activityInfo.packageName }.toSet()
        }.getOrDefault(emptySet())
    }

    private suspend fun tickLoop() {
        while (scope.isActive) {
            delay(1000)
            followClockJumps()
            rollOverDay()
            if (++updateTicks >= UPDATE_CHECK_EVERY_TICKS) {
                updateTicks = 0
                scope.launch { Updater.checkIfDue(this@BlockerAccessibilityService, UPDATE_INTERVAL_MS) }
            }
            if (++noticeTicks >= NOTICE_CHECK_TICKS) {
                noticeTicks = 0
                cancelStaleUnlockNotices()
            }
            if (++browserTicks >= BROWSER_REFRESH_TICKS) {
                browserTicks = 0
                browsers = installedBrowsers()
            }
            if (!power.isInteractive) {
                lastTickAt = 0L
                continue
            }
            val frontRead = evaluate()
            val covered = overlay.blockedPackage.takeIf { overlay.isShowing }
            val others = Enforcement.uncovered(otherVisibleTargets(), covered)
            val inUse = RuleEngine.inUse(rules, Enforcement.uncovered(others + listOfNotNull(Enforcement.frontToCount(active, frontRead)), covered))
            val tickAt = SystemClock.elapsedRealtime()
            // Sobra de milissegundos de um tique para o outro; sem nada em uso, não há o que levar adiante.
            usageCarryMs = if (inUse.isEmpty()) 0L else usageCarryMs + Enforcement.tickMs(lastTickAt, tickAt)
            lastTickAt = tickAt
            val seconds = usageCarryMs / 1000
            usageCarryMs %= 1000
            if (seconds > 0) {
                inUse.forEach { usage[it.id] = (usage[it.id] ?: 0L) + seconds }
                if (++dirtyTicks >= FLUSH_EVERY_TICKS) flushUsage()
            }
            checkBlock()
            handleBlockedOffscreen(others)
        }
    }

    /** A liberação dura tempo real: se a hora do aparelho mudou, desloca as liberações junto (ver [AppStore.shiftClock]). */
    private suspend fun followClockJumps() {
        val wall = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        // A primeira amostra (depois do boot ou de o serviço ligar) não tem com o que comparar.
        val jump = if (lastElapsedMs == 0L) 0L else Enforcement.clockJump(lastWallMs, lastElapsedMs, wall, elapsed)
        lastWallMs = wall
        lastElapsedMs = elapsed
        if (jump != 0L) store.shiftClock(jump)
    }

    private suspend fun rollOverDay() {
        val today = LocalDate.now().toString()
        if (today == usageDate) return
        // Voltar para uma data já vista (relógio mexido) recupera o uso dela em vez de começar do zero.
        val entering = store.rollOverUsage(DailyUsage(usageDate, usage.toMap()), today)
        usageDate = today
        usage.clear()
        usage.putAll(entering)
        dirtyTicks = 0
    }

    private suspend fun flushUsage() {
        dirtyTicks = 0
        store.saveUsage(DailyUsage(usageDate, usage.toMap()))
    }

    private fun checkBlock() {
        val target = active ?: return
        val blocking = blocking(target)
        val (rule, reason) = blocking.firstOrNull() ?: return
        block(target, rule, reason.name, blocking.map { it.first.id })
    }

    /** Regras que bloqueiam [target] agora (ver [RuleEngine.blocking]), a que conduz o bloqueio primeiro. */
    private fun blocking(target: ActiveTarget): List<Pair<Rule, BlockReason>> {
        val now = LocalDateTime.now()
        return Enforcement.leadingFirst(
            RuleEngine.blocking(
                rules, target, usage, now, System.currentTimeMillis(), unlocks,
                devotionPending = devotionEnabled && !Devotion.isDone(devotionDoneDay, now),
            ),
        )
    }

    /** [ruleIds]: todas as regras que bloqueiam o alvo, para a leitura liberar o app de vez. */
    private fun block(target: ActiveTarget, rule: Rule, reason: String, ruleIds: List<String>) {
        // O aviso por cima já está bloqueando: não adianta tentar abrir a tela de novo.
        if (overlay.isShowing) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastBlockAt < BLOCK_COOLDOWN_MS) return
        lastBlockAt = now
        active = null
        val section = Enforcement.blocksOnlySection(rule, target)
        val blockedScreen = blockedScreenIntent(target.packageName, rule, reason, ruleIds, section)
        if (section) {
            // No Shorts/Reels (ou no site de um app, no navegador) basta voltar: o resto continua liberado.
            // Espera o app sair da seção.
            performGlobalAction(GLOBAL_ACTION_BACK)
            scope.launch {
                delay(SECTION_BACK_DELAY_MS)
                val requestedAt = SystemClock.elapsedRealtime()
                startActivity(blockedScreen)
                ensureShown(target.packageName, rule, reason, blockedScreen, requestedAt, section)
            }
        } else {
            val requestedAt = SystemClock.elapsedRealtime()
            startActivity(blockedScreen)
            scope.launch { ensureShown(target.packageName, rule, reason, blockedScreen, requestedAt, section) }
        }
        // Antes da carga inicial, o mapa ainda está vazio e apagaria o uso do dia gravado.
        if (usageLoaded) scope.launch { flushUsage() }
    }

    private fun blockedScreenIntent(pkg: String, rule: Rule, reason: String, ruleIds: List<String>, section: Boolean): Intent =
        // NO_USER_ACTION evita que o app de baixo receba o "usuário saiu", que é o gatilho do
        // picture-in-picture automático (YouTube e afins continuariam tocando numa janelinha).
        // Sair da tela de bloqueio já leva à tela inicial, então não é preciso apertar Home aqui.
        Intent(this, BlockedActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            .putExtra(BlockedActivity.EXTRA_PACKAGE, pkg)
            .putExtra(BlockedActivity.EXTRA_LABEL, rule.label)
            .putExtra(BlockedActivity.EXTRA_REASON, reason)
            .putExtra(BlockedActivity.EXTRA_TYPE, rule.type.name)
            .putExtra(BlockedActivity.EXTRA_SECTION, section)
            .putExtra(BlockedActivity.EXTRA_RULE_IDS, ruleIds.toTypedArray())

    /**
     * Alguns sistemas (Xiaomi sem a permissão de janelas pop-up em segundo plano) ignoram o startActivity
     * sem erro nenhum: o Shorts fechava, mas a tela de bloqueio não aparecia. Se ela não abriu, o serviço
     * desenha o bloqueio ele mesmo por cima do app.
     */
    private suspend fun ensureShown(
        pkg: String,
        rule: Rule,
        reason: String,
        blockedScreen: Intent,
        requestedAt: Long,
        section: Boolean,
    ) {
        delay(SHOW_CHECK_MS)
        if (Enforcement.blockScreenOpened(BlockedActivity.lastResumedAt, requestedAt)) return
        if (overlay.isShowing || frontAppPackage() != pkg) return
        showOverlay(pkg, rule, reason, blockedScreen, followsFront = true) {
            if (!section) performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    /** O aviso cobre o app, mas não o para: a mídia é pausada aqui, e o tempo dele deixa de contar no tickLoop. */
    private fun showOverlay(pkg: String, rule: Rule, reason: String, blockedScreen: Intent, followsFront: Boolean, onLeave: () -> Unit) {
        pauseMedia()
        active = null
        overlay.show(
            blockedPackage = pkg,
            label = rule.label,
            reason = reason,
            followsFront = followsFront,
            onOpenApp = { startActivity(blockedScreen) },
            onLeave = {
                overlay.hide()
                onLeave()
            },
        )
    }

    /** Fecha o aviso quando a tela de bloqueio de verdade abriu ou o app bloqueado saiu (ver [Enforcement.keepOverlay]). */
    private fun syncOverlay() {
        val windows = appWindows()
        val visible = windows.mapNotNull { it.packageName }
        if (pipNoticeDismissed != null && pipNoticeDismissed !in visible) pipNoticeDismissed = null
        if (!overlay.isShowing) return
        val keep = Enforcement.keepOverlay(
            overlay.blockedPackage, overlay.followsFront, BlockedActivity.isShowing, Enforcement.frontApp(windows), visible,
        )
        if (!keep) overlay.hide()
    }

    /** Janelas de aplicativo na tela, inclusive as em PiP; o próprio aviso por cima não entra. */
    private fun appWindows(): List<AppWindow> = windows
        .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION || it.isInPictureInPictureMode }
        .map { w ->
            val bounds = Rect().also(w::getBoundsInScreen)
            AppWindow(
                w.root?.packageName?.toString(), w.isInPictureInPictureMode, w.isActive, w.isFocused,
                bounds.width().toLong() * bounds.height(),
            )
        }

    private fun frontAppPackage(): String? = Enforcement.frontApp(appWindows())

    /**
     * Um alvo bloqueado fora da janela ativa (em picture-in-picture, ou na outra metade da tela dividida)
     * não é expulso pelo bloqueio normal. Em geral ele é trazido para a frente, para o bloqueio agir; com a
     * leitura ou os 7 minutos na frente, só a mídia é pausada, para não tirar a pessoa do que vai liberá-lo.
     */
    private fun handleBlockedOffscreen(others: List<ActiveTarget>) {
        val nowMs = SystemClock.elapsedRealtime()
        if (nowMs - lastExpandAt < EXPAND_COOLDOWN_MS) return
        val target = others.firstOrNull { blocking(it).isNotEmpty() }
        val ownScreen = when {
            !ownAppInFront -> null
            ReadingActivity.isShowing || DevotionActivity.isShowing -> OwnScreen.READING
            else -> OwnScreen.OTHER
        }
        when (Enforcement.offscreenAction(target != null, ownScreen)) {
            OffscreenAction.NONE -> return
            OffscreenAction.PAUSE -> pauseMedia()
            OffscreenAction.PULL -> {
                val launch = packageManager.getLaunchIntentForPackage(target!!.packageName)
                // Sem tela de abertura não há como puxar o app: pelo menos para o vídeo.
                if (launch == null) {
                    pauseMedia()
                } else {
                    val requestedAt = SystemClock.elapsedRealtime()
                    startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    scope.launch { ensurePulled(target, requestedAt) }
                }
            }
        }
        lastExpandAt = nowMs
    }

    /**
     * O Xiaomi sem a permissão de janelas pop-up também ignora este startActivity, e o vídeo em PiP tocaria
     * sem fim. Se o app não veio para a frente, o serviço pausa a mídia e mostra o aviso por cima, que fica
     * enquanto o PiP estiver na tela.
     */
    private suspend fun ensurePulled(target: ActiveTarget, requestedAt: Long) {
        delay(SHOW_CHECK_MS)
        val pkg = target.packageName
        val windows = appWindows()
        // Veio para a frente e foi bloqueado normalmente, ou o PiP já fechou.
        if (Enforcement.blockScreenOpened(BlockedActivity.lastResumedAt, requestedAt)) return
        if (Enforcement.frontApp(windows) == pkg || windows.none { it.packageName == pkg }) return
        val blocking = blocking(target)
        val (rule, reason) = blocking.firstOrNull() ?: return
        // O aviso já pausou ao abrir. Dispensado, só a pausa repetida segura o vídeo no PiP.
        if (overlay.isShowing) return
        if (pipNoticeDismissed == pkg) return pauseMedia()
        val blockedScreen = blockedScreenIntent(pkg, rule, reason.name, blocking.map { it.first.id }, Enforcement.blocksOnlySection(rule, target))
        showOverlay(pkg, rule, reason.name, blockedScreen, followsFront = false) { pipNoticeDismissed = pkg }
    }

    /**
     * Pausa o que está tocando. A tecla vai para a sessão de mídia ativa do sistema, que quase sempre é a do
     * app bloqueado, mas pode ser outro player (uma música de fundo): é o preço de parar o vídeo sem puxá-lo.
     */
    private fun pauseMedia() {
        val audio = getSystemService(AudioManager::class.java)
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
    }

    companion object {
        private const val EVAL_THROTTLE_MS = 300L
        private const val BLOCK_COOLDOWN_MS = 1500L
        private const val SECTION_BACK_DELAY_MS = 400L
        private const val SHOW_CHECK_MS = 1200L
        private const val EXPAND_COOLDOWN_MS = 3000L
        private const val FLUSH_EVERY_TICKS = 10
        private const val UPDATE_CHECK_EVERY_TICKS = 15 * 60
        private const val BROWSER_REFRESH_TICKS = 5 * 60
        private const val NOTICE_CHECK_TICKS = 5
        private const val UPDATE_INTERVAL_MS = 6 * 60 * 60 * 1000L
        // Bolhas de conversa (Messenger, Telegram) aparecem como janela do systemui e por isso escapam das
        // regras. Talvez o nó-filho da bolha traga o pacote do app; falta conferir em aparelho antes de usar.
        private val IGNORED_PACKAGES = setOf("com.android.systemui")

        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, BlockerAccessibilityService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
