package com.tableau.modules.gradle

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.TaskProvider
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

    /**
     * Convenience getter for the extension added to a project by this plugin
     */
    var testTimeoutPolicy: TimeoutPolicyExtension? = null
        private set(value) { field = value }

    override fun apply(project: Project) {
        // Create and configure the extension
        // Supply a factory method that sets sensible defaults and provides a reference to the project
        val enforcerExtension = TimeoutPolicyExtension(project, defaultTimeout = 1, defaultTimeUnit = TimeUnit.MINUTES) {

            // Keep track of actions added to CompileJava tasks so they can be removed later if necessary
            val actions = mutableMapOf<String, Action<Task>>()

            /**
             * When an entry is added to the DSL modify the corresponding compile task action to include the
             * bytecode transformation
             */
            whenObjectAdded { enforcementSpec ->
                enforcementSpec.compileTestTask.with {
                    // Ensure that changing the timeout value in the DSL would cause recompilation
                    inputs.property("${enforcementSpec.actionName()}TimeoutMillis", enforcementSpec.timeoutMillis)

                    // Add the action which actually does the transformation
                    val transformActionName = enforcementSpec.actionName()
                    val transformTaskAction = enforcementSpec.getTransformTaskAction()

                    actions[transformActionName] = transformTaskAction
                    doLast(transformActionName, transformTaskAction)
                }
            }

            /**
             * Only likely to be called if the DSL is used to completely remove the default timeout policy
             */
            whenObjectRemoved { enforcementSpec ->
                val action = actions.remove(enforcementSpec.actionName())
                enforcementSpec.compileTestTask.with {
                    this.actions.remove(action)
                }
            }
        }
        testTimeoutPolicy = enforcerExtension
        project.extensions.add(TimeoutPolicyExtension.defaultExtensionName, enforcerExtension)
    }

    /**
     * Convenience function to avoid having to over-indent TaskProvider.configure{it.apply{}} all over the place
     */
    private fun <T : Task> TaskProvider<T>.with(configFun: T.() -> Unit): TaskProvider<T> {
        configure(configFun)
        return this
    }

    /**
     * Get a transform action which may be added to a JavaCompile
     */
    private fun TimeoutSpec.getTransformTaskAction(): Action<Task> = Action {
        val enforcementSpec = this
        enforcementSpec.testTask.with {
            classpath
                .map { it.toURI().toURL() }
                .toTypedArray()
                .let { URLClassLoader(it) }
                .use { cl ->
                    val timeoutTransformer = Junit4TimeoutTransform(
                            timeoutDurationMillis = enforcementSpec.timeoutMillis,
                            cl = cl)

                    val candidateClasses = candidateClassFiles
                            // Skip inner classes/closures, we only care about top-level
                            .filterNot { it.name.contains("$") }
                            .map {
                                try {
                                    val loadableName = it.relativeTo(compileTestTask.get().destinationDir).toString()
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
                    log.debug("$path transform candidates: $candidateClasses")

                    val applicableClasses = candidateClasses.filter {
                        it.applicability == Junit4TimeoutTransform.Applicability.APPLICABLE
                    }

                    log.info("$testTask has ${applicableClasses.size} classes applicable" +
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
        }
    }

    private fun TimeoutSpec.actionName(): String = "${this.testTaskName}TimeoutTransform"
}
