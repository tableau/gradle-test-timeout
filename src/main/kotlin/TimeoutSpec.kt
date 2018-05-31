package com.tableau.modules.gradle

import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import java.util.concurrent.TimeUnit

/**
 * Extension for configuring the TimeoutEnforcerPlugin
 */
data class TimeoutSpec(
    val project: Project,
    val testTaskName: String,
    var timeout: Long,
    var timeoutUnits: TimeUnit
) : Named {
    companion object {
        const val defaultExtensionName: String = "testTimeoutPolicy"
    }

    // NamedDomainObjectContainer insists that getName() exist, but enforces it at runtime rather by actually
    // constraining the type parameter. Seems a bit strange to me... but perhaps that's natural given
    // the API's history of being based on Groovy which tends to treat generics' type parameters as comments
    override fun getName(): String = testTaskName

    /**
     * Convenience getter for retrieving the sourceSet corresponding to the given test task.
     * One sourceSet can correspond to many test tasks, but each test task will have only one associated sourceSet
     * So look for a sourceSet whose outputs overlap with the inputs of a test task's inputs
     */
    val sourceSet: SourceSet
        get() = project.convention.getPlugin(JavaPluginConvention::class.java)
                .sourceSets.find { it.output.classesDirs.intersect(testTask.testClassesDirs).isNotEmpty() }
                ?: throw IllegalArgumentException("Couldn't find sourceSet corresponding to $testTask")

    /**
     * Convenience getter for retrieving the Test task associated with
     */
    val testTask: Test
        get() = project.tasks.getByName(testTaskName) as Test

    /**
     * Convenience getter for the task which compiles the code to be transformed
     */
    val compileTestTask: JavaCompile
        get() = project.tasks.getByName(sourceSet.compileJavaTaskName) as JavaCompile

    // So that a build.gradle doesn't have to fully qualify or import java.util.concurrent.TimeUnit
    fun timeoutUnits(unit: String) {
        timeoutUnits = TimeUnit.valueOf(unit)
    }

    val timeoutMillis: Long
        get() = timeoutUnits.toMillis(timeout)
}
