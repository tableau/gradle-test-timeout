package com.tableau.modules.gradle

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.compile.JavaCompile
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
        val javaConvention = project.convention.findPlugin(JavaPluginConvention::class.java)
                ?: throw RuntimeException("project ${project.path} must apply a Java plugin before applying TimeoutEnforcerPlugin")

        // Keep track of actions added to CompileJava tasks so they can be removed later if necessary
        val actions = mutableMapOf<String, Action<Task>>()

        // Add the extension and set the defaults
        val enforcerExtension = project.container(TimeoutSpec::class.java)

        enforcerExtension.apply {
            /**
             * When an entry is added to the DSL modify the corresponding compile task action to include the
             * bytecode transformation
             */
            whenObjectAdded { enforcementSpec ->
                val sourceSet = javaConvention.sourceSets.getByName(enforcementSpec.sourceSetName)
                val compileTestTask = project.tasks.getByName(sourceSet.compileJavaTaskName) as JavaCompile

                // Ensure that changing the timeout value in the DSL would cause recompilation
                compileTestTask.inputs.property("timeoutMillis", enforcementSpec.timeoutMillis)

                // Add the action which actually does the transformation
                val transformActionName = enforcementSpec.actionName()
                val transformTaskAction = compileTestTask.transformActionFor(enforcementSpec, sourceSet)

                actions[transformActionName] = transformTaskAction
                compileTestTask.doLast(transformActionName, transformTaskAction)
            }

            /**
             * Only likely to be called if the DSL is used to completely remove the default timeout policy
             */
            whenObjectRemoved { enforcementSpec ->
                val sourceSet = javaConvention.sourceSets.getByName(enforcementSpec.sourceSetName)
                val compileTestTask = project.tasks.getByName(sourceSet.compileJavaTaskName) as JavaCompile

                val action = actions.remove(enforcementSpec.actionName())
                compileTestTask.actions.remove(action)
            }

            /**
             * Default timeout policy
             */
            add(TimeoutSpec(sourceSetName = "test", timeout = 10, timeoutUnits = TimeUnit.MINUTES))
        }

        project.extensions.add("testTimeoutPolicy", enforcerExtension)
    }

    /**
     * Get a transform action which may be added to a JavaCompile
     */
    private fun JavaCompile.transformActionFor(enforcementSpec: TimeoutSpec, sourceSet: SourceSet): Action<Task> = Action {
        val compileTestTask = this

        val cl = compileTestTask.classpath
                .plus(sourceSet.output.classesDirs)
                .map { it.toURI().toURL() }
                .toTypedArray()
                .let { URLClassLoader(it) }

        val timeoutTransformer = Junit4TimeoutTransform(
                timeoutDurationMillis = enforcementSpec.timeoutMillis,
                cl = cl)

        val candidateClasses = sourceSet.output
                .classesDirs
                .flatMap { project.fileTree(it).files }
                .filter { it.name.endsWith(".class") } // Skip resource files
                .map {
                    val loadableName = it.relativeTo(compileTestTask.destinationDir).toString()
                            .replace(Regex("""[/\\]"""), ".")
                            .replace(".class", "")
                    TransformSpec(
                            destination = it,
                            className = loadableName,
                            applicability = timeoutTransformer.isApplicable(loadableName)
                    )
                }
        log.debug("${project.path} sourceSet ${sourceSet.name} transform candidates: $candidateClasses")

        val applicableClasses = candidateClasses.filter {
            it.applicability == Junit4TimeoutTransform.Applicability.APPLICABLE
        }

        log.info("${project.path} sourceSet ${sourceSet.name} has ${applicableClasses.size} classes applicable for the Junit4TimeoutTransform. " +
                "Applying transform with test timeout timeout ${enforcementSpec.timeoutMillis}ms")

        // TODO: Parallelize per file? Either directly with coroutines or via gradle worker api.
        // Measure perf hit from this additional step and decide accordingly
        applicableClasses.forEach {
            log.debug("Applying Junit Timeout Transform to ${it.destination}")
            it.destination.writeBytes(timeoutTransformer.apply(it.destination))
        }
    }

    private fun TimeoutSpec.actionName(): String = "${this.sourceSetName}TimeoutTransform"
}
