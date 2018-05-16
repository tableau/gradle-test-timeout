package com.tableau.modules.gradle

import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.junit.runner.JUnitCore
import org.junit.runners.model.TestTimedOutException
import org.objectweb.asm.ClassReader
import org.objectweb.asm.util.CheckClassAdapter
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URLClassLoader
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

data class TestDef(val name: String, val expectedApplicability: Junit4TimeoutTransform.Applicability)
val tests = listOf(
        TestDef("BasicJunitTestWithTimeout", Junit4TimeoutTransform.Applicability.EXISTENT_TIMEOUT),
        TestDef("HelloWorld", Junit4TimeoutTransform.Applicability.NO_TESTS),
        TestDef("JunitTestSubclass", Junit4TimeoutTransform.Applicability.EXISTENT_TIMEOUT),
        TestDef("ClassWithTimeoutButNoTests", Junit4TimeoutTransform.Applicability.EXISTENT_TIMEOUT), // NO_TESTS also acceptable
        TestDef("BasicJunitTest", Junit4TimeoutTransform.Applicability.APPLICABLE),
        TestDef("JunitTestWithTempFolderRule", Junit4TimeoutTransform.Applicability.APPLICABLE),
        TestDef("StaticTimeoutField", Junit4TimeoutTransform.Applicability.APPLICABLE),
        TestDef("PrivateTimeoutField", Junit4TimeoutTransform.Applicability.APPLICABLE))

class Junit4TimeoutTransformTest : Spek({
    given("a junit4TimeoutTransform") {
        val samplesBuildDir = File(System.getProperty("user.dir"), "build/classes/java/samples/")
        if (!samplesBuildDir.exists()) {
            throw RuntimeException(
                    "Path ${samplesBuildDir.path} does not exist, cannot proceed with tests. " +
                    "Run gradlew build before expecting this to work in your IDE")
        }
        val unmodifiedClassloader = URLClassLoader(arrayOf(samplesBuildDir.toURI().toURL()), this.javaClass.classLoader)

        val junit4TimeoutTransform = Junit4TimeoutTransform(
                cl = unmodifiedClassloader,
                timeoutDurationMillis = 1L)

        tests.forEach {
            val classToTransform = unmodifiedClassloader.loadClass("com.tableau.modules.gradle.${it.name}")
            on("evaluating class ${it.name} transform applicability") {
                it("should determine that ${it.name}.class applicability is ${it.expectedApplicability}") {
                    val actualApplicability = junit4TimeoutTransform.isApplicable(classToTransform)
                    assertEquals(it.expectedApplicability, actualApplicability)
                }
            }
            if (it.expectedApplicability.shouldTransform) {
                on("applying the Junit4TimeoutTransform") {
                    val modifiedBytecode = junit4TimeoutTransform.apply(classToTransform)

                    it("the resulting bytes should be a valid Java .class file according to ASM") {
                        val stringWriter = StringWriter()
                        val writer = PrintWriter(stringWriter)
                        val modifiedReader = ClassReader(modifiedBytecode)
                        CheckClassAdapter.verify(modifiedReader, null, false, writer)

                        val verificationText = stringWriter.toString()
                        if (verificationText.contains("Exception")) {
                            fail(verificationText)
                        }
                    }

                    val modifiedClassDirectory = createTempDir()
                    File(modifiedClassDirectory, "com/tableau/modules/gradle/${it.name}.class").apply {
                        parentFile.mkdirs()
                        createNewFile()
                        writeBytes(modifiedBytecode)
                    }

                    val cl = URLClassLoader(arrayOf(modifiedClassDirectory.toURI().toURL()), this.javaClass.classLoader)
                    val modifiedClass = cl.loadClass("com.tableau.modules.gradle.${it.name}")

                    it("the resulting bytes should evaluate as NOT being applicable for transform") {
                        assertEquals(Junit4TimeoutTransform.Applicability.EXISTENT_TIMEOUT, junit4TimeoutTransform.isApplicable(modifiedClass))
                    }

                    val timeoutField = modifiedClass.getField(Junit4TimeoutTransform.timeoutFieldName)
                    it("The added field to the class is of type ${org.junit.rules.Timeout::class.java}") {
                        assertEquals(org.junit.rules.Timeout::class.java, timeoutField.type)
                    }

                    val modifiedInstance = modifiedClass.newInstance()
                    val timeoutFieldValue = timeoutField.get(modifiedInstance)
                    it("the added field should be initialized to a non-null value") {
                        assertNotNull(timeoutFieldValue)
                    }
                    it("the added field is initialized to an instance of ${org.junit.rules.Timeout::class.java}") {
                        assertTrue(timeoutFieldValue is org.junit.rules.Timeout)
                    }

                    val junit4Core = JUnitCore()
                    val result = junit4Core.run(modifiedClass)
                    it("${it.name} will timeout when run by Junit4") {
                        assertTrue(result.failures.first().exception is TestTimedOutException)
                    }
                }
            }
        }
    }
})
