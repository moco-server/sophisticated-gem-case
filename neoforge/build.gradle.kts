plugins {
    `java-library`
    `maven-publish`
    alias(libs.plugins.kotlin)
    alias(libs.plugins.moddev)
}
kotlin { jvmToolchain(21) }
val templateModId = providers.gradleProperty("mod_id").get()
base { archivesName.set(templateModId) }
repositories {
    maven("https://api.modrinth.com/maven") {
        content { includeGroup("maven.modrinth") }
    }
    maven("https://thedarkcolour.github.io/KotlinForForge/") {
        content { includeGroup("thedarkcolour") }
    }
}
dependencies {
    constraints {
        implementation(libs.fml.loader) {
            because("KFFのloader範囲依存をNeoForge 21.1.252の4.0.44へ揃え、JPMSの同名module衝突を防ぐ")
        }
    }
    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation(project(":core"))
    implementation(libs.kff)
    implementation(libs.apotheosis)
    implementation(libs.sophisticated.backpacks)
    implementation(libs.sophisticated.core)
    implementation(libs.placebo)
    implementation(libs.apothic.attributes)
    runtimeOnly(libs.patchouli)
    runtimeOnly(libs.apothic.spawners)
    runtimeOnly(libs.apothic.enchanting)
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
            } +
                mapOf(
                    "apotheosis_version" to
                        libs.versions.apotheosis
                            .get()
                            .substringAfter("1.21.1-"),
                    "backpacks_version" to
                        libs.versions.sophisticated.backpacks
                            .get()
                            .substringAfter("1.21.1-")
                            .substringBeforeLast('.'),
                    "core_version" to
                        libs.versions.sophisticated.core
                            .get()
                            .substringAfter("1.21.1-")
                            .substringBeforeLast('.'),
                )
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
// 実行別のMODはlegacy classpathでは検出されないため、専用source setのruntimeへ置く。
val clientDevelopment = sourceSets.create("clientDevelopment")
val dataDevelopment = sourceSets.create("dataDevelopment")
listOf(clientDevelopment, dataDevelopment).forEach { development ->
    development.runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath
    configurations[development.implementationConfigurationName].extendsFrom(configurations.implementation.get())
    configurations[development.runtimeOnlyConfigurationName].extendsFrom(configurations.runtimeOnly.get())
}
dependencies {
    add(clientDevelopment.runtimeOnlyConfigurationName, libs.jei)
    // Apotheosis 8.9.0のGatherDataEventは任意連携Gatewaysの型も参照する。
    add(dataDevelopment.runtimeOnlyConfigurationName, libs.gateways)
}
val gametest =
    sourceSets.create("gametest") {
        compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
        runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath
    }
configurations[gametest.implementationConfigurationName].extendsFrom(configurations.implementation.get())
// GameTestから本番のinternal実装を検収する。通常JARにはfixtureを入れない。
kotlin.target.compilations
    .getByName("gametest")
    .associateWith(kotlin.target.compilations.getByName("main"))
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
            sourceSet = clientDevelopment
            gameDirectory.set(rootProject.file(".local/client"))
        }
        create("server") {
            server()
            programArgument("--nogui")
            gameDirectory.set(rootProject.file(".local/server"))
        }
        create("data") {
            data()
            sourceSet = dataDevelopment
            gameDirectory.set(rootProject.file(".local/data"))
            programArguments.addAll(
                "--mod",
                templateModId,
                "--all",
                "--output",
                file("src/generated/resources").absolutePath,
                "--existing",
                file("src/main/resources").absolutePath,
                // 元MODの基板textureも生成モデルの参照先として検査する。
                "--existing-mod",
                "sophisticatedbackpacks",
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
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
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
                "$path/core/GemCounts.class",
                "$path/core/GemFilter.class",
                "$path/core/GemMaterialAllocator.class",
                "$path/core/GemPurityPlan.class",
                "$path/storage/GemStorageCodec.class",
                "$path/storage/GemSettingsCodec.class",
                "$path/storage/GemMaterialCodec.class",
                "$path/storage/GemMaterialHandler.class",
                "$path/processing/GemProcessingEngine.class",
                "$path/integration/GemCaseConversion.class",
                "$path/integration/GemMaterialClicks.class",
                "$path/network/GemProcessingRequest.class",
                "$path/client/GemCaseModal.class",
                "$path/mixin/GemSlotPositionAccessor.class",
                "$path/storage/GemProcessingHandler.class",
                "$path/storage/GemExternalTransfers.class",
                "$path/integration/GemTransferBridge.class",
                "$path/network/GemSettingsRequest.class",
                "$path/client/GemSettingsPanel.class",
                "$path/mixin/DepositUpgradeMixin.class",
                "$path/mixin/RestockUpgradeMixin.class",
                "$path/mixin/GemUpgradeStorageAccessor.class",
                "$path/menu/GemCaseContainer.class",
                "$path/mixin/BackpackWrapperMixin.class",
                "sophisticated_gem_case.mixins.json",
                "data/sophisticatedbackpacks/tags/item/upgrade.json",
                "assets/$templateModId/models/item/gem_case_upgrade.json",
                "assets/$templateModId/models/item/advanced_gem_case_upgrade.json",
                "assets/$templateModId/models/item/gem_case_upgrade_conversion.json",
                "assets/$templateModId/textures/item/gem_case_upgrade.png",
                "assets/$templateModId/textures/item/advanced_gem_case_upgrade.png",
                "assets/$templateModId/textures/item/gem_case_upgrade_conversion.png",
                "META-INF/neoforge.mods.toml",
                "META-INF/LICENSE",
                "assets/$templateModId/lang/en_us.json",
                "assets/$templateModId/lang/ja_jp.json",
                "assets/$templateModId/models/item/sample_token.json",
                "data/$templateModId/recipe/sample_token.json",
                "data/$templateModId/recipe/gem_case_upgrade.json",
                "data/$templateModId/recipe/gem_case_upgrade_conversion.json",
                "data/$templateModId/advancement/recipes/misc/gem_case_upgrade.json",
                "data/$templateModId/advancement/recipes/misc/gem_case_upgrade_conversion.json",
            ),
        )
    }
tasks.named("build") { dependsOn(verifyModJar) }

publishing {
    publications {
        create<MavenPublication>("mod") {
            artifactId = "sophisticated-gem-case"
            // 公開時は CI で検証した通常 JAR をそのまま使う。
            val releaseJar = providers.gradleProperty("releaseJar").orNull
            if (releaseJar == null) {
                artifact(tasks.jar) { builtBy(verifyModJar) }
            } else {
                artifact(rootProject.file(releaseJar))
            }
            pom {
                name.set(providers.gradleProperty("mod_name"))
                description.set(providers.gradleProperty("mod_description"))
                url.set(providers.gradleProperty("mod_repo_url"))
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            val repoUrl = providers.gradleProperty("mod_repo_url").get()
            url = uri(repoUrl.replace("https://github.com/", "https://maven.pkg.github.com/"))
            credentials {
                username = providers.environmentVariable("GITHUB_ACTOR").orNull
                password = providers.environmentVariable("GITHUB_TOKEN").orNull
            }
        }
    }
}

tasks.processResources { exclude("**/.cache/**") }

tasks.test { useJUnitPlatform() }
