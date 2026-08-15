package eif.viko.lt.appsas.paskaitutvarkarastis.glance

data class ChangeDto(
    val date: String = "",
    val paskaita: String = "",
    val destytojas: String = "",
    val auditorija: String = "",
    val grupe: String = ""
)
