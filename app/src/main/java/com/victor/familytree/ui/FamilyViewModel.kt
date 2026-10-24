package com.victor.familytree.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.victor.familytree.BuildConfig
import com.victor.familytree.data.FamilyData
import com.victor.familytree.data.FamilyRepository
import com.victor.familytree.data.ImportBatch
import com.victor.familytree.data.TreeDatabase
import com.victor.familytree.data.gedcom.Decision
import com.victor.familytree.data.gedcom.Gedcom
import com.victor.familytree.data.gedcom.GedcomExport
import com.victor.familytree.data.gedcom.GedcomFile
import com.victor.familytree.data.gedcom.ImportPlanner
import com.victor.familytree.data.gedcom.ImportPreview
import com.victor.familytree.data.gedcom.Matcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

sealed interface LoadState {
    data object Loading : LoadState
    data class Ready(val data: FamilyData) : LoadState
    data class Failed(val message: String) : LoadState
}

/** The GEDCOM import flow: reading -> review (preview + decisions) -> committing. */
sealed interface ImportUi {
    data class Working(val message: String) : ImportUi
    data class Review(
        val preview: ImportPreview,
        val decisions: Map<String, Decision>,
        /** Uncertain records the user has explicitly decided. */
        val reviewed: Set<String>,
    ) : ImportUi
    data class Failed(val message: String) : ImportUi
}

class FamilyViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow<LoadState>(LoadState.Loading)
    val state: StateFlow<LoadState> = _state

    private val db = TreeDatabase.get(app)
    private val repo = FamilyRepository(app)

    val imports: StateFlow<List<ImportBatch>> = db.imports().summaries()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _importUi = MutableStateFlow<ImportUi?>(null)
    val importUi: StateFlow<ImportUi?> = _importUi

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    fun messageShown() { _message.value = null }

    private var pending: GedcomFile? = null

    init {
        viewModelScope.launch {
            _state.value = try {
                db.imports().deleteUnfinished()
                LoadState.Ready(loadMerged())
            } catch (e: Exception) {
                LoadState.Failed(e.message ?: e.toString())
            }
        }
    }

    private suspend fun loadMerged(): FamilyData = withContext(Dispatchers.Default) {
        repo.load(db.imports().all().map { it.overlay })
    }

    private suspend fun reload() {
        runCatching { loadMerged() }.onSuccess { _state.value = LoadState.Ready(it) }
            .onFailure { _message.value = "Couldn't reload the tree: ${it.message}" }
    }

    private fun current(): FamilyData? = (_state.value as? LoadState.Ready)?.data

    private fun displayName(uri: Uri): String {
        val r = getApplication<Application>().contentResolver
        return runCatching {
            r.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file.ged"
    }

    fun startImport(uri: Uri) {
        val data = current() ?: return
        viewModelScope.launch {
            _importUi.value = ImportUi.Working("Reading the file…")
            try {
                val name = displayName(uri)
                val bytes = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: error("Couldn't open $name")
                val g = withContext(Dispatchers.Default) { Gedcom.parse(bytes) }
                if (g.individuals.isEmpty()) error("$name has no people (INDI records). Is it a GEDCOM file?")
                _importUi.value = ImportUi.Working("Comparing ${g.individuals.size} people with your tree…")
                val (matches, decisions) = withContext(Dispatchers.Default) {
                    val m = Matcher(data).match(g); m to ImportPlanner.defaultDecisions(m)
                }
                val preview = withContext(Dispatchers.Default) { ImportPlanner.preview(data, g, name, matches, decisions) }
                pending = g
                _importUi.value = ImportUi.Review(preview, decisions, emptySet())
            } catch (e: Exception) {
                _importUi.value = ImportUi.Failed(e.message ?: e.toString())
            }
        }
    }

    fun decide(xref: String, decision: Decision) {
        val ui = _importUi.value as? ImportUi.Review ?: return
        val data = current() ?: return
        val dec = ui.decisions.toMutableMap().apply { put(xref, decision) }
        _importUi.value = ui.copy(decisions = dec, reviewed = ui.reviewed + xref)
        viewModelScope.launch {
            val p = withContext(Dispatchers.Default) { ImportPlanner.preview(data, ui.preview.gedcom, ui.preview.fileName, ui.preview.matches, dec) }
            (_importUi.value as? ImportUi.Review)?.takeIf { it.decisions == dec }?.let { _importUi.value = it.copy(preview = p) }
        }
    }

    fun cancelImport() { _importUi.value = null; pending = null }

    fun commitImport() {
        val ui = _importUi.value as? ImportUi.Review ?: return
        val data = current() ?: return
        val g = pending ?: return
        viewModelScope.launch {
            _importUi.value = ImportUi.Working("Importing…")
            try {
                val p = ui.preview
                val dao = db.imports()
                val id = dao.insert(ImportBatch(fileName = p.fileName, importedAt = System.currentTimeMillis(), gedcomVersion = g.version,
                    newPeople = 0, matchedPeople = 0, newFamilies = 0, alternates = 0, overlay = ""))
                val ov = withContext(Dispatchers.Default) { ImportPlanner.build(data, g, p.fileName, ui.decisions, id, LocalDate.now()) }
                dao.finish(id, ov.toString(), p.newCount, p.matchedCount, p.newFamilies, p.conflicts.size)
                reload()
                _importUi.value = null; pending = null
                _message.value = "Imported ${p.fileName}: ${p.newCount} new, ${p.matchedCount} matched, ${p.conflicts.size} alternate values"
            } catch (e: Exception) {
                _importUi.value = ImportUi.Failed("Import failed: ${e.message}")
            }
        }
    }

    fun removeImport(b: ImportBatch) {
        viewModelScope.launch {
            db.imports().delete(b.id)
            reload()
            _message.value = "Removed the import of ${b.fileName}"
        }
    }

    fun exportTo(uri: Uri) {
        val data = current() ?: return
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.Default) { GedcomExport.export(data, BuildConfig.VERSION_NAME) }
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                        ?: error("Couldn't write the file")
                }
                _message.value = "Exported ${data.visiblePeople.size} people and ${data.families.size} families"
            } catch (e: Exception) {
                _message.value = "Export failed: ${e.message}"
            }
        }
    }
}
