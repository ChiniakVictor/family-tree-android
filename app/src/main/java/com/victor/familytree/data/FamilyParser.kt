package com.victor.familytree.data

import org.json.JSONObject

/**
 * Parses the research JSON files. Every field is optional and unknown fields are kept
 * (shown as "Other details") so new research data appears without code changes.
 */
object FamilyParser {
    /** Privacy: flagged living, or no death/burial record and born within the last 100 years -> treated as living. */
    fun isLiving(flag: Boolean?, birth: Fact?, death: Fact?, burial: Fact?): Boolean {
        if (flag == true) return true
        if (death != null && !death.isEmpty || burial != null && !burial.isEmpty) return false
        val by = birth?.date?.let(::yearOf) ?: return false
        return by >= java.time.Year.now().value - 100
    }


    fun parse(
        people: String?,
        families: String?,
        events: String?,
        sources: String?,
        places: String?,
        bundle: String? = null,
    ): FamilyData {
        val placeList = rootArray(places, "places").objectList().mapNotNull(::parsePlace)
        val placeById = placeList.filter { it.id != null }.associateBy { it.id!! }
        val ctx = Ctx(placeById)

        val personList = rootArray(people, "people").objectList().mapNotNull { ctx.person(it) }
        val familyList = rootArray(families, "families").objectList().mapNotNull { ctx.family(it) }
        val eventList = rootArray(events, "events").objectList().mapNotNull { ctx.event(it) }
        val sourceList = rootArray(sources, "sources").objectList().mapNotNull(::parseSource)

        val notes = listOfNotNull(sources, places).mapNotNull { txt ->
            runCatching { JSONObject(txt).str("note") }.getOrNull()
        }
        val bundleInfo = bundle?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?.let { BundleInfo(it.str("bundled_at"), it.str("source_dir")) }

        return FamilyData(
            people = personList.associateBy { it.id },
            families = familyList.associateBy { it.id },
            events = eventList,
            sources = sourceList.associateBy { it.id },
            places = placeList,
            bundle = bundleInfo,
            notes = notes,
        )
    }

    private class Ctx(val places: Map<String, Place>) {

        fun placeName(v: Any?): String? = when (v) {
            null, JSONObject.NULL -> null
            is String -> v.trim().takeIf { it.isNotEmpty() }?.let { places[it]?.name ?: it }
            is JSONObject -> v.str("name", "full_name", "title", "display_name")
                ?: v.str("id")?.let { places[it]?.name ?: it }
                ?: describe(v).takeIf { it.isNotBlank() }
            else -> describe(v).takeIf { it.isNotBlank() }
        }

        fun sourceRef(v: Any): SourceRef? = when (v) {
            is String -> SourceRef(v, null, null, null, null, null, null)
            is JSONObject -> SourceRef(
                sourceId = v.str("source", "source_id", "id"),
                page = v.str("page", "pages"),
                text = v.str("card_text", "citation", "text", "quote", "title"),
                confidence = Confidence.parse(v.str("confidence")),
                origin = v.str("origin"),
                url = v.str("url", "link", "href") ?: v.strings("record_urls").firstOrNull(),
                note = v.str("note", "notes", "detail"),
            )
            else -> null
        }

        fun sourceRefs(o: JSONObject) = (o.items("sources") + o.items("citations")).mapNotNull(::sourceRef)

        fun fact(v: Any?): Fact? = when (v) {
            null, JSONObject.NULL -> null
            is String -> Fact(v, null, null, null, emptyList(), emptyList(), emptyList())
            is JSONObject -> {
                val alternatives = ALT_KEYS.flatMap { v.strings(it) }
                val date = v.str("date", "date_text", "year")
                val details = v.keyList()
                    .filterNot { it in FACT_KEYS || it in ALT_KEYS }
                    .mapNotNull { k -> describe(v.opt(k)).takeIf { it.isNotBlank() }?.let { humanize(k) to it } }
                    .toMutableList()
                v.str("pdf_value")?.takeIf { it != date && yearOf(it) != date?.let(::yearOf) }
                    ?.let { details += "PDF chart showed" to it }
                Fact(
                    date = date,
                    place = placeName(v.opt("place")) ?: placeName(v.opt("place_id")),
                    confidence = Confidence.parse(v.str("confidence")),
                    origin = v.str("origin"),
                    alternatives = alternatives,
                    details = details,
                    sources = sourceRefs(v),
                    alternativeYears = ALT_KEYS.flatMap { v.items(it) }.map { alt ->
                        when (alt) {
                            is JSONObject -> alt.str("date", "year")?.let(::yearOf)
                            else -> yearOf(describe(alt))
                        }
                    },
                )
            }
            else -> Fact(describe(v), null, null, null, emptyList(), emptyList(), emptyList())
        }

        fun person(o: JSONObject): Person? {
            val id = o.str("id") ?: return null
            val given = o.str("given_names", "given")
            val surname = o.str("surname")
            val name = o.str("full_name", "display_name", "name")
                ?: listOfNotNull(given, surname).joinToString(" ").ifBlank { "Unknown ($id)" }

            val infoLists = mutableListOf<InfoList>()
            val extras = mutableListOf<Pair<String, String>>()
            for (k in o.keyList().filterNot { it in PERSON_KEYS }) {
                val raw = o.opt(k)
                if (k == "pdf_name" && o.str(k) == name) continue
                if (raw is org.json.JSONArray) {
                    val items = o.strings(k)
                    if (items.isNotEmpty()) infoLists += InfoList(humanize(k), items)
                } else {
                    describe(raw).takeIf { it.isNotBlank() }?.let { extras += humanize(k) to it }
                }
            }
            // Keep the familiar sections in a fixed order ahead of any new list fields.
            infoLists.sortBy { LIST_ORDER.indexOf(it.title).let { i -> if (i < 0) 99 else i } }

            return Person(
                id = id,
                displayName = name,
                givenNames = given,
                surname = surname,
                suffix = o.str("suffix"),
                maidenName = o.str("maiden_name"),
                marriedName = o.str("married_name"),
                nicknames = o.strings("nicknames"),
                aliases = o.strings("aliases").filterNot { it.equals(name, ignoreCase = true) },
                sex = o.str("sex"),
                living = isLiving(o.bool("living"), fact(o.opt("birth")), fact(o.opt("death")), fact(o.opt("burial"))),
                birth = fact(o.opt("birth")),
                death = fact(o.opt("death")),
                burial = fact(o.opt("burial")),
                notes = o.strings("notes"),
                parentLinks = o.items("parents").mapNotNull { p ->
                    when (p) {
                        is String -> ParentLink(null, p, null, null)
                        is JSONObject -> ParentLink(
                            familyId = p.str("family"),
                            parent1 = p.str("father_or_parent1", "parent1", "father", "id"),
                            parent2 = p.str("mother_or_parent2", "parent2", "mother"),
                            confidence = Confidence.parse(p.str("confidence")),
                        )
                        else -> null
                    }
                },
                spouseLinks = o.items("spouses").mapNotNull { s ->
                    when (s) {
                        is String -> SpouseLink(s, null, null, null)
                        is JSONObject -> SpouseLink(
                            personId = s.str("id", "spouse"),
                            familyId = s.str("family"),
                            marriage = fact(s.opt("marriage"))?.takeUnless { it.isEmpty },
                            connector = s.str("connector"),
                        )
                        else -> null
                    }
                },
                childIds = o.items("children").mapNotNull { c ->
                    when (c) {
                        is String -> c
                        is JSONObject -> c.str("id")
                        else -> null
                    }
                },
                sources = sourceRefs(o) + o.items("research_sources").mapNotNull(::sourceRef)
                    .map { if (it.origin == null) it.copy(origin = "research") else it },
                generation = o.int("generation_relative_to_victor"),
                directAncestor = o.obj("direct_ancestor_of_victor")?.let {
                    DirectAncestor(it.str("relationship"), Confidence.parse(it.str("link_confidence", "confidence")))
                },
                possibleDuplicates = (o.items("possible_duplicates") + o.items("duplicate_of")).mapNotNull {
                    when (it) {
                        is String -> it
                        is JSONObject -> it.str("id")
                        else -> null
                    }
                },
                nameTruncated = o.bool("name_truncated_on_card") ?: false,
                origin = o.str("origin"),
                confidence = Confidence.parse(o.str("confidence", "relationship_confidence")),
                infoLists = infoLists,
                extras = extras,
                ancestry = o.obj("ancestry_gedcom")?.let { g ->
                    AncestryInfo(
                        urls = g.strings("person_urls"),
                        sourceTitles = g.strings("sources").distinct(),
                        recordUrls = g.strings("source_records"),
                        mediaCount = g.items("media").size,
                        profiles = g.items("xrefs").size,
                        xrefs = g.strings("xrefs"),
                    )
                },
                externalIds = (o.obj("ancestry_gedcom")?.strings("xrefs").orEmpty() + o.strings("import_xrefs")).distinct(),
                importBatch = o.opt("import_batch").let { (it as? Number)?.toLong() },
                disputes = o.objects("disputed_parents").mapNotNull { it.str("reason") },
                disputedParents = o.objects("disputed_parents").map { d ->
                    DisputedParent(d.str("family"), d.str("father_or_parent1", "parent1"), d.str("mother_or_parent2", "parent2"),
                        d.str("reason"), sourceRefs(d))
                },
                duplicateOf = o.opt("duplicate_of").let { v -> when (v) { is String -> v; is JSONObject -> v.str("id"); else -> null } }?.takeIf { it.isNotBlank() },
            )
        }

        fun family(o: JSONObject): Family? {
            val id = o.str("id") ?: return null
            return Family(
                id = id,
                parent1 = o.str("parent1"),
                parent2 = o.str("parent2"),
                unknownParent = o.str("unknown_parent_placeholder"),
                marriage = fact(o.opt("marriage"))?.takeUnless { it.isEmpty },
                children = o.items("children").mapNotNull { c ->
                    when (c) {
                        is String -> ChildLink(c, null)
                        is JSONObject -> c.str("id")?.let { ChildLink(it, Confidence.parse(c.str("confidence"))) }
                        else -> null
                    }
                },
                connector = o.strings("relationship_connector").firstOrNull(),
                sources = sourceRefs(o),
                notes = o.strings("notes"),
                disputedChildren = o.objects("disputed_children").mapNotNull { c ->
                    c.str("id")?.let { DisputedChild(it, c.str("reason"), c.str("status")) }
                },
            )
        }

        fun event(o: JSONObject): Event? {
            val id = o.str("id") ?: return null
            return Event(
                id = id,
                type = o.str("type") ?: "event",
                date = o.str("date"),
                place = placeName(o.opt("place")),
                participants = o.objects("participants").map {
                    Participant(it.str("id"), it.str("name"), it.str("role"))
                },
                familyId = o.str("family"),
                note = o.str("note", "notes", "description"),
                confidence = Confidence.parse(o.str("confidence")),
                origin = o.str("origin"),
                sources = sourceRefs(o),
                details = o.keyList().filterNot { it in EVENT_KEYS || it in ALT_KEYS }
                    .mapNotNull { k -> describe(o.opt(k)).takeIf { it.isNotBlank() }?.let { humanize(k) to it } },
                alternatives = ALT_KEYS.flatMap { o.strings(it) },
            )
        }
    }

    private fun parseSource(o: JSONObject): Source? {
        val id = o.str("id") ?: return null
        return Source(
            id = id,
            title = o.str("title", "name") ?: id,
            type = o.str("type"),
            author = o.str("author", "publisher"),
            date = o.str("date"),
            url = o.str("url", "link"),
            summary = o.str("summary", "description", "note"),
        )
    }

    private fun parsePlace(o: JSONObject): Place? {
        val name = o.str("name", "full_name", "title", "display_name") ?: return null
        return Place(
            id = o.str("id"),
            name = name,
            details = o.keyList().filterNot { it in setOf("id", "name", "full_name", "title", "display_name") }
                .mapNotNull { k -> describe(o.opt(k)).takeIf { it.isNotBlank() }?.let { humanize(k) to it } },
        )
    }

    private val ALT_KEYS = listOf(
        "alternatives", "alternate_values", "alternate_dates", "alternate_places", "conflicts",
        "conflicting_values", "other_values", "variants",
    )
    private val FACT_KEYS = setOf(
        "date", "date_text", "year", "place", "place_id", "confidence", "origin", "sources", "date_precision",
        "citations", "pdf_value",
    )
    private val EVENT_KEYS = setOf(
        "id", "type", "date", "place", "participants", "family", "note", "notes", "description",
        "confidence", "origin", "sources", "date_precision", "connector", "citations", "year",
    )
    private val PERSON_KEYS = setOf(
        "id", "display_name", "given_names", "given", "surname", "full_name", "name", "maiden_name", "married_name",
        "nicknames", "aliases", "name_truncated_on_card", "sex", "sex_basis", "living", "birth", "death",
        "lifespan_as_shown", "burial", "notes", "parents", "spouses", "children", "sources", "appears_in",
        "generation_relative_to_victor", "direct_ancestor_of_victor", "merge_evidence", "possible_duplicates",
        "suffix", "origin", "confidence", "ancestry_gedcom", "import_xrefs", "import_batch", "lifespan", "duplicate_of", "disputed_parents",
        "living_status", "research_sources", "relationship_confidence",
    )
    private val LIST_ORDER = listOf(
        "Occupations", "Residences", "Military service", "Education", "Religion", "Immigration & naturalization",
        "Other events", "Research findings",
    )
}
