package com.victor.familytree.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.victor.familytree.data.Confidence
import com.victor.familytree.data.DescendantTree
import com.victor.familytree.data.FamilyData

@Composable
fun DescendantsScreen(
    data: FamilyData,
    rootId: String,
    selectedId: String?,
    onOpen: (String) -> Unit,
    onChangeRoot: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var collapsed by remember(rootId) { mutableStateOf(emptySet<String>()) }
    var picking by remember { mutableStateOf(false) }
    val rows = remember(data, rootId, collapsed) { DescendantTree.rows(data, rootId, collapsed) }
    val total = remember(data, rootId) { DescendantTree.count(data, rootId) }
    val root = data.person(rootId)

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Descendants of", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(root?.displayName ?: "?", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$total descendants recorded", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { picking = true }) { Icon(Icons.Filled.PersonSearch, "Choose person") }
        }
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            items(rows, key = { it.person.id }) { r ->
                val indent = (minOf(r.depth, 10) * 14).dp
                Row(
                    Modifier.fillMaxWidth().padding(start = 8.dp + indent, end = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (r.person.id == selectedId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                        .clickable { onOpen(r.person.id) }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (r.depth > 0) Box(Modifier.width(3.dp).height(36.dp).background(sexColor(r.person.sex).copy(alpha = 0.6f)))
                    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        if (r.childCount > 0) {
                            IconButton(onClick = {
                                collapsed = if (r.person.id in collapsed) collapsed - r.person.id else collapsed + r.person.id
                            }) {
                                Icon(
                                    if (r.person.id in collapsed) Icons.Filled.ChevronRight else Icons.Filled.ExpandMore,
                                    if (r.person.id in collapsed) "Expand" else "Collapse",
                                )
                            }
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("G${r.depth}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(28.dp))
                            Text(r.person.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            if (r.confidence != null && r.confidence != Confidence.HIGH) {
                                Spacer(Modifier.width(6.dp)); ConfidenceBadge(r.confidence, compact = true)
                            }
                        }
                        val sub = listOfNotNull(
                            r.person.lifespan.ifBlank { null },
                            r.spouses.takeIf { it.isNotEmpty() }?.joinToString(prefix = "+ ") { it.displayName },
                            when (r.childCount) { 0 -> null; 1 -> "1 child"; else -> "${r.childCount} children" },
                        ).joinToString(" · ")
                        if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 28.dp))
                    }
                }
            }
        }
    }
    if (picking) PersonPickerDialog(data, "Show descendants of", onPick = { onChangeRoot(it); picking = false }, onDismiss = { picking = false })
}
