package eif.viko.lt.appsas.paskaitutvarkarastis.glance

/**
 * "Lekt. M. Gžegoževskis" carries no information the reader needs from the title, and the
 * title is what makes the label overflow. Stripped at display time - in the widget caption
 * and in notification lines alike - so the stored label stays the full one.
 */
private val ACADEMIC_TITLE = Regex("""^(Lekt|Doc|Prof|Asist|Dr|Doktor)\.\s+""")

internal fun String.withoutAcademicTitle(): String = ACADEMIC_TITLE.replace(this, "")
