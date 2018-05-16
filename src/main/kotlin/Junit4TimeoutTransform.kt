package com.tableau.modules.gradle

import org.junit.Rule
import org.junit.rules.Timeout
import org.junit.runners.model.TestClass
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.ClassWriter.COMPUTE_MAXS
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.objectweb.asm.Opcodes.ALOAD
import org.objectweb.asm.Opcodes.DUP
import org.objectweb.asm.Opcodes.GETSTATIC
import org.objectweb.asm.Opcodes.INVOKESPECIAL
import org.objectweb.asm.Opcodes.NEW
import org.objectweb.asm.Opcodes.PUTFIELD

import java.io.File

/**
 * Uses ASM to rewrite the bytecode of a Junit4 test class to include a Timeout
 */
class Junit4TimeoutTransform(
    val timeoutDurationMillis: Long,
    val asmApi: Int = Opcodes.ASM4,
    val cl: ClassLoader = Junit4TimeoutTransform::class.java.classLoader,
    val readerFactory: (ByteArray) -> ClassReader = ::ClassReader,
    val writerFactory: (ClassReader, Int) -> ClassWriter = ::ClassWriter
) {
    companion object {
        const val defaultConstructorName = "<init>"
        const val timeoutFieldName = "globalTimeout"
        const val junitTimeoutRuleDescriptor = "Lorg/junit/rules/Timeout;"
        const val junitTestAnnotationDescriptor = "Lorg/junit/Test;"
        const val junitRuleAnnotationDescriptor = "Lorg/junit/Rule;"
    }
    enum class Applicability(val shouldTransform: Boolean, val message: String) {
        APPLICABLE(true, "Class has junit4 test methods but no timeout rule"),
        NO_TESTS(false, "Class does not have any junit4 test methods"),
        EXISTENT_TIMEOUT(false, "Class already has a timeout rule, no transform needed"),
        NOT_TESTCLASS(false, "Not a valid Junit4 test class ")
    }

    fun isApplicable(className: String): Applicability = isApplicable(cl.loadClass(className))
    fun isApplicable(clazz: Class<*>): Applicability = isApplicable(TestClass(clazz))
    fun isApplicable(clazz: TestClass): Applicability =
        when {
            !clazz.isValidJunitClass() -> Applicability.NOT_TESTCLASS
            clazz.hasTimeoutRuleField() -> Applicability.EXISTENT_TIMEOUT
            !clazz.hasJunitTestMethod() -> Applicability.NO_TESTS
            else -> Applicability.APPLICABLE
        }

    private fun TestClass.isValidJunitClass(): Boolean =
        try {
            // getOnlyConstructor() throws AssertionError if there is more than one constructor
            val constructor = this.getOnlyConstructor()
            constructor.parameterCount == 0 && !this.isANonStaticInnerClass && this.isPublic
        } catch (e: AssertionError) {
            false
        }

    /**
     * Returns true if the class has any public instance field of type @org.junit.rules.Timeout that
     * is annotated with @org.junit.Rule
     */
    private fun TestClass.hasTimeoutRuleField(): Boolean =
        this.getAnnotatedFields(Rule::class.java)
            .find { it.type == Timeout::class.java && it.isPublic && !it.isStatic }
            ?.let { true } ?: false

    /**
     * Returns true if the class has any visible method annotated with @org.junit.Test
     */
    private fun TestClass.hasJunitTestMethod(): Boolean =
            this.annotatedMethods
                .flatMap { it.annotations.toList() }
                    // in some circumstances comparing the type with "is" or "type == org.junit.Test" fails
                    // The reason probably has to do with different classloaders having loaded the type...
                    // Why that's a problem here but not for hasTimeoutRuleField() probably has some arcane explanation
                .find { it.annotationClass.qualifiedName == "org.junit.Test" }
                ?.let { true } ?: false

    fun apply(clazz: Class<*>): ByteArray {
        val loadableName = clazz.name.replace('.', '/') + ".class"
        cl.getResourceAsStream(loadableName).use {
            return apply(it.readBytes())
        }
    }
    fun apply(classFile: File): ByteArray = apply(classFile.readBytes())
    fun apply(classBytes: ByteArray): ByteArray {
        val reader = readerFactory(classBytes)
        val writer = writerFactory(reader, asmApi + COMPUTE_MAXS )

        // Will be java/lang/Object for most but not all Junit test classes
        val superClass = reader.superName

        val visitor = object : ClassVisitor(asmApi, writer) {
            override fun visitMethod(access: Int, name: String?, descriptor: String?, signature: String?, exceptions: Array<out String>?): MethodVisitor {
                val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                return if (defaultConstructorName == name) {
                    object : MethodVisitor(asmApi, mv) {
                        override fun visitCode() {
                            // Add new instructions to the beginning of the default constructor
                            super.visitCode()
                            // java says every constructor begins with a call to its super class's constructor
                            // junit says that every test class must have a zero-argument constructor
                            // So we're safe in hardcoding the descriptor to ()V
                            super.visitVarInsn(ALOAD, 0)
                            super.visitMethodInsn(INVOKESPECIAL, superClass, defaultConstructorName, "()V", false)

                            // Now begin adding the 'new Timeout()' call
                            visitVarInsn(ALOAD, 0)
                            visitTypeInsn(NEW, "org/junit/rules/Timeout")
                            visitInsn(DUP)
                            visitLdcInsn(timeoutDurationMillis)
                            visitFieldInsn(GETSTATIC,
                                    "java/util/concurrent/TimeUnit",
                                    "MILLISECONDS",
                                    "Ljava/util/concurrent/TimeUnit;")
                            visitMethodInsn(
                                    INVOKESPECIAL,
                                    "org/junit/rules/Timeout",
                                    defaultConstructorName,
                                    "(JLjava/util/concurrent/TimeUnit;)V",
                                    false)
                            visitFieldInsn(
                                    PUTFIELD,
                                    reader.className,
                                    timeoutFieldName,
                                    junitTimeoutRuleDescriptor)
                            visitMaxs(0, 0)
                        }

                        override fun visitMethodInsn(opcode: Int, owner: String?, name: String?, descriptor: String?, isInterface: Boolean) {
                            if (opcode == INVOKESPECIAL && owner == superClass && name == "<init>") {
                                // visitCode() already added a super() call at the start of the constructor
                                // So remove any subsequent super() call as Java dictates THERE CAN BE ONLY ONE
                            } else {
                                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
                            }
                        }
                    }
                } else {
                    mv
                }
            }
        }

        // Create a field of type org.junit.rules.Timeout and annotated with org.junit.Rule
        visitor.visitField(ACC_PUBLIC, timeoutFieldName, junitTimeoutRuleDescriptor, null, null )
                .apply {
                    visitAnnotation(junitRuleAnnotationDescriptor, true).visitEnd()
                    visitEnd()
                }

        // Apply the visitor to the class file, then write out the resulting bytes
        reader.accept(visitor, 0)
        return writer.toByteArray()
    }
}