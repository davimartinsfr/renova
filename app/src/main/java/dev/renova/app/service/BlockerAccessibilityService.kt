package dev.renova.app.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import dev.renova.app.data.AppStore
import dev.renova.app.data.DailyUsage
import dev.renova.app.data.Updater
import dev.renova.app.domain.ActiveTarget
import dev.renova.app.domain.BlockReason
import dev.renova.app.domain.Devotion
import dev.renova.app.domain.Rule
import dev.renova.app.domain.RuleEngine
import dev.renova.app.domain.TargetType
import dev.renova.app.ui.BlockedActivity
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
    private var usageDate = LocalDate.now().toString()
    private val usage = mutableMapOf<String, Long>()
    private var usageLoaded = false
    private var dirtyTicks = 0
    private var updateTicks = UPDATE_CHECK_EVERY_TICKS - 30

    private var active: ActiveTarget? = null
    /** O Renova está em primeiro plano (lendo para liberar, nos 7 minutos, nas regras). */
    private var ownAppInFront = false
    private var lastEvalAt = 0L
    private var lastBlockAt = 0L
    private var lastExpandAt = 0L

    override fun onServiceConnected() {
        store = AppStore.get(this)
        power = getSystemService(PowerManager::class.java)
        overlay = BlockOverlay(this)
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
        val now = System.currentTimeMillis()
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

    private fun evaluate() {
        syncOverlay()
        val root = rootInActiveWindow ?: return
        val target = TargetDetector.detect(root) ?: return
        ownAppInFront = target.packageName == packageName
        active = target.takeIf(::isCounted)
        checkBlock()
    }

    private fun isCounted(target: ActiveTarget) = target.packageName != packageName && target.packageName !in IGNORED_PACKAGES

    /**
     * Apps visíveis fora da janela ativa: um vídeo em picture-in-picture (YouTube, Netflix…) ou a outra
     * metade da tela dividida. Eles não aparecem em [rootInActiveWindow], mas o tempo deles conta igual.
     */
    private fun otherVisibleTargets(): List<ActiveTarget> = windows
        .filter { (it.type == AccessibilityWindowInfo.TYPE_APPLICATION || it.isInPictureInPictureMode) && !it.isActive }
        .mapNotNull { window -> window.root?.let(TargetDetector::detect) }
        .filter(::isCounted)

    private suspend fun tickLoop() {
        while (scope.isActive) {
            delay(1000)
            rollOverDay()
            if (++updateTicks >= UPDATE_CHECK_EVERY_TICKS) {
                updateTicks = 0
                scope.launch { Updater.checkIfDue(this@BlockerAccessibilityService, UPDATE_INTERVAL_MS) }
            }
            if (!power.isInteractive) continue
            evaluate()
            val others = otherVisibleTargets()
            val inUse = RuleEngine.inUse(rules, others + listOfNotNull(active))
            if (inUse.isEmpty()) continue
            inUse.forEach { usage[it.id] = (usage[it.id] ?: 0L) + 1 }
            if (++dirtyTicks >= FLUSH_EVERY_TICKS) flushUsage()
            checkBlock()
            expandBlocked(others)
        }
    }

    private suspend fun rollOverDay() {
        val today = LocalDate.now().toString()
        if (today != usageDate) {
            usageDate = today
            usage.clear()
            flushUsage()
        }
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

    /** Regras que bloqueiam [target] agora (ver [RuleEngine.blocking]). */
    private fun blocking(target: ActiveTarget): List<Pair<Rule, BlockReason>> {
        val now = LocalDateTime.now()
        return RuleEngine.blocking(
            rules, target, usage, now, System.currentTimeMillis(), unlocks,
            devotionPending = devotionEnabled && !Devotion.isDone(devotionDoneDay, now),
        )
    }

    /** [ruleIds]: todas as regras que bloqueiam o alvo, para a leitura liberar o app de vez. */
    private fun block(target: ActiveTarget, rule: Rule, reason: String, ruleIds: List<String>) {
        // O aviso por cima já está bloqueando: não adianta tentar abrir a tela de novo.
        if (overlay.isShowing) return
        val now = System.currentTimeMillis()
        if (now - lastBlockAt < BLOCK_COOLDOWN_MS) return
        lastBlockAt = now
        active = null
        // NO_USER_ACTION evita que o app de baixo receba o "usuário saiu", que é o gatilho do
        // picture-in-picture automático (YouTube e afins continuariam tocando numa janelinha).
        // Sair da tela de bloqueio já leva à tela inicial, então não é preciso apertar Home aqui.
        val blockedScreen = Intent(this, BlockedActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            .putExtra(BlockedActivity.EXTRA_LABEL, rule.label)
            .putExtra(BlockedActivity.EXTRA_REASON, reason)
            .putExtra(BlockedActivity.EXTRA_TYPE, rule.type.name)
            .putExtra(BlockedActivity.EXTRA_RULE_IDS, ruleIds.toTypedArray())
        if (rule.type.isSection) {
            // No Shorts/Reels basta voltar (o resto do app continua liberado); espera o app sair da seção.
            performGlobalAction(GLOBAL_ACTION_BACK)
            scope.launch {
                delay(SECTION_BACK_DELAY_MS)
                startActivity(blockedScreen)
                ensureShown(target.packageName, rule, reason, blockedScreen)
            }
        } else {
            startActivity(blockedScreen)
            scope.launch { ensureShown(target.packageName, rule, reason, blockedScreen) }
        }
        scope.launch { flushUsage() }
    }

    /**
     * Alguns sistemas (Xiaomi sem a permissão de janelas pop-up em segundo plano) ignoram o startActivity
     * sem erro nenhum: o Shorts fechava, mas a tela de bloqueio não aparecia. Se ela não abriu, o serviço
     * desenha o bloqueio ele mesmo por cima do app.
     */
    private suspend fun ensureShown(pkg: String, rule: Rule, reason: String, blockedScreen: Intent) {
        delay(SHOW_CHECK_MS)
        if (BlockedActivity.isShowing || overlay.isShowing || frontAppPackage() != pkg) return
        overlay.show(
            blockedPackage = pkg,
            label = rule.label,
            reason = reason,
            onOpenApp = { startActivity(blockedScreen) },
            onLeave = {
                overlay.hide()
                if (!rule.type.isSection) performGlobalAction(GLOBAL_ACTION_HOME)
            },
        )
    }

    /** Fecha o aviso quando a tela de bloqueio de verdade abriu ou a pessoa saiu do app bloqueado. */
    private fun syncOverlay() {
        if (!overlay.isShowing) return
        val front = frontAppPackage()
        if (BlockedActivity.isShowing || (front != null && front != overlay.blockedPackage)) overlay.hide()
    }

    /** App na janela mais de cima (a lista de janelas vem de cima para baixo); ignora o próprio aviso. */
    private fun frontAppPackage(): String? = windows
        .firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        ?.root?.packageName?.toString()

    /**
     * Um alvo bloqueado fora da janela ativa (em picture-in-picture, ou na outra metade da tela dividida)
     * não é expulso pelo bloqueio normal. Trazê-lo para a frente deixa o bloqueio agir.
     */
    private fun expandBlocked(others: List<ActiveTarget>) {
        // Com o Renova na frente, puxar o app tiraria a pessoa da leitura que vai liberá-lo.
        if (ownAppInFront) return
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastExpandAt < EXPAND_COOLDOWN_MS) return
        for (target in others) {
            if (blocking(target).isEmpty()) continue
            val launch = packageManager.getLaunchIntentForPackage(target.packageName) ?: continue
            lastExpandAt = nowMs
            startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
    }

    companion object {
        private const val EVAL_THROTTLE_MS = 300L
        private const val BLOCK_COOLDOWN_MS = 1500L
        private const val SECTION_BACK_DELAY_MS = 400L
        private const val SHOW_CHECK_MS = 1200L
        private const val EXPAND_COOLDOWN_MS = 3000L
        private const val FLUSH_EVERY_TICKS = 10
        private const val UPDATE_CHECK_EVERY_TICKS = 15 * 60
        private const val UPDATE_INTERVAL_MS = 6 * 60 * 60 * 1000L
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
