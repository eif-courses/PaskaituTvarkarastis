package eif.viko.lt.appsas.paskaitutvarkarastis.glance

/**
 * Lectures that exist only in the change feed.
 *
 * The department expresses a reschedule as two records: "Paskaitos nėra" in the old slot,
 * and a named record (teacher + room) in the new one. The first matches a lecture the
 * timetable has and strikes it out. The second lands in a slot where the timetable has
 * nothing, so nothing could carry it - and the moved lecture vanished from the widget. This
 * turns such a record into a row of its own: a *new lecture*, the same way the department's
 * TV board shows it in green.
 *
 * The record carries no subject, so the row's title is the teacher's name; the group, room
 * and period go on the meta line as usual. Times come from [PeriodGrid].
 */
object AddedLectures {

    /** Marker in [LecturesDto.type] that tells the row it was synthesised here. */
    const val TYPE = "added"

    /**
     * One synthetic lecture per named change record that no lecture in [feed] matches, for
     * the widget bound to [binding] / [entityLabel]:
     * - GROUP: records for the bound group (the label's first word is the group code).
     *   Any subgroup in the record goes into `groupnames` so the subgroup filter applies.
     * - TEACHER: records whose teacher is the bound teacher, titles ignored.
     * - CLASSROOM: none; classroom widgets do not show changes at all.
     */
    fun synthesize(
        changes: List<ChangeDto>,
        feed: List<LecturesDto>,
        binding: PickerMode,
        entityLabel: String
    ): List<LecturesDto> {
        if (binding == PickerMode.CLASSROOM) return emptyList()
        val boundGroup = entityLabel.trim().substringBefore(" ")
        return changes.mapNotNull { change ->
            if (ChangeMatcher.statusOf(change) != ChangeStatus.CHANGED) return@mapNotNull null
            if (change.destytojas.isBlank()) return@mapNotNull null
            val date = ChangeMatcher.normalizeDate(change.date)
            val (start, end) = PeriodGrid.times(change.paskaita) ?: return@mapNotNull null
            val group = ChangeMatcher.normalizeGroup(change.grupe)
            val base = group.substringBefore(" ")
            if (base.isEmpty()) return@mapNotNull null
            val ours = when (binding) {
                PickerMode.GROUP -> base.equals(boundGroup, ignoreCase = true)
                PickerMode.TEACHER -> TeacherName.same(change.destytojas, entityLabel)
                PickerMode.CLASSROOM -> false
            }
            if (!ours) return@mapNotNull null
            // The slot already has a lecture: that is a room change, handled on its row.
            if (feed.any { it.date.trim() == date && ChangeMatcher.findFor(it, listOf(change)) != null }) {
                return@mapNotNull null
            }
            val subgroup = group.removePrefix(base).trim()
            LecturesDto(
                type = TYPE,
                date = date,
                uniperiod = change.paskaita.trim(),
                starttime = start,
                endtime = end,
                subjectid = change.destytojas.trim().withoutAcademicTitle(),
                classids = listOf(base),
                groupnames = if (subgroup.isEmpty()) emptyList() else listOf(subgroup),
                classroomids = listOf(change.auditorija.trim()).filter { it.isNotEmpty() && it != "-" },
                // The teacher is the title already; an empty list keeps the teacher line off.
                teacherids = emptyList()
            )
        }
    }

    fun LecturesDto.isAdded(): Boolean = type == TYPE
}

/**
 * Teacher names as the two feeds spell them: "Doc. L. Gžegoževskė" in the timetable,
 * "dr. L. Gžegoževskė" in a change record. Titles differ and are not news, so they are
 * stripped on both sides, case-insensitively, before an exact comparison.
 */
object TeacherName {
    fun same(a: String, b: String): Boolean =
        canonical(a).isNotEmpty() && canonical(a).equals(canonical(b), ignoreCase = true)

    private fun canonical(name: String): String =
        name.trim().withoutAcademicTitle().replace(Regex("""\s+"""), " ").trim()
}
