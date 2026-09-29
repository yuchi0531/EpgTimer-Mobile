package com.starrow.epgtimer.data.guide

import com.starrow.epgtimer.data.model.ServiceInfo

/**
 * A run of services shown as one column band in the newspaper-style guide.
 *
 * EpgTimer merges services of the same ONID/TSID whose SIDs are registered in
 * descending order (sub channels such as 1, 2, 3 laid out as 3, 2, 1). The last
 * (lowest SID) member represents the group and every member's programs are drawn
 * inside the same band.
 */
data class ServiceGroup(
    val primary: ServiceInfo,
    val members: List<ServiceInfo>,
    val span: Int,
) {
    val isMerged: Boolean
        get() = span > 1

    companion object {
        fun listing(services: List<ServiceInfo>): List<ServiceGroup> {
            if (services.isEmpty()) return emptyList()
            val groups = ArrayList<ServiceGroup>()
            var i = 0
            while (i < services.size) {
                var end = i
                while (end + 1 < services.size &&
                    services[end + 1].onid == services[i].onid &&
                    services[end + 1].tsid == services[i].tsid &&
                    services[end + 1].sid < services[end].sid
                ) {
                    end++
                }
                val span = end - i + 1
                val members = services.subList(i, end + 1)
                groups.add(
                    ServiceGroup(
                        primary = if (span > 1) members[members.size - 1] else members[0],
                        members = members,
                        span = span,
                    ),
                )
                i = end + 1
            }
            return groups
        }
    }
}
