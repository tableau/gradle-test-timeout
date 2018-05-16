package com.tableau.modules.gradle

import org.gradle.api.Named
import java.util.concurrent.TimeUnit

/**
 * Extension for configuring the TimeoutEnforcerPlugin
 */
data class TimeoutSpec(
    val sourceSetName: String,
    var timeout: Long,
    var timeoutUnits: TimeUnit
) : Named {
    // NamedDomainObjectContainer insists that getName() exist, but enforces it at runtime rather by actually
    // constraining the type parameter. Seems a bit strange to me... but perhaps that's natural given
    // the API's history of being based on Groovy which tends to treat generics' type parameters as comments
    override fun getName(): String = sourceSetName

    // So that a build.gradle doesn't have to fully qualify or import java.util.concurrent.TimeUnit
    fun timeoutUnits(unit: String) {
        timeoutUnits = TimeUnit.valueOf(unit)
    }

    val timeoutMillis: Long
        get() = timeoutUnits.toMillis(timeout)
}
