package com.victor.familytree.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import android.content.Context
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.victor.familytree.data.FamilyData

enum class Tab(val label: String, val icon: ImageVector) {
    PEOPLE("People", Icons.Filled.People),
    TREE("Ancestors", Icons.Filled.AccountTree),
    DESCENDANTS("Descendants", Icons.Filled.FamilyRestroom),
    STATS("Stats", Icons.Filled.BarChart),
    ABOUT("Settings", Icons.Filled.Settings),
}

/** Optional start state (used by test hooks / deep links). */
data class StartArgs(val tab: Tab? = null, val personId: String? = null, val rootId: String? = null, val query: String? = null, val singlePane: Boolean? = null)

@Composable
fun FamilyApp(vm: FamilyViewModel, widthClass: WindowWidthSizeClass, start: StartArgs) {
    val state by vm.state.collectAsStateWithLifecycle()
    when (val s = state) {
        LoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is LoadState.Failed -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Couldn't load the family data: ${s.message}")
        }
        is LoadState.Ready -> {
            val context = LocalContext.current
            val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
            var singlePane by remember {
                mutableStateOf(start.singlePane?.also { prefs.edit().putBoolean("inner_single_pane", it).apply() }
                    ?: prefs.getBoolean("inner_single_pane", false))
            }
            val wide = widthClass != WindowWidthSizeClass.Compact
            FamilyContent(s.data, wide && !singlePane, wide, start, singlePane, { ImportExportSection(vm) }) {
                singlePane = it; prefs.edit().putBoolean("inner_single_pane", it).apply()
            }
            ImportReviewDialog(vm, s.data)
            val msg by vm.message.collectAsState()
            LaunchedEffect(msg) {
                msg?.let { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show(); vm.messageShown() }
            }
        }
    }
}

@Composable
private fun FamilyContent(
    data: FamilyData, twoPane: Boolean, wide: Boolean, start: StartArgs,
    singlePane: Boolean, importSection: @Composable () -> Unit, onSinglePane: (Boolean) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(start.tab ?: Tab.PEOPLE) }
    var query by rememberSaveable { mutableStateOf(start.query ?: "") }
    var surname by rememberSaveable { mutableStateOf<String?>(null) }
    val fallbackRoot = data.defaultRootId ?: ""
    var treeRoot by rememberSaveable { mutableStateOf(start.rootId?.takeIf { data.person(it) != null } ?: fallbackRoot) }
    var descRoot by rememberSaveable {
        mutableStateOf(start.rootId?.takeIf { data.person(it) != null } ?: data.defaultDescendantRootId ?: fallbackRoot)
    }
    var generations by rememberSaveable { mutableIntStateOf(5) }
    // Detail back stack (person ids). In two-pane mode the top is shown beside the list.
    val stack = remember { mutableStateListOf<String>().apply { start.personId?.takeIf { data.person(it) != null }?.let(::add) } }
    val selected = stack.lastOrNull()

    fun open(id: String, fromList: Boolean = false) {
        if (twoPane && fromList) stack.clear()
        if (stack.lastOrNull() != id) stack.add(id)
    }
    fun showTree(id: String) { treeRoot = id; tab = Tab.TREE; if (!twoPane) stack.clear() }
    fun showDesc(id: String) { descRoot = id; tab = Tab.DESCENDANTS; if (!twoPane) stack.clear() }

    BackHandler(enabled = stack.isNotEmpty() && (!twoPane || stack.size > 1)) { stack.removeAt(stack.lastIndex) }

    val detail: @Composable (Modifier, Boolean) -> Unit = { mod, showBack ->
        if (selected != null) {
            PersonDetailScreen(
                data, selected, onOpen = { open(it) },
                onBack = if (showBack) ({ stack.removeAt(stack.lastIndex) }) else null,
                onShowTree = ::showTree, onShowDescendants = ::showDesc, modifier = mod,
            )
        } else {
            Box(mod.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Select a person", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    val main: @Composable (Modifier) -> Unit = { mod ->
        when (tab) {
            Tab.PEOPLE -> PeopleScreen(data, query, { query = it }, surname, { surname = it }, selected, { open(it, fromList = true) }, mod)
            Tab.TREE -> PedigreeScreen(data, treeRoot, generations, { generations = it }, selected, { open(it, fromList = true) }, { treeRoot = it }, mod)
            Tab.DESCENDANTS -> DescendantsScreen(data, descRoot, selected, { open(it, fromList = true) }, { descRoot = it }, mod)
            Tab.STATS -> StatsScreen(data, mod)
            Tab.ABOUT -> AboutScreen(data, wide, singlePane, onSinglePane, mod, importSection)
        }
    }

    if (twoPane) {
        Row(Modifier.fillMaxSize()) {
            NavigationRail(Modifier.fillMaxHeight(), windowInsets = WindowInsets(0)) {
                Tab.entries.forEach { t ->
                    NavigationRailItem(selected = tab == t, onClick = { tab = t }, icon = { Icon(t.icon, null) }, label = { Text(t.label) })
                }
            }
            if (tab == Tab.ABOUT || tab == Tab.STATS) {
                main(Modifier.weight(1f))
            } else {
                val mainWeight = if (tab == Tab.TREE) 0.58f else 0.46f
                main(Modifier.weight(mainWeight))
                VerticalDivider()
                Column(Modifier.weight(1f - mainWeight)) {
                    detail(Modifier, stack.size > 1)
                }
            }
        }
    } else {
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                if (stack.isEmpty()) NavigationBar(windowInsets = WindowInsets(0)) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t, onClick = { tab = t }, icon = { Icon(t.icon, null) },
                            label = { Text(if (!wide && t == Tab.DESCENDANTS) "Descend." else t.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false) },
                        )
                    }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad)) {
                if (stack.isNotEmpty()) detail(Modifier, true) else main(Modifier)
            }
        }
    }
}
