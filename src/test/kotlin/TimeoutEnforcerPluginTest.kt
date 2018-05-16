package com.tableau.modules.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.io.File
import kotlin.test.assertEquals

class TimeoutEnforcerPluginTest : Spek({
    given("a java project with the TimeoutEnforcerPlugin") {
        val testProjectDir = createTempDir("timeoutEnforcerPluginTest", "")
        val testSrcDir = File(testProjectDir, "src/test/java/com/tableau/whatever/").apply { mkdirs() }
        val buildGradleFile = File(testProjectDir, "build.gradle")
        buildGradleFile.writeText("""
            plugins {
                id 'java'
                id 'com.tableau.modules.timeout-enforcer'
            }

            testTimeoutPolicy {
                test {
                    timeout = 10
                    timeoutUnits = 'MILLISECONDS'
                }
            }

            dependencies {
                testCompile "junit:junit:4.12"
            }
        """.trimIndent())

        val testSourceFile = File(testSrcDir, "BadTests.java")
        testSourceFile.writeText("""
            package com.tableau.whatever;

            import org.junit.Test;
            import java.lang.Thread;
            import java.lang.System;

            public class BadTests {
                @Test
                public void noopTest() {
                    System.out.println("noopTest nooping right along");
                }

                @Test
                public void sleepFor1s() throws Exception {
                    System.out.println("sleepFor1s test about to sleep");
                    Thread.sleep(1*1000);
                }
            }
        """.trimIndent())

        on("running a deadlocking test") {
            val result = GradleRunner.create()
                    .withProjectDir(testProjectDir)
                    .withPluginClasspath()
                    .withArguments("test", "--stacktrace", "--debug", "--no-scan", "--tests", "com.tableau.whatever.BadTests.sleepFor1s")
                    .buildAndFail()
            val testTask = result.tasks.find { it.path == ":test" }!!
            println(result.output)

            it("test task should have failed with a Test") {
                assertEquals(TaskOutcome.FAILED, testTask.outcome)
            }
        }

        on("running a test that does not deadlock") {
            try {
                val result = GradleRunner.create()
                        .withProjectDir(testProjectDir)
                        .withPluginClasspath()
                        .withArguments("test", "--stacktrace", "--info", "--no-scan", "--tests", "com.tableau.whatever.BadTests.noopTest")
                        .build()
                val testTask = result.tasks.find { it.path == ":test" }!!
                println(result.output)
                it("test task should succeed") {
                    assertEquals(TaskOutcome.SUCCESS, testTask.outcome)
                }
            } catch (e: Exception) {
                throw e
            }
        }
    }
})
