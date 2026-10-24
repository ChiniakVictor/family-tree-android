package com.victor.familytree.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * The on-device, editable layer of the tree. The bundled research data (assets, refreshed with each app update) is
 * the base; every GEDCOM import is stored here as one batch whose overlay (new people, new families, values added to
 * existing people) is re-applied on top of the base at load time. Removing a batch undoes that import exactly.
 */
@Entity(tableName = "import_batch")
data class ImportBatch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val importedAt: Long,
    val gedcomVersion: String?,
    val newPeople: Int,
    val matchedPeople: Int,
    val newFamilies: Int,
    val alternates: Int,
    /** JSON overlay, see ImportPlanner.build. Empty while the batch is being written. */
    val overlay: String,
)

@Dao
interface ImportDao {
    @Query("SELECT * FROM import_batch WHERE overlay != '' ORDER BY id")
    suspend fun all(): List<ImportBatch>

    @Query("SELECT id, fileName, importedAt, gedcomVersion, newPeople, matchedPeople, newFamilies, alternates, '' AS overlay FROM import_batch WHERE overlay != '' ORDER BY id DESC")
    fun summaries(): Flow<List<ImportBatch>>

    @Insert
    suspend fun insert(b: ImportBatch): Long

    @Query("UPDATE import_batch SET overlay = :overlay, newPeople = :newPeople, matchedPeople = :matched, newFamilies = :fams, alternates = :alts WHERE id = :id")
    suspend fun finish(id: Long, overlay: String, newPeople: Int, matched: Int, fams: Int, alts: Int)

    @Query("DELETE FROM import_batch WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM import_batch WHERE overlay = ''")
    suspend fun deleteUnfinished()
}

@Database(entities = [ImportBatch::class], version = 1, exportSchema = false)
abstract class TreeDatabase : RoomDatabase() {
    abstract fun imports(): ImportDao

    companion object {
        @Volatile private var instance: TreeDatabase? = null
        fun get(context: Context): TreeDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, TreeDatabase::class.java, "family-tree.db").build().also { instance = it }
        }
    }
}
