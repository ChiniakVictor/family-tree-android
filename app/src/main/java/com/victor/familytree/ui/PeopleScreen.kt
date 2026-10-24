package com.victor.familytree.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.victor.familytree.data.FamilyData
import com.victor.familytree.data.Person

/** Case-insensitive match of every word in [query] against names, aliases, id and (non-living) years. */
fun matches(p: Person, query: String): Boolean {
    val words = query.lowercase().split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return true
    val hay = buildString {
        append(p.displayName); append(' ')
        p.aliases.forEach { append(it).append(' ') }
        p.nicknames.forEach { append(it).append(' ') }
        p.maidenName?.let { append(it).append(' ') }
        p.marriedName?.let { append(it).append(' ') }
        append(p.id).append(' ')
        if (!p.living) append(p.lifespan) else append("living")
    }.lowercase()
    return words.all { it in hay }
}

fun filterPeople(data: FamilyData, query: String, surname: String?): List<Person> =
    data.sortedPeople.filter { p ->
        (surname == null || p.surname?.let(FamilyData::normaliseSurname) == surname) && matches(p, query)
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PeopleScreen(
    data: FamilyData,
    query: String,
    onQuery: (String) -> Unit,
    surname: String?,
    onSurname: (String?) -> Unit,
    selectedId: String?,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val people = remember(data, query, surname) { filterPeople(data, query, surname) }
    val flagged = data.flaggedIds
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
            Text("Family Tree", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "${people.size} of ${data.visiblePeople.size} people",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                placeholder = { Text("Search names, years, IDs") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Clear, "Clear search") }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = MaterialTheme.shapes.extraLarge,
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(selected = surname == null, onClick = { onSurname(null) }, label = { Text("All") })
            }
            items(data.surnameCounts, key = { it.first }) { (name, count) ->
                FilterChip(
                    selected = surname == name,
                    onClick = { onSurname(if (surname == name) null else name) },
                    label = { Text("$name $count") },
                )
            }
        }
        val grouped = remember(people) { people.groupBy { (it.surname ?: "?").take(1).uppercase() } }
        LazyColumn(contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 24.dp)) {
            if (people.isEmpty()) item {
                Text("No one matches.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            grouped.forEach { (letter, group) ->
                stickyHeader(key = "h_$letter") {
                    Text(
                        letter,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
                items(group, key = { it.id }) { p ->
                    PersonRow(
                        p = p,
                        onClick = { onOpen(p.id) },
                        subtitle = p.directAncestor?.relationship,
                        selected = p.id == selectedId,
                        flagged = p.id in flagged,
                    )
                }
            }
        }
    }
}
