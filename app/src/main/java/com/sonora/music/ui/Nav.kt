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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
    NavItem("Raccolta", Icons.Rounded.LibraryMusic)
)

/**
 * Barra di navigazione fluttuante (stile Material Expressive): una pillola staccata dai bordi
 * dove la voce selezionata si allarga con effetto molla e mostra la sua etichetta.
 */
@Composable
fun FloatingNavBar(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(36.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 10.dp,
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
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
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
        Icon(item.icon, contentDescription = item.label, tint = content, modifier = Modifier.size(26.dp))
        AnimatedVisibility(
            visible = selected,
            enter = expandHorizontally() + fadeIn(),
            exit = shrinkHorizontally() + fadeOut()
        ) {
            Row {
                Spacer(Modifier.width(8.dp))
                Text(
                    item.label,
                    color = content,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1
                )
            }
        }
    }
}
