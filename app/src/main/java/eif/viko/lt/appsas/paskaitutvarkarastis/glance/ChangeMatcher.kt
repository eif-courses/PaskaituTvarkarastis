package eif.viko.lt.appsas.paskaitutvarkarastis.glance

import java.text.Normalizer
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

enum class ChangeStatus { CHANGED, CANCELLED }

object ChangeMatcher {

    private const val TARGET_PATTERN = "yyyy-MM-dd"

    /**
     * What the study department writes into `destytojas` when a lecture is cancelled.
     *
     * Deliberately NOT a string resource and NOT translated. This is feed data, not UI text:
     * it is compared against what the department types into Firebase, and the department
     * types Lithuanian regardless of the phone's language. The text shown to the user for a
     * cancelled lecture is a separate resource, `R.string.lesson_cancelled`.
     */
    const val CANCELLED_MARKER = "Paskaitos nėra"

    // Locale.US throughout: Firebase stores JavaScript Date.prototype.toDateString()
    // output, whose weekday and month abbreviations are English whatever the app locale is.
    private val INPUT_PATTERNS = listOf(
        "EEE MMM d yyyy",
        "EEE MMM d yyyy HH:mm:ss",
        "MMM d, yyyy",
        "MMM dd, yyyy",
        "yyyy/MM/dd",
        "dd.MM.yyyy"
    )

    private val ALREADY_ISO = Regex("""\d{4}-\d{2}-\d{2}""")
    private val WHITESPACE = Regex("""\s+""")

    // Marker folding only. Kept separate from WHITESPACE so the group rule is unaffected;
    //   is included because this data is typed into an HTML form.
    private val MARKER_WHITESPACE = Regex("""[\s ]+""")
    private val COMBINING_MARKS = Regex("""\p{Mn}+""")

    private val FOLDED_CANCELLED_MARKER = foldForMarker(CANCELLED_MARKER)

    /** Strips bold tags and parentheses, shortens "pogrupis" and squeezes whitespace. */
    fun normalizeGroup(raw: String): String =
        raw.replace("<b>", "", ignoreCase = true)
            .replace("</b>", "", ignoreCase = true)
            .replace("(", " ")
            .replace(")", " ")
            .replace("pogrupis", "pogr.", ignoreCase = true)
            .replace(WHITESPACE, " ")
            .trim()

    /** Converts a Firebase date into yyyy-MM-dd (UTC). Returns [raw] unchanged if nothing parses. */
    fun normalizeDate(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return raw
        if (ALREADY_ISO.matches(trimmed)) return trimmed

        for (pattern in INPUT_PATTERNS) {
            val parsed = parseUtc(trimmed, pattern) ?: continue
            return formatter(TARGET_PATTERN).format(parsed)
        }
        return raw
    }

    fun findFor(lecture: LecturesDto, changes: List<ChangeDto>): ChangeDto? =
        changes.firstOrNull { change ->
            normalizeDate(change.date) == lecture.date.trim() &&
                change.paskaita.trim() == lecture.uniperiod.trim() &&
                groupMatches(lecture, change)
        }

    /**
     * A cancellation shows up two ways: a dash for the room, or the marker written into the
     * teacher field. The room is not reliably cleared when the marker is used, so a stale
     * room number must not be allowed to mask a cancellation.
     */
    fun statusOf(change: ChangeDto): ChangeStatus {
        val cancelled = change.auditorija.trim() == "-" ||
            foldForMarker(change.destytojas)
                .contains(FOLDED_CANCELLED_MARKER, ignoreCase = true)

        return if (cancelled) ChangeStatus.CANCELLED else ChangeStatus.CHANGED
    }

    /**
     * Strips diacritics and normalises whitespace so the marker survives hand typing:
     * "Paskaitos nera" and "Paskaitos  nėra" both fold to the same text.
     *
     * NFD decomposition plus dropping combining marks, rather than a hand-written fold —
     * every Lithuanian diacritic decomposes, so ė/ž/š/ų/ū/į/ą/č are all covered without
     * enumerating them. Comparison stays exact after folding: no edit distance, so a
     * near-miss like "Paskaita nera" fails rather than guessing a lecture is cancelled.
     */
    private fun foldForMarker(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .replace(MARKER_WHITESPACE, " ")
            .trim()

    private fun groupMatches(lecture: LecturesDto, change: ChangeDto): Boolean {
        val base = lecture.classids.firstOrNull()?.trim()?.substringBefore(" ").orEmpty()
        if (base.isEmpty()) return false

        val group = normalizeGroup(change.grupe)
        if (group.substringBefore(" ") != base) return false

        // A change filed for the whole group applies to every subgroup's lecture.
        if (group == base) return true

        val subgroups = lecture.groupnames
            .map { normalizeGroup(it) }
            .filter { it.isNotBlank() }

        // Without subgroups on the lecture there is nothing finer to distinguish, so any
        // change under the same base applies. Otherwise the subgroup has to line up.
        return if (subgroups.isEmpty()) {
            group.startsWith("$base ")
        } else {
            subgroups.any { group == "$base $it" }
        }
    }

    private fun parseUtc(value: String, pattern: String): java.util.Date? {
        val position = ParsePosition(0)
        val parsed = formatter(pattern).parse(value, position) ?: return null
        // Reject partial matches such as "2023/09/05" swallowed by a shorter pattern.
        return if (position.index == value.length) parsed else null
    }

    private fun formatter(pattern: String) =
        SimpleDateFormat(pattern, Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }
}
