package com.victor.familytree.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.victor.familytree.data.FamilyData

@Composable
fun PersonPickerDialog(data: FamilyData, title: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val results = remember(q) { filterPeople(data, q, null).take(200) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = q, onValueChange = { q = it }, singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    placeholder = { Text("Search") },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 420.dp).padding(top = 8.dp)) {
                    items(results, key = { it.id }) { p -> PersonRow(p, onClick = { onPick(p.id) }, avatarSize = 32.dp) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
