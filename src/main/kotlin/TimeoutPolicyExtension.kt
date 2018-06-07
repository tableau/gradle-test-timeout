package com.tableau.modules.gradle

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import java.util.concurrent.TimeUnit

public class TimeoutPolicyExtension(
    val project: Project,
    val defaultTimeout: Long,
    val defaultTimeUnit: TimeUnit,

    /**
     * Set sensible defaults for the specs within this collection and satisfy NamedDomainObjectContainer's
     * requirement that it have a factory that can construct the objects it contains with just a name
     */
    val timeoutSpecFactory: (testTaskName: String) -> TimeoutSpec = { testTaskName ->
        TimeoutSpec(
            project = project,
            testTaskName = testTaskName,
            timeout = defaultTimeout,
            timeoutUnits = defaultTimeUnit)
    },

    /**
     * The collection this object delegates to to implement NamedDomainObjectContainer<>
     */
    private val container: NamedDomainObjectContainer<TimeoutSpec> =
            project.container(TimeoutSpec::class.java, timeoutSpecFactory),

    /**
     * An optional initial configuration action so that you can simultaneously construct & configure this object
     * in a DSL-looking way.
     */
    initialConfigAction: TimeoutPolicyExtension.() -> Unit = {}
)
        : NamedDomainObjectContainer<TimeoutSpec> by container {

    companion object {
        const val defaultExtensionName: String = "testTimeoutPolicy"
    }

    init {
        // Apply the initial configuration closure if one is provided
        initialConfigAction(this)
    }

    // Is there a *need* to be this flexible with this function?
    // Not really. This is something of an experiment in trying to achieve DSL-terseness parity with Groovy DSLs
    // while retaining the advantages of type safety
    /**
     * Convenience methods for adding & configuring a new TimeoutSpec to this collection
     * Supports somewhat flexible syntax. Either of these invocations will work equally well:
     *
     * policy("test", 1, TimeUnit.MINUTES)
     *
     * or
     *
     * policy("test") {
     *     timeout = 1
     *     timeoutUnits = TimeUnit.MINUTES
     * }
     *
     * If timeout or timeoutUnits are not specified, the default values according to timeoutSpecFactory will be used
     */
    fun policy(
        testTaskName: String,
        timeout: Long = defaultTimeout,
        timeoutUnits: TimeUnit = defaultTimeUnit
    ): TimeoutSpec = policy(testTaskName) { this.timeout = timeout; this.timeoutUnits = timeoutUnits }
    fun policy(testTaskName: String, action: TimeoutSpec.() -> Unit): TimeoutSpec {
        val newSpec = timeoutSpecFactory(testTaskName).apply(action)
        container.add(newSpec)
        return newSpec
    }

    /**
     * Allow this object to be configured with a DSL-looking syntax
     */
    operator fun invoke(action: TimeoutPolicyExtension.() -> Unit): TimeoutPolicyExtension = apply(action)
}
