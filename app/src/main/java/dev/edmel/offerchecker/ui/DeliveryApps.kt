package dev.edmel.offerchecker.ui

import dev.edmel.offerchecker.service.KNOWN_UBER_PACKAGES

/**
 * A delivery app Home can open. Only Uber's offers are read ([verdicts]); the others are launch
 * tiles for now, and nah.meter never reads another app's screen.
 */
data class DeliveryApp(
    val id: String,
    val name: String,
    /** A neutral letter, not the app's logo. */
    val mark: String,
    /** Package names to try, in order. */
    val packages: List<String>,
    val verdicts: Boolean,
)

object DeliveryApps {
    val UBER = DeliveryApp("uber", "Uber Eats", "U", KNOWN_UBER_PACKAGES, verdicts = true)
    val DOORDASH = DeliveryApp("doordash", "DoorDash", "D", listOf("com.doordash.driverapp"), verdicts = false)

    // SkipTheDishes' courier app: package names still to confirm on a phone that has it.
    val SKIP = DeliveryApp("skip", "SkipTheDishes", "S", listOf("com.skipthedishes.courier", "com.skipthedishes.couriers"), verdicts = false)

    val ALL = listOf(UBER, DOORDASH, SKIP)
}
