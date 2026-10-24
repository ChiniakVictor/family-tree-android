package com.victor.familytree.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FamilyParserTest {

    private val dataDir = File(System.getProperty("familyData") ?: "../sample-data")

    private fun real(): FamilyData {
        fun f(n: String) = File(dataDir, n).takeIf { it.exists() }?.readText()
        return FamilyParser.parse(f("people.json"), f("families.json"), f("events.json"), f("sources.json"), f("places.json"))
    }

    @Test fun parsesSampleData() {
        if (!File(dataDir, "people.json").exists()) return
        val d = real()
        assertTrue(d.people.isNotEmpty())
        val root = d.person(d.defaultRootId)
        assertNotNull(root)
        val ped = PedigreeLayout.build(d, root!!.id, 8)
        assertTrue(ped.nodes.isNotEmpty())
        val t0 = System.nanoTime()
        val d2 = real().warmUp()
        val ms = (System.nanoTime() - t0) / 1_000_000
        println("Parsed ${d2.people.size} people, ${d2.events.size} events, ${d2.places.size} places in $ms ms; flagged=${d2.flaggedIds.size}")
        d2.people.keys.flatMap { d2.checksFor(it) }.filter { it.severity == DataCheck.Severity.WARNING }
            .groupingBy { it.message.split(' ').take(3).joinToString(" ") }.eachCount()
            .entries.sortedByDescending { it.value }.take(12).forEach { println("check: ${it.key} = ${it.value}") }
        // Living people never expose dates.
        d2.people.values.filter { it.living }.forEach { assertEquals("Living", it.lifespan) }
    }

    @Test fun livingPeopleShowNoDates() {
        val d = FamilyParser.parse(
            """[{"id":"A","full_name":"Ann Test","living":true,"birth":{"date":"12 Mar 1982","place":"Kodiak, Alaska"}}]""",
            null, null, null, null,
        )
        assertEquals("Living", d.person("A")!!.lifespan)
    }

    @Test fun toleratesExtraAndMissingFields() {
        val people = """
            {"people":[
              {"id":"P1","given_names":"John","surname":"Doe","sex":"M",
               "birth":{"date":"ABT 1850","place":"PL1","confidence":"medium","origin":"research","alternatives":["1851"],"citation_note":"census"},
               "death":"1920",
               "occupations":[{"title":"Farmer","date":"1880","place":"Ohio"}],
               "new_research_field":{"a":1},"children":["P2"]},
              {"id":"P2","full_name":"Jane Doe","parents":[{"family":"F1","confidence":"low"}],"origin":"gedcom"},
              {"no_id":true}
            ]}""".trimIndent()
        val families = """[{"id":"F1","parent1":"P1","children":[{"id":"P2","confidence":"low"}],"extra":[1,2]}]"""
        val places = """{"places":[{"id":"PL1","name":"Springfield, Ohio, USA","lat":39.9}]}"""
        val events = """[{"id":"E1","type":"residence","date":"1900","place":{"name":"Columbus"},"participants":[{"id":"P1"}],"weird":null}]"""
        val d = FamilyParser.parse(people, families, events, null, places)
        val p1 = d.person("P1")!!
        assertEquals("John Doe", p1.displayName)
        assertEquals("Springfield, Ohio, USA", p1.birth!!.place)
        assertEquals(Confidence.MEDIUM, p1.birth!!.confidence)
        assertTrue(p1.birth!!.hasConflict)
        assertEquals("1920", p1.death!!.date)
        assertEquals("1850 – 1920", p1.lifespan)
        assertTrue(p1.infoLists.any { it.title == "Occupations" && it.items.single().contains("Farmer") })
        assertTrue(p1.extras.any { it.first == "New research field" })
        assertEquals(listOf("P2"), d.childrenOf("P1").map { it.person.id })
        assertEquals(Confidence.LOW, d.parentsOf("P2").single().confidence)
        assertEquals("Columbus", d.eventsFor("P1").single().place)
        assertFalse(d.people.containsKey(""))
        assertEquals(2, d.people.size)
    }

    @Test fun fullDatesGiveYears() {
        assertEquals(1901, yearOf("12 MAR 1901"))
        assertEquals(1850, yearOf("ABT 1850"))
        assertEquals(1700, yearOf("BET 1700 AND 1710"))
    }
}
