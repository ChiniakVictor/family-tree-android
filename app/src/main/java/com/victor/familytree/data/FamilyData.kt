package com.victor.familytree.data

/** A related person plus how sure the link is. */
data class Relative(val person: Person, val confidence: Confidence?, val note: String? = null)

data class Stats(
    val people: Int,
    val living: Int,
    val families: Int,
    val events: Int,
    val sources: Int,
    val places: Int,
    val directAncestors: Int,
    val surnames: Int,
    val earliestYear: Int?,
    val latestYear: Int?,
    val generations: Int,
    val withResearch: Int,
    val uncertainLinks: Int,
    val flagged: Int,
)

class FamilyData(
    val people: Map<String, Person>,
    val families: Map<String, Family>,
    val events: List<Event>,
    val sources: Map<String, Source>,
    val places: List<Place>,
    val bundle: BundleInfo?,
    val notes: List<String>,
) {
    /** Real people: duplicate pointer records are left out of lists and counts. */
    val visiblePeople: List<Person> = people.values.filter { it.duplicateOf == null }
    val duplicateRecords: Int = people.size - visiblePeople.size

    val sortedPeople: List<Person> = visiblePeople.sortedWith(
        compareBy<Person>({ it.sortSurname }, { it.sortGiven }, { it.birthYear ?: Int.MAX_VALUE })
    )

    private val eventsByPerson: Map<String, List<Event>> = events
        .flatMap { e -> e.participants.mapNotNull { it.id }.distinct().map { it to e } }
        .groupBy({ it.first }, { it.second })

    private val familiesAsChild: Map<String, List<Pair<Family, ChildLink>>> = families.values
        .flatMap { f -> f.children.map { c -> c.id to (f to c) } }
        .groupBy({ it.first }, { it.second })

    private val familiesAsParent: Map<String, List<Family>> = families.values
        .flatMap { f -> listOfNotNull(f.parent1, f.parent2).map { it to f } }
        .groupBy({ it.first }, { it.second })

    /** Surnames (case-normalised) with counts, most common first. */
    val surnameCounts: List<Pair<String, Int>> = visiblePeople
        .mapNotNull { it.surname?.let(::normaliseSurname) }
        .groupingBy { it }.eachCount()
        .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { it.key to it.value }

    val defaultRootId: String? =
        people.values.firstOrNull { it.generation == 0 }?.id
            ?: sortedPeople.firstOrNull()?.id

    /** Top of the paternal line above the root: a good default start for the descendant view. */
    val defaultDescendantRootId: String? = defaultRootId?.let { start ->
        var cur = start
        val seen = mutableSetOf(cur)
        while (true) {
            val (f, m) = fatherAndMother(cur)
            val next = (f ?: m)?.id ?: break
            if (!seen.add(next)) break
            cur = next
        }
        cur
    }

    fun person(id: String?): Person? = id?.let { people[it] }

    fun eventsFor(id: String): List<Event> = eventsByPerson[id].orEmpty()

    fun parentsOf(id: String): List<Relative> {
        val p = people[id] ?: return emptyList()
        val out = LinkedHashMap<String, Relative>()
        fun add(pid: String?, c: Confidence?) {
            val rel = person(pid) ?: return
            if (rel.id != id && rel.id !in out) out[rel.id] = Relative(rel, c)
        }
        for (link in p.parentLinks) {
            val fam = link.familyId?.let { families[it] }
            add(link.parent1 ?: fam?.parent1, link.confidence)
            add(link.parent2 ?: fam?.parent2, link.confidence)
        }
        for ((fam, child) in familiesAsChild[id].orEmpty()) {
            add(fam.parent1, child.confidence)
            add(fam.parent2, child.confidence)
        }
        return out.values.toList()
    }

    /** Father first (by sex), then mother; either may be null. */
    fun fatherAndMother(id: String): Pair<Person?, Person?> {
        val ps = parentsOf(id).map { it.person }.take(2)
        val father = ps.firstOrNull { it.sex == "M" } ?: ps.firstOrNull { it.sex != "F" }
        val mother = ps.firstOrNull { it != father }
        return father to mother
    }

    fun childrenOf(id: String): List<Relative> {
        val out = LinkedHashMap<String, Relative>()
        for (fam in familiesAsParent[id].orEmpty()) for (c in fam.children) {
            person(c.id)?.let { if (it.id !in out) out[it.id] = Relative(it, c.confidence) }
        }
        people[id]?.childIds?.forEach { cid ->
            val child = person(cid) ?: return@forEach
            if (child.id !in out) {
                val c = child.parentLinks.firstOrNull { it.parent1 == id || it.parent2 == id }?.confidence
                out[child.id] = Relative(child, c)
            }
        }
        return out.values.sortedBy { it.person.birthYear ?: Int.MAX_VALUE }
    }

    fun spousesOf(id: String): List<Relative> {
        val out = LinkedHashMap<String, Relative>()
        val selfLiving = people[id]?.living == true
        fun noteFor(connector: String?, marriage: Fact?, spouse: Person): String? {
            val parts = mutableListOf<String>()
            marriage?.takeUnless { selfLiving || spouse.living }?.let { m ->
                listOfNotNull(m.date?.let { "married $it" }, m.place).joinToString(", ").takeIf { it.isNotBlank() }
                    ?.let(parts::add)
            }
            if (connector == "dotted") parts += "relationship status not set"
            return parts.joinToString(" · ").ifBlank { null }
        }
        people[id]?.spouseLinks?.forEach { s ->
            val sp = person(s.personId) ?: return@forEach
            val fam = s.familyId?.let { families[it] }
            out.getOrPut(sp.id) { Relative(sp, null, noteFor(s.connector ?: fam?.connector, s.marriage ?: fam?.marriage, sp)) }
        }
        for (fam in familiesAsParent[id].orEmpty()) {
            val other = if (fam.parent1 == id) fam.parent2 else fam.parent1
            val sp = person(other) ?: continue
            out.getOrPut(sp.id) { Relative(sp, null, noteFor(fam.connector, fam.marriage, sp)) }
        }
        return out.values.toList()
    }

    fun checksFor(id: String): List<DataCheck> = DataChecks.forPerson(this, id)

    /** Parents that research contradicts for this person (from the person record or a family's disputed_children). */
    fun disputedParentsOf(id: String): List<DisputedRelative> {
        val out = LinkedHashMap<String, DisputedRelative>()
        val real = parentsOf(id).map { it.person.id }.toSet()
        fun add(pid: String?, reason: String?, src: List<SourceRef>) {
            val rel = person(pid) ?: return
            if (rel.id != id && rel.id !in real && rel.id !in out) out[rel.id] = DisputedRelative(rel, reason, src)
        }
        people[id]?.disputedParents?.forEach { d ->
            val fam = d.familyId?.let { families[it] }
            add(d.parent1 ?: fam?.parent1, d.reason, d.sources)
            add(d.parent2 ?: fam?.parent2, d.reason, d.sources)
        }
        families.values.forEach { f -> f.disputedChildren.filter { it.id == id }.forEach { c ->
            add(f.parent1, c.reason, emptyList()); add(f.parent2, c.reason, emptyList())
        } }
        return out.values.toList()
    }

    /** Children whose link to this person is disputed. */
    fun disputedChildrenOf(id: String): List<DisputedRelative> {
        val out = LinkedHashMap<String, DisputedRelative>()
        val real = childrenOf(id).map { it.person.id }.toSet()
        for (f in families.values) if (f.parent1 == id || f.parent2 == id) for (c in f.disputedChildren) {
            val ch = person(c.id) ?: continue
            if (ch.id !in real && ch.id !in out) out[ch.id] = DisputedRelative(ch, c.reason, emptyList())
        }
        for (p in people.values) for (d in p.disputedParents) {
            val fam = d.familyId?.let { families[it] }
            if (id in listOfNotNull(d.parent1 ?: fam?.parent1, d.parent2 ?: fam?.parent2) && p.id !in real && p.id !in out)
                out[p.id] = DisputedRelative(p, d.reason, d.sources)
        }
        return out.values.toList()
    }

    /** People with at least one warning (computed once, off the main thread via [warmUp]). */
    val flaggedIds: Set<String> by lazy {
        people.keys.filter { id -> checksFor(id).any { it.severity == DataCheck.Severity.WARNING } }.toSet()
    }

    private val statsCache by lazy { computeStats() }
    fun stats(): Stats = statsCache

    /** Precompute derived data so the first screens open instantly. */
    fun warmUp(): FamilyData { flaggedIds; statsCache; return this }

    private fun computeStats(): Stats {
        val people = visiblePeople.associateBy { it.id }
        val years = people.values.filterNot { it.living }.flatMap { listOfNotNull(it.birthYear, it.deathYear) }
        val gens = people.values.mapNotNull { it.generation }
        return Stats(
            people = people.size,
            living = people.values.count { it.living },
            families = families.size,
            events = events.size,
            sources = sources.size,
            places = places.size,
            directAncestors = people.values.count { it.directAncestor != null },
            surnames = surnameCounts.size,
            earliestYear = years.minOrNull(),
            latestYear = years.maxOrNull(),
            generations = if (gens.isEmpty()) 0 else gens.max() - gens.min() + 1,
            withResearch = people.values.count { p ->
                p.origin != null || p.extras.isNotEmpty() || p.infoLists.isNotEmpty() ||
                    listOfNotNull(p.birth, p.death, p.burial).any { it.place != null || it.origin != null }
            },
            uncertainLinks = people.values.sumOf { p -> p.parentLinks.count { it.confidence?.isUncertain == true } },
            flagged = flaggedIds.size,
        )
    }

    companion object {
        fun normaliseSurname(s: String): String =
            s.trim().lowercase().split(' ', '-').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    }
}
