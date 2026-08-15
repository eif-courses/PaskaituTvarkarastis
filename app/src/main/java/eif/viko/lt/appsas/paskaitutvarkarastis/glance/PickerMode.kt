package eif.viko.lt.appsas.paskaitutvarkarastis.glance

/**
 * Which kind of timetable is being browsed or, for a widget, which one it is bound to.
 *
 * The name is what goes into [TimetableWidget.entityTypeKey], so the stored values are
 * "TEACHER", "GROUP" and "CLASSROOM".
 */
enum class PickerMode(val label: String) {
    TEACHER("Dėstytojas"),
    GROUP("Grupė"),
    CLASSROOM("Auditorija");

    companion object {
        /** Unknown or missing values fall back to TEACHER. */
        fun fromNameOrTeacher(name: String?): PickerMode =
            values().firstOrNull { it.name == name } ?: TEACHER
    }
}
