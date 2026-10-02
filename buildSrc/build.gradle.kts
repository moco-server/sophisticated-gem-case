plugins {
    `kotlin-dsl`
    alias(libs.plugins.ktlint)
}
repositories { mavenCentral() }
dependencies {
    implementation("org.ow2.asm:asm-commons:${libs.versions.asm.get()}")
    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
kotlin { jvmToolchain(21) }
tasks.test { useJUnitPlatform() }
ktlint {
    version.set(
        libs.versions.ktlint
            .asProvider()
            .get(),
    )
}
configurations
    .matching {
        it.name in
            setOf("compileClasspath", "runtimeClasspath", "testCompileClasspath", "testRuntimeClasspath")
    }.configureEach { resolutionStrategy.activateDependencyLocking() }
