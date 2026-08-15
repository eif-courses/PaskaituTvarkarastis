package eif.viko.lt.appsas.paskaitutvarkarastis.glance

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ChangeMatcherTest {

    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, ChangeDto>>() {}.type

    private fun parse(json: String): Map<String, ChangeDto> = gson.fromJson(json, mapType)

    private fun lecture(
        classids: List<String> = listOf("PI21E"),
        groupnames: List<String> = emptyList(),
        date: String = "2023-09-05",
        uniperiod: String = "3"
    ) = LecturesDto(
        classids = classids,
        groupnames = groupnames,
        date = date,
        uniperiod = uniperiod
    )

    // --- Gson tolerance: paskaita as string vs number -------------------------------------

    @Test
    fun `paskaita given as a JSON string is read as a string`() {
        val changes = parse(
            """{"-A":{"date":"Sep 5, 2023","paskaita":"3","destytojas":"X","auditorija":"201","grupe":"PI21E"}}"""
        )
        assertEquals("3", changes.getValue("-A").paskaita)
    }

    @Test
    fun `paskaita given as a JSON number is read as a string`() {
        val changes = parse(
            """{"-A":{"date":"Sep 5, 2023","paskaita":3,"destytojas":"X","auditorija":"201","grupe":"PI21E"}}"""
        )
        assertEquals("3", changes.getValue("-A").paskaita)
    }

    @Test
    fun `missing fields fall back to empty strings`() {
        val changes = parse("""{"-A":{"paskaita":7}}""")
        val change = changes.getValue("-A")
        assertEquals("7", change.paskaita)
        assertEquals("", change.date)
        assertEquals("", change.grupe)
        assertEquals("", change.auditorija)
        assertEquals("", change.destytojas)
    }

    @Test
    fun `an unknown key such as starCount is ignored`() {
        val changes = parse(
            """{"-A":{"auditorija":"311","date":"Mon Aug 17 2026","destytojas":"M. Gzegozevskis","grupe":"<b>PI23SN</b>","paskaita":"1","starCount":0}}"""
        )
        val change = changes.getValue("-A")
        assertEquals("311", change.auditorija)
        assertEquals("1", change.paskaita)
        assertEquals("Mon Aug 17 2026", change.date)
    }

    // --- normalizeDate -------------------------------------------------------------------

    @Test
    fun `normalizeDate handles JavaScript toDateString output`() {
        assertEquals("2026-08-17", ChangeMatcher.normalizeDate("Mon Aug 17 2026"))
        assertEquals("2026-08-18", ChangeMatcher.normalizeDate("Tue Aug 18 2026"))
    }

    @Test
    fun `normalizeDate handles a single-digit day in toDateString output`() {
        assertEquals("2026-09-04", ChangeMatcher.normalizeDate("Fri Sep 4 2026"))
    }

    @Test
    fun `normalizeDate handles toDateString output carrying a time`() {
        assertEquals("2026-08-17", ChangeMatcher.normalizeDate("Mon Aug 17 2026 00:00:00"))
    }

    @Test
    fun `normalizeDate handles MMM d yyyy`() {
        assertEquals("2023-09-05", ChangeMatcher.normalizeDate("Sep 5, 2023"))
    }

    @Test
    fun `normalizeDate handles MMM dd yyyy`() {
        assertEquals("2023-09-05", ChangeMatcher.normalizeDate("Sep 05, 2023"))
    }

    @Test
    fun `normalizeDate handles slash format`() {
        assertEquals("2023-09-05", ChangeMatcher.normalizeDate("2023/09/05"))
    }

    @Test
    fun `normalizeDate handles dotted format`() {
        assertEquals("2023-09-05", ChangeMatcher.normalizeDate("05.09.2023"))
    }

    @Test
    fun `normalizeDate passes through an already ISO date`() {
        assertEquals("2023-09-05", ChangeMatcher.normalizeDate("2023-09-05"))
    }

    @Test
    fun `normalizeDate returns the input unchanged when nothing parses`() {
        assertEquals("not a date", ChangeMatcher.normalizeDate("not a date"))
        assertEquals("", ChangeMatcher.normalizeDate(""))
    }

    // --- normalizeGroup ------------------------------------------------------------------

    @Test
    fun `normalizeGroup strips bold tags`() {
        assertEquals("PI21E", ChangeMatcher.normalizeGroup("<b>PI21E</b>"))
    }

    @Test
    fun `normalizeGroup strips parentheses and shortens pogrupis`() {
        assertEquals("PI21E 2 pogr.", ChangeMatcher.normalizeGroup("<b>PI21E</b> (2 pogrupis)"))
    }

    @Test
    fun `normalizeGroup squeezes whitespace and trims`() {
        assertEquals("PI21E 1 pogr.", ChangeMatcher.normalizeGroup("  PI21E  ( 1  pogrupis )  "))
    }

    // --- findFor -------------------------------------------------------------------------

    @Test
    fun `findFor matches on the base group`() {
        val changes = listOf(
            ChangeDto(date = "Sep 5, 2023", paskaita = "3", auditorija = "201", grupe = "<b>PI21E</b>")
        )
        assertEquals(changes[0], ChangeMatcher.findFor(lecture(), changes))
    }

    @Test
    fun `findFor matches a subgroup listed in groupnames`() {
        val changes = listOf(
            ChangeDto(
                date = "2023/09/05",
                paskaita = "3",
                auditorija = "305",
                grupe = "<b>PI21E</b> (2 pogrupis)"
            )
        )
        val found = ChangeMatcher.findFor(
            lecture(groupnames = listOf("2 pogr.")),
            changes
        )
        assertNotNull(found)
        assertEquals("305", found!!.auditorija)
    }

    @Test
    fun `findFor does not match a different lecture number`() {
        val changes = listOf(
            ChangeDto(date = "Sep 5, 2023", paskaita = "4", auditorija = "201", grupe = "PI21E")
        )
        assertNull(ChangeMatcher.findFor(lecture(uniperiod = "3"), changes))
    }

    @Test
    fun `findFor does not match a different date`() {
        val changes = listOf(
            ChangeDto(date = "Sep 6, 2023", paskaita = "3", auditorija = "201", grupe = "PI21E")
        )
        assertNull(ChangeMatcher.findFor(lecture(date = "2023-09-05"), changes))
    }

    @Test
    fun `findFor does not match a change filed for another subgroup`() {
        val changes = listOf(
            ChangeDto(
                date = "Sep 5, 2023",
                paskaita = "3",
                auditorija = "201",
                grupe = "<b>PI23E</b> (I pogrupis)"
            )
        )
        assertNull(
            ChangeMatcher.findFor(
                lecture(classids = listOf("PI23E"), groupnames = listOf("II pogr.")),
                changes
            )
        )
    }

    @Test
    fun `findFor matches a change filed for this subgroup`() {
        val changes = listOf(
            ChangeDto(
                date = "Sep 5, 2023",
                paskaita = "3",
                auditorija = "201",
                grupe = "<b>PI23E</b> (I pogrupis)"
            )
        )
        assertEquals(
            changes[0],
            ChangeMatcher.findFor(
                lecture(classids = listOf("PI23E"), groupnames = listOf("I pogr.")),
                changes
            )
        )
    }

    @Test
    fun `findFor matches a whole-group change regardless of subgroup`() {
        val changes = listOf(
            ChangeDto(date = "Sep 5, 2023", paskaita = "3", auditorija = "201", grupe = "<b>PI23E</b>")
        )
        assertEquals(
            changes[0],
            ChangeMatcher.findFor(
                lecture(classids = listOf("PI23E"), groupnames = listOf("II pogr.")),
                changes
            )
        )
    }

    @Test
    fun `findFor ignores blank groupnames entries`() {
        val changes = listOf(
            ChangeDto(
                date = "Sep 5, 2023",
                paskaita = "3",
                auditorija = "201",
                grupe = "PI23E (I pogrupis)"
            )
        )
        assertEquals(
            changes[0],
            ChangeMatcher.findFor(
                lecture(classids = listOf("PI23E"), groupnames = listOf("  ")),
                changes
            )
        )
    }

    @Test
    fun `findFor does not match a different base group`() {
        val changes = listOf(
            ChangeDto(date = "Sep 5, 2023", paskaita = "3", auditorija = "201", grupe = "<b>PI21D</b>")
        )
        assertNull(ChangeMatcher.findFor(lecture(classids = listOf("PI21E")), changes))
    }

    @Test
    fun `findFor uses only the part of classid before the first space`() {
        val changes = listOf(
            ChangeDto(date = "05.09.2023", paskaita = "3", auditorija = "201", grupe = "PI21E")
        )
        assertEquals(
            changes[0],
            ChangeMatcher.findFor(lecture(classids = listOf("PI21E 1 pogr.")), changes)
        )
    }

    // --- end to end against real Firebase records ----------------------------------------

    /** The exact payload observed under user-posts, including the extra starCount key. */
    private val realChanges: List<ChangeDto> = parse(
        """
        {
          "-A": {"auditorija":"311","date":"Mon Aug 17 2026","destytojas":"M. Gzegozevskis",
                 "grupe":"<b>PI23SN</b>","paskaita":"1","starCount":0},
          "-B": {"auditorija":"320","date":"Tue Aug 18 2026","destytojas":"M. Gzegozevskis",
                 "grupe":"<b>KS25</b>","paskaita":"2","starCount":0}
        }
        """.trimIndent()
    ).values.toList()

    @Test
    fun `real record matches the PI23SN lecture and reports CHANGED`() {
        val found = ChangeMatcher.findFor(
            LecturesDto(
                date = "2026-08-17",
                uniperiod = "1",
                classids = listOf("PI23SN"),
                groupnames = emptyList()
            ),
            realChanges
        )
        assertNotNull(found)
        assertEquals("311", found!!.auditorija)
        assertEquals(ChangeStatus.CHANGED, ChangeMatcher.statusOf(found))
    }

    @Test
    fun `real record matches the KS25 lecture and reports CHANGED`() {
        val found = ChangeMatcher.findFor(
            LecturesDto(
                date = "2026-08-18",
                uniperiod = "2",
                classids = listOf("KS25"),
                groupnames = emptyList()
            ),
            realChanges
        )
        assertNotNull(found)
        assertEquals("320", found!!.auditorija)
        assertEquals(ChangeStatus.CHANGED, ChangeMatcher.statusOf(found))
    }

    @Test
    fun `the PI23SN record does not match the KS25 lecture`() {
        val pi23snOnly = realChanges.filter { it.grupe.contains("PI23SN") }
        assertNull(
            ChangeMatcher.findFor(
                LecturesDto(
                    date = "2026-08-18",
                    uniperiod = "2",
                    classids = listOf("KS25"),
                    groupnames = emptyList()
                ),
                pi23snOnly
            )
        )
    }

    // --- statusOf ------------------------------------------------------------------------

    @Test
    fun `statusOf reports a cancelled lecture`() {
        assertEquals(ChangeStatus.CANCELLED, ChangeMatcher.statusOf(ChangeDto(auditorija = " - ")))
    }

    @Test
    fun `statusOf reports a changed lecture`() {
        assertEquals(ChangeStatus.CHANGED, ChangeMatcher.statusOf(ChangeDto(auditorija = "201")))
    }

    @Test
    fun `statusOf treats a dashed room as cancelled even with a real teacher name`() {
        assertEquals(
            ChangeStatus.CANCELLED,
            ChangeMatcher.statusOf(
                ChangeDto(auditorija = "-", destytojas = "M. Gžegoževskis")
            )
        )
    }

    /** The room is not reliably cleared, so a stale one must not mask the marker. */
    @Test
    fun `statusOf treats the marker as cancelled even with a stale room number`() {
        assertEquals(
            ChangeStatus.CANCELLED,
            ChangeMatcher.statusOf(
                ChangeDto(auditorija = "311", destytojas = "Paskaitos nėra")
            )
        )
    }

    @Test
    fun `statusOf treats the marker plus a dashed room as cancelled`() {
        assertEquals(
            ChangeStatus.CANCELLED,
            ChangeMatcher.statusOf(
                ChangeDto(auditorija = "-", destytojas = "Paskaitos nėra")
            )
        )
    }

    @Test
    fun `statusOf treats a real teacher and a room number as changed`() {
        assertEquals(
            ChangeStatus.CHANGED,
            ChangeMatcher.statusOf(
                ChangeDto(auditorija = "311", destytojas = "M. Gžegoževskis")
            )
        )
    }

    /**
     * A stale room is kept on every case so the marker is the only thing that can trigger
     * the cancellation.
     */
    @Test
    fun `statusOf accepts realistic typing variation of the cancellation marker`() {
        listOf(
            "Paskaitos nėra",
            "Paskaitos nera",
            "paskaitos NERA",
            "PASKAITOS NĖRA",
            "Paskaitos  nera",
            " Paskaitos nera ",
            "Paskaitos\tnera",
            "Paskaitos\nnera",
            "Paskaitos nėra (liga)",
            "Paskaitos nera (liga)"
        ).forEach { destytojas ->
            assertEquals(
                "expected CANCELLED for destytojas=<$destytojas>",
                ChangeStatus.CANCELLED,
                ChangeMatcher.statusOf(ChangeDto(auditorija = "311", destytojas = destytojas))
            )
        }
    }

    /** Exact after folding, so a near-miss stays CHANGED rather than being guessed at. */
    @Test
    fun `statusOf does not treat near misses as cancellation`() {
        listOf(
            "M. Gžegoževskis",
            "Paskaita nera",
            "Paskaitos",
            "nera",
            ""
        ).forEach { destytojas ->
            assertEquals(
                "expected CHANGED for destytojas=<$destytojas>",
                ChangeStatus.CHANGED,
                ChangeMatcher.statusOf(ChangeDto(auditorija = "311", destytojas = destytojas))
            )
        }
    }
}
