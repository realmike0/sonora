package com.sonora.music.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private data class NavItem(val label: String, val icon: ImageVector)

private val NAV_ITEMS = listOf(
    NavItem("Home", Icons.Rounded.Home),
    NavItem("Ricerca", Icons.Rounded.Search),
    NavItem("Esplora", Icons.Rounded.Explore),
    NavItem("Raccolta", Icons.Rounded.LibraryMusic)
)

/**
 * Barra di navigazione fluttuante (stile Material Expressive): una pillola staccata dai bordi
 * dove la voce selezionata si allarga con effetto molla e mostra la sua etichetta.
 */
@Composable
fun FloatingNavBar(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    when (AppStyle.id) {
        StyleId.YOU -> { FlatNavBar(selected, onSelect, indicator = true, modifier = modifier); return }
        StyleId.MIUIX -> { FlatNavBar(selected, onSelect, indicator = false, modifier = modifier); return }
        else -> Unit
    }
    val glass = AppStyle.id == StyleId.GLASS
    Surface(
        shape = RoundedCornerShape(36.dp),
        color = if (glass) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = if (glass) 0.dp else 3.dp,
        shadowElevation = if (glass) 0.dp else 10.dp,
        border = if (glass) androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)) else null,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Row(
            Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            NAV_ITEMS.forEachIndexed { index, item ->
                NavPill(
                    item = item,
                    selected = selected == index,
                    onClick = { onSelect(index) },
                    modifier = Modifier
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavPill(
    item: NavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val weight by animateFloatAsState(
        targetValue = if (selected) 1.9f else 1f,
        animationSpec = spring(if (AppStyle.bouncy) Spring.DampingRatioMediumBouncy else Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow),
        label = "navWeight"
    )
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "navContainer"
    )
    val content by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "navContent"
    )

    Row(
        Modifier
            .weight(weight)
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(container)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(item.icon, contentDescription = tr(item.label), tint = content, modifier = Modifier.size(26.dp))
        AnimatedVisibility(
            visible = selected,
            enter = expandHorizontally() + fadeIn(),
            exit = shrinkHorizontally() + fadeOut()
        ) {
            Row {
                Spacer(Modifier.width(8.dp))
                Text(
                    tr(item.label),
                    color = content,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1
                )
            }
        }
    }
}

/** Barra a tutta larghezza: icona sopra e nome sotto. Con "indicator" la voce scelta ha la pillola (Material You); senza è solo colorata (Miuix). */
@Composable
private fun FlatNavBar(selected: Int, onSelect: (Int) -> Unit, indicator: Boolean, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            NAV_ITEMS.forEachIndexed { index, item ->
                val on = selected == index
                val tint by animateColorAsState(
                    if (on) {
                        if (indicator) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                    } else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "flatTint"
                )
                androidx.compose.foundation.layout.Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSelect(index) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (indicator && on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                    ) {
                        Icon(item.icon, contentDescription = tr(item.label), tint = tint, modifier = Modifier.size(24.dp))
                    }
                    Text(tr(item.label), color = tint, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
        }
    }
}
