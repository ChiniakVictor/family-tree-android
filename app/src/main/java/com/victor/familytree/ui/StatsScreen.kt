package com.victor.familytree.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.victor.familytree.data.Confidence
import com.victor.familytree.data.DataCheck
import com.victor.familytree.data.FamilyData

/** Everything the Stats tab shows, computed once from the bundled data. Living people never contribute dates. */
data class TreeStats(
    val byCentury: List<Pair<String, Int>>,
    val topSurnames: List<Pair<String, Int>>,
    val sex: List<Pair<String, Int>>,
    val ancestorsByGeneration: List<Pair<Int, Int>>,
    val linkConfidence: List<Pair<Confidence, Int>>,
    val withSources: Int,
    val withoutSources: Int,
    val conflicts: Int,
    val duplicates: Int,
    val warnings: Int,
    val origins: List<Pair<String, Int>>,
    val avgLifespan: Int?,
    val longestLived: Pair<String, Int>?,
)

fun computeTreeStats(data: FamilyData): TreeStats {
    val people = data.visiblePeople
    val dead = people.filterNot { it.living }
    val byCentury = dead.mapNotNull { it.birthYear }.groupingBy { it / 100 }.eachCount()
        .toSortedMap().map { (c, n) -> "${c * 100}s" to n }
    val sex = people.groupingBy {
        when (it.sex?.lowercase()?.take(1)) { "m" -> "Male"; "f" -> "Female"; else -> "Unknown" }
    }.eachCount().entries.sortedByDescending { it.value }.map { it.key to it.value }
    val gens = people.filter { it.directAncestor != null }.mapNotNull { it.generation }
        .groupingBy { kotlin.math.abs(it) }.eachCount().toSortedMap().map { it.key to it.value }
    val conf = people.flatMap { p -> p.parentLinks.map { it.confidence ?: Confidence.UNKNOWN } }
        .groupingBy { it }.eachCount().entries.sortedBy { it.key.ordinal }.map { it.key to it.value }
    val withSrc = people.count { it.sources.isNotEmpty() || listOfNotNull(it.birth, it.death).any { f -> f.sources.isNotEmpty() } }
    val conflicts = people.count { p -> listOfNotNull(p.birth, p.death, p.burial).any { it.hasConflict } || p.disputes.isNotEmpty() }
    val checks = people.map { data.checksFor(it.id) }
    val spans = dead.mapNotNull { p -> val b = p.birthYear; val d = p.deathYear; if (b != null && d != null && d >= b && d - b < 115) p to d - b else null }
    val longest = spans.maxByOrNull { it.second }?.let { it.first.displayName to it.second }
    val origins = people.flatMap { p -> listOfNotNull(p.origin) + listOfNotNull(p.birth?.origin) }
        .groupingBy { originLabel(it) }.eachCount().entries.sortedByDescending { it.value }.take(6).map { it.key to it.value }
    return TreeStats(
        byCentury = byCentury,
        topSurnames = data.surnameCounts.take(12),
        sex = sex,
        ancestorsByGeneration = gens,
        linkConfidence = conf,
        withSources = withSrc,
        withoutSources = people.size - withSrc,
        conflicts = conflicts,
        duplicates = people.count { it.possibleDuplicates.isNotEmpty() },
        warnings = checks.count { l -> l.any { it.severity == DataCheck.Severity.WARNING } },
        origins = origins,
        avgLifespan = spans.map { it.second }.takeIf { it.isNotEmpty() }?.average()?.toInt(),
        longestLived = longest,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsScreen(data: FamilyData, modifier: Modifier = Modifier) {
    val s = remember(data) { data.stats() }
    val t = remember(data) { computeTreeStats(data) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Text("Tree statistics", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("${s.people} people · ${s.families} families · ${s.generations} generations",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { SectionTitle("At a glance") }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BigStat("People", "${s.people}")
                BigStat("Living", "${s.living}")
                BigStat("Direct ancestors", "${s.directAncestors}")
                BigStat("Surnames", "${s.surnames}")
                BigStat("Places", "${s.places}")
                BigStat("Sources", "${s.sources}")
                BigStat("Events", "${s.events}")
                t.avgLifespan?.let { BigStat("Avg lifespan", "$it yrs") }
            }
        }
        t.longestLived?.let { (n, y) -> item { LabeledValue("Longest recorded life", "$n · $y years") } }
        if (s.earliestYear != null) item { LabeledValue("Years covered", "${s.earliestYear} – ${s.latestYear} (deceased people only)") }
        item { SectionTitle("Births by century") }
        item { Bars(t.byCentury.map { it.first to it.second }) }
        item { SectionTitle("Top surnames") }
        item { Bars(t.topSurnames) }
        item { SectionTitle("Direct ancestors per generation") }
        item { Bars(t.ancestorsByGeneration.map { (g, n) -> generationLabel(g) to n }) }
        item { SectionTitle("Sex") }
        item { Bars(t.sex) }
        item { SectionTitle("Research quality") }
        item {
            Bars(t.linkConfidence.map { (c, n) -> "${c.label} parent links" to n }, colorFor = { label ->
                Confidence.entries.firstOrNull { label.startsWith(it.label) }?.let(::confidenceColor)
            })
        }
        item {
            Column(Modifier.padding(top = 8.dp)) {
                LabeledValue("People with sources", "${t.withSources} (${t.withoutSources} without)")
                LabeledValue("Conflicting values", "${t.conflicts} people")
                LabeledValue("Possible duplicates", "${t.duplicates} people")
                LabeledValue("People to check (warnings)", "${t.warnings}")
            }
        }
        if (t.origins.isNotEmpty()) {
            item { SectionTitle("Where the data came from") }
            item { Bars(t.origins) }
        }
        item {
            Text("Living people are counted but never contribute dates to these charts.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp))
        }
    }
}

private fun generationLabel(g: Int): String = when (g) {
    0 -> "You"; 1 -> "Parents"; 2 -> "Grandparents"; 3 -> "Great-grandparents"
    else -> "${g - 2}× great-grandparents"
}

@Composable
private fun BigStat(label: String, value: String) {
    Card(Modifier.widthIn(min = 96.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun Bars(rows: List<Pair<String, Int>>, colorFor: (String) -> Color? = { null }) {
    if (rows.isEmpty()) { Text("No data", style = MaterialTheme.typography.bodySmall); return }
    val max = rows.maxOf { it.second }.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { (label, n) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.width(132.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.weight(1f).height(16.dp)) {
                    Box(
                        Modifier.fillMaxHeight().fillMaxWidth(n.toFloat() / max)
                            .background(colorFor(label) ?: MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                    )
                }
                Text("$n", Modifier.width(44.dp).padding(start = 6.dp), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
