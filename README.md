# Gradle Test Timeout Plugin
This project exists to prevent badly behaved tests or product code under test from 
hanging the build. Tests that exceed the specified timeout will fail with a 
[TestTimedOutException](https://junit.org/junit4/javadoc/4.12/org/junit/runners/model/TestTimedOutException.html).

## Usage
This plugin can generally be applied drop-in to any Java project with JUnit4 testing without having to modify any
indvidual tests. Note that the plugin will have no effect if the `testTimeoutPolicy` DSL is not configured. 
This policy is expected to be used as baseline so precedence is given to an individual test's timeout configuration.


in a build.gradle apply & configure the plugin:
```gradle
// Use either buildscript+apply or plugins block to declare dependency but not both
buildscript {
    dependencies {
        classpath 'com.tableau.modules:gradle-test-timeout:1.2.0'
    }
}
apply plugin: com.tableau.modules.gradle.TimeoutEnforcerPlugin

plugins {
    id 'java'
    id 'com.tableau.modules.timeout-enforcer' version '1.2.0'
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
@CompileStatic
class configureTimeoutPolicy implements Plugin<Project> {
    @Override void apply(Project project) {
        project.plugins.apply(com.tableau.modules.gradle.TimeoutEnforcerPlugin)
        def testTimeoutPolicy = project.extensions.getByName('testTimeoutPolicy') as NamedDomainObjectCollection<TimeoutSpec>
        
        // The DSL definitely looks nicer in dynamic-groovy, but what DSL doesn't? 
        testTimeoutPolicy.add(new TimeoutSpec(project,'test', 1, TimeUnit.MINUTES))
        testTimeoutPolicy.add(new TimeoutSpec(project,'someOtherTestTask', 100, TimeUnit.SECONDS))
    }
}
```

## Limitations

* Due to the nature of JUnit4 Rules, cannot prevent deadlocks/hangs in test class initialization.
this is most noticable with runners that do a lot of heavy lifting during this phase like 
[SpringJUnit4ClassRunner](https://docs.spring.io/autorepo/docs/spring-framework/3.2.8.RELEASE/javadoc-api/org/springframework/test/context/junit4/SpringJUnit4ClassRunner.html)
* Only works on JVM languages. Only tested against Java. 
* Only works on JUnit4 based test runners. Untested with JUnit5 legacy engine or Spock but it might work
* The timeout is implemented as a Junit4 [Rule](https://github.com/junit-team/junit4/wiki/rules), so it changes the thread the test itself runs in. So certain
kinds of state-leackage between tests or [ThreadLocal](https://docs.oracle.com/javase/8/docs/api/java/lang/ThreadLocal.html) variables 

## How It Works 

This project uses [asm](http://asm.ow2.io/) to modify the bytecode produced by normal compilation 
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

    @Test
    public void noopTest() {
        System.out.println("noopTest nooping right along");
    }

    @Test
    public void sleepFor10s() throws Exception {
        System.out.println("sleepFor10s test about to sleep");
        Thread.sleep(10*1000);
    }

    @Rule
    public Timeout timeout = new Timeout(5000L, TimeUnit.MILLISECONDS);
}
```
