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

data class TestDef(
    val name: String,
    val expectedApplicability: Junit4TimeoutTransform.Applicability,
    val expectedFieldName: String = Junit4TimeoutTransform.defaultTimeoutFieldName
)

val tests = listOf(
        TestDef("BasicJunitTestWithTimeout", Junit4TimeoutTransform.Applicability.EXISTENT_TIMEOUT),
        TestDef("HelloWorld", Junit4TimeoutTransform.Applicability.NO_TESTS),
        TestDef("JunitTestSubclass", Junit4TimeoutTransform.Applicability.EXISTENT_TIMEOUT),
        TestDef("ClassWithTimeoutButNoTests", Junit4TimeoutTransform.Applicability.EXISTENT_TIMEOUT), // NO_TESTS would also be acceptable
        TestDef("BasicJunitTest", Junit4TimeoutTransform.Applicability.APPLICABLE),
        TestDef("JunitTestWithTempFolderRule", Junit4TimeoutTransform.Applicability.APPLICABLE),
        TestDef("PrivateTimeoutFieldDuplicateFieldName", Junit4TimeoutTransform.Applicability.APPLICABLE, "globalTimeout1"),
        TestDef("JunitWithAnonymousInner", Junit4TimeoutTransform.Applicability.APPLICABLE),
        TestDef("JunitWithAnonymousInner\$1", Junit4TimeoutTransform.Applicability.NOT_TESTCLASS),
        TestDef("HasMultipleConstructors", Junit4TimeoutTransform.Applicability.NOT_TESTCLASS),
        TestDef("BasicJunitTestWithSecondaryPrivateConstructor", Junit4TimeoutTransform.Applicability.APPLICABLE),
        TestDef("UsesJmockit", Junit4TimeoutTransform.Applicability.APPLICABLE),
        TestDef("UsesJmockit$1", Junit4TimeoutTransform.Applicability.NOT_TESTCLASS),
        TestDef("UsesJmockit$2", Junit4TimeoutTransform.Applicability.NOT_TESTCLASS),
        TestDef("HasMultipleConstructors", Junit4TimeoutTransform.Applicability.NOT_TESTCLASS))

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

        for (test in tests) {
            val classToTransform = unmodifiedClassloader.loadClass("com.tableau.modules.gradle.${test.name}")
            on("evaluating class ${test.name} transform applicability") {
                it("evaluates ${test.name}.class applicability as ${test.expectedApplicability}") {
                    val actualApplicability = junit4TimeoutTransform.isApplicable(classToTransform)
                    assertEquals(test.expectedApplicability, actualApplicability)
                }
            }
            if (test.expectedApplicability.shouldTransform) {
                on("applying the Junit4TimeoutTransform to ${test.name}") {
                    val modifiedBytecode = junit4TimeoutTransform.apply(classToTransform)

                    val modifiedClassDirectory = createTempDir()
                    File(modifiedClassDirectory, "com/tableau/modules/gradle/${test.name}.class").apply {
                        parentFile.mkdirs()
                        createNewFile()
                        writeBytes(modifiedBytecode)
                    }

                    File(samplesBuildDir, "com/tableau/modules/gradle/").apply {
                        // Also copy the untouched inner classes so that subsequent steps may load them and proceed
                        // For the same reason, copy the superclass so long as it isn't just 'Object'
                        listFiles { _, name -> name.startsWith("${test.name}\$") || name.startsWith(classToTransform.superclass.simpleName) }
                            .forEach { it.copyTo(File(modifiedClassDirectory, "com/tableau/modules/gradle/${it.name}")) }
                    }

                    val modifiedClassloader = URLClassLoader(arrayOf(modifiedClassDirectory.toURI().toURL()), this.javaClass.classLoader)
                    val modifiedClass = modifiedClassloader.loadClass("com.tableau.modules.gradle.${test.name}")
                    it("produces a transformed ${test.name} that is a valid .class file according to ASM") {
                        val stringWriter = StringWriter()
                        val writer = PrintWriter(stringWriter)
                        val modifiedReader = ClassReader(modifiedBytecode)
                        CheckClassAdapter.verify(modifiedReader, modifiedClassloader, false, writer)

                        val verificationText = stringWriter.toString()
                        if (verificationText.contains("Exception")) {
                            fail(verificationText)
                        }
                    }

                    it("evaluates the resulting bytes of ${test.name} as NOT being applicable for transform") {
                        assertEquals(Junit4TimeoutTransform.Applicability.EXISTENT_TIMEOUT, junit4TimeoutTransform.isApplicable(modifiedClass))
                    }

                    val timeoutField = modifiedClass.getField(test.expectedFieldName)
                    val modifiedInstance = modifiedClass.newInstance()
                    val timeoutFieldValue = timeoutField.get(modifiedInstance)
                    it("produces a ${org.junit.rules.Timeout::class.java} field on ${test.name} that is initialized to an instance of ${org.junit.rules.Timeout::class.java}") {
                        assertEquals(org.junit.rules.Timeout::class.java, timeoutField.type)
                        assertNotNull(timeoutFieldValue)
                        assertTrue(timeoutFieldValue is org.junit.rules.Timeout)
                    }

                    val junit4Core = JUnitCore()
                    val result = junit4Core.run(modifiedClass)
                    it("produces a ${test.name} that times out when run by Junit4") {
                        assertTrue(result.failures.first().exception is TestTimedOutException)
                    }
                }
            }
        }
    }
})
