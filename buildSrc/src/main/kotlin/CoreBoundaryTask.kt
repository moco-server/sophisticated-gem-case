import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper
import java.io.File
import java.util.jar.JarFile

/** coreの本番依存と型参照を検査する。連携MODのprefixは各MODで追加する。 */
abstract class CoreBoundaryTask : DefaultTask() {
    @get:Input
    abstract val forbiddenPackagePrefixes: ListProperty<String>

    @get:Classpath
    abstract val productionClasspath: ConfigurableFileCollection

    @get:Classpath
    abstract val productionClasses: ConfigurableFileCollection

    init {
        forbiddenPackagePrefixes.convention(
            listOf(
                "net/minecraft/",
                "net/neoforged/",
                "net/minecraftforge/",
                "net/fabricmc/",
                "thedarkcolour/kotlinforforge/",
            ),
        )
    }

    @TaskAction
    fun verify() {
        inspectCoreBoundary(productionClasspath.files, productionClasses.files, forbiddenPackagePrefixes.get())
    }
}

internal fun inspectCoreBoundary(
    classpath: Collection<File>,
    classes: Collection<File>,
    prefixes: List<String>,
) {
    require(
        prefixes.all {
            it.isNotBlank() && it.endsWith('/')
        },
    ) { "Forbidden prefixes must be non-empty JVM package paths" }
    val violations = sortedSetOf<String>()

    fun inspectClass(
        origin: String,
        bytes: ByteArray,
    ) {
        val remapper =
            object : Remapper() {
                override fun map(internalName: String): String {
                    if (prefixes.any(internalName::startsWith)) violations.add("$origin references $internalName")
                    return internalName
                }
            }
        ClassReader(bytes).accept(ClassRemapper(ClassWriter(0), remapper), 0)
    }

    fun inspectEntry(
        origin: String,
        name: String,
        bytes: () -> ByteArray,
    ) {
        if (!name.endsWith(".class")) return
        // multi-release JARも通常のパッケージ名として検査する。
        val normalized = name.replace(Regex("^META-INF/versions/[0-9]+/"), "")
        if (prefixes.any(normalized::startsWith)) {
            violations.add("$origin contains $normalized")
        } else {
            inspectClass("$origin!/$name", bytes())
        }
    }
    (classpath + classes).distinct().filter(File::exists).forEach { artifact ->
        when {
            artifact.isDirectory ->
                artifact.walkTopDown().filter { it.isFile }.forEach { file ->
                    inspectEntry(artifact.path, file.relativeTo(artifact).invariantSeparatorsPath, file::readBytes)
                }
            artifact.extension == "jar" ->
                JarFile(artifact).use { jar ->
                    jar.entries().asSequence().filter { !it.isDirectory }.forEach { entry ->
                        inspectEntry(artifact.path, entry.name) { jar.getInputStream(entry).use { it.readBytes() } }
                    }
                }
            artifact.extension == "class" -> inspectClass(artifact.path, artifact.readBytes())
        }
    }
    check(violations.isEmpty()) { "core boundary violations:\n${violations.joinToString("\n")}" }
}
