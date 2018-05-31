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
    given("a Java project with Junit4 tests applying the TimeoutEnforcerPlugin") {
        val testProjectDir = createTempDir("timeoutEnforcerPluginTest", "")
        val testSrcDir = File(testProjectDir, "src/test/java/com/tableau/whatever/").apply { mkdirs() }
        val buildGradleFile = File(testProjectDir, "build.gradle")
        buildGradleFile.writeText("""
            plugins {
                id 'java'
                id 'com.tableau.modules.timeout-enforcer'
            }

            // We could interpolate in TimeoutSpec.defaultExtensionName but then the test wouldn't let you know
            // that you'd committed a breaking change
            testTimeoutPolicy {
                test {
                    timeout = 10
                    timeoutUnits = 'MILLISECONDS'
                }
            }

            test {
                exclude 'com/tableau/whatever/InapplicableTests.class'
            }

            task testInapplicable(type: Test) {
                exclude 'com/tableau/whatever/BadTests.class'
                include 'com/tableau/whatever/InapplicableTests.class'
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

        val inapplicableTestSourceFile = File(testSrcDir, "InapplicableTests.java")
        inapplicableTestSourceFile.writeText("""
            package com.tableau.whatever;

            import org.junit.Test;
            import java.lang.Thread;
            import java.lang.System;

            public class InapplicableTests {
                @Test
                public void sleepFor1s() throws Exception {
                    System.out.println("sleepFor1s test about to sleep");
                    Thread.sleep(1*1000);
                }
            }
        """.trimIndent())

        on("running a test which applied the transform and should time out") {
            val result = GradleRunner.create()
                    .withProjectDir(testProjectDir)
                    .withPluginClasspath()
                    .withArguments("test", "--stacktrace", "--debug", "--no-scan", "--tests", "com.tableau.whatever.BadTests.sleepFor1s")
                    .buildAndFail()
            val testTask = result.tasks.find { it.path == ":test" }!!
            println(result.output)

            it("fails the test") {
                assertEquals(TaskOutcome.FAILED, testTask.outcome)
            }
        }

        on("running a test that applied the transform but should not timed out") {
            val result = GradleRunner.create()
                    .withProjectDir(testProjectDir)
                    .withPluginClasspath()
                    .withArguments("test", "--stacktrace", "--info", "--no-scan", "--tests", "com.tableau.whatever.BadTests.noopTest")
                    .build()
            val testTask = result.tasks.find { it.path == ":test" }!!
            println(result.output)
            it("succeeds at running the test") {
                assertEquals(TaskOutcome.SUCCESS, testTask.outcome)
            }
        }

        on("running a test from the same sourceSet that isn't part of the transformed test task") {
            val result = GradleRunner.create()
                    .withProjectDir(testProjectDir)
                    .withPluginClasspath()
                    .withArguments("testInapplicable", "--stacktrace", "--info", "--no-scan")
                    .build()
            val testTask = result.tasks.find { it.path == ":testInapplicable" }!!
            println(result.output)
            it(" should succeed when running test that would timeout were the transform applied to it") {
                assertEquals(TaskOutcome.SUCCESS, testTask.outcome)
            }
        }
    }
})
