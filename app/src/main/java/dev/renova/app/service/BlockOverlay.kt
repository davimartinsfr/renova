package dev.renova.app.service

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.renova.app.ui.AppTheme
import dev.renova.app.ui.BlockedScreen

/**
 * Tela de bloqueio desenhada pelo próprio serviço, para quando o sistema não deixa abrir a
 * BlockedActivity em segundo plano (Xiaomi sem a permissão de janelas pop-up, e afins).
 * A janela de acessibilidade não precisa de permissão nenhuma.
 */
internal class BlockOverlay(private val service: AccessibilityService) {

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var view: FrameLayout? = null
    private var owner: Owner? = null

    /** App que estava bloqueado quando o aviso abriu; trocar de app fecha o aviso. */
    var blockedPackage: String? = null
        private set

    /** O aviso é do app da frente (fecha quando outro vem para a frente), e não de um app em PiP. */
    var followsFront = true
        private set

    val isShowing get() = view != null

    /** [onLeave] também vale para o Voltar do sistema. */
    fun show(
        blockedPackage: String,
        label: String,
        reason: String,
        onOpenApp: () -> Unit,
        onLeave: () -> Unit,
        followsFront: Boolean = true,
    ) {
        hide()
        this.blockedPackage = blockedPackage
        this.followsFront = followsFront
        val owner = Owner().also { this.owner = it }
        val compose = ComposeView(service).apply {
            setContent { AppTheme { BlockedScreen(label, reason, null, onLeave, {}, {}, onOpenApp) } }
        }
        val root = object : FrameLayout(service) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) onLeave()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        root.addView(compose)
        root.setViewTreeLifecycleOwner(owner)
        root.setViewTreeSavedStateRegistryOwner(owner)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            0, // focável: recebe o Voltar em vez do app de baixo
            PixelFormat.OPAQUE,
        )
        runCatching { windowManager.addView(root, params) }
            .onSuccess { view = root }
            .onFailure { hide() }
    }

    fun hide() {
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
        owner?.destroy()
        owner = null
        blockedPackage = null
    }

    /** Ciclo de vida mínimo que o Compose exige fora de uma Activity. */
    private class Owner : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val savedState = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry get() = savedState.savedStateRegistry

        init {
            savedState.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
