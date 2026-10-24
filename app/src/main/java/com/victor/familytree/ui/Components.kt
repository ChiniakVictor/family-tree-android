package com.victor.familytree.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.victor.familytree.data.Confidence
import com.victor.familytree.data.Person

fun sexColor(sex: String?): Color = when (sex) {
    "M" -> FamilyColors.male
    "F" -> FamilyColors.female
    else -> FamilyColors.unknown
}

fun confidenceColor(c: Confidence): Color = when (c) {
    Confidence.HIGH -> FamilyColors.high
    Confidence.MEDIUM -> FamilyColors.medium
    Confidence.LOW -> FamilyColors.low
    Confidence.UNKNOWN -> FamilyColors.unrated
}

/** Friendly name for a data origin tag ("ancestry_gedcom" -> "Ancestry tree"). */
fun originLabel(origin: String): String = when (origin.lowercase()) {
    "ancestry_gedcom", "gedcom" -> "Ancestry tree"
    "pdf", "pdfs" -> "PDF charts"
    else -> origin.replace('_', ' ')
}

fun initials(p: Person): String {
    val g = p.givenNames?.firstOrNull()?.uppercaseChar()
    val s = p.surname?.firstOrNull()?.uppercaseChar()
    return listOfNotNull(g, s).joinToString("").ifEmpty { p.displayName.take(1).uppercase() }
}

@Composable
fun Avatar(p: Person, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(sexColor(p.sex).copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials(p),
            color = sexColor(p.sex),
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.38f).sp,
        )
    }
}

/** Coloured pill; [compact] shows only the level word. */
@Composable
fun ConfidenceBadge(c: Confidence, compact: Boolean = false) {
    val color = confidenceColor(c)
    Text(
        text = if (compact) c.label else "${c.label} confidence",
        color = color,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
fun Tag(text: String, color: Color = MaterialTheme.colorScheme.secondary) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** One row representing a person; used in lists, relatives and descendants. */
@Composable
fun PersonRow(
    p: Person,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    selected: Boolean = false,
    flagged: Boolean = false,
    confidence: Confidence? = null,
    avatarSize: Dp = 40.dp,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(p, avatarSize)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val line = listOfNotNull(p.lifespan.ifBlank { null }, subtitle).joinToString(" · ")
            if (line.isNotBlank()) {
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (confidence != null && confidence != Confidence.HIGH) ConfidenceBadge(confidence, compact = true)
            if (flagged) {
                Icon(Icons.Filled.Warning, contentDescription = "Needs checking", tint = FamilyColors.medium, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(top = 16.dp, bottom = 6.dp),
    )
}

/** "Disputed" pill, shared by the person detail and the ancestor chart. */
@Composable
fun DisputedBadge(text: String = "Disputed") = Tag(text, FamilyColors.low)

/** A doubted relationship: dashed red line on the left, the person, a Disputed badge and why it is doubted. */
@Composable
fun DisputedRelativeRow(r: com.victor.familytree.data.DisputedRelative, showReason: Boolean, sourceTitles: List<String>, onClick: () -> Unit) {
    var expanded by androidx.compose.runtime.saveable.rememberSaveable(r.person.id) { androidx.compose.runtime.mutableStateOf(false) }
    val color = FamilyColors.low
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
        androidx.compose.foundation.Canvas(Modifier.width(14.dp).fillMaxHeight()) {
            val x = size.width / 2
            drawLine(color, androidx.compose.ui.geometry.Offset(x, 4f), androidx.compose.ui.geometry.Offset(x, size.height - 4f),
                strokeWidth = 2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        }
        Column(Modifier.weight(1f)) {
            PersonRow(r.person, onClick = onClick, avatarSize = 36.dp)
            Row(Modifier.padding(start = 4.dp, bottom = 4.dp)) { DisputedBadge() }
            if (showReason && r.reason != null) {
                Text(
                    "Why: ${r.reason}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp).clickable { expanded = !expanded },
                )
                if (!expanded) Text("Show more", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp).clickable { expanded = true })
                if (expanded && sourceTitles.isNotEmpty()) Text("Sources: ${sourceTitles.distinct().joinToString("; ")}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
