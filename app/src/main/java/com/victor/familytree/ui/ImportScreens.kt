package com.victor.familytree.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.victor.familytree.data.FamilyData
import com.victor.familytree.data.ImportBatch
import com.victor.familytree.data.gedcom.Decision
import com.victor.familytree.data.gedcom.GedIndi
import com.victor.familytree.data.gedcom.GedcomFile
import com.victor.familytree.data.gedcom.IndiMatch
import com.victor.familytree.data.gedcom.MatchStatus
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date

/** Settings section: Import GEDCOM, Export GEDCOM, and the list of past imports (each can be removed). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportExportSection(vm: FamilyViewModel) {
    val imports by vm.imports.collectAsState()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::startImport) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-gedcom")) { uri -> uri?.let(vm::exportTo) }
    var confirm by remember { mutableStateOf<ImportBatch?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Add people from a GEDCOM file (Ancestry, FamilySearch, MyHeritage…). Matches are merged, " +
            "differences are kept as alternates, and every import can be removed again.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { pick.launch(arrayOf("application/x-gedcom", "text/x-gedcom", "text/plain", "application/octet-stream", "*/*")) }) {
                Icon(Icons.Filled.FileDownload, null); Spacer(Modifier.width(6.dp)); Text("Import GEDCOM")
            }
            OutlinedButton(onClick = { save.launch("family-tree-${LocalDate.now()}.ged") }) {
                Icon(Icons.Filled.FileUpload, null); Spacer(Modifier.width(6.dp)); Text("Export GEDCOM", maxLines = 1, softWrap = false)
            }
        }
        if (imports.isEmpty()) {
            Text("No imports yet.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("Imports", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            imports.forEach { b ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(b.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(b.importedAt))} · " +
                                "${b.newPeople} new · ${b.matchedPeople} matched · ${b.alternates} alternates",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { confirm = b }) { Text("Remove this import") }
                    }
                }
            }
        }
    }
    confirm?.let { b ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Remove this import?") },
            text = { Text("This removes the ${b.newPeople} people, families and alternate values added from ${b.fileName}. " +
                "The research data and your other imports stay as they are.") },
            confirmButton = { TextButton(onClick = { vm.removeImport(b); confirm = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

/** Full-screen review of a pending import. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportReviewDialog(vm: FamilyViewModel, data: FamilyData) {
    val ui by vm.importUi.collectAsState()
    val state = ui ?: return
    Dialog(onDismissRequest = vm::cancelImport, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          Box(Modifier.fillMaxSize().systemBarsPadding()) {
            when (state) {
                is ImportUi.Working -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(); Spacer(Modifier.height(12.dp)); Text(state.message)
                    }
                }
                is ImportUi.Failed -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.message); Spacer(Modifier.height(12.dp))
                        Button(onClick = vm::cancelImport) { Text("Close") }
                    }
                }
                is ImportUi.Review -> ReviewContent(vm, data, state)
            }
          }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewContent(vm: FamilyViewModel, data: FamilyData, r: ImportUi.Review) {
    val p = r.preview
    val g = p.gedcom
    val uncertain = p.matches.filter { it.status == MatchStatus.UNCERTAIN }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("Import ${p.fileName}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("GEDCOM ${g.version ?: "?"} · ${g.charset} · ${g.individuals.size} people · ${g.families.size} families · " +
                    "${g.sources.size} sources · ${g.objects.size} media · ${g.notes.size} notes",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Nothing changes until you tap Import.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("New people", p.newCount)
                    Stat("Matched", p.matchedCount)
                    Stat("Uncertain", p.uncertainCount)
                    Stat("Alternate values", p.conflicts.size)
                    Stat("New families", p.newFamilies)
                    Stat("New living (private)", p.newLiving)
                }
            }
            item {
                Text("Values that differ from your tree are kept as alternates with the source " +
                    "“Imported from ${p.fileName} ${LocalDate.now()}”; nothing is overwritten. Living people get no dates.",
                    style = MaterialTheme.typography.bodySmall)
            }
            if (uncertain.isNotEmpty()) {
                item {
                    SectionTitle("Needs review (${r.reviewed.count { x -> uncertain.any { it.xref == x } }} of ${uncertain.size} decided)")
                    Text("Undecided records are added as separate people.", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(uncertain, key = { it.xref }) { m -> CompareCard(vm, data, g, m, r.decisions[m.xref], m.xref in r.reviewed) }
            }
            if (p.conflicts.isNotEmpty()) {
                item { SectionTitle("Alternate values (${p.conflicts.size})") }
                items(p.conflicts.take(200)) { c ->
                    Column {
                        Text("${c.personName} · ${c.field}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("Tree: ${c.existing}", style = MaterialTheme.typography.bodySmall)
                        Text("File: ${c.imported}  (kept as alternate)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
            val newOnes = p.matches.filter { r.decisions[it.xref] !is Decision.MergeInto }
            if (newOnes.isNotEmpty()) {
                item { SectionTitle("New people (${newOnes.size})") }
                items(newOnes.take(300), key = { "n" + it.xref }) { m ->
                    val i = g.individuals.getValue(m.xref)
                    Text("${i.fullName}  ${lifespanOf(i, data)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            TextButton(onClick = vm::cancelImport) { Text("Cancel") }
            Button(onClick = vm::commitImport) { Text("Import") }
        }
    }
}

private fun lifespanOf(i: GedIndi, data: FamilyData): String {
    if (i.markedLiving || (!i.isDeceased && (i.birthYear ?: 0) >= LocalDate.now().year - 100)) return "Living"
    val b = i.birthYear; val d = i.deathYear
    return when { b == null && d == null -> ""; d == null -> "b. $b"; b == null -> "d. $d"; else -> "$b – $d" }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompareCard(vm: FamilyViewModel, data: FamilyData, g: GedcomFile, m: IndiMatch, decision: Decision?, decided: Boolean) {
    val i = g.individuals.getValue(m.xref)
    var candIdx by remember(m.xref) { mutableStateOf(0) }
    val cand = m.candidates.getOrNull(candIdx)
    val person = cand?.let { data.person(it.personId) }
    val mergedInto = (decision as? Decision.MergeInto)?.personId
    Card(border = if (decided) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (m.candidates.size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    m.candidates.forEachIndexed { k, c ->
                        FilterChip(selected = k == candIdx, onClick = { candIdx = k },
                            label = { Text("${data.person(c.personId)?.displayName ?: c.personId} (${c.score})", maxLines = 1) })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("In the file", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(i.fullName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    val live = lifespanOf(i, data) == "Living"
                    Text(lifespanOf(i, data), style = MaterialTheme.typography.bodySmall)
                    if (!live) {
                        i.birth?.place?.let { Text("b. $it", style = MaterialTheme.typography.bodySmall) }
                        i.death?.place?.let { Text("d. $it", style = MaterialTheme.typography.bodySmall) }
                    }
                    rel("Parents", g.parentsOf(i.xref).map { it.fullName })
                    rel("Spouses", g.spousesOf(i.xref).map { it.fullName })
                    rel("Children", g.childrenOf(i.xref).map { it.fullName })
                }
                Column(Modifier.weight(1f)) {
                    Text("In your tree", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (person == null) Text("—") else {
                        Text(person.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(person.lifespan, style = MaterialTheme.typography.bodySmall)
                        if (!person.living) {
                            person.birth?.place?.let { Text("b. $it", style = MaterialTheme.typography.bodySmall) }
                            person.death?.place?.let { Text("d. $it", style = MaterialTheme.typography.bodySmall) }
                        }
                        rel("Parents", data.parentsOf(person.id).map { it.person.displayName })
                        rel("Spouses", data.spousesOf(person.id).map { it.person.displayName })
                        rel("Children", data.childrenOf(person.id).map { it.person.displayName })
                    }
                }
            }
            cand?.let { Text("Score ${it.score}: ${it.reasons.joinToString(", ")}", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val isMerged = mergedInto != null && mergedInto == person?.id
                if (isMerged) Button(onClick = {}) { Text("Merged") }
                else FilledTonalButton(onClick = { person?.let { vm.decide(m.xref, Decision.MergeInto(it.id)) } }, enabled = person != null) { Text("Merge") }
                val sep = decided && decision is Decision.KeepSeparate
                if (sep) Button(onClick = {}) { Text("Kept separate") }
                else OutlinedButton(onClick = { vm.decide(m.xref, Decision.KeepSeparate) }) { Text("Keep separate") }
            }
        }
    }
}

@Composable
private fun rel(label: String, names: List<String>) {
    if (names.isEmpty()) return
    Text("$label: ${names.distinct().take(4).joinToString(", ")}${if (names.distinct().size > 4) "…" else ""}",
        style = MaterialTheme.typography.labelSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
}
