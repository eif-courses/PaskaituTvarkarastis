package eif.viko.lt.appsas.paskaitutvarkarastis.glance

/**
 * Decides whether a lecture survives a subgroup filter such as "I pogr.".
 *
 * Kept out of TimetableWidget so it stays free of Android types and can be unit tested.
 */
object SubgroupFilter {

    /**
     * A lecture with no subgroup of its own is attended by the whole group, so it survives
     * any filter; otherwise it has to carry the selected subgroup.
     *
     * Compared through [ChangeMatcher.normalizeGroup], so "II pogrupis", "(II pogr.)" and
     * "II pogr." all count as the same subgroup.
     */
    fun matches(lecture: LecturesDto, subgroup: String): Boolean {
        if (subgroup.isBlank()) return true

        val wanted = ChangeMatcher.normalizeGroup(subgroup)
        val names = lecture.groupnames
            .map { ChangeMatcher.normalizeGroup(it) }
            .filter { it.isNotBlank() }

        return names.isEmpty() || names.any { it.equals(wanted, ignoreCase = true) }
    }
}
