package eif.viko.lt.appsas.paskaitutvarkarastis.glance

/**
 * The first segment of a lesson row's meta line - the one that says "which cohort".
 *
 * Kept out of TimetableWidget so it stays free of Android types and can be unit tested.
 */
object MetaLead {

    /**
     * Teacher and classroom bindings show the group codes: the reader does not otherwise
     * know who is in front of them. That path is unchanged.
     *
     * A group binding is the group, so the code would repeat on every row and say nothing
     * the header does not. It is dropped, and the subgroup label takes its place - the only
     * field that tells two parallel subgroup lectures at the same time apart. A group
     * lecture with no subgroup gets no lead segment at all rather than the group code:
     * the row reads "Room 104 · Period 1", and the absence itself says "whole group".
     *
     * Labels are rendered as the feed carries them ("I pogr.", "FIN", "DB"), only trimmed
     * and de-blanked; ChangeMatcher's normalisation is for matching, not display.
     */
    fun of(lecture: LecturesDto, binding: PickerMode): String? =
        if (binding == PickerMode.GROUP) {
            lecture.groupnames
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString(", ")
                .takeIf { it.isNotBlank() }
        } else {
            lecture.classids.joinToString(", ").takeIf { it.isNotBlank() }
        }
}
