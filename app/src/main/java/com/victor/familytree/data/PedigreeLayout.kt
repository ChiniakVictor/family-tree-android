package com.victor.familytree.data

/**
 * Ancestor chart layout in abstract units: x = generation (0 = root), y = slot index.
 * Leaves take consecutive slots and each person sits midway between their parents,
 * so missing branches don't waste space.
 */
data class PedigreeNode(val person: Person, val generation: Int, val slot: Float, val hasMoreAncestors: Boolean, val disputed: Boolean = false)

data class PedigreeEdge(val child: Int, val parent: Int, val disputed: Boolean = false)

data class Pedigree(val nodes: List<PedigreeNode>, val edges: List<PedigreeEdge>, val slots: Int, val generations: Int)

object PedigreeLayout {
    fun build(data: FamilyData, rootId: String, maxGenerations: Int): Pedigree {
        val root = data.person(rootId) ?: return Pedigree(emptyList(), emptyList(), 0, 0)
        val nodes = mutableListOf<PedigreeNode>()
        val edges = mutableListOf<PedigreeEdge>()
        var nextSlot = 0
        var deepest = 0

        fun place(p: Person, gen: Int, path: Set<String>): Int {
            val (f, m) = data.fatherAndMother(p.id)
            val parents = listOfNotNull(f, m).filter { it.id !in path }
            val expand = gen < maxGenerations - 1
            val parentIdx = if (expand) parents.map { place(it, gen + 1, path + it.id) } else emptyList()
            // Disputed parents are drawn after the real ones with a dashed line; their own ancestors are not expanded.
            val disputed = if (expand) data.disputedParentsOf(p.id).map { it.person }.filter { it.id !in path } else emptyList()
            val disputedIdx = disputed.map { d ->
                nodes += PedigreeNode(d, gen + 1, (nextSlot++).toFloat(), hasMoreAncestors = data.parentsOf(d.id).isNotEmpty(), disputed = true)
                deepest = maxOf(deepest, gen + 1)
                nodes.lastIndex
            }
            val slot = if (parentIdx.isEmpty()) (if (disputedIdx.isEmpty()) (nextSlot++).toFloat() else disputedIdx.map { nodes[it].slot }.average().toFloat())
                else parentIdx.map { nodes[it].slot }.average().toFloat()
            deepest = maxOf(deepest, gen)
            nodes += PedigreeNode(p, gen, slot, hasMoreAncestors = !expand && parents.isNotEmpty())
            val me = nodes.lastIndex
            parentIdx.forEach { edges += PedigreeEdge(me, it) }
            disputedIdx.forEach { edges += PedigreeEdge(me, it, disputed = true) }
            return me
        }
        place(root, 0, setOf(root.id))
        return Pedigree(nodes, edges, nextSlot, deepest + 1)
    }
}
