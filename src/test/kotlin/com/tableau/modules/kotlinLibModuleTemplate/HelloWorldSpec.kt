package com.tableau.modules.kotlinLibModuleTemplate

import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import kotlin.test.assertEquals

/** An example test using Spek */
object HelloWorldSpec : Spek({
    given("an instance of HelloWorld", {
        val helloWorld = HelloWorld()
        on("calling getHelloWorldMessage", {
            val message = helloWorld.getHelloWorldMessage()
            it("should return the expected message", {
                assertEquals("Hello, world!", message)
            })
        })
    })
})
