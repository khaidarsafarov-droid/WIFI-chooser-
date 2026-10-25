package com.wifipriority.wifi

/**
 * Decides which suggestions to register.
 *
 * Both hotspots are registered in the normal case. Phone uses a higher
 * [priorityGroup] and [priority] than truck (larger value wins on API 33+
 * `setPriorityGroup` / `setPriority`). The system can then fail over to the
 * truck as soon as the phone disappears.
 *
 * When the engine target is [WifiTarget.PHONE] but the device is associated to
 * the truck, the truck suggestion is removed until the phone SSID is the
 * connected network. That forces the return the priority values alone did not.
 * While that removal is in effect, a null connected SSID keeps the truck
 * removed so it is not re-added during the roam.
 */
object SuggestionPlanner {
    const val PHONE_PRIORITY: Int = 100
    const val TRUCK_PRIORITY: Int = 0
    const val PHONE_GROUP: Int = 1
    const val TRUCK_GROUP: Int = 0
    const val MIN_PASSPHRASE_LENGTH: Int = 8

    data class PlannedSuggestion(
        val role: WifiTarget,
        val ssid: String,
        val passphrase: String,
        val priority: Int,
        val priorityGroup: Int,
    )

    data class Plan(
        val suppressingTruck: Boolean,
        val suggestions: List<PlannedSuggestion>,
    )

    fun plan(
        target: WifiTarget,
        connectedSsid: String?,
        config: PriorityConfig,
        suppressingTruck: Boolean,
    ): Plan {
        if (target == WifiTarget.NONE) {
            return Plan(suppressingTruck = false, suggestions = emptyList())
        }
        val phone = usable(config.phone, WifiTarget.PHONE, PHONE_PRIORITY, PHONE_GROUP)
        val truck = usable(config.truck, WifiTarget.TRUCK, TRUCK_PRIORITY, TRUCK_GROUP)
        val onPhone = phone != null && connectedSsid == phone.ssid
        val onTruck = truck != null && connectedSsid == truck.ssid
        val suppress = when {
            target != WifiTarget.PHONE -> false
            phone == null || truck == null -> false
            onPhone -> false
            onTruck -> true
            else -> suppressingTruck
        }
        val suggestions = buildList {
            if (phone != null) add(phone)
            if (truck != null && !suppress) add(truck)
        }
        return Plan(suppressingTruck = suppress, suggestions = suggestions)
    }

    private fun usable(
        credential: NetworkCredential?,
        role: WifiTarget,
        priority: Int,
        priorityGroup: Int,
    ): PlannedSuggestion? {
        val ssid = credential?.ssid?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val passphrase = credential.password
        if (passphrase.length < MIN_PASSPHRASE_LENGTH) return null
        return PlannedSuggestion(
            role = role,
            ssid = ssid,
            passphrase = passphrase,
            priority = priority,
            priorityGroup = priorityGroup,
        )
    }
}
