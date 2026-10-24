package com.victor.familytree.data.gedcom

import com.victor.familytree.data.FamilyData
import com.victor.familytree.data.FamilyParser
import com.victor.familytree.data.Person
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.Year

/** The user's choice for one imported record. */
sealed interface Decision {
    data class MergeInto(val personId: String) : Decision
    data object KeepSeparate : Decision
}

data class Conflict(val personId: String, val personName: String, val field: String, val existing: String, val imported: String)

data class ImportPreview(
    val fileName: String,
    val gedcom: GedcomFile,
    val matches: List<IndiMatch>,
    val newCount: Int,
    val matchedCount: Int,
    val uncertainCount: Int,
    val conflicts: List<Conflict>,
    val newFamilies: Int,
    val newLiving: Int,
)

internal fun JSONObject.s(k: String): String? = if (isNull(k)) null else optString(k).trim().takeIf { it.isNotEmpty() && it != "null" }

object ImportPlanner {
    private val FACTS = listOf("birth", "death", "burial")

    fun sourceTitle(fileName: String, date: LocalDate) = "Imported from $fileName $date"

    /** Default decisions: clear matches merge, everything else (incl. uncertain until the user decides) stays separate. */
    fun defaultDecisions(matches: List<IndiMatch>): MutableMap<String, Decision> =
        matches.associate { m ->
            m.xref to (if (m.status == MatchStatus.MATCHED) Decision.MergeInto(m.best!!.personId) else Decision.KeepSeparate)
        }.toMutableMap()

    fun preview(data: FamilyData, g: GedcomFile, fileName: String, matches: List<IndiMatch>, decisions: Map<String, Decision>): ImportPreview {
        val overlay = build(data, g, fileName, decisions, batchId = 0, date = LocalDate.now())
        val conflicts = mutableListOf<Conflict>()
        val patches = overlay.optJSONObject("person_patches") ?: JSONObject()
        for (pid in patches.keys()) {
            val p = data.person(pid) ?: continue
            val vals = patches.getJSONObject(pid).optJSONObject("values") ?: continue
            for (f in FACTS) {
                val iv = vals.optJSONObject(f) ?: continue
                val ex = factOf(p, f) ?: continue
                val d = iv.s("date"); val pl = iv.s("place")
                val fact = when (f) { "birth" -> p.birth; "death" -> p.death; else -> p.burial }
                val already = yearOf(d) != null && fact?.alternativeYears?.contains(yearOf(d)) == true
                if (!already && differs(ex.first, ex.second, d, pl)) {
                    conflicts += Conflict(pid, p.displayName, f, listOfNotNull(ex.first, ex.second).joinToString(", "),
                        listOfNotNull(d, pl).joinToString(", "))
                }
            }
        }
        val people = overlay.getJSONArray("people")
        return ImportPreview(
            fileName = fileName, gedcom = g, matches = matches,
            newCount = people.length(),
            matchedCount = decisions.values.count { it is Decision.MergeInto },
            uncertainCount = matches.count { it.status == MatchStatus.UNCERTAIN },
            conflicts = conflicts,
            newFamilies = overlay.getJSONArray("families").length(),
            newLiving = (0 until people.length()).count { people.getJSONObject(it).optBoolean("living") },
        )
    }

    private fun factOf(p: Person, f: String): Pair<String?, String?>? = when (f) {
        "birth" -> p.birth; "death" -> p.death; else -> p.burial
    }?.takeUnless { it.isEmpty }?.let { it.date to it.place }

    private val PLACE_NOISE = setOf("usa", "us", "united", "states", "of", "the", "america", "colonial", "county", "co",
        "co.", "twp", "township", "province", "kingdom", "uk", "england", "deutschland", "germany")

    private val STATES = mapOf("al" to "alabama", "ak" to "alaska", "az" to "arizona", "ar" to "arkansas", "ca" to "california",
        "co" to "colorado", "ct" to "connecticut", "de" to "delaware", "fl" to "florida", "ga" to "georgia", "hi" to "hawaii",
        "id" to "idaho", "il" to "illinois", "in" to "indiana", "ia" to "iowa", "ks" to "kansas", "ky" to "kentucky",
        "la" to "louisiana", "me" to "maine", "md" to "maryland", "ma" to "massachusetts", "mi" to "michigan", "mn" to "minnesota",
        "ms" to "mississippi", "mo" to "missouri", "mt" to "montana", "ne" to "nebraska", "nv" to "nevada", "nh" to "new hampshire",
        "nj" to "new jersey", "nm" to "new mexico", "ny" to "new york", "nc" to "north carolina", "nd" to "north dakota",
        "oh" to "ohio", "ok" to "oklahoma", "or" to "oregon", "pa" to "pennsylvania", "ri" to "rhode island", "sc" to "south carolina",
        "sd" to "south dakota", "tn" to "tennessee", "tx" to "texas", "ut" to "utah", "vt" to "vermont", "va" to "virginia",
        "wa" to "washington", "wv" to "west virginia", "wi" to "wisconsin", "wy" to "wyoming")

    private fun placeTokens(p: String?): Set<String> {
        // "N.D." / "U.S.A." -> "nd" / "usa" before splitting, then expand state codes
        val raw = (p ?: "").replace(Regex("\\b([A-Za-z])\\.(?=[A-Za-z]\\.)"), "$1").replace(Regex("\\b([A-Za-z])\\.([A-Za-z])\\.?"), "$1$2")
        return Matcher.norm(raw).split(' ').flatMap { t -> STATES[t]?.split(' ') ?: listOf(t) }
            .filter { it.length > 1 && it !in PLACE_NOISE }.toSet()
    }

    /** True when two values really disagree: different years, or places that aren't one inside the other
     *  ("Peoria, Illinois" vs "Peoria, Peoria, Illinois, USA" agree; country spellings are ignored). */
    fun differs(exDate: String?, exPlace: String?, date: String?, place: String?): Boolean {
        val dy = yearOf(date); val ey = yearOf(exDate)
        if (dy != null && ey != null && dy != ey) return true
        val a = placeTokens(place); val b = placeTokens(exPlace)
        return a.isNotEmpty() && b.isNotEmpty() && !a.containsAll(b) && !b.containsAll(a)
    }

    /** Builds the overlay (stored per import batch). Values are kept neutral and reconciled at load time, so a later
     *  research update to the bundled data is merged with the import instead of being overwritten by it. */
    fun build(data: FamilyData, g: GedcomFile, fileName: String, decisions: Map<String, Decision>, batchId: Long, date: LocalDate): JSONObject {
        val srcId = "IMP-$batchId"
        val title = sourceTitle(fileName, date)
        val idFor = mutableMapOf<String, String>()
        for (i in g.individuals.values) {
            idFor[i.xref] = when (val d = decisions[i.xref]) {
                is Decision.MergeInto -> d.personId
                else -> "G${batchId}_" + i.xref.trim('@')
            }
        }
        val isNew = { x: String -> decisions[x] !is Decision.MergeInto }
        val thisYear = Year.now().value

        // Living guess for new people: flagged by the file, or no death/burial and born < 100 years ago, or no dates
        // at all with a parent who is living or born within the last 100 years.
        val livingCache = mutableMapOf<String, Boolean>()
        fun living(i: GedIndi, depth: Int = 0): Boolean = livingCache.getOrPut(i.xref) {
            if (i.markedLiving) return@getOrPut true
            if (i.isDeceased) return@getOrPut false
            val by = i.birthYear
            if (by != null) return@getOrPut by >= thisYear - 100
            if (depth > 3) return@getOrPut false
            g.parentsOf(i.xref).any { par ->
                val existing = if (!isNew(par.xref)) data.person(idFor[par.xref]) else null
                if (existing != null) existing.living || (existing.birthYear ?: 0) >= thisYear - 100
                else living(par, depth + 1) || (par.birthYear ?: 0) >= thisYear - 100
            }
        }

        fun srcRef(note: String) = JSONObject().put("source", srcId).put("note", note)
        fun factJson(e: GedEvent?): JSONObject? {
            if (e == null || (e.date == null && e.place == null)) return null
            return JSONObject().put("date", e.date ?: JSONObject.NULL).put("place", e.place ?: JSONObject.NULL)
                .put("origin", "import").put("sources", JSONArray().put(srcRef(title)))
        }

        val people = JSONArray()
        val patches = JSONObject()
        for (i in g.individuals.values) {
            val id = idFor.getValue(i.xref)
            val recNote = "GEDCOM record ${i.xref} in $fileName"
            if (isNew(i.xref)) {
                val live = living(i)
                val o = JSONObject()
                    .put("id", id).put("display_name", i.fullName).put("given_names", i.given ?: JSONObject.NULL)
                    .put("surname", i.surname ?: JSONObject.NULL).put("full_name", i.fullName)
                    .put("sex", i.sex ?: JSONObject.NULL).put("living", live)
                    .put("aliases", JSONArray(i.altNames)).put("import_xrefs", JSONArray().put(i.xref))
                    .put("import_batch", batchId).put("origin", "import")
                    .put("sources", JSONArray().put(srcRef(recNote)))
                    .put("parents", JSONArray()).put("spouses", JSONArray()).put("children", JSONArray())
                if (!live) {   // privacy: no dates or events are even stored for living people
                    factJson(i.birth)?.let { o.put("birth", it) }
                    factJson(i.death)?.let { o.put("death", it) }
                    factJson(i.burial)?.let { o.put("burial", it) }
                    if (i.otherEvents.isNotEmpty()) o.put("imported_events", JSONArray(i.otherEvents.map { (t, e) ->
                        listOfNotNull(t, e.date, e.place).joinToString(" · ") }))
                    if (i.notes.isNotEmpty()) o.put("notes", JSONArray(i.notes))
                }
                people.put(o)
            } else {
                val existing = data.person(id)
                val vals = JSONObject()
                if (existing?.living != true) {
                    for ((k, e) in listOf("birth" to i.birth, "death" to i.death, "burial" to i.burial)) {
                        if (e != null && (e.date != null || e.place != null)) vals.put(k, JSONObject().put("date", e.date ?: JSONObject.NULL).put("place", e.place ?: JSONObject.NULL))
                    }
                }
                val names = (listOf(i.fullName) + i.altNames).filter { n -> existing == null ||
                    (Matcher.norm(n) != Matcher.norm(existing.displayName) && existing.aliases.none { Matcher.norm(it) == Matcher.norm(n) }) }
                patches.put(id, JSONObject().put("values", vals).put("aliases", JSONArray(names.distinct()))
                    .put("import_xrefs", JSONArray().put(i.xref)).put("source", srcRef(recNote)).put("title", title))
            }
        }

        // Families: new ones whenever they bring a new person or a link the tree doesn't have yet;
        // children added to an existing couple's family.
        val families = JSONArray()
        val famPatches = JSONObject()
        for (f in g.families.values) {
            val p1 = f.husband?.let(idFor::get); val p2 = f.wife?.let(idFor::get)
            val kids = f.children.mapNotNull(idFor::get)
            val members = listOfNotNull(p1, p2) + kids
            if (members.size < 2) continue
            val anyNew = listOfNotNull(f.husband, f.wife).plus(f.children).any { isNew(it) }
            val existingFam = data.families.values.firstOrNull { ef ->
                val ps = setOfNotNull(ef.parent1, ef.parent2)
                ps.isNotEmpty() && ps == setOfNotNull(p1, p2)
            }
            if (existingFam != null) {
                val have = existingFam.children.map { it.id }.toSet()
                val add = kids.filter { it !in have }
                if (add.isNotEmpty()) famPatches.put(existingFam.id, JSONObject().put("children",
                    JSONArray(add.map { JSONObject().put("id", it).put("origin", "import") })))
                continue
            }
            // all members already in the tree and already related as parent/child or spouses -> nothing new
            val known = !anyNew && run {
                val rel = members.all { m -> data.person(m) != null }
                rel && kids.all { k -> data.parentsOf(k).map { it.person.id }.containsAll(listOfNotNull(p1, p2)) } &&
                    (p1 == null || p2 == null || data.spousesOf(p1).any { it.person.id == p2 })
            }
            if (known) continue
            val liveCouple = listOfNotNull(f.husband, f.wife).any { x -> g.individuals[x]?.let { if (isNew(x)) living(it) else data.person(idFor[x])?.living == true } == true }
            val fo = JSONObject().put("id", "GF${batchId}_" + f.xref.trim('@'))
                .put("parent1", p1 ?: JSONObject.NULL).put("parent2", p2 ?: JSONObject.NULL)
                .put("children", JSONArray(kids.map { JSONObject().put("id", it).put("origin", "import") }))
                .put("origin", "import").put("sources", JSONArray().put(srcRef("GEDCOM family ${f.xref}")))
            if (!liveCouple) factJson(f.marriage)?.let { fo.put("marriage", it) }
            families.put(fo)
        }

        val source = JSONObject().put("id", srcId).put("title", title).put("type", "GEDCOM import")
            .put("date", date.toString()).put("author", g.sourceSystem ?: JSONObject.NULL)
            .put("summary", "GEDCOM ${g.version ?: "?"} (${g.charset}): ${g.individuals.size} people, ${g.families.size} families, " +
                "${g.sources.size} sources, ${g.objects.size} media records")
        val gedSources = JSONArray(g.sources.values.map { s ->
            JSONObject().put("id", "$srcId:${s.xref.trim('@')}").put("title", s.title ?: s.xref)
                .put("author", s.author ?: JSONObject.NULL).put("summary", s.publication ?: JSONObject.NULL).put("type", "From $fileName")
        })
        return JSONObject()
            .put("batch", batchId).put("file", fileName).put("imported_at", date.toString())
            .put("source", source).put("gedcom_sources", gedSources)
            .put("people", people).put("families", families)
            .put("person_patches", patches).put("family_patches", famPatches)
    }
}

/** Merges stored import overlays into the bundled JSON before parsing. Bundled data always comes from the current
 *  app's assets, so research updates arrive with app updates while user imports are re-applied on top. */
object Overlay {
    private val FACTS = listOf("birth", "death", "burial")

    data class Merged(val people: String, val families: String, val sources: String, val orphanPatches: Int, val alternatesAdded: Int, val gapsFilled: Int)

    @Synchronized fun apply(peopleJson: String, familiesJson: String?, sourcesJson: String?, overlays: List<JSONObject>): Merged {
        val people = rootArray(peopleJson, "people")
        val families = rootArray(familiesJson, "families")
        val sourcesRoot = sourcesJson?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: JSONObject().also { r -> sourcesJson?.let { runCatching { JSONArray(it) }.getOrNull() }?.let { r.put("sources", it) } }
        val sources = sourcesRoot.optJSONArray("sources") ?: JSONArray().also { sourcesRoot.put("sources", it) }
        val byId = (0 until people.length()).mapNotNull { people.optJSONObject(it) }.associateBy { it.optString("id") }.toMutableMap()
        val famById = (0 until families.length()).mapNotNull { families.optJSONObject(it) }.associateBy { it.optString("id") }.toMutableMap()
        var orphans = 0
        alts = 0; gaps = 0
        for (ov in overlays) {
            ov.optJSONObject("source")?.let(sources::put)
            ov.optJSONArray("gedcom_sources")?.let { a -> for (k in 0 until a.length()) sources.put(a.get(k)) }
            ov.optJSONArray("people")?.let { a -> for (k in 0 until a.length()) a.optJSONObject(k)?.let { p ->
                if (p.optString("id") !in byId) { people.put(p); byId[p.optString("id")] = p }
            } }
            ov.optJSONArray("families")?.let { a -> for (k in 0 until a.length()) a.optJSONObject(k)?.let { f ->
                if (f.optString("id") !in famById) { families.put(f); famById[f.optString("id")] = f }
            } }
            val pp = ov.optJSONObject("person_patches") ?: JSONObject()
            for (pid in pp.keys()) {
                val target = byId[pid]
                if (target == null) { orphans++; continue }
                applyPatch(target, pp.getJSONObject(pid))
            }
            val fp = ov.optJSONObject("family_patches") ?: JSONObject()
            for (fid in fp.keys()) {
                val f = famById[fid] ?: continue
                val kids = f.optJSONArray("children") ?: JSONArray().also { f.put("children", it) }
                val have = (0 until kids.length()).map { kids.get(it).let { c -> if (c is JSONObject) c.optString("id") else c.toString() } }.toSet()
                fp.getJSONObject(fid).optJSONArray("children")?.let { a -> for (k in 0 until a.length()) {
                    val c = a.getJSONObject(k); if (c.optString("id") !in have) kids.put(c)
                } }
            }
        }
        return Merged(people.toString(), families.toString(), sourcesRoot.toString(), orphans, alts, gaps)
    }

    private var alts = 0
    private var gaps = 0

    @Synchronized private fun applyPatch(p: JSONObject, patch: JSONObject) {
        val src = patch.optJSONObject("source")
        val title = patch.optString("title")
        val living = p.optBoolean("living", false)
        val vals = patch.optJSONObject("values") ?: JSONObject()
        if (!living) for (f in FACTS) {
            val iv = vals.optJSONObject(f) ?: continue
            val date = iv.optString("date").takeIf { it.isNotEmpty() && it != "null" }
            val place = iv.optString("place").takeIf { it.isNotEmpty() && it != "null" }
            val cite = JSONObject().put("source", src?.optString("source")).put("note", title)
            val ex = p.optJSONObject(f)
            val exDate = ex?.optString("date")?.takeIf { it.isNotEmpty() && it != "null" }
            val exPlace = ex?.optString("place")?.takeIf { it.isNotEmpty() && it != "null" }
            when {
                ex == null || (exDate == null && exPlace == null) -> {
                    // fill a gap (nothing overwritten)
                    val o = ex ?: JSONObject().also { p.put(f, it) }
                    gaps++
                    o.put("date", date ?: JSONObject.NULL).put("place", place ?: JSONObject.NULL).put("origin", "import")
                    o.put("sources", (o.optJSONArray("sources") ?: JSONArray()).put(cite))
                }
                ImportPlanner.differs(exDate, exPlace, date, place) -> {
                    val alts = ex.optJSONArray("alternatives") ?: JSONArray().also { ex.put("alternatives", it) }
                    val known = (0 until alts.length()).mapNotNull { alts.optJSONObject(it) }.firstOrNull { a ->
                        !ImportPlanner.differs(a.s("date"), a.s("place"), date, place) && (yearOf(a.s("date")) == yearOf(date))
                    }
                    if (known != null) {   // the research data already lists this value as an alternate
                        (known.optJSONArray("corroborated_by") ?: JSONArray().also { known.put("corroborated_by", it) }).put(JSONObject().put("origin", title))
                    } else (alts.also { this.alts++ }).put(JSONObject().put("date", date ?: JSONObject.NULL).put("place", place ?: JSONObject.NULL)
                        .put("origin", title).put("sources", JSONArray().put(cite)))
                }
                else -> {
                    if (exPlace == null && place != null) ex.put("place", place)   // same date, adds the missing place
                    if (exDate == null && date != null) ex.put("date", date)
                    val c = ex.optJSONArray("corroborated_by") ?: JSONArray().also { ex.put("corroborated_by", it) }
                    c.put(JSONObject().put("origin", title))
                }
            }
        }
        val aliases = p.optJSONArray("aliases") ?: JSONArray().also { p.put("aliases", it) }
        val have = (0 until aliases.length()).map { aliases.optString(it).lowercase() }.toMutableSet()
        patch.optJSONArray("aliases")?.let { a -> for (k in 0 until a.length()) { val n = a.optString(k); if (have.add(n.lowercase())) aliases.put(n) } }
        val xr = p.optJSONArray("import_xrefs") ?: JSONArray().also { p.put("import_xrefs", it) }
        patch.optJSONArray("import_xrefs")?.let { a -> for (k in 0 until a.length()) xr.put(a.get(k)) }
        if (src != null) (p.optJSONArray("sources") ?: JSONArray().also { p.put("sources", it) }).put(src)
    }

    private fun rootArray(text: String?, key: String): JSONArray {
        if (text.isNullOrBlank()) return JSONArray()
        return runCatching { JSONArray(text) }.getOrNull()
            ?: runCatching { JSONObject(text).optJSONArray(key) }.getOrNull() ?: JSONArray()
    }

    /** Convenience for tests/repository: merged, parsed data. */
    fun load(people: String, families: String?, events: String?, sources: String?, places: String?, bundle: String?, overlays: List<JSONObject>): FamilyData {
        if (overlays.isEmpty()) return FamilyParser.parse(people, families, events, sources, places, bundle)
        val m = apply(people, families, sources, overlays)
        return FamilyParser.parse(m.people, m.families, events, m.sources, places, bundle)
    }
}
