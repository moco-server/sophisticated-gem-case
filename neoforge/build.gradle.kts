plugins {
    `java-library`
    alias(libs.plugins.kotlin)
    alias(libs.plugins.moddev)
}
kotlin { jvmToolchain(21) }
val templateModId = providers.gradleProperty("mod_id").get()
base { archivesName.set(templateModId) }
repositories {
    maven("https://thedarkcolour.github.io/KotlinForForge/") {
        content { includeGroup("thedarkcolour") }
    }
}
dependencies {
    implementation(project(":core"))
    implementation(libs.kff)
}
val metadata =
    tasks.register<ProcessResources>("generateModMetadata") {
        val properties =
            listOf(
                "mod_id",
                "mod_name",
                "mod_version",
                "mod_authors",
                "mod_description",
                "mod_license",
                "mod_repo_url",
            ).associateWith {
                providers
                    .gradleProperty(it)
                    .get()
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t")
            }
        inputs.properties(properties)
        filteringCharset = "UTF-8"
        expand(properties)
        from("src/main/templates")
        into(layout.buildDirectory.dir("generated/modMetadata"))
    }
sourceSets.main {
    resources.srcDir(metadata)
    resources.srcDir("src/generated/resources")
}
val gametest =
    sourceSets.create("gametest") {
        compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
        runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath
    }
configurations[gametest.implementationConfigurationName].extendsFrom(configurations.implementation.get())
val fixtureMetadata =
    tasks.register<ProcessResources>("generateGameTestMetadata") {
        val values = mapOf("mod_id" to templateModId, "mod_version" to project.version.toString())
        inputs.properties(values)
        expand(values)
        from("src/gametest/templates")
        into(layout.buildDirectory.dir("generated/gameTestMetadata"))
    }
gametest.resources.srcDir(fixtureMetadata)
neoForge {
    version = providers.gradleProperty("neo_version").get()
    mods {
        create(templateModId) {
            sourceSet(sourceSets.main.get())
            sourceSet(project(":core").sourceSets.main.get())
        }
    }
    mods.create("${templateModId}_tests") { sourceSet(gametest) }
    runs {
        create("client") {
            client()
            gameDirectory.set(rootProject.file(".local/client"))
        }
        create("server") {
            server()
            programArgument("--nogui")
            gameDirectory.set(rootProject.file(".local/server"))
        }
        create("data") {
            data()
            gameDirectory.set(rootProject.file(".local/data"))
            programArguments.addAll(
                "--mod",
                templateModId,
                "--all",
                "--output",
                file("src/generated/resources").absolutePath,
                "--existing",
                file("src/main/resources").absolutePath,
            )
        }
        create("gameTestServer") {
            type = "gameTestServer"
            gameDirectory.set(rootProject.file(".local/gametest"))
            systemProperty("neoforge.enabledGameTestNamespaces", templateModId)
        }
    }
    runs.configureEach {
        loadedMods.set(listOf(mods.getByName(templateModId)))
    }
    runs.getByName("gameTestServer").apply {
        sourceSet = gametest
        loadedMods.set(listOf(mods.getByName(templateModId), mods.getByName("${templateModId}_tests")))
    }
    ideSyncTask(metadata)
}
tasks.jar {
    from(
        project(":core")
            .sourceSets.main
            .get()
            .output,
    )
    from(rootProject.file("LICENSE")) { into("META-INF") }
    duplicatesStrategy = DuplicatesStrategy.FAIL
}

val verifyModJar =
    tasks.register<VerifyModJarTask>("verifyModJar") {
        modJar.set(tasks.jar.flatMap { it.archiveFile })
        modId.set(templateModId)
        val path = providers.gradleProperty("mod_package").get().replace('.', '/')
        packagePath.set(path)
        requiredEntries.set(
            listOf(
                "$path/core/QuantityRulesKt.class",
                "$path/ModItems.class",
                "META-INF/neoforge.mods.toml",
                "META-INF/LICENSE",
                "assets/$templateModId/lang/en_us.json",
                "assets/$templateModId/lang/ja_jp.json",
                "assets/$templateModId/models/item/sample_token.json",
                "data/$templateModId/recipe/sample_token.json",
            ),
        )
    }
tasks.named("build") { dependsOn(verifyModJar) }

tasks.processResources { exclude("**/.cache/**") }
