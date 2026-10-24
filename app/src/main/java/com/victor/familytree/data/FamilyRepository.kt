package com.victor.familytree.data

import android.content.Context
import com.victor.familytree.data.gedcom.Overlay
import org.json.JSONObject

/** Loads the family data bundled into assets/data at build time (works fully offline), plus the user's imports. */
class FamilyRepository(private val context: Context) {

    private fun read(name: String): String? =
        runCatching { context.assets.open("data/$name").bufferedReader().use { it.readText() } }.getOrNull()

    /** The bundled research data only (used to tell imports apart). */
    fun loadBundled(): FamilyData = load(emptyList())

    fun load(overlays: List<String> = emptyList()): FamilyData {
        val people = read("people.json") ?: error("No family data bundled in this build (assets/data/people.json).")
        val ovs = overlays.mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
        return Overlay.load(people, read("families.json"), read("events.json"), read("sources.json"), read("places.json"),
            read("bundle.json"), ovs).warmUp()
    }
}
