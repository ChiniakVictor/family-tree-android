package com.victor.familytree.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.victor.familytree.data.FamilyData
import com.victor.familytree.data.PedigreeLayout
import com.victor.familytree.data.Person
import kotlin.math.roundToInt

private val CARD_W = 176.dp
private val CARD_H = 60.dp
private val COL_GAP = 40.dp
private val SLOT_H = 72.dp
private val MARGIN = 24.dp

@Composable
fun PedigreeScreen(
    data: FamilyData,
    rootId: String,
    generations: Int,
    onGenerations: (Int) -> Unit,
    selectedId: String?,
    onOpen: (String) -> Unit,
    onChangeRoot: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val root = data.person(rootId)
    val chart = remember(data, rootId, generations) { PedigreeLayout.build(data, rootId, generations) }
    var picking by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Ancestors of", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(root?.displayName ?: "?", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { picking = true }) { Icon(Icons.Filled.PersonSearch, "Choose person") }
            IconButton(onClick = { onGenerations((generations - 1).coerceAtLeast(2)) }) { Icon(Icons.Filled.Remove, "Fewer generations") }
            Text("$generations gen", style = MaterialTheme.typography.labelLarge)
            IconButton(onClick = { onGenerations((generations + 1).coerceAtMost(16)) }) { Icon(Icons.Filled.Add, "More generations") }
        }
        if (root?.id != data.defaultRootId && data.defaultRootId != null) {
            TextButton(onClick = { onChangeRoot(data.defaultRootId) }, modifier = Modifier.padding(horizontal = 4.dp)) {
                Text("Back to ${data.person(data.defaultRootId)?.displayName}")
            }
        }

        BoxWithConstraints(Modifier.fillMaxSize().clipToBounds().background(MaterialTheme.colorScheme.surface)) {
            val density = LocalDensity.current
            val px = with(density) { object { val cardW = CARD_W.toPx(); val cardH = CARD_H.toPx(); val col = (CARD_W + COL_GAP).toPx(); val slot = SLOT_H.toPx(); val margin = MARGIN.toPx() } }
            val viewH = constraints.maxHeight.toFloat()
            val rootNode = chart.nodes.lastOrNull()
            fun home(): Offset = Offset(px.margin * 0.5f, viewH / 2f - (rootNode?.let { px.margin + it.slot * px.slot + px.cardH / 2 } ?: 0f))

            var scale by remember { mutableFloatStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            LaunchedEffect(chart, viewH) { scale = 1f; offset = home() }

            val contentW = with(density) { (MARGIN * 2 + (CARD_W + COL_GAP) * chart.generations) }
            val contentH = with(density) { MARGIN * 2 + SLOT_H * chart.slots.coerceAtLeast(1) }
            val lineColor = MaterialTheme.colorScheme.outline

            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(0.25f, 3f)
                        offset = centroid - (centroid - offset) * (newScale / scale) + pan
                        scale = newScale
                    }
                },
            ) {
                Box(
                    Modifier
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .size(contentW, contentH)
                        .graphicsLayer {
                            translationX = offset.x; translationY = offset.y
                            scaleX = scale; scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0f)
                        },
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = Stroke(width = 2.dp.toPx())
                        chart.edges.forEach { e ->
                            val c = chart.nodes[e.child]
                            val p = chart.nodes[e.parent]
                            val x1 = px.margin + c.generation * px.col + px.cardW
                            val y1 = px.margin + c.slot * px.slot + px.cardH / 2
                            val x2 = px.margin + p.generation * px.col
                            val y2 = px.margin + p.slot * px.slot + px.cardH / 2
                            val mid = (x1 + x2) / 2
                            val path = Path().apply { moveTo(x1, y1); lineTo(mid, y1); lineTo(mid, y2); lineTo(x2, y2) }
                            if (e.disputed) drawPath(path, FamilyColors.low, style = Stroke(width = 2.dp.toPx(),
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 9f))))
                            else drawPath(path, lineColor, style = stroke)
                        }
                    }
                    chart.nodes.forEach { n ->
                        val x = px.margin + n.generation * px.col
                        val y = px.margin + n.slot * px.slot
                        TreeCard(
                            n.person,
                            disputed = n.disputed,
                            selected = n.person.id == selectedId,
                            more = n.hasMoreAncestors,
                            onClick = { onOpen(n.person.id) },
                            modifier = Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) },
                        )
                    }
                }
            }
            Column(
                Modifier.align(Alignment.BottomEnd).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SmallFloatingActionButton(onClick = { scale = (scale * 1.25f).coerceAtMost(3f) }) { Icon(Icons.Filled.ZoomIn, "Zoom in") }
                SmallFloatingActionButton(onClick = { scale = (scale / 1.25f).coerceAtLeast(0.25f) }) { Icon(Icons.Filled.ZoomOut, "Zoom out") }
                SmallFloatingActionButton(onClick = { scale = 1f; offset = home() }) { Icon(Icons.Filled.CenterFocusStrong, "Re-centre") }
            }
            if (chart.nodes.size <= 1) {
                Text(
                    "No parents recorded for this person yet.",
                    Modifier.align(Alignment.TopCenter).padding(top = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (picking) PersonPickerDialog(data, "Show ancestors of", onPick = { onChangeRoot(it); picking = false }, onDismiss = { picking = false })
}

@Composable
private fun TreeCard(p: Person, disputed: Boolean = false, selected: Boolean, more: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val accent = if (disputed) FamilyColors.low else sexColor(p.sex)
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .size(CARD_W, CARD_H)
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else accent.copy(alpha = 0.5f), shape)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 5.dp, height = CARD_H).background(accent))
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(p.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = if (disputed) 1 else 2, overflow = TextOverflow.Ellipsis)
            if (disputed) Row(verticalAlignment = Alignment.CenterVertically) {
                DisputedBadge()
                if (p.lifespan.isNotBlank()) Text(" ${p.lifespan}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            } else if (p.lifespan.isNotBlank()) Text(p.lifespan, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (more) Text("›", Modifier.padding(end = 6.dp), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    }
}
