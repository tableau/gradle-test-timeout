import java.io.ByteArrayOutputStream
import com.google.googlejavaformat.java.Formatter

plugins {
    id("java-gradle-plugin")
    id("com.tableau.nerv-java-lib-module-plugin") version("2.2.0")
    id("org.jmailen.kotlinter") version("1.11.3")
    id("maven-publish")
    kotlin("jvm") version("1.2.41")
}
buildscript {
    dependencies.add("classpath","com.google.googlejavaformat:google-java-format:1.5" )
}

// Create a "samples" sourceset for sample junit tests to live within
// They are not tests *for* this project, but they are used *by* this project's tests
java {
    sourceSets {
        "samples" {
            java {
                srcDir(project.file("src/samples/java"))
            }
        }
    }
}
val samplesSourceSet:SourceSet = java.sourceSets.getByName("samples")

val junitJupiterVersion = "5.1.0"
val spekVersion = "1.1.5"
val asmVersion = "6.1.1"
val jmockitVersion = "1.24"

dependencies {
    compile(kotlin("stdlib-jdk8"))
    compile(kotlin("reflect"))
    compile("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:0.22.5") {
        // Tries to bring in earlier versions of kotlin libs than we're using
        exclude(group = "org.jetbrains.kotlin")
    }
    compile("junit:junit:4.12")

    "samplesCompile"("junit:junit:4.12")
    "samplesCompile"("org.jmockit:jmockit:$jmockitVersion")

    compile(gradleApi())
    compile("org.ow2.asm:asm:$asmVersion")
    compile("org.ow2.asm:asm-tree:$asmVersion")

    testCompile(gradleTestKit())
    testCompile("org.ow2.asm:asm-util:$asmVersion")
    testCompile(kotlin("test"))
    testCompile("org.jetbrains.spek:spek-api:$spekVersion")
    testCompile("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")

    testRuntime("org.jetbrains.spek:spek-junit-platform-engine:$spekVersion")
    testRuntime("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
    testRuntime("org.jmockit:jmockit:$jmockitVersion")
}

gradlePlugin {
    (plugins) {
        "simplePlugin" {
            id = "com.tableau.modules.timeout-enforcer"
            implementationClass = "com.tableau.modules.gradle.TimeoutEnforcerPlugin"
        }
    }
}

tasks {
    withType<Test> {
        dependsOn(samplesSourceSet.output)
        useJUnitPlatform {
            includeEngines("spek")
        }
    }

    // Skip dependency locking in favor of static versioning of dependencies
    findByName("nervEnsureDependenciesLocked")?.enabled = false
}

val asmifyBasicJunitTest by tasks.creating(JavaExec::class) {
    classpath(samplesSourceSet.output,
            project.configurations.getByName("testRuntime"))

    val inputFile = file("${project.java.sourceSets.getByName("samples").java.outputDir}/com/tableau/modules/gradle/BasicJunitTest.class")
    val outputFile = file("build/gen/BasicJunitTestDump.java")
    val outputStream = ByteArrayOutputStream()
    inputs.file(inputFile)
    outputs.file(outputFile)

    main = "org.objectweb.asm.util.ASMifier"
    args(file("build/classes/java/test/com/tableau/modules/gradle/BasicJunitTest.class"))
    standardOutput = outputStream

    doFirst {
        outputFile.parentFile.mkdirs()
        outputFile.createNewFile()
    }
    doLast {
        // ASMifier doesn't bother pretty-printing the source code
        // So, purely for convenience, have google-java-format make it easier to read
        val prettyPrintedSource = Formatter().formatSource(outputStream.toString())
        outputFile.writeText(prettyPrintedSource)
        println("BasicJunitTestDump.java available under ${outputFile.path}")
    }
}

val asmifyBasicJunitTestWithTimeout by tasks.creating(JavaExec::class) {
    classpath(samplesSourceSet.output,
            project.configurations.getByName("testRuntime"))

    val inputFile = file("${project.java.sourceSets.getByName("samples").java.outputDir}/com/tableau/modules/gradle/BasicJunitTestWithTimeout.class")
    val outputFile = file("build/gen/BasicJunitTestWithTimeoutDump.java")
    val outputStream = ByteArrayOutputStream()
    inputs.file(inputFile)
    outputs.file(outputFile)

    main = "org.objectweb.asm.util.ASMifier"
    args(inputFile)
    standardOutput = outputStream

    doFirst {
        outputFile.parentFile.mkdirs()
        outputFile.createNewFile()
    }
    doLast {
        // ASMifier doesn't bother pretty-printing the source code
        // So, purely for convenience, have google-java-format make it easier to read
        val prettyPrintedSource = Formatter().formatSource(outputStream.toString())
        outputFile.writeText(prettyPrintedSource)
        println("BasicJunitTestWithTimeoutDump.java available under ${outputFile.path}")
    }
}

val asmify by tasks.creating {
    dependsOn(asmifyBasicJunitTest, asmifyBasicJunitTestWithTimeout)
    description = "Convenience task for generating ASM classes that would produce the bytecode of BasicJunitTest and BasicJunitTestWithTimeout"
    group = "ASM"
}
