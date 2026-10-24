package com.victor.familytree.data

import com.victor.familytree.data.DataCheck.Severity.INFO
import com.victor.familytree.data.DataCheck.Severity.WARNING

/** Flags uncertain links and implausible values so they can be shown clearly. */
object DataChecks {

    fun forPerson(data: FamilyData, id: String): List<DataCheck> {
        val p = data.person(id) ?: return emptyList()
        val out = mutableListOf<DataCheck>()

        // Value-level conflicts recorded by research.
        listOf("Birth" to p.birth, "Death" to p.death, "Burial" to p.burial).forEach { (label, f) ->
            if (f != null && f.hasConflict) {
                val years = f.conflictingYears
                out += if (years.isNotEmpty()) {
                    DataCheck(WARNING, "$label year conflict: ${f.date?.let(::yearOf) ?: "?"} vs ${years.joinToString(", ")}")
                } else {
                    val n = f.alternatives.size
                    DataCheck(INFO, "$label: $n other record${if (n == 1) "" else "s"} with a different date or place (same year)")
                }
            }
            if (f?.confidence?.isUncertain == true) out += DataCheck(INFO, "$label is ${f.confidence.label.lowercase()} confidence")
        }
        p.disputes.forEach { out += DataCheck(WARNING, "Disputed parent link: $it") }
        p.confidence?.takeIf { it.isUncertain }?.let { out += DataCheck(INFO, "Identification/link is ${it.label.lowercase()} confidence") }

        // Links.
        data.parentsOf(id).filter { it.confidence?.isUncertain == true }.forEach {
            out += DataCheck(if (it.confidence == Confidence.LOW) WARNING else INFO, "Link to parent ${it.person.displayName} is ${it.confidence!!.label.lowercase()} confidence", it.person.id)
        }
        p.directAncestor?.confidence?.takeIf { it.isUncertain }?.let {
            out += DataCheck(INFO, "Direct-ancestor line to Victor is ${it.label.lowercase()} confidence")
        }
        p.possibleDuplicates.forEach { dup ->
            out += DataCheck(WARNING, "Possible duplicate of ${data.person(dup)?.displayName ?: dup}", dup)
        }
        if (p.nameTruncated) out += DataCheck(INFO, "Name was cut off on the source chart")

        // Dates (never for living people: their dates stay hidden).
        if (!p.living) {
            val b = p.birthYear
            val d = p.deathYear
            if (b != null && d != null) {
                if (d < b) out += DataCheck(WARNING, "Death year $d is before birth year $b")
                else if (d - b > 105) out += DataCheck(WARNING, "Lifespan of ${d - b} years: check dates")
            }
            if (b != null) for (rel in data.parentsOf(id)) {
                val par = rel.person
                if (par.living) continue
                val pb = par.birthYear
                val pd = par.deathYear
                val name = par.displayName
                if (pb != null) {
                    val age = b - pb
                    when {
                        age < 13 -> out += DataCheck(WARNING, "Born when parent $name was $age", par.id)
                        par.sex == "F" && age > 55 -> out += DataCheck(WARNING, "Born when mother $name was $age", par.id)
                        age > 75 -> out += DataCheck(WARNING, "Born when parent $name was $age", par.id)
                    }
                }
                val grace = if (par.sex == "M") 1 else 0
                if (pd != null && b > pd + grace) out += DataCheck(WARNING, "Born $b, after parent $name died ($pd)", par.id)
            }
        }
        return out.distinctBy { it.message }
    }
}
