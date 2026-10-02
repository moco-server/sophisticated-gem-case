import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class CoreBoundaryTaskTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `allows JDK and Kotlin references`() {
        val file = directory.resolve("Allowed.class").toFile()
        file.writeBytes(fixture("Ljava/lang/String;", "Ljava/util/List<Ljava/lang/String;>;"))
        inspectCoreBoundary(emptyList(), listOf(file), listOf("net/minecraft/"))
    }

    @Test
    fun `rejects forbidden classes in a transitive jar`() {
        val jar = directory.resolve("transitive.jar").toFile()
        JarOutputStream(jar.outputStream()).use {
            it.putNextEntry(JarEntry("net/minecraft/world/item/Item.class"))
            it.write(byteArrayOf(0))
            it.closeEntry()
        }
        val error =
            assertThrows(IllegalStateException::class.java) {
                inspectCoreBoundary(listOf(jar), emptyList(), listOf("net/minecraft/"))
            }
        assertTrue(error.message.orEmpty().contains("transitive.jar"))
        assertTrue(error.message.orEmpty().contains("net/minecraft/world/item/Item"))
    }

    @Test
    fun `rejects a generic public signature`() {
        val file = directory.resolve("Leak.class").toFile()
        file.writeBytes(fixture("Ljava/util/List;", "Ljava/util/List<Lnet/minecraft/world/item/Item;>;"))
        val error =
            assertThrows(IllegalStateException::class.java) {
                inspectCoreBoundary(emptyList(), listOf(file), listOf("net/minecraft/"))
            }
        assertTrue(error.message.orEmpty().contains("Leak.class"))
        assertTrue(error.message.orEmpty().contains("net/minecraft/world/item/Item"))
    }

    @Test
    fun `rejects fully qualified instruction references`() {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "Fixture", null, "java/lang/Object", null)
        val method = writer.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "run", "()V", null, null)
        method.visitTypeInsn(Opcodes.NEW, "net/neoforged/example/LoaderType")
        method.visitInsn(Opcodes.POP)
        method.visitInsn(Opcodes.RETURN)
        method.visitMaxs(1, 0)
        method.visitEnd()
        writer.visitEnd()
        val file = directory.resolve("Instruction.class").toFile()
        file.writeBytes(writer.toByteArray())
        assertThrows(IllegalStateException::class.java) {
            inspectCoreBoundary(emptyList(), listOf(file), listOf("net/neoforged/"))
        }
    }

    private fun fixture(
        descriptor: String,
        signature: String,
    ): ByteArray {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "Fixture", null, "java/lang/Object", null)
        writer.visitField(Opcodes.ACC_PUBLIC, "value", descriptor, signature, null).visitEnd()
        writer.visitEnd()
        return writer.toByteArray()
    }
}
