package eif.viko.lt.appsas.paskaitutvarkarastis.glance

/**
 * One entry from any of the three `.../ids` endpoints. The router returns the same shape for
 * all of them, so a single DTO covers teachers, groups and classrooms.
 *
 * Only groups actually carry `name`; teachers and classrooms return `{id, short}` alone, so
 * `name` defaults to empty rather than being absent.
 */
data class EntityDto(
    val id: String = "",
    val short: String = "",
    val name: String = ""
) {
    /** What to show in the picker: `short` everywhere, with `name` as a fallback. */
    val label: String get() = short.ifBlank { name }.ifBlank { id }
}
