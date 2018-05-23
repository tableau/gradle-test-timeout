package com.tableau.modules.gradle

import org.junit.rules.Timeout
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.ClassWriter.COMPUTE_MAXS
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Opcodes.ALOAD
import org.objectweb.asm.Opcodes.DUP
import org.objectweb.asm.Opcodes.GETSTATIC
import org.objectweb.asm.Opcodes.INVOKESPECIAL
import org.objectweb.asm.Opcodes.NEW
import org.objectweb.asm.Opcodes.PUTFIELD
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode

import java.io.File
import java.lang.reflect.Modifier

/**
 * Uses ASM to rewrite the bytecode of a Junit4 test class to include a Timeout
 */
class Junit4TimeoutTransform(
    val timeoutDurationMillis: Long,
    val asmApi: Int = Opcodes.ASM6,
    val cl: ClassLoader = Junit4TimeoutTransform::class.java.classLoader,
    val readerFactory: (ByteArray) -> ClassReader = ::ClassReader,
    val writerFactory: (ClassReader, Int) -> ClassWriter = ::ClassWriter,
    val classNodeFactory: () -> ClassNode = ::ClassNode
) {
    companion object {
        const val jvmConstructorMethodName = "<init>"
        const val defaultTimeoutFieldName = "globalTimeout"
        const val junitTimeoutRuleDescriptor = "Lorg/junit/rules/Timeout;"
        const val junitRuleAnnotationDescriptor = "Lorg/junit/Rule;"
    }
    enum class Applicability(val shouldTransform: Boolean, val message: String) {
        APPLICABLE(true, "Class has junit4 test methods but no timeout rule"),
        NO_TESTS(false, "Class does not have any junit4 test methods"),
        EXISTENT_TIMEOUT(false, "Class already has a timeout rule, no transform needed"),
        NOT_TESTCLASS(false, "Not a valid Junit4 test class ")
    }

    fun isApplicable(className: String): Applicability = isApplicable(cl.loadClass(className))
    fun isApplicable(clazz: Class<*>): Applicability =
        when {
            !clazz.isValidJunitClass() -> Applicability.NOT_TESTCLASS
            clazz.hasTimeoutRuleField() -> Applicability.EXISTENT_TIMEOUT
            !clazz.hasJunitTestMethod() -> Applicability.NO_TESTS
            else -> Applicability.APPLICABLE
        }

    /**
     * A valid Junit test class is a public, non-static reference type with exactly 1 public constructor
     */
    private fun Class<*>.isValidJunitClass(): Boolean =
            constructors.size == 1 && !isEnum && !isInterface && !isArray && !isPrimitive &&
                    !Modifier.isStatic(modifiers) && Modifier.isPublic(modifiers)

    /**
     * Returns true if the class has any public instance field of type @org.junit.rules.Timeout that
     * is annotated with @org.junit.Rule
     */
    private fun Class<*>.hasTimeoutRuleField(): Boolean =
            this.fields
                .filter { it.annotations.find { it.annotationClass.qualifiedName == "org.junit.Rule" } != null }
                .find { it.type == Timeout::class.java && Modifier.isPublic(it.modifiers) && !Modifier.isStatic(it.modifiers) }
                ?.let { true } ?: false

    /**
     * Returns true if the class has any visible method annotated with @org.junit.Test
     */
    private fun Class<*>.hasJunitTestMethod(): Boolean =
            this.methods
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
        val classTreeView = classNodeFactory()

        // Read the bytes into our classTreeView
        reader.accept(classTreeView, 0)

        // Get a unique name for our globalTimeout field just in case there's some other field with the same name
        val takenFieldNames = classTreeView.fields.map { it.name }.toSet()
        val globalTimeoutFieldName = getUniqueName(defaultTimeoutFieldName, takenFieldNames)

        // Create our global timeout field, annotated with @Rule
        classTreeView.fields.add(
                FieldNode(asmApi, Opcodes.ACC_PUBLIC, globalTimeoutFieldName, junitTimeoutRuleDescriptor, null, null).apply {
                    visitAnnotation(junitRuleAnnotationDescriptor, true)
                })

        // Now that the field has been declared we must initialize it.
        //
        // Some considerations to keep in mind:
        // 1. Junit only allows one public constructor, but there may be package/protected/private constructors.
        // 2. Java requires that fields may not be left uninitialized, so even if we could ignore the non-public
        //    constructors - which we can't because the public one might call them - it would not be a well-formed class
        // 3. It's perfectly possible for constructors to have multiple exit points since a "return" could be anywhere
        // 4. Per Java language spec section 8.8.7:
        //        The first statement of a constructor body may be an explicit invocation of another constructor of the
        //        same class or of the direct superclass.
        //        If a constructor body does not begin with an explicit constructor invocation and the constructor being
        //        declared is not part of the primordial class Object, then the constructor body implicitly begins with
        //        a superclass constructor invocation "super();"
        //
        // Also see the Java language specification:
        //     https://docs.oracle.com/javase/specs/jls/se8/html
        //
        // Since every constructor must begin with either a "this()" or a "super()" type call, we add our
        // initialization right after calls to super() to respect the above considerations
        // Constructors that begin with a "this()" call can be skipped since by definition they must eventually call
        // a constructor with a "super()" that has our initialization code
        val constructors = classTreeView.methods
                .filter { it.name == jvmConstructorMethodName }
        for (constructor in constructors ) {
            val instructions = constructor.instructions

            // Get the super() call
            val superConstructorInvocation = instructions.find {
                it is MethodInsnNode && it.opcode == Opcodes.INVOKESPECIAL && it.owner == classTreeView.superName }
            ?: continue // or skip modifying this constructor

            instructions.insert(superConstructorInvocation,
                    insnListOf(
                            VarInsnNode(ALOAD, 0),
                            TypeInsnNode(NEW, "org/junit/rules/Timeout"),
                            InsnNode(DUP),
                            LdcInsnNode(timeoutDurationMillis),
                            FieldInsnNode(GETSTATIC,
                                    "java/util/concurrent/TimeUnit",
                                    "MILLISECONDS",
                                    "Ljava/util/concurrent/TimeUnit;"),
                            MethodInsnNode(
                                    INVOKESPECIAL,
                                    "org/junit/rules/Timeout",
                                    jvmConstructorMethodName,
                                    "(JLjava/util/concurrent/TimeUnit;)V",
                                    false),
                            FieldInsnNode(
                                    PUTFIELD,
                                    reader.className,
                                    globalTimeoutFieldName,
                                    junitTimeoutRuleDescriptor)
                    ))
        }

        // Write the modified class to the given writer
        classTreeView.accept(writer)
        return writer.toByteArray()
    }
    private fun getUniqueName(desiredName: String, namesAlreadyTaken: Set<String>): String {
        if (!namesAlreadyTaken.contains(desiredName)) {
            return desiredName
        }
        for (i in 1..100) {
            val candidateName = desiredName + i.toString()
            if (!namesAlreadyTaken.contains(candidateName)) {
                return candidateName
            }
        }
        throw IllegalArgumentException("Couldn't find unique field for $desiredName... " +
                "seriously who has 100 sequentially named fields on one class? " +
                "Are you applying this transform over and over to the same class? " +
                "Why would you do that. You monster.")
    }

    /**
     * Convenience builder method for InsnLists which don't naturally expose any method or constructor that allows for
     * adding multiple items at once
     */
    private fun insnListOf(vararg instructions: AbstractInsnNode): InsnList =
        InsnList().apply {
            instructions.forEach { add(it) }
        }

    /**
     * Convenience methods for searching an InsnList, which is a doubly linked list that does not implement any
     * of the typical JVM collections interfaces, for a particular instruction
     */
    private fun InsnList.find(predicate: (AbstractInsnNode) -> Boolean): AbstractInsnNode? = this.first?.find(predicate)
    private fun AbstractInsnNode.find(predicate: (AbstractInsnNode) -> Boolean): AbstractInsnNode? =
            when {
                predicate(this) -> this
                else -> next?.find(predicate)
            }
}