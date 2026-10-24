package com.victor.familytree.data

/** One visible row of the descendant outline. */
data class DescendantRow(
    val person: Person,
    val depth: Int,
    val spouses: List<Person>,
    val childCount: Int,
    val confidence: Confidence?,
)

object DescendantTree {
    /** Depth-first outline from [rootId]; children of ids in [collapsed] are skipped. */
    fun rows(data: FamilyData, rootId: String, collapsed: Set<String>, maxDepth: Int = 30): List<DescendantRow> {
        val root = data.person(rootId) ?: return emptyList()
        val out = mutableListOf<DescendantRow>()
        val seen = mutableSetOf<String>()
        fun visit(p: Person, depth: Int, conf: Confidence?) {
            if (!seen.add(p.id)) return
            val kids = data.childrenOf(p.id)
            out += DescendantRow(p, depth, data.spousesOf(p.id).map { it.person }, kids.size, conf)
            if (p.id in collapsed || depth >= maxDepth) return
            kids.forEach { visit(it.person, depth + 1, it.confidence) }
        }
        visit(root, 0, null)
        return out
    }

    fun count(data: FamilyData, rootId: String): Int = rows(data, rootId, emptySet()).size - 1
}
