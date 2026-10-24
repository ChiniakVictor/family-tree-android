package com.victor.familytree.data.gedcom

import com.victor.familytree.data.FamilyData
import com.victor.familytree.data.Fact
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Writes the merged tree as GEDCOM 5.5.1 (UTF-8). Living people are exported with names and links only (RESN privacy). */
object GedcomExport {
    fun export(data: FamilyData, appVersion: String): String {
        val sb = StringBuilder()
        fun line(level: Int, tag: String, value: String? = null, xref: String? = null) {
            val v = if (value != null && Regex("^@[^@]+@$").matches(value)) value else value?.replace("\r", "")?.replace("@", "@@")
            val parts = (v ?: "").split('\n')
            fun emit(lv: Int, t: String, text: String, x: String?) {
                var rest = text
                var first = true
                do {
                    val chunk = rest.take(240); rest = rest.drop(240)
                    if (first) sb.append(lv).append(' ').append(if (x != null) "$x " else "").append(t).append(if (chunk.isNotEmpty() || value != null && chunk.isNotEmpty()) " $chunk" else "").append('\n')
                    else sb.append(lv + 1).append(" CONC ").append(chunk).append('\n')
                    first = false
                } while (rest.isNotEmpty())
            }
            emit(level, tag, parts[0], xref)
            parts.drop(1).forEach { emit(level + 1, "CONT", it, null) }
        }
        fun ptr(id: String) = "@" + id.replace(Regex("[^A-Za-z0-9_]"), "_") + "@"

        line(0, "HEAD")
        line(1, "SOUR", "FAMILY_TREE_APP"); line(2, "VERS", appVersion); line(2, "NAME", "Family Tree (Android)")
        line(1, "DATE", LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)).uppercase())
        line(1, "GEDC"); line(2, "VERS", "5.5.1"); line(2, "FORM", "LINEAGE-LINKED")
        line(1, "CHAR", "UTF-8")
        line(1, "SUBM", "@SUBM1@")
        line(0, "SUBM", xref = "@SUBM1@"); line(1, "NAME", "Family Tree app export")

        val famsOf = data.families.values.flatMap { f -> listOfNotNull(f.parent1, f.parent2).map { it to f.id } }.groupBy({ it.first }, { it.second })
        val famcOf = data.families.values.flatMap { f -> f.children.map { it.id to f.id } }.groupBy({ it.first }, { it.second })

        fun fact(tag: String, f: Fact?) {
            if (f == null || f.isEmpty) return
            line(1, tag)
            f.date?.let { line(2, "DATE", it) }
            f.place?.let { line(2, "PLAC", it) }
            f.sources.mapNotNull { it.sourceId }.filter { it in data.sources }.distinct().forEach { line(2, "SOUR", ptr(it)) }
            if (f.alternatives.isNotEmpty()) line(2, "NOTE", "Alternate values: " + f.alternatives.joinToString("; "))
        }

        for (p in data.sortedPeople) {
            line(0, "INDI", xref = ptr(p.id))
            val given = p.givenNames ?: p.displayName.substringBeforeLast(' ', p.displayName)
            line(1, "NAME", "$given /${p.surname ?: ""}/")
            p.givenNames?.let { line(2, "GIVN", it) }
            p.surname?.let { line(2, "SURN", it) }
            p.aliases.forEach { line(1, "NAME", it); line(2, "TYPE", "aka") }
            p.sex?.let { line(1, "SEX", it.take(1).uppercase()) }
            if (p.living) {
                line(1, "RESN", "privacy")
            } else {
                fact("BIRT", p.birth); fact("DEAT", p.death); fact("BURI", p.burial)
                p.notes.forEach { line(1, "NOTE", it) }
            }
            famcOf[p.id].orEmpty().distinct().forEach { line(1, "FAMC", ptr(it)) }
            famsOf[p.id].orEmpty().distinct().forEach { line(1, "FAMS", ptr(it)) }
            p.sources.mapNotNull { it.sourceId }.filter { it in data.sources }.distinct().forEach { line(1, "SOUR", ptr(it)) }
            line(1, "REFN", p.id)
        }
        for (f in data.families.values) {
            line(0, "FAM", xref = ptr(f.id))
            val a = data.person(f.parent1); val b = data.person(f.parent2)
            val (h, w) = if (a?.sex == "F" || b?.sex == "M") b to a else a to b
            h?.let { line(1, "HUSB", ptr(it.id)) }
            w?.let { line(1, "WIFE", ptr(it.id)) }
            f.children.filter { data.person(it.id) != null }.forEach { line(1, "CHIL", ptr(it.id)) }
            if (listOfNotNull(a, b).none { it.living }) fact("MARR", f.marriage)
        }
        for (s in data.sources.values) {
            line(0, "SOUR", xref = ptr(s.id))
            line(1, "TITL", s.title)
            s.author?.let { line(1, "AUTH", it) }
            s.url?.let { line(1, "PUBL", it) }
        }
        line(0, "TRLR")
        return sb.toString()
    }
}
