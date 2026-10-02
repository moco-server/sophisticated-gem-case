import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.jar.JarFile

/** 通常配布JARの必須ファイルと開発用ファイルの混入を検査する。 */
abstract class VerifyModJarTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val modJar: RegularFileProperty

    @get:Input
    abstract val requiredEntries: ListProperty<String>

    @get:Input
    abstract val modId: Property<String>

    @get:Input
    abstract val packagePath: Property<String>

    @TaskAction
    fun verify() {
        verifyModJar(modJar.get().asFile, requiredEntries.get(), modId.get(), packagePath.get())
    }
}

internal fun verifyModJar(
    file: File,
    required: List<String>,
    modId: String,
    packagePath: String,
) {
    JarFile(file).use { jar ->
        val entries =
            jar
                .entries()
                .asSequence()
                .filter { !it.isDirectory }
                .map { it.name }
                .toSet()
        val missing = required.filterNot(entries::contains)
        val forbidden =
            entries.filter {
                (
                    it.endsWith(".class") &&
                        (!it.startsWith("$packagePath/") || it.contains("GameTests") || it.contains("/tests/"))
                ) ||
                    it.startsWith(".cache/") ||
                    it.startsWith("docs/") ||
                    it.startsWith(".agents/") ||
                    it.startsWith("scripts/") ||
                    it.startsWith("data/${modId}_tests/") ||
                    it.endsWith("SKILL.md")
            }
        check(missing.isEmpty() && forbidden.isEmpty()) {
            "Invalid MOD JAR ${file.name}: missing=$missing forbidden=$forbidden"
        }
    }
}
