package eif.viko.lt.appsas.paskaitutvarkarastis.glance

data class TeacherDto(
    val cb_hidden: Boolean = false,
    val expired: Boolean = false,
    val id: String = "",
    val short: String = ""
)