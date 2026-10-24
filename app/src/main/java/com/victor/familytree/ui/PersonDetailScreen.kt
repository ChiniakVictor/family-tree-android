package com.victor.familytree.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.victor.familytree.data.Confidence
import com.victor.familytree.data.DataCheck
import com.victor.familytree.data.DisputedRelative
import com.victor.familytree.data.Event
import com.victor.familytree.data.Fact
import com.victor.familytree.data.FamilyData
import com.victor.familytree.data.Person
import com.victor.familytree.data.Relative
import com.victor.familytree.data.SourceRef
import com.victor.familytree.data.humanize
import com.victor.familytree.data.yearOf

private val VITAL_TYPES = setOf("birth", "death", "burial")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailScreen(
    data: FamilyData,
    id: String,
    onOpen: (String) -> Unit,
    onBack: (() -> Unit)?,
    onShowTree: (String) -> Unit,
    onShowDescendants: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = data.person(id)
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(p?.displayName ?: "Not found", maxLines = 1) },
            navigationIcon = {
                if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            },
            windowInsets = WindowInsets(0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        )
        if (p == null) {
            Text("This person isn't in the current data.", Modifier.padding(16.dp))
            return@Column
        }
        val parents = remember(data, id) { data.parentsOf(id) }
        val spouses = remember(data, id) { data.spousesOf(id) }
        val children = remember(data, id) { data.childrenOf(id) }
        val disputedParents = remember(data, id) { data.disputedParentsOf(id) }
        val disputedChildren = remember(data, id) { data.disputedChildrenOf(id) }
        val checks = remember(data, id) { data.checksFor(id) }
        val events = remember(data, id) { visibleEvents(data, p) }
        val listState = rememberLazyListState()
        LaunchedEffect(id) { listState.scrollToItem(0) }

        LazyColumn(state = listState, contentPadding = PaddingValues(16.dp)) {
            item { Header(p) }
            item {
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { onShowTree(p.id) }, label = { Text("Ancestors") },
                        leadingIcon = { Icon(Icons.Filled.AccountTree, null, Modifier.size(18.dp)) })
                    AssistChip(onClick = { onShowDescendants(p.id) }, label = { Text("Descendants") },
                        leadingIcon = { Icon(Icons.Filled.FamilyRestroom, null, Modifier.size(18.dp)) })
                }
            }
            if (checks.isNotEmpty()) item { ChecksCard(checks, onOpen) }

            names(p)
            vitals(p)

            relatives("Parents", parents, onOpen)
            disputed("Disputed parent link", disputedParents, data, onOpen)
            relatives("Spouses & partners", spouses, onOpen)
            relatives("Children", children, onOpen)
            disputed("Disputed child link", disputedChildren, data, onOpen)

            if (events.isNotEmpty()) {
                item { SectionTitle("Timeline") }
                items(events, key = { it.id }) { e -> EventRow(data, p, e, onOpen) }
            }
            p.infoLists.forEach { list ->
                item { SectionTitle(list.title) }
                items(list.items) { Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp)) }
            }
            if (p.extras.isNotEmpty()) {
                item { SectionTitle("Other details") }
                items(p.extras) { (k, v) -> LabeledValue(k, v) }
            }
            if (p.notes.isNotEmpty()) {
                item { SectionTitle("Notes") }
                items(p.notes) { Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp)) }
            }
            p.ancestry?.let { a -> item { AncestryCard(a) } }
            val refs = p.sources
            if (refs.isNotEmpty()) {
                item { SectionTitle("Sources (${refs.size})") }
                items(refs) { SourceRow(data, it) }
            }
            item {
                Text(
                    "Record ${p.id}" + (p.generation?.let { " · generation $it relative to Victor" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(p: Person) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(p, 64.dp)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                listOfNotNull(p.displayName, p.suffix?.takeUnless { p.displayName.endsWith(it) }).joinToString(" "),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (p.lifespan.isNotBlank()) {
                Text(p.lifespan, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when (p.sex) {
                    "M" -> Tag("Male", FamilyColors.male)
                    "F" -> Tag("Female", FamilyColors.female)
                    else -> Tag("Sex unknown", FamilyColors.unknown)
                }
                p.directAncestor?.let { da ->
                    da.relationship?.let { Tag(it, MaterialTheme.colorScheme.primary) }
                    da.confidence?.let { ConfidenceBadge(it) }
                }
                p.confidence?.let { ConfidenceBadge(it) }
                p.origin?.let { Tag("from ${originLabel(it)}") }
            }
        }
    }
}

private fun LazyListScope.names(p: Person) {
    val rows = buildList {
        if (p.aliases.isNotEmpty()) add("Also known as" to p.aliases.joinToString(", "))
        if (p.nicknames.isNotEmpty()) add("Nicknames" to p.nicknames.joinToString(", "))
        p.maidenName?.let { add("Maiden name" to it) }
        p.marriedName?.let { add("Married name" to it) }
    }
    if (rows.isEmpty()) return
    item { SectionTitle("Names") }
    items(rows) { (k, v) -> LabeledValue(k, v) }
}

private fun LazyListScope.vitals(p: Person) {
    item { SectionTitle("Life") }
    if (p.living) {
        item {
            LabeledValue("Status", "Living. Dates are hidden for privacy.")
        }
        return
    }
    val facts = listOf("Birth" to p.birth, "Death" to p.death, "Burial" to p.burial).filter { it.second?.isEmpty == false }
    if (facts.isEmpty()) item { LabeledValue("Dates", "Not recorded yet") }
    items(facts) { (label, f) -> FactRow(label, f!!) }
}

@Composable
private fun FactRow(label: String, f: Fact) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(72.dp).padding(top = 3.dp))
            Column(Modifier.weight(1f)) {
                Text(f.date ?: "Date unknown", style = MaterialTheme.typography.bodyLarge)
                f.place?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            f.confidence?.let { ConfidenceBadge(it, compact = true) }
        }
        if (f.hasConflict) {
            Text(
                (if (f.conflictingYears.isNotEmpty()) "Conflicting records: " else "Other records: ") +
                    f.alternatives.joinToString("; "),
                color = if (f.conflictingYears.isNotEmpty()) FamilyColors.low else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 80.dp, top = 2.dp),
            )
        }
        f.details.forEach { (k, v) ->
            Text("$k: $v", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 80.dp))
        }
        f.origin?.let {
            Text("from ${originLabel(it)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(start = 80.dp))
        }
    }
}

private fun LazyListScope.disputed(title: String, list: List<DisputedRelative>, data: FamilyData, onOpen: (String) -> Unit) {
    if (list.isEmpty()) return
    item { SectionTitle(if (list.size == 1) title else "${title}s (${list.size})") }
    // parents of the same disputed family share one reason: show it once, after the last of them
    list.forEachIndexed { i, r ->
        val showReason = r.reason != null && list.getOrNull(i + 1)?.reason != r.reason
        item(key = "$title-${r.person.id}") {
            DisputedRelativeRow(r, showReason, sourceTitles = r.sources.mapNotNull { s -> s.sourceId?.let { data.sources[it]?.title } ?: s.url },
                onClick = { onOpen(r.person.id) })
        }
    }
}

private fun LazyListScope.relatives(title: String, list: List<Relative>, onOpen: (String) -> Unit) {
    if (list.isEmpty()) return
    item { SectionTitle("$title (${list.size})") }
    items(list, key = { "$title-${it.person.id}" }) { r ->
        PersonRow(r.person, onClick = { onOpen(r.person.id) }, subtitle = r.note, confidence = r.confidence, avatarSize = 36.dp)
    }
}

@Composable
private fun ChecksCard(checks: List<DataCheck>, onOpen: (String) -> Unit) {
    val warn = checks.any { it.severity == DataCheck.Severity.WARNING }
    val tint = if (warn) FamilyColors.medium else MaterialTheme.colorScheme.tertiary
    Card(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.10f)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                if (warn) "Needs checking" else "Notes on certainty",
                style = MaterialTheme.typography.titleSmall,
                color = tint,
                fontWeight = FontWeight.SemiBold,
            )
            checks.forEach { c ->
                Row(
                    Modifier.fillMaxWidth()
                        .then(if (c.relatedId != null) Modifier.clickable { onOpen(c.relatedId) } else Modifier)
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        if (c.severity == DataCheck.Severity.WARNING) Icons.Filled.Warning else Icons.Filled.Info,
                        null, tint = tint, modifier = Modifier.size(16.dp).padding(top = 2.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(c.message, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Events for the timeline; living people get no dated birth/death entries. */
private fun visibleEvents(data: FamilyData, p: Person): List<Event> =
    data.eventsFor(p.id)
        .filterNot { e ->
            val anyLiving = p.living || e.participants.any { data.person(it.id)?.living == true }
            (p.living && e.type.lowercase() in VITAL_TYPES) || (anyLiving && e.date != null)
        }
        .sortedBy { it.date?.let(::yearOf) ?: Int.MAX_VALUE }

@Composable
private fun EventRow(data: FamilyData, p: Person, e: Event, onOpen: (String) -> Unit) {
    val others = e.participants.filter { it.id != p.id }
    val title = when (e.type.lowercase()) {
        "couple_relationship" -> "Partnership"
        else -> humanize(e.type)
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text(
            e.date ?: "—",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(96.dp),
        )
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                e.confidence?.let { ConfidenceBadge(it, compact = true) }
            }
            e.place?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            others.forEach { o ->
                val person = data.person(o.id)
                Text(
                    "with ${person?.displayName ?: o.name ?: "?"}" + (o.role?.takeIf { it != "partner" && it != "principal" }?.let { " ($it)" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (person != null) MaterialTheme.colorScheme.primary else Color.Unspecified,
                    modifier = if (person != null) Modifier.clickable { onOpen(person.id) } else Modifier,
                )
            }
            if (e.alternatives.isNotEmpty()) {
                Text("Conflicting: " + e.alternatives.joinToString("; "), style = MaterialTheme.typography.bodySmall, color = FamilyColors.low)
            }
            e.details.forEach { (k, v) ->
                Text("$k: $v", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (e.sources.isNotEmpty()) {
                Text(
                    "Sources: " + e.sources.joinToString(", ") { s ->
                        s.text?.takeIf { s.sourceId?.startsWith("GED:") == true }
                            ?: listOfNotNull(s.sourceId, s.page?.let { "p.$it" }).joinToString(" ")
                    }.let { if (it.length > 160) it.take(157) + "…" else it },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SourceRow(data: FamilyData, ref: SourceRef) {
    val src = ref.sourceId?.let { data.sources[it] }
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    src?.title ?: ref.sourceId ?: "Source",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                ConfidenceBadge(ref.confidence ?: Confidence.UNKNOWN, compact = true)
            }
            val meta = listOfNotNull(ref.sourceId, ref.page?.let { "page $it" }, src?.date, ref.origin?.let { "from ${originLabel(it)}" })
            Text(meta.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ref.text?.let { Text("“$it”", style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, modifier = Modifier.padding(top = 4.dp)) }
            ref.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp)) }
            (ref.url ?: src?.url)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun AncestryCard(a: com.victor.familytree.data.AncestryInfo) {
    val uri = LocalUriHandler.current
    SectionTitle("Ancestry tree")
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(12.dp)) {
            val meta = listOfNotNull(
                "${a.profiles} profile" + if (a.profiles == 1) "" else "s",
                a.mediaCount.takeIf { it > 0 }?.let { "$it photos/documents online" },
                a.recordUrls.size.takeIf { it > 0 }?.let { "$it linked records" },
            )
            Text(meta.joinToString(" · "), style = MaterialTheme.typography.labelMedium)
            if (a.sourceTitles.isNotEmpty()) {
                Text("Record collections", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp))
                a.sourceTitles.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
            a.urls.firstOrNull()?.let { url ->
                Text(
                    "Open profile on Ancestry (needs internet)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp).clickable { runCatching { uri.openUri(url) } },
                )
            }
        }
    }
}

@Composable
fun LabeledValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(120.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
