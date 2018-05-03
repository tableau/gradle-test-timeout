package com.tableau.modules.kotlinLibModuleTemplate

class HelloWorld {
    fun printHelloWorld() {
        println(HELLO_WORLD_MESSAGE)
    }

    fun getHelloWorldMessage(): String {
        return HELLO_WORLD_MESSAGE
    }

    companion object {
        private val HELLO_WORLD_MESSAGE = "Hello, world!"
    }
}
