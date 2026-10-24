package com.victor.familytree.data.gedcom

import java.nio.charset.Charset
import java.text.Normalizer

/** One GEDCOM line with its sub-lines. CONC/CONT are folded into [value]. */
class GedNode(val level: Int, val xref: String?, val tag: String, var value: String) {
    val children = mutableListOf<GedNode>()
    fun first(tag: String): GedNode? = children.firstOrNull { it.tag == tag }
    fun all(tag: String): List<GedNode> = children.filter { it.tag == tag }
    fun v(tag: String): String? = first(tag)?.value?.trim()?.takeIf { it.isNotEmpty() }
    /** A pointer value like @I1@ (GEDCOM 7 uses @VOID@ for "none"). */
    val pointer: String? get() = value.trim().takeIf { it.length > 2 && it.startsWith("@") && it.endsWith("@") && it != "@VOID@" }
}

data class GedEvent(val date: String?, val place: String?, val sources: List<String>, val note: String?)

data class GedIndi(
    val xref: String,
    val given: String?,
    val surname: String?,
    val fullName: String,
    val altNames: List<String>,
    val sex: String?,
    val birth: GedEvent?,
    val death: GedEvent?,
    val burial: GedEvent?,
    val otherEvents: List<Pair<String, GedEvent>>,
    val famc: List<String>,
    val fams: List<String>,
    val notes: List<String>,
    val sources: List<String>,
    val objects: List<String>,
    /** True when the file itself marks the person as living/private (e.g. Ancestry "Living" placeholders). */
    val markedLiving: Boolean,
) {
    val birthYear: Int? get() = yearOf(birth?.date)
    val deathYear: Int? get() = yearOf(death?.date)
    val isDeceased: Boolean get() = death != null || burial != null
}

data class GedFam(val xref: String, val husband: String?, val wife: String?, val children: List<String>, val marriage: GedEvent?)
data class GedSource(val xref: String, val title: String?, val author: String?, val publication: String?)
data class GedObject(val xref: String, val file: String?, val title: String?)

data class GedcomFile(
    val version: String?,
    val charset: String,
    val sourceSystem: String?,
    val individuals: Map<String, GedIndi>,
    val families: Map<String, GedFam>,
    val sources: Map<String, GedSource>,
    val notes: Map<String, String>,
    val objects: Map<String, GedObject>,
) {
    fun parentsOf(xref: String): List<GedIndi> =
        individuals[xref]?.famc.orEmpty().mapNotNull { families[it] }
            .flatMap { listOfNotNull(it.husband, it.wife) }.mapNotNull { individuals[it] }
    fun spousesOf(xref: String): List<GedIndi> =
        individuals[xref]?.fams.orEmpty().mapNotNull { families[it] }
            .mapNotNull { if (it.husband == xref) it.wife else it.husband }.mapNotNull { individuals[it] }
    fun childrenOf(xref: String): List<GedIndi> =
        individuals[xref]?.fams.orEmpty().mapNotNull { families[it] }.flatMap { it.children }.mapNotNull { individuals[it] }
}

private val YEAR = Regex("""\b(\d{3,4})\b""")
fun yearOf(date: String?): Int? = date?.let { d -> YEAR.findAll(d).map { it.value.toInt() }.firstOrNull { it in 500..2100 } }

object Gedcom {
    /** Decodes GEDCOM bytes: BOM (UTF-8/UTF-16LE/BE), the HEAD CHAR tag (UTF-8, UNICODE, ANSEL, ASCII, ANSI). */
    fun decode(bytes: ByteArray): Pair<String, String> {
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE) to "UTF-16LE"
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE) to "UTF-16BE"
        // UTF-16 without BOM: "0\u0000 \u0000H" pattern
        if (bytes.size >= 4 && bytes[1] == 0.toByte() && bytes[3] == 0.toByte()) return String(bytes, Charsets.UTF_16LE) to "UTF-16LE"
        if (bytes.size >= 4 && bytes[0] == 0.toByte() && bytes[2] == 0.toByte()) return String(bytes, Charsets.UTF_16BE) to "UTF-16BE"
        val off = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0
        val head = String(bytes, off, minOf(bytes.size - off, 4000), Charsets.ISO_8859_1)
        val char = Regex("""(?m)^1\s+CHAR\s+(\S+)""").find(head)?.groupValues?.get(1)?.uppercase()
        return when {
            off == 3 -> String(bytes, off, bytes.size - off, Charsets.UTF_8) to "UTF-8"
            char == "ANSEL" -> Ansel.decode(bytes, off) to "ANSEL"
            char == "ANSI" || char == "WINDOWS-1252" || char == "CP1252" -> String(bytes, off, bytes.size - off, Charset.forName("windows-1252")) to "ANSI"
            char == "UNICODE" -> String(bytes, off, bytes.size - off, Charsets.UTF_16LE) to "UTF-16LE"
            else -> String(bytes, off, bytes.size - off, Charsets.UTF_8) to (char ?: "UTF-8")   // GEDCOM 7 is always UTF-8
        }
    }

    fun parse(bytes: ByteArray): GedcomFile {
        val (text, charset) = decode(bytes)
        return parseText(text, charset)
    }

    private val LINE = Regex("""^\s*(\d+)\s+(?:(@[^@]+@)\s+)?([A-Za-z0-9_]+)(?:\s(.*))?$""")

    fun parseText(text: String, charset: String = "UTF-8"): GedcomFile {
        val roots = mutableListOf<GedNode>()
        val stack = ArrayDeque<GedNode>()
        for (raw in text.lineSequence()) {
            val line = raw.trimEnd('\r', '\n').trimStart('\uFEFF')
            if (line.isBlank()) continue
            val m = LINE.find(line) ?: continue
            val level = m.groupValues[1].toInt()
            val tag = m.groupValues[3].uppercase()
            val value = m.groupValues[4].replace("@@", "@")
            while (stack.isNotEmpty() && stack.last().level >= level) stack.removeLast()
            val parent = stack.lastOrNull()
            if (parent != null && (tag == "CONC" || tag == "CONT")) {
                parent.value += if (tag == "CONT") "\n$value" else value
                continue
            }
            val node = GedNode(level, m.groupValues[2].ifEmpty { null }, tag, value)
            if (parent == null) roots += node else parent.children += node
            stack.addLast(node)
        }
        val head = roots.firstOrNull { it.tag == "HEAD" }
        val notes = roots.filter { (it.tag == "NOTE" || it.tag == "SNOTE") && it.xref != null }.associate { it.xref!! to it.value.trim() }
        fun noteText(n: GedNode): String? = (n.pointer?.let { notes[it] } ?: n.value.trim()).takeIf { it.isNotEmpty() }
        fun event(n: GedNode?): GedEvent? = n?.let {
            GedEvent(
                date = it.v("DATE"),
                place = it.v("PLAC"),
                sources = it.all("SOUR").mapNotNull { s -> s.pointer ?: s.value.trim().takeIf(String::isNotEmpty) },
                note = it.all("NOTE").mapNotNull(::noteText).joinToString("\n").ifBlank { null },
            )
        }
        val indis = roots.filter { it.tag == "INDI" && it.xref != null }.map { n ->
            val names = n.all("NAME")
            val primary = names.firstOrNull()
            val rawName = primary?.value.orEmpty()
            val surname = primary?.v("SURN") ?: Regex("/([^/]*)/").find(rawName)?.groupValues?.get(1)?.trim()?.ifEmpty { null }
            val given = primary?.v("GIVN") ?: rawName.substringBefore('/').trim().ifEmpty { null }
            val full = listOfNotNull(given, surname).joinToString(" ").ifBlank { rawName.replace("/", "").trim() }.ifBlank { "Unknown" }
            val living = n.first("_LIVING") != null || n.v("RESN")?.lowercase() in setOf("privacy", "confidential") ||
                given.equals("Living", true) || rawName.trim().equals("Living", true)
            GedIndi(
                xref = n.xref!!,
                given = given, surname = surname, fullName = full,
                altNames = names.drop(1).map { it.value.replace("/", "").replace(Regex("\\s+"), " ").trim() }.filter { it.isNotEmpty() },
                sex = n.v("SEX")?.uppercase()?.take(1),
                birth = event(n.first("BIRT") ?: n.first("CHR") ?: n.first("BAPM")),
                death = event(n.first("DEAT")),
                burial = event(n.first("BURI") ?: n.first("CREM")),
                otherEvents = n.children.filter { it.tag in OTHER_EVENTS }.mapNotNull { e -> event(e)?.let { OTHER_EVENTS.getValue(e.tag) to it } },
                famc = n.all("FAMC").mapNotNull { it.pointer },
                fams = n.all("FAMS").mapNotNull { it.pointer },
                notes = n.all("NOTE").mapNotNull(::noteText) + n.all("SNOTE").mapNotNull(::noteText),
                sources = n.all("SOUR").mapNotNull { it.pointer },
                objects = n.all("OBJE").mapNotNull { it.pointer ?: it.v("FILE") },
                markedLiving = living,
            )
        }.associateBy { it.xref }
        val fams = roots.filter { it.tag == "FAM" && it.xref != null }.map { n ->
            GedFam(n.xref!!, n.first("HUSB")?.pointer, n.first("WIFE")?.pointer, n.all("CHIL").mapNotNull { it.pointer }, event(n.first("MARR")))
        }.associateBy { it.xref }
        val sources = roots.filter { it.tag == "SOUR" && it.xref != null }.map { n ->
            GedSource(n.xref!!, n.v("TITL") ?: n.v("ABBR"), n.v("AUTH"), n.v("PUBL"))
        }.associateBy { it.xref }
        val objects = roots.filter { it.tag == "OBJE" && it.xref != null }.map { n ->
            GedObject(n.xref!!, n.v("FILE"), n.v("TITL") ?: n.first("FILE")?.v("TITL"))
        }.associateBy { it.xref }
        return GedcomFile(
            version = head?.first("GEDC")?.v("VERS"),
            charset = charset,
            sourceSystem = head?.v("SOUR"),
            individuals = indis, families = fams, sources = sources, notes = notes, objects = objects,
        )
    }

    private val OTHER_EVENTS = mapOf(
        "RESI" to "Residence", "OCCU" to "Occupation", "MARR" to "Marriage", "IMMI" to "Immigration",
        "EMIG" to "Emigration", "NATU" to "Naturalization", "CENS" to "Census", "MILI" to "Military", "EVEN" to "Event",
    )
}

/** ANSEL (Z39.47) decoder: spacing specials and combining diacritics (which precede their base letter). */
object Ansel {
    private val SPACING = mapOf(
        0xA1 to "\u0141", 0xA2 to "\u00D8", 0xA3 to "\u0110", 0xA4 to "\u00DE", 0xA5 to "\u00C6", 0xA6 to "\u0152",
        0xA7 to "\u02B9", 0xA8 to "\u00B7", 0xA9 to "\u266D", 0xAA to "\u00AE", 0xAB to "\u00B1", 0xAC to "\u01A0",
        0xAD to "\u01AF", 0xAE to "\u02BC", 0xB0 to "\u02BB", 0xB1 to "\u0142", 0xB2 to "\u00F8", 0xB3 to "\u0111",
        0xB4 to "\u00FE", 0xB5 to "\u00E6", 0xB6 to "\u0153", 0xB7 to "\u02BA", 0xB8 to "\u0131", 0xB9 to "\u00A3",
        0xBA to "\u00F0", 0xBC to "\u01A1", 0xBD to "\u01B0", 0xC0 to "\u00B0", 0xC1 to "\u2113", 0xC2 to "\u2117",
        0xC3 to "\u00A9", 0xC4 to "\u266F", 0xC5 to "\u00BF", 0xC6 to "\u00A1", 0xC7 to "\u00DF", 0xC8 to "\u20AC",
    )
    private val COMBINING = mapOf(
        0xE0 to '\u0309', 0xE1 to '\u0300', 0xE2 to '\u0301', 0xE3 to '\u0302', 0xE4 to '\u0303', 0xE5 to '\u0304',
        0xE6 to '\u0306', 0xE7 to '\u0307', 0xE8 to '\u0308', 0xE9 to '\u030C', 0xEA to '\u030A', 0xEB to '\uFE20',
        0xEC to '\uFE21', 0xED to '\u0315', 0xEE to '\u030B', 0xEF to '\u0310', 0xF0 to '\u0327', 0xF1 to '\u0328',
        0xF2 to '\u0323', 0xF3 to '\u0324', 0xF4 to '\u0325', 0xF5 to '\u0333', 0xF6 to '\u0332', 0xF7 to '\u0326',
        0xF8 to '\u031C', 0xF9 to '\u032E', 0xFA to '\uFE22', 0xFB to '\uFE23', 0xFE to '\u0313',
    )

    fun decode(bytes: ByteArray, offset: Int = 0): String {
        val sb = StringBuilder()
        val pending = StringBuilder()
        var i = offset
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            when {
                b < 0x80 -> { sb.append(b.toChar()); if (pending.isNotEmpty()) { sb.append(pending); pending.clear() } }
                b in COMBINING -> pending.append(COMBINING.getValue(b))
                else -> { sb.append(SPACING[b] ?: "\uFFFD"); if (pending.isNotEmpty()) { sb.append(pending); pending.clear() } }
            }
            i++
        }
        return Normalizer.normalize(sb, Normalizer.Form.NFC)
    }
}
