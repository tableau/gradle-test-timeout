plugins {
    id("java-gradle-plugin")
    id("org.jmailen.kotlinter") version("1.11.3")
    id("maven-publish")
    kotlin("jvm") version("1.3.10")
}

repositories {
    jcenter()
}

// Create a "samples" sourceset for sample junit tests to live within
// They are not tests *for* this project, but they are used *by* this project's tests
java {
    sourceSets {
        create("samples") {
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
    plugins {
        register("simplePlugin") {
            id = "com.tableau.modules.timeout-enforcer"
            implementationClass = "com.tableau.modules.gradle.TimeoutEnforcerPlugin"
        }
    }
}

tasks {
    withType<Test>().configureEach {
        dependsOn(samplesSourceSet.output)
        useJUnitPlatform {
            includeEngines("spek")
        }
    }
}
