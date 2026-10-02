plugins {
    base
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.moddev) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint)
}
allprojects {
    group = providers.gradleProperty("mod_group_id").get()
    version = providers.gradleProperty("mod_version").get()
    repositories { mavenCentral() }
}
tasks.named("check") { dependsOn(":core:check", ":neoforge:check") }

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")
    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set(
            rootProject.libs.versions.ktlint
                .asProvider()
                .get(),
        )
    }
    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    }
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions.allWarningsAsErrors.set(true)
    }
    tasks.matching { it.name == "check" }.configureEach { dependsOn("detektMain", "detektTest") }
}
ktlint {
    version.set(
        libs.versions.ktlint
            .asProvider()
            .get(),
    )
}
val checkBuildLogic =
    tasks.register<GradleBuild>("checkBuildLogic") {
        dir = file("buildSrc")
        tasks = listOf("check")
        buildName = "mod-build-checks-validation"
    }
tasks.named("check") { dependsOn(checkBuildLogic) }
