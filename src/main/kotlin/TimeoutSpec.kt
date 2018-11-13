package com.tableau.modules.gradle

import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import java.util.concurrent.TimeUnit

/**
 * Extension for configuring the TimeoutEnforcerPlugin
 */
data class TimeoutSpec(
    val project: Project,
    val testTaskName: String,
    val compileTestTaskName: String = "compileTestJava",
    var timeout: Long,
    var timeoutUnits: TimeUnit
) : Named {

    // NamedDomainObjectContainer insists that getName() exist, but enforces it at runtime rather by actually
    // constraining the type parameter. Seems a bit strange to me... but perhaps that's natural given
    // the API's history of being based on Groovy which tends to treat generics' type parameters as comments
    override fun getName(): String = testTaskName

    /**
     * Convenience getter for retrieving the Test task associated with this spec
     */
    @Suppress("UNCHECKED_CAST")
    val testTask: TaskProvider<Test>
        get() = project.tasks.named(testTaskName) as TaskProvider<Test>

    /**
     * Convenience getter for the task which compiles the code to be transformed
     */
    @Suppress("UNCHECKED_CAST")
    val compileTestTask: TaskProvider<JavaCompile>
        get() = project.tasks.named(compileTestTaskName) as TaskProvider<JavaCompile>

    // So that a build.gradle doesn't have to fully qualify or import java.util.concurrent.TimeUnit
    fun timeoutUnits(unit: String) {
        timeoutUnits = TimeUnit.valueOf(unit)
    }

    val timeoutMillis: Long
        get() = timeoutUnits.toMillis(timeout)

    /**
     * Allow this type of object to be configured through a DSL-looking syntax
     */
    operator fun invoke(action: TimeoutSpec.() -> Unit): TimeoutSpec = apply(action)
}
