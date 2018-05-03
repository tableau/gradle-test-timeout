package com.tableau.modules.kotlinLibModuleTemplate

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** An example test using JUnit jupiter */
class HelloWorldTest {
    @Test
    fun testThatHelloWorldMessageIsCorrect() {
        val helloWorld = HelloWorld()
        val message = helloWorld.getHelloWorldMessage()
        assertEquals("Hello, world!", message)
    }
}
