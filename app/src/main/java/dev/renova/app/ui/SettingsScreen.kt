package dev.renova.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.renova.app.BuildConfig
import dev.renova.app.data.AppStore
import dev.renova.app.data.Bible
import dev.renova.app.data.ThemeMode
import dev.renova.app.data.ThemePrefs
import dev.renova.app.data.Updater
import dev.renova.app.service.BlockerAccessibilityService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val PRIVACY_URL = "https://davimartinsfr.github.io/renova/privacidade.html"

/** Tema, plano de leitura, permissões e informações do app. */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { AppStore.get(context) }
    val scope = rememberCoroutineScope()
    val position by store.biblePosition.collectAsState(initial = null)
    var picking by remember { mutableStateOf(false) }
    var confirmRestart by remember { mutableStateOf(false) }
    var showCredits by remember { mutableStateOf(false) }
    var showDisclosure by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    val devotionEnabled by store.devotionEnabled.collectAsState(initial = false)
    var needReading by remember { mutableStateOf(false) }

    // Permissões mudam fora do app: reavaliadas ao voltar para a tela.
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeTick++
        onPauseOrDispose {}
    }
    val serviceOn = remember(resumeTick) { BlockerAccessibilityService.isEnabled(context) }
    val batteryOk = remember(resumeTick) {
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }
    val notifOk = remember(resumeTick) {
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { resumeTick++ }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopRow(
                label = null,
                navIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navLabel = "Voltar",
                onNav = onBack,
                modifier = Modifier.statusBarsPadding(),
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Configurações", style = MaterialTheme.typography.displaySmall)

            SectionTitle("Tema")
            val mode = ThemePrefs.mode(context)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = mode == m,
                        onClick = { if (mode != m) ThemePrefs.set(context, m) },
                        shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size, MaterialTheme.shapes.small),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary,
                            activeBorderColor = MaterialTheme.colorScheme.primary,
                            inactiveContainerColor = MaterialTheme.colorScheme.background,
                            inactiveBorderColor = MaterialTheme.colorScheme.outline,
                        ),
                        icon = {},
                    ) { Text(m.label, style = MaterialTheme.typography.labelLarge) }
                }
            }

            SectionTitle("7 minutos com o Senhor")
            ListRow(
                "Bloquear até fazer",
                subtitle = "Os apps das regras ficam bloqueados até os 7 minutos do dia",
                divider = false,
            ) {
                Switch(checked = devotionEnabled, onCheckedChange = { on ->
                    scope.launch {
                        // Ligar é livre; desligar afrouxa o bloqueio e pede leitura, como afrouxar uma regra.
                        if (on || store.isUnlockedForEdits()) store.setDevotionEnabled(on) else needReading = true
                    }
                })
            }

            SectionTitle("Leitura")
            Column {
                ListRow(
                    "Ir para um capítulo",
                    subtitle = position?.let { "Próximo: ${Bible.title(it)}" },
                    onClick = { picking = true },
                )
                ListRow(
                    "Recomeçar de Gênesis 1",
                    divider = false,
                    onClick = { confirmRestart = true },
                )
            }

            SectionTitle("Permissões")
            Column {
                PermissionRow("Acessibilidade", serviceOn, divider = true) { showDisclosure = true }
                PermissionRow("Segundo plano", batteryOk, divider = true) { requestIgnoreBattery(context) }
                PermissionRow("Notificações", notifOk, divider = false) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            SectionTitle("Sobre")
            Column {
                ListRow("Créditos", subtitle = "Bíblia, fontes e ícones", onClick = { showCredits = true })
                ListRow("Política de privacidade", onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL)))
                })
                ListRow("Versão", subtitle = "v${BuildConfig.VERSION_NAME}", divider = false) {
                    // Na Play Store quem atualiza é a loja.
                    if (!BuildConfig.PLAY_STORE) {
                        TextButton(enabled = !checking, onClick = {
                            scope.launch {
                                checking = true
                                Updater.check(context)
                                val update = store.availableUpdate.first()
                                Toast.makeText(
                                    context,
                                    if (update != null) "Nova versão: v${update.version}. Atualize pela tela inicial." else "Você já tem a versão mais recente.",
                                    Toast.LENGTH_LONG,
                                ).show()
                                checking = false
                            }
                        }) { Text(if (checking) "Verificando…" else "Verificar", style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
            Box(Modifier.heightIn(min = 24.dp))
        }
    }

    if (picking) {
        ChapterPickerDialog(onDismiss = { picking = false }) { book, chapter ->
            picking = false
            scope.launch { store.setPosition(book, chapter) }
        }
    }
    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            title = { Text("Recomeçar o plano?") },
            text = { Text("O próximo capítulo volta a ser Gênesis 1.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestart = false
                    scope.launch { store.setPosition(0, 0) }
                }) { Text("Recomeçar") }
            },
            dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text("Cancelar") } },
        )
    }
    if (needReading) LooseningDialog("desligar os 7 minutos") { needReading = false }
    if (showCredits) {
        AlertDialog(
            onDismissRequest = { showCredits = false },
            title = { Text("Créditos") },
            text = {
                Text(
                    "Texto bíblico: Almeida Revista e Atualizada, 2ª ed. © Sociedade Bíblica do Brasil, 1993.\n\n" +
                        "Fontes: Fraunces, Gelasio e Inter, licença SIL Open Font License.\n\n" +
                        "Ampulheta: “Hourglass” de Rahmad romadoni, Noun Project, licença CC BY 3.0.",
                )
            },
            confirmButton = { TextButton(onClick = { showCredits = false }) { Text("Fechar") } },
        )
    }
    if (showDisclosure) AccessibilityDisclosure(
        onAccept = {
            showDisclosure = false
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        },
        onDismiss = { showDisclosure = false },
    )
}

@Composable
private fun PermissionRow(title: String, ok: Boolean, divider: Boolean, onFix: () -> Unit) {
    ListRow(title, divider = divider) {
        if (ok) {
            Text("Ativa", style = MaterialTheme.typography.labelLarge, color = LocalExtraColors.current.ok)
        } else {
            TextButton(onClick = onFix) { Text("Ativar", style = MaterialTheme.typography.labelLarge) }
        }
    }
}

/** Escolha do próximo capítulo: primeiro o livro, depois o número. */
@Composable
private fun ChapterPickerDialog(onDismiss: () -> Unit, onPick: (book: Int, chapter: Int) -> Unit) {
    val context = LocalContext.current
    val bible by produceState<Bible?>(null) { value = Bible.load(context) }
    var book by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(book?.let { Bible.BOOK_NAMES[it] } ?: "Escolher livro") },
        text = {
            val b = bible
            val chosen = book
            when {
                b == null -> CircularProgressIndicator(Modifier.padding(16.dp))
                chosen == null -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    itemsIndexed(Bible.BOOK_NAMES) { i, name ->
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().clickable { book = i }.padding(vertical = 12.dp),
                        )
                    }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.heightIn(max = 420.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items((1..b.chaptersPerBook[chosen]).toList()) { n ->
                        Box(
                            Modifier.padding(4.dp).aspectRatio(1f).clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .clickable { onPick(chosen, n - 1) },
                            contentAlignment = Alignment.Center,
                        ) { Text("$n", style = MaterialTheme.typography.titleMedium) }
                    }
                }
            }
        },
        confirmButton = {
            if (book != null) TextButton(onClick = { book = null }) { Text("Outro livro") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
