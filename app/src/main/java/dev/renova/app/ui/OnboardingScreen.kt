package dev.renova.app.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.renova.app.R
import dev.renova.app.data.AppStore
import dev.renova.app.domain.Devotion
import dev.renova.app.domain.UnlockOption
import kotlinx.coroutines.launch

/** Passos do tutorial; o das notificações só existe no Android 13+, onde elas pedem permissão. */
internal enum class OnboardingStep { WELCOME, UNLOCK, ACCESSIBILITY, BATTERY, NOTIFICATIONS, DEVOTION, DONE }

internal fun onboardingSteps(sdk: Int = Build.VERSION.SDK_INT) =
    OnboardingStep.entries.filter { it != OnboardingStep.NOTIFICATIONS || sdk >= 33 }

/**
 * Tutorial da primeira abertura: explica o app e pede cada permissão com um toque. Nenhum passo prende:
 * dá para continuar sem ativar, e os avisos da tela inicial continuam lá para quem pulou.
 */
@Composable
fun OnboardingScreen(initialStep: Int = 0, onFinish: (createRule: Boolean) -> Unit) {
    val context = LocalContext.current
    val store = remember { AppStore.get(context) }
    val scope = rememberCoroutineScope()
    val permissions = rememberPermissionState()
    val devotionEnabled by store.devotionEnabled.collectAsState(initial = false)
    val steps = remember { onboardingSteps() }
    var index by rememberSaveable { mutableIntStateOf(initialStep) }
    val step = steps[index]
    val next = { if (index < steps.lastIndex) index++ }
    BackHandler(enabled = index > 0) { index-- }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                Dots(count = steps.size, current = index, modifier = Modifier.weight(1f))
                if (step != OnboardingStep.DONE) QuietButton("Pular", onClick = { onFinish(false) })
            }
            AnimatedContent(
                targetState = index,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally { it / 4 * dir } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 * dir } + fadeOut())
                },
                modifier = Modifier.weight(1f),
                label = "passo",
            ) { i ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) { StepContent(steps[i], permissions) }
            }
            Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when (step) {
                    OnboardingStep.WELCOME -> PrimaryButton("Começar", next, Modifier.fillMaxWidth())
                    OnboardingStep.UNLOCK -> PrimaryButton("Continuar", next, Modifier.fillMaxWidth())
                    OnboardingStep.ACCESSIBILITY -> PermissionButtons(permissions.serviceOn, "Concordo, ativar", next) {
                        openAccessibilitySettings(context)
                    }
                    OnboardingStep.BATTERY -> PermissionButtons(permissions.batteryOk, "Permitir", next) {
                        requestIgnoreBattery(context)
                    }
                    OnboardingStep.NOTIFICATIONS -> PermissionButtons(permissions.notifOk, "Permitir", next) {
                        permissions.requestNotifications()
                    }
                    OnboardingStep.DEVOTION -> if (devotionEnabled) {
                        PrimaryButton("Continuar", next, Modifier.fillMaxWidth())
                    } else {
                        PrimaryButton("Ativar o bloqueio", {
                            scope.launch { store.setDevotionEnabled(true) }
                            next()
                        }, Modifier.fillMaxWidth())
                        QuietButton("Agora não", next, Modifier.fillMaxWidth())
                    }
                    OnboardingStep.DONE -> {
                        PrimaryButton("Criar minha primeira regra", { onFinish(true) }, Modifier.fillMaxWidth())
                        QuietButton("Ir para o app", { onFinish(false) }, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

/** Ativar (ou continuar, se já está ativo) e "Depois", que segue sem ativar. */
@Composable
private fun PermissionButtons(granted: Boolean, action: String, next: () -> Unit, request: () -> Unit) {
    if (granted) {
        PrimaryButton("Continuar", next, Modifier.fillMaxWidth())
    } else {
        PrimaryButton(action, request, Modifier.fillMaxWidth())
        QuietButton("Depois", next, Modifier.fillMaxWidth())
    }
}

@Composable
private fun StepContent(step: OnboardingStep, permissions: PermissionState) {
    when (step) {
        OnboardingStep.WELCOME -> {
            // A camada do ícone tem 108 de lado e o desenho ocupa os 72 do meio.
            Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(120.dp * 108 / 72))
            }
            Title("Menos tela, mais Bíblia")
            Body(
                "O Renova limita o Shorts, o Reels e os apps que você escolher. Quando o tempo acaba, " +
                    "o app para, e você libera mais tempo lendo a Bíblia.",
            )
        }
        OnboardingStep.UNLOCK -> {
            Title("Leia para liberar")
            Body("Quando um app estiver bloqueado, escolha quanto ler:")
            Column {
                UnlockOption.entries.forEachIndexed { i, option ->
                    ListRow(option.reading, divider = i < UnlockOption.entries.lastIndex) {
                        Text(
                            "libera ${option.duration}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Body("Para afrouxar uma regra, é preciso ler pelo menos um capítulo.")
        }
        OnboardingStep.ACCESSIBILITY -> {
            Title("Acessibilidade")
            Body(ACCESSIBILITY_DISCLOSURE)
            if (permissions.serviceOn) Granted()
        }
        OnboardingStep.BATTERY -> {
            Title("Segundo plano")
            Body("Para o Android não desligar o bloqueio, permita que o Renova rode em segundo plano.")
            if (permissions.batteryOk) Granted()
        }
        OnboardingStep.NOTIFICATIONS -> {
            Title("Notificações")
            Body("Depois da leitura, o Renova mostra numa notificação quanto tempo de uso ainda falta.")
            if (permissions.notifOk) Granted()
        }
        OnboardingStep.DEVOTION -> {
            Title("7 minutos com o Senhor")
            Body(
                "Sete etapas guiadas para começar o dia: " +
                    Devotion.STEPS.joinToString(", ") { it.title.lowercase() } + ".",
            )
            Body("Se quiser, o Renova bloqueia os apps das regras até você ter esse tempo com o Senhor. Dá para mudar depois nas Configurações.")
        }
        OnboardingStep.DONE -> {
            Title("Tudo pronto")
            Body("Crie sua primeira regra: escolha o Shorts, o Reels ou um app e defina o limite por dia ou os horários.")
        }
    }
}

@Composable
private fun Title(text: String) = Text(text, style = MaterialTheme.typography.displaySmall)

@Composable
private fun Body(text: String) =
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Granted() =
    Text("Ativado ✓", style = MaterialTheme.typography.titleMedium, color = LocalExtraColors.current.ok)

/** Pontos de progresso: o atual em ocre e mais largo. */
@Composable
private fun Dots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(width = if (i == current) 20.dp else 8.dp, height = 8.dp)
                    .clip(CircleShape)
                    .background(if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}
