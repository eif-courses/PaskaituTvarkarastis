package eif.viko.lt.appsas.paskaitutvarkarastis.glance

data class LecturesDto(
    val classids: List<String> = emptyList(),
    val classroomids: List<String> = emptyList(),
    val colors: List<String> = emptyList(),
    val date: String ="",
    val endtime: String="",
    val groupnames: List<String> = emptyList(),
    val igroupid: String = "",
    val starttime: String ="",
    val subjectid: String ="",
    val teacherids: List<String> = emptyList(),
    val type: String = "",
    val uniperiod: String="",
    /**
     * How many consecutive periods this card spans; the feed omits it for a single period
     * and older cached JSON never had it, so it is nullable and null means one.
     */
    val durationperiods: Int? = null
)