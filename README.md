# Gradle Test Timeout Plugin
![Community Supported](https://img.shields.io/badge/Support%20Level-Community%20Supported-457387.svg)

This project exists to prevent badly behaved Junit4 tests, or the product code under test, from
hanging the build. After applying this Gradle plugin, tests that exceed the specified timeout will fail with a
[TestTimedOutException](https://junit.org/junit4/javadoc/4.12/org/junit/runners/model/TestTimedOutException.html).
# Why?
Sometimes code behaves poorly and fails to terminate. Even if this happens very rarely, say 1% of the time, the 
"hang indefinitely" failure mode can be a painful, hard to debug, waste of resources. Even if the CI pipeline
as a whole has a timeout it is likely a much longer timeout than an individual hanging test should be afforded. 
And a full thread dump comes with a lot of noise, particularly if you're running a highly paralleled workflow.
Since JUnit 4 and 5 currently have no way to set a Global Timeout policy we created this project.

This plugin was developed against Gradle 4.x but should nicely compliment the 
[task level timeouts](https://docs.gradle.org/5.0/userguide/more_about_tasks.html#sec:task_timeouts) feature
released in Gradle 5.0.

## Prerequisites & Limitations 
* This plugin is not yet published to the Gradle Plugins Portal so for the time being you'll have to build 
 from source and publish to a repository internal to your organization.
* This plugin is designed for the Gradle build orchestration system and is not compatible with Maven, Bazel, Ant, etc.
* Only works on JUnit4 based test runners. Untested with JUnit5 legacy engine or Spock but it might work
* Due to the nature of JUnit4 Rules, cannot prevent hangs in test class initialization.
* Only works on JVM languages. JVM bytecode emitted by the Groovy, Kotlin, or Scala compilers should work but it's only 
been tested against Java
* The timeout is implemented as a Junit4 [Rule](https://github.com/junit-team/junit4/wiki/rules), 
so it changes the thread the test itself runs in.
So certain kinds of state-leackage between tests or usage of 
[ThreadLocal](https://docs.oracle.com/javase/8/docs/api/java/lang/ThreadLocal.html)
variables may necessitate some modification of test code to continue working. Internally at Tableau we had to modify
less than 1% of our test classes to be compatible. 


## Get Started
This plugin can generally be applied drop-in to any Java project with JUnit4 testing without having to modify any
individual tests. Note that the plugin will have no effect if the `testTimeoutPolicy` DSL is not configured.
This policy is expected to be used as baseline so precedence is given to an individual test's timeout configuration.

in a build.gradle apply & configure the plugin:
```gradle
// Use either buildscript+apply or plugins block to declare dependency but not both
buildscript {
    repositories {
        // The internal repository within your organization you've published this to 
    }
    dependencies {
        classpath 'com.tableau.modules:gradle-test-timeout:2.0'
    }
}
apply plugin: com.tableau.modules.gradle.TimeoutEnforcerPlugin

// Until this plugin is posted to Gradle's plugins portal 
plugins {
    id 'java'
    id 'com.tableau.modules.timeout-enforcer' version '2.0
}

testTimeoutPolicy {
    // The name of the test task to apply the policy to
    test {
        timeout = 1
        timeoutUnits = 'MINUTES' // may supply any value of java.util.concurrent.TimeUnit
    }
    someOtherTestTask {
        timeout = 100
        timeoutUnits = 'SECONDS'
    }
}
```

In statically-compiled Groovy or a similar statically typed language like Java or Kotlin,
applying & configuring this plugin might look like this:
```groovy
import org.gradle.api.Plugin
import org.gradle.api.Project
import com.tableau.modules.gradle.TimeoutEnforcerPlugin
import java.util.concurrent.TimeUnit

@CompileStatic
class ConfigureTimeoutPolicy implements Plugin<Project> {
    @Override void apply(Project project) {
        TimeoutPolicyExtension testTimeoutPolicy = project.plugins.apply(TimeoutEnforcerPlugin).testTimeoutPolicy

        testTimeoutPolicy.with {
            // Either syntax for adding a new timeout policy works
            policy('test') {
                it.timeout = 1
                it.timeoutUnits = TimeUnit.MINUTES
            }

            policy('someOtherTestTask', 100, TimeUnit.SECONDS)
        }
    }
}
```

## How It Works

This project uses [asm](http://asm.ow2.io/) to modify the bytecode produced by normal Java compilation
for tests to add a JUnit4 [Timeout](https://junit.org/junit4/javadoc/4.12/org/junit/rules/Timeout.html) [Rule](https://junit.org/junit4/javadoc/4.12/org/junit/Rule.html).

This effectively takes a test class like this one:
```java
package com.tableau.modules.gradle;

import org.junit.Test;
import java.lang.Thread;
import java.lang.System;

public class BasicJunitTest {

    @Test
    public void noopTest() {
        System.out.println("noopTest nooping right along");
    }

    @Test
    public void sleepFor10s() throws Exception {
        System.out.println("sleepFor10s test about to sleep");
        Thread.sleep(10*1000);
    }
}

```
and ensures that when compiled the final, resulting bytecode looks like
what would have been produced if this were the source:
```java
package com.tableau.modules.gradle;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.lang.Thread;
import java.lang.System;
import java.util.concurrent.TimeUnit;

public class BasicJunitTest {

    @Rule
    public Timeout timeout = new Timeout(5000L, TimeUnit.MILLISECONDS);

    @Test
    public void noopTest() {
        System.out.println("noopTest nooping right along");
    }

    @Test
    public void sleepFor10s() throws Exception {
        System.out.println("sleepFor10s test about to sleep");
        Thread.sleep(10*1000);
    }
}
```

# Contributions 
Before we can accept pull requests from contributors, we require a signed 
[Contributor License Agreement (CLA)](http://tableau.github.io/contributing.html).

This project is written in [kotlin](https://kotlinlang.org/) and deals with some intimate details of 
JVM bytecode. Knowledge of these domains is prerequisite for altering the code in any substantial way.
 
Contact a maintainer before you start working on any particularly difficult merge request! 
Some examples of requests we'd be happy to help merge:
  
 * Adding new kinds of useful bytecode or abstract syntax tree transformations - 
 so long as they don't interfere with any existing transformation. 
 * Adding support for new test frameworks like TestNG or Junit5
 * Adding compatibility for any JUnit4 runner which might interfere with the usual `@Rule`s 
 * Fixing bugs, improving documentation, adding test cases
 * Adding support for JUnit4's 
 [DisableOnDebug](https://junit.org/junit4/javadoc/4.12/org/junit/rules/DisableOnDebug.html) meta-rule
