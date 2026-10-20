package dev.renova.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Título grande em serifa com um versículo curto embaixo: a abertura de cada tela. */
@Composable
fun PageHeader(title: String, verse: String? = null, ref: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 24.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.displayMedium)
        if (verse != null) {
            val muted = MaterialTheme.colorScheme.onSurfaceVariant
            Text(
                buildAnnotatedString {
                    append(verse)
                    if (ref != null) {
                        append(" ")
                        withStyle(SpanStyle(color = muted.copy(alpha = 0.7f))) { append(ref) }
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
            )
        }
    }
}

/** Bloco de cor chapada com texto branco. */
@Composable
fun Tile(
    color: Color,
    title: String,
    subtitle: String? = null,
    action: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(color)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineLarge, color = RenovaColors.OnTile)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = RenovaColors.OnTile.copy(alpha = 0.85f))
        }
        if (action != null) {
            Text(
                if (onClick != null) "$action  →" else action,
                style = MaterialTheme.typography.labelLarge,
                color = RenovaColors.OnTile,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/** Título de seção com uma ação opcional de "adicionar" à direita. */
@Composable
fun SectionTitle(text: String, onAdd: (() -> Unit)? = null, addLabel: String? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        if (onAdd != null) {
            IconButton(
                onClick = onAdd,
                modifier = Modifier.size(44.dp).border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            ) { Icon(Icons.Default.Add, addLabel) }
        }
    }
}

/** Linha de lista com divisória fina embaixo. */
@Composable
fun ListRow(
    title: String,
    subtitle: String? = null,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .heightIn(min = 52.dp)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            trailing()
        }
        if (divider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Aviso de configuração numa linha compacta, com a ação à direita. */
@Composable
fun NoticeRow(text: String, action: String, detail: String? = null, urgent: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(8.dp).clip(CircleShape)
                .background(if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
        )
        Column(Modifier.weight(1f).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TextButton(onClick = onClick) { Text(action, style = MaterialTheme.typography.labelLarge) }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** Botão só de texto, na cor do texto (não na cor de destaque). */
@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
    ) {
        if (icon != null) Icon(icon, null, Modifier.padding(end = 8.dp).size(20.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Barra fina de progresso em ocre. */
@Composable
fun ThinProgress(progress: Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        strokeCap = StrokeCap.Butt,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

/** Barra superior mínima: ícone de fechar/voltar e um rótulo pequeno. */
@Composable
fun TopRow(
    label: String?,
    navIcon: ImageVector,
    navLabel: String,
    onNav: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onNav) { Icon(navIcon, navLabel) }
        Text(
            label.orEmpty(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        actions()
    }
}

private val WHEEL_ROW = 44.dp

/**
 * Seletor em roda, com três linhas visíveis: o valor do meio é o escolhido. Rola com encaixe;
 * tocar num valor também o seleciona.
 */
@Composable
fun WheelPicker(
    values: List<Int>,
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 64.dp,
    format: (Int) -> String = { it.toString() },
) {
    val initial = values.indexOf(selected).coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initial)
    val scope = rememberCoroutineScope()
    // Com o respiro de uma linha em cima, o item do meio é o primeiro visível (ou o seguinte, passada a metade).
    val center by remember {
        derivedStateOf {
            val half = state.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.div(2) ?: 0
            (state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > half) 1 else 0).coerceIn(values.indices)
        }
    }
    val current by rememberUpdatedState(selected)
    val report by rememberUpdatedState(onSelected)
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collect { scrolling ->
            if (!scrolling && values[center] != current) report(values[center])
        }
    }
    LazyColumn(
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = WHEEL_ROW),
        modifier = modifier.width(width).height(WHEEL_ROW * 3),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        itemsIndexed(values) { i, v ->
            Box(
                Modifier.fillMaxWidth().height(WHEEL_ROW).clickable {
                    scope.launch { state.animateScrollToItem(i) }
                    onSelected(v)
                },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    format(v),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    color = if (i == center) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
            }
        }
    }
}
