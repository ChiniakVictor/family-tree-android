package com.victor.familytree.data.gedcom

import com.victor.familytree.data.FamilyData
import com.victor.familytree.data.Person
import java.text.Normalizer

enum class MatchStatus { MATCHED, UNCERTAIN, NEW }

data class Candidate(val personId: String, val score: Int, val reasons: List<String>)

data class IndiMatch(val xref: String, val status: MatchStatus, val candidates: List<Candidate>) {
    val best: Candidate? get() = candidates.firstOrNull()
}

/**
 * Scores every GEDCOM individual against the existing tree: name (exact, phonetic, nickname variants, initials),
 * birth/death year and place, sex, and the names of parents, spouses and children. A known record id (the same
 * Ancestry export or an earlier import) is a strong extra signal. Clear winners are matched automatically; close or
 * weak calls are left for the user.
 */
class Matcher(private val data: FamilyData, private val useRecordIds: Boolean = true) {
    companion object {
        const val AUTO = 70
        const val MARGIN = 15
        const val MAYBE = 40
        const val STRONG = 90

        fun norm(s: String?): String = s?.let {
            Normalizer.normalize(it, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
                .lowercase().replace(Regex("[^a-z ]"), " ").replace(Regex("\\s+"), " ").trim()
        } ?: ""

        fun soundex(word: String): String {
            val w = norm(word).replace(" ", "")
            if (w.isEmpty()) return ""
            fun code(c: Char) = when (c) {
                'b', 'f', 'p', 'v' -> '1'; 'c', 'g', 'j', 'k', 'q', 's', 'x', 'z' -> '2'; 'd', 't' -> '3'
                'l' -> '4'; 'm', 'n' -> '5'; 'r' -> '6'; else -> '0'
            }
            val sb = StringBuilder().append(w[0].uppercaseChar())
            var last = code(w[0])
            for (c in w.drop(1)) {
                val k = code(c)
                if (k != '0' && k != last) sb.append(k)
                if (c != 'h' && c != 'w') last = k
                if (sb.length == 4) break
            }
            return sb.toString().padEnd(4, '0')
        }

        private val NICK = listOf(
            "william bill will billy willie", "robert bob rob bobby robbie bert", "richard dick rick ricky rich",
            "james jim jimmy jamie", "john jack johnny jon", "elizabeth eliza liz lizzie beth betsy betty bess libby",
            "margaret maggie peggy meg marge margie greta", "mary polly molly mae mamie", "catherine katherine kathryn kate kathy katie cathy kitty",
            "charles charlie chuck carl", "edward ed eddie ned ted", "henry hank harry", "thomas tom tommy",
            "joseph joe joey", "samuel sam sammy", "benjamin ben benny", "daniel dan danny", "nicholas nick nicky",
            "michael mike mick mickey", "jacob jake", "joshua josh", "anthony tony", "frederick fred freddie fritz",
            "albert al bert", "alfred al alf fred", "abraham abe", "isaac ike", "andrew andy drew", "christopher chris kit",
            "sarah sally sadie sara", "susan sue susie susanna susannah suzanne", "ann anna anne annie nancy hannah",
            "dorothy dot dottie dolly", "eleanor ellen ellie nell nellie helen", "frances fanny fran", "harriet hattie",
            "martha patsy mattie", "rebecca becky", "virginia ginny", "johann johannes hans john", "wilhelm william",
            "heinrich henry", "friedrich frederick", "jakob jacob", "katharina catherine", "margaretha margaret",
            "lucinda lucy cindy", "matilda tillie maud", "theodore ted teddy theo", "lawrence larry", "leonard leo len",
            "stephen steven steve", "gerald jerry", "kristi kristine christine", "victor vic", "dennis denny",
        ).map { it.split(' ').toSet() }
        fun variants(a: String, b: String): Boolean = a == b || NICK.any { a in it && b in it }
    }

    private val normCache = HashMap<String, String>()
    private val sxCache = HashMap<String, String>()
    private fun n(s: String?): String = if (s == null) "" else normCache.getOrPut(s) { norm(s) }
    private fun sx(s: String): String = sxCache.getOrPut(s) { soundex(s) }
    private val relCache = HashMap<String, Triple<List<Person>, List<Person>, List<Person>>>()
    private fun rels(id: String) = relCache.getOrPut(id) {
        Triple(data.parentsOf(id).map { it.person }, data.spousesOf(id).map { it.person }, data.childrenOf(id).map { it.person })
    }
    private val gRelCache = HashMap<String, Triple<List<GedIndi>, List<GedIndi>, List<GedIndi>>>()
    private val pairCache = HashMap<Pair<String, String>, Boolean>()

    private val bySoundex: Map<String, List<Person>> by lazy { data.people.values
        .flatMap { p -> surnamesOf(p).map { sx(it) to p } }
        .groupBy({ it.first }, { it.second }) }
    private val byGivenSx: Map<String, List<Person>> by lazy { data.people.values
        .mapNotNull { p -> firstGiven(p.givenNames ?: p.displayName)?.let { sx(it) to p } }
        .groupBy({ it.first }, { it.second }) }
    private val byRecordId: Map<String, Person> by lazy { data.people.values
        .flatMap { p -> p.externalIds.map { it to p } }.toMap() }

    private val surnameCache = HashMap<String, List<String>>()
    private fun surnamesOf(p: Person): List<String> = surnameCache.getOrPut(p.id) {
        listOfNotNull(p.surname, p.maidenName, p.marriedName).map { n(it) }.filter { it.isNotEmpty() }.distinct()
    }

    private fun firstGiven(s: String?): String? = n(s).split(' ').firstOrNull { it.length > 1 } ?: n(s).split(' ').firstOrNull { it.isNotEmpty() }

    /** Name similarity 0..55 plus reasons. */
    private fun nameScore(givenA: String?, surA: String?, p: Person, reasons: MutableList<String>): Int {
        var score = 0
        val sa = n(surA)
        val sbs = surnamesOf(p)
        when {
            sa.isNotEmpty() && sa in sbs -> { score += 25; reasons += "same surname" }
            sa.isNotEmpty() && sbs.any { sx(it) == sx(sa) } -> { score += 17; reasons += "surname sounds alike" }
            sa.isNotEmpty() && sbs.isNotEmpty() -> score -= 10
        }
        val ga = n(givenA).split(' ').filter { it.isNotEmpty() }
        val gb = n(p.givenNames ?: p.displayName.substringBeforeLast(' ')).split(' ').filter { it.isNotEmpty() }
        if (ga.isNotEmpty() && gb.isNotEmpty()) {
            val a0 = ga[0]; val b0 = gb[0]
            when {
                a0 == b0 -> { score += 22; reasons += "same first name" }
                variants(a0, b0) -> { score += 17; reasons += "name variant ($a0/$b0)" }
                a0.length > 2 && b0.length > 2 && (sx(a0) == sx(b0) ||
                    (a0[0] in "aeiouy" && b0[0] in "aeiouy" && sx(a0).drop(1) == sx(b0).drop(1))) -> { score += 14; reasons += "first name sounds alike" }
                (a0.length == 1 || b0.length == 1) && a0[0] == b0[0] -> { score += 8; reasons += "initial matches" }
                else -> score -= 30   // siblings share parents, dates and places: a different first name must weigh heavily
            }
            // middle names / initials agree
            if (ga.size > 1 && gb.size > 1) {
                if (ga[1][0] == gb[1][0]) score += 5 else score -= 4
            }
        }
        return score
    }

    private fun yearScore(a: Int?, b: Int?, weight: Int, label: String, reasons: MutableList<String>): Int {
        if (a == null || b == null) return 0
        val d = kotlin.math.abs(a - b)
        return when {
            d == 0 -> { reasons += "same $label year"; weight }
            d == 1 -> { reasons += "$label year ±1"; weight * 3 / 4 }
            d <= 3 -> { reasons += "$label year ±$d"; weight / 3 }
            d <= 10 -> -weight / 2
            else -> -weight * 2
        }
    }

    private fun placeScore(a: String?, b: String?, reasons: MutableList<String>): Int {
        val ta = n(a).split(' ').filter { it.length > 2 }.toSet()
        val tb = n(b).split(' ').filter { it.length > 2 }.toSet()
        if (ta.isEmpty() || tb.isEmpty()) return 0
        val common = ta intersect tb - setOf("usa", "united", "states", "america", "county", "co")
        return if (common.isNotEmpty()) { reasons += "place overlaps (${common.first()})"; minOf(8, 3 + 2 * common.size) } else -2
    }

    private fun relativesScore(g: GedcomFile, x: String, p: Person, reasons: MutableList<String>): Int {
        fun sameName(i: GedIndi, q: Person): Boolean = pairCache.getOrPut(i.xref to q.id) {
            nameScore(i.given, i.surname, q, mutableListOf()) >= 35
        }
        var score = 0
        val (ps, sp, ch) = rels(p.id)
        val (gp, gs, gc) = gRelCache.getOrPut(x) { Triple(g.parentsOf(x), g.spousesOf(x), g.childrenOf(x)) }
        val parentHits = gp.count { gi -> ps.any { sameName(gi, it) } }
        if (parentHits > 0) { score += 10 * parentHits; reasons += "$parentHits parent name(s) match" }
        val spouseHits = gs.count { gi -> sp.any { sameName(gi, it) } }
        if (spouseHits > 0) { score += 10 * minOf(spouseHits, 2); reasons += "spouse matches" }
        val childHits = gc.count { gi -> ch.any { sameName(gi, it) } }
        if (childHits > 0) { score += minOf(15, 5 * childHits); reasons += "$childHits child name(s) match" }
        return score
    }

    fun score(g: GedcomFile, i: GedIndi, p: Person): Candidate {
        val reasons = mutableListOf<String>()
        var s = nameScore(i.given, i.surname, p, reasons)
        if (!p.living) {
            s += yearScore(i.birthYear, p.birthYear, 20, "birth", reasons)
            s += yearScore(i.deathYear, p.deathYear, 15, "death", reasons)
            s += placeScore(i.birth?.place, p.birth?.place, reasons)
            s += placeScore(i.death?.place, p.death?.place, reasons) / 2
        } else if (i.birthYear != null && p.birthYear != null) {
            s += yearScore(i.birthYear, p.birthYear, 20, "birth", reasons)
        }
        if (i.sex != null && p.sex != null && i.sex != "U" && p.sex != "U" && i.sex != p.sex.take(1).uppercase()) { s -= 30; reasons += "different sex" }
        s += relativesScore(g, i.xref, p, reasons)
        if (useRecordIds && i.xref in p.externalIds) { s += 60; reasons += "same record id ${i.xref}" }
        return Candidate(p.id, s, reasons)
    }

    fun match(g: GedcomFile): List<IndiMatch> {
        val raw = g.individuals.values.map { i ->
            val pool = LinkedHashSet<Person>()
            i.surname?.let { pool += bySoundex[sx(it)].orEmpty() }
            firstGiven(i.given)?.let { gname -> pool += byGivenSx[sx(gname)].orEmpty() }
            if (useRecordIds) byRecordId[i.xref]?.let(pool::add)
            val cands = pool.map { score(g, i, it) }.filter { it.score >= MAYBE - 10 }.sortedByDescending { it.score }.take(4)
            i to cands
        }
        // An existing person normally absorbs one imported record (strongest claim wins). Very strong matches may share
        // a person: Ancestry trees often hold the same individual twice (e.g. under maiden and married name).
        val claimed = mutableMapOf<String, Pair<String, Int>>()
        raw.flatMap { (i, c) -> c.firstOrNull()?.let { listOf(Triple(i.xref, it.personId, it.score)) } ?: emptyList() }
            .sortedByDescending { it.third }
            .forEach { (x, pid, sc) -> if (pid !in claimed && sc >= AUTO) claimed[pid] = x to sc }
        return raw.map { (i, cands) ->
            val best = cands.firstOrNull()
            val second = cands.getOrNull(1)?.score ?: 0
            val status = when {
                best == null || best.score < MAYBE -> MatchStatus.NEW
                best.score >= AUTO && best.score - second >= MARGIN &&
                    (claimed[best.personId]?.first == i.xref || best.score >= STRONG) -> MatchStatus.MATCHED
                else -> MatchStatus.UNCERTAIN
            }
            IndiMatch(i.xref, status, if (status == MatchStatus.NEW) cands.filter { it.score >= MAYBE - 10 } else cands)
        }
    }
}
