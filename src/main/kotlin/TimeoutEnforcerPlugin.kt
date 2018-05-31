package com.tableau.modules.gradle

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.testing.Test
import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.TimeUnit

public class TimeoutEnforcerPlugin : Plugin<Project> {
    companion object {
        val log: Logger = Logging.getLogger(TimeoutEnforcerPlugin::class.java)
    }

    /**
     * Internal struct grouping together information about a file to be transformed
     */
    private data class TransformSpec(
        val className: String,
        val destination: File,
        val applicability: Junit4TimeoutTransform.Applicability
    )

    override fun apply(project: Project) {

        // Keep track of actions added to CompileJava tasks so they can be removed later if necessary
        val actions = mutableMapOf<String, Action<Task>>()

        // Create and configure the extension
        // Supply a factory method that sets sensible defaults and provides a reference to the project
        val enforcerExtension = project.container(TimeoutSpec::class.java,
                { testTaskName ->
                    TimeoutSpec(
                        project = project,
                        testTaskName = testTaskName,
                        timeout = 1,
                        timeoutUnits = TimeUnit.MINUTES)
                })
        enforcerExtension.apply {
            /**
             * When an entry is added to the DSL modify the corresponding compile task action to include the
             * bytecode transformation
             */
            whenObjectAdded { enforcementSpec ->
                val compileTestTask = enforcementSpec.compileTestTask

                // Ensure that changing the timeout value in the DSL would cause recompilation
                compileTestTask.inputs.property("timeoutMillis", enforcementSpec.timeoutMillis)
                // TODO: Determine if additional inputs need to be declared to prevent false up-to-date ?

                // Add the action which actually does the transformation
                val transformActionName = enforcementSpec.actionName()
                val transformTaskAction = enforcementSpec.getTransformTaskAction()

                actions[transformActionName] = transformTaskAction
                compileTestTask.doLast(transformActionName, transformTaskAction)
            }

            /**
             * Only likely to be called if the DSL is used to completely remove the default timeout policy
             */
            whenObjectRemoved { enforcementSpec ->
                val action = actions.remove(enforcementSpec.actionName())
                enforcementSpec.compileTestTask.actions.remove(action)
            }
        }

        project.extensions.add(TimeoutSpec.defaultExtensionName, enforcerExtension)
    }

    /**
     * Get a transform action which may be added to a JavaCompile
     */
    private fun TimeoutSpec.getTransformTaskAction(): Action<Task> = Action {
        val enforcementSpec = this
        val testTask: Test = enforcementSpec.testTask

        val cl = testTask.classpath
                .map { it.toURI().toURL() }
                .toTypedArray()
                .let { URLClassLoader(it) }

        val timeoutTransformer = Junit4TimeoutTransform(
                timeoutDurationMillis = enforcementSpec.timeoutMillis,
                cl = cl)

        val candidateClasses = testTask.candidateClassFiles
                // Skip inner classes/closures, we only care about top-level
                .filterNot { it.name.contains("$") }
                .map {
                    try {
                        val loadableName = it.relativeTo(compileTestTask.destinationDir).toString()
                                .replace(Regex("""[/\\]"""), ".")
                                .replace(".class", "")
                        TransformSpec(
                                destination = it,
                                className = loadableName,
                                applicability = timeoutTransformer.isApplicable(loadableName)
                        )
                    } catch (e: Throwable) {
                        throw RuntimeException("Problem determining transform applicability for $it", e)
                    }
                }
        log.debug("${testTask.path} transform candidates: $candidateClasses")

        val applicableClasses = candidateClasses.filter {
            it.applicability == Junit4TimeoutTransform.Applicability.APPLICABLE
        }

        log.info("${testTask.path} has ${applicableClasses.size} classes applicable" +
                " for the Junit4TimeoutTransform. Applying transform with test timeout timeout " +
                "${enforcementSpec.timeoutMillis}ms")

        // TODO: Parallelize per file? Either directly with coroutines or via gradle worker api.
        // Measure perf hit from this additional step and decide accordingly
        applicableClasses.forEach {
            log.debug("Applying Junit Timeout Transform to ${it.destination}")
            try {
                it.destination.writeBytes(timeoutTransformer.apply(it.destination))
            } catch (e: Throwable) {
                throw RuntimeException("Problem applying transformation to ${it.className}", e)
            }
        }
    }

    private fun TimeoutSpec.actionName(): String = "${this.testTaskName}TimeoutTransform"
}
