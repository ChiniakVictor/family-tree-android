package com.victor.familytree.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.victor.familytree.BuildConfig
import com.victor.familytree.data.Confidence
import com.victor.familytree.data.FamilyData

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutScreen(
    data: FamilyData, wide: Boolean, singlePane: Boolean, onSinglePane: (Boolean) -> Unit,
    modifier: Modifier = Modifier, importSection: @Composable () -> Unit = {},
) {
    val s = remember(data) { data.stats() }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Text("Settings & about", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Family Tree ${BuildConfig.VERSION_NAME} · works fully offline", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { SectionTitle("Settings") }
        item {
            Column {
                Text("Layout on the unfolded (inner) screen", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().selectable(!singlePane) { onSinglePane(false) }) {
                    RadioButton(selected = !singlePane, onClick = { onSinglePane(false) })
                    Text("Two panes: list and details side by side", style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().selectable(singlePane) { onSinglePane(true) }) {
                    RadioButton(selected = singlePane, onClick = { onSinglePane(true) })
                    Text("Single pane, like the cover screen", style = MaterialTheme.typography.bodyMedium)
                }
                Text(if (wide) "Applies now." else "The cover screen always uses a single pane; this applies when unfolded.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { SectionTitle("Import & export") }
        item { importSection() }
        item { SectionTitle("Data") }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("People", s.people)
                Stat("Living", s.living)
                Stat("Families", s.families)
                Stat("Events", s.events)
                Stat("Surnames", s.surnames)
                Stat("Direct ancestors", s.directAncestors)
                Stat("Generations", s.generations)
                Stat("Places", s.places)
                Stat("Sources", s.sources)
                Stat("With research data", s.withResearch)
                Stat("Uncertain parent links", s.uncertainLinks)
                Stat("People to check", s.flagged)
            }
        }
        item {
            val range = if (s.earliestYear != null) "${s.earliestYear} – ${s.latestYear}" else "—"
            LabeledValue("Years covered", "$range (deceased people)")
            if (data.duplicateRecords > 0) LabeledValue("Duplicate records", "${data.duplicateRecords} hidden (kept only as pointers to the real person)")
            data.bundle?.bundledAt?.let { LabeledValue("Data bundled", it.take(16).replace('T', ' ')) }
        }
        item { SectionTitle("Top surnames") }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                data.surnameCounts.take(16).forEach { (n, c) -> Tag("$n $c") }
            }
        }
        item { SectionTitle("Confidence badges") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Legend(Confidence.HIGH, "Clearly shown in the source")
                Legend(Confidence.MEDIUM, "Probable; inferred from dates, names or chart layout")
                Legend(Confidence.LOW, "Possible; needs a record to confirm")
                Legend(Confidence.UNKNOWN, "Not rated")
                Text("A warning triangle marks people with conflicting or implausible values, possible duplicates or uncertain links.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        item { SectionTitle("Privacy") }
        item {
            Text("People marked living show only \"Living\". Their dates are never displayed.", style = MaterialTheme.typography.bodyMedium)
        }
        if (data.sources.isNotEmpty()) {
            item { SectionTitle("Sources") }
            data.sources.values.forEach { src ->
                item {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text("${src.id} · ${src.title}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        listOfNotNull(src.date, src.author).joinToString(" · ").takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if (data.notes.isNotEmpty()) {
            item { SectionTitle("Data notes") }
            data.notes.forEach { n -> item { Text(n, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp)) } }
        }
    }
}

@Composable
internal fun Stat(label: String, value: Int) {
    Card(Modifier.widthIn(min = 100.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text("$value", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun Legend(c: Confidence, text: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ConfidenceBadge(c, compact = true)
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}
