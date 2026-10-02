import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class VerifyModJarTaskTest {
    @TempDir
    lateinit var directory: Path

    private val required =
        listOf(
            "example/core/QuantityRulesKt.class",
            "example/ModItems.class",
            "META-INF/neoforge.mods.toml",
            "META-INF/LICENSE",
            "assets/example/lang/en_us.json",
            "data/example/recipe/sample_token.json",
        )

    @Test
    fun `accepts a complete normal jar`() {
        assertDoesNotThrow { verifyModJar(jar(required), required, "example", "example") }
    }

    @Test
    fun `rejects each required file missing`() {
        required.forEach { missing ->
            assertThrows(IllegalStateException::class.java) {
                verifyModJar(jar(required - missing), required, "example", "example")
            }
        }
    }

    @Test
    fun `rejects test fixtures development files and external classes`() {
        listOf(
            "example/TemplateGameTests.class",
            "data/example_tests/structure/empty.nbt",
            ".cache/datagen-cache",
            "docs/design.md",
            ".agents/skills/mod-kotlin/SKILL.md",
            "net/minecraft/Item.class",
            "kotlin/Unit.class",
            "thirdparty/External.class",
        ).forEach { forbidden ->
            assertThrows(IllegalStateException::class.java) {
                verifyModJar(jar(required + forbidden), required, "example", "example")
            }
        }
    }

    private fun jar(entries: List<String>): java.io.File {
        val file = directory.resolve("normal.jar").toFile()
        JarOutputStream(file.outputStream()).use { jar ->
            entries.forEach {
                jar.putNextEntry(JarEntry(it))
                jar.write(byteArrayOf(0))
                jar.closeEntry()
            }
        }
        return file
    }
}
