package com.victor.familytree.data

/** How sure we are about a value or a link. Unknown strings map to [UNKNOWN]. */
enum class Confidence(val label: String) {
    HIGH("High"), MEDIUM("Medium"), LOW("Low"), UNKNOWN("Unrated");

    val isUncertain: Boolean get() = this == MEDIUM || this == LOW

    companion object {
        fun parse(raw: String?): Confidence? {
            val s = raw?.trim()?.lowercase() ?: return null
            if (s.isEmpty()) return null
            return when {
                s.startsWith("high") || s == "confirmed" || s == "certain" -> HIGH
                s.startsWith("med") || s == "probable" || s == "likely" -> MEDIUM
                s.startsWith("low") || s == "possible" || s == "speculative" || s == "uncertain" -> LOW
                else -> UNKNOWN
            }
        }
    }
}

/** A citation of a source, from a person, fact, family or event. */
data class SourceRef(
    val sourceId: String?,
    val page: String?,
    val text: String?,
    val confidence: Confidence?,
    val origin: String?,
    val url: String?,
    val note: String?,
)

/** A dated/placed fact such as birth, death, burial or marriage. Extra fields go into [details]. */
data class Fact(
    val date: String?,
    val place: String?,
    val confidence: Confidence?,
    val origin: String?,
    val alternatives: List<String>,
    val details: List<Pair<String, String>>,
    val sources: List<SourceRef>,
    /** Years found in the alternative values (null when an alternative has no year). */
    val alternativeYears: List<Int?> = emptyList(),
) {
    val isEmpty: Boolean
        get() = date.isNullOrBlank() && place.isNullOrBlank() && alternatives.isEmpty() && details.isEmpty()
    val hasConflict: Boolean get() = alternatives.isNotEmpty()

    /** Alternative years that disagree with the main date's year. */
    val conflictingYears: List<Int>
        get() {
            val main = date?.let(::yearOf) ?: return alternativeYears.filterNotNull().distinct()
            return alternativeYears.filterNotNull().filter { it != main }.distinct()
        }
}

data class ParentLink(val familyId: String?, val parent1: String?, val parent2: String?, val confidence: Confidence?)

data class SpouseLink(val personId: String?, val familyId: String?, val marriage: Fact?, val connector: String?)

data class DirectAncestor(val relationship: String?, val confidence: Confidence?)

/** A list-type section of a person record (occupations, residences, ...), already flattened to text. */
data class InfoList(val title: String, val items: List<String>)

data class Person(
    val id: String,
    val displayName: String,
    val givenNames: String?,
    val surname: String?,
    val suffix: String?,
    val maidenName: String?,
    val marriedName: String?,
    val nicknames: List<String>,
    val aliases: List<String>,
    val sex: String?,
    val living: Boolean,
    val birth: Fact?,
    val death: Fact?,
    val burial: Fact?,
    val notes: List<String>,
    val parentLinks: List<ParentLink>,
    val spouseLinks: List<SpouseLink>,
    val childIds: List<String>,
    val sources: List<SourceRef>,
    val generation: Int?,
    val directAncestor: DirectAncestor?,
    val possibleDuplicates: List<String>,
    val nameTruncated: Boolean,
    val origin: String?,
    val confidence: Confidence?,
    val infoLists: List<InfoList>,
    val extras: List<Pair<String, String>>,
    val ancestry: AncestryInfo? = null,
    val disputes: List<String> = emptyList(),
    /** GEDCOM record ids this person is known by (Ancestry export xrefs, earlier imports). */
    val externalIds: List<String> = emptyList(),
    /** Import batch that created this person (null = bundled research data). */
    val importBatch: Long? = null,
    /** Parent links that research has contradicted (kept for transparency, never used as real parents). */
    val disputedParents: List<DisputedParent> = emptyList(),
    /** Set on a duplicate record that only points to the real person; hidden from lists and counts. */
    val duplicateOf: String? = null,
) {
    /** Living people never show dates. */
    val lifespan: String
        get() {
            if (living) return "Living"
            val b = birth?.date?.let(::yearOf)
            val d = death?.date?.let(::yearOf)
            return when {
                b == null && d == null -> ""
                d == null -> "b. $b"
                b == null -> "d. $d"
                else -> "$b – $d"
            }
        }

    val sortSurname: String get() = (surname ?: displayName).lowercase()
    val sortGiven: String get() = (givenNames ?: displayName).lowercase()
    val birthYear: Int? get() = birth?.date?.let(::yearOf)
    val deathYear: Int? get() = death?.date?.let(::yearOf)
}

/** Summary of the matching Ancestry GEDCOM profile(s). */
data class AncestryInfo(val urls: List<String>, val sourceTitles: List<String>, val recordUrls: List<String>, val mediaCount: Int, val profiles: Int, val xrefs: List<String> = emptyList())

data class ChildLink(val id: String, val confidence: Confidence?)

data class Family(
    val id: String,
    val parent1: String?,
    val parent2: String?,
    val unknownParent: String?,
    val marriage: Fact?,
    val children: List<ChildLink>,
    val connector: String?,
    val sources: List<SourceRef>,
    val notes: List<String>,
    val disputedChildren: List<DisputedChild> = emptyList(),
)

data class DisputedParent(val familyId: String?, val parent1: String?, val parent2: String?, val reason: String?, val sources: List<SourceRef>)
data class DisputedChild(val id: String, val reason: String?, val status: String?)

/** A relationship shown with a "Disputed" badge and a dashed line, with the reason it is doubted. */
data class DisputedRelative(val person: Person, val reason: String?, val sources: List<SourceRef>)

data class Participant(val id: String?, val name: String?, val role: String?)

data class Event(
    val id: String,
    val type: String,
    val date: String?,
    val place: String?,
    val participants: List<Participant>,
    val familyId: String?,
    val note: String?,
    val confidence: Confidence?,
    val origin: String?,
    val sources: List<SourceRef>,
    val details: List<Pair<String, String>>,
    val alternatives: List<String> = emptyList(),
)

data class Source(
    val id: String,
    val title: String,
    val type: String?,
    val author: String?,
    val date: String?,
    val url: String?,
    val summary: String?,
)

data class Place(val id: String?, val name: String, val details: List<Pair<String, String>>)

data class BundleInfo(val bundledAt: String?, val sourceDir: String?)

/** A data-quality flag shown on a person (uncertain link, implausible date, possible duplicate...). */
data class DataCheck(val severity: Severity, val message: String, val relatedId: String? = null) {
    enum class Severity { INFO, WARNING }
}

private val YEAR = Regex("""\b(\d{3,4})\b""")

/** First 3-4 digit year in a free-text date ("abt 1850", "12 Mar 1901"). */
fun yearOf(date: String): Int? = YEAR.find(date)?.groupValues?.get(1)?.toIntOrNull()
