plugins {
    `java-library`
    alias(libs.plugins.kotlin)
    alias(libs.plugins.detekt)
}
kotlin {
    jvmToolchain(21)
    explicitApi()
}
dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.test { useJUnitPlatform() }
val checkCoreBoundary =
    tasks.register<CoreBoundaryTask>("checkCoreBoundary") {
        productionClasspath.from(configurations.compileClasspath, configurations.runtimeClasspath)
        productionClasses.from(sourceSets.main.map { it.output.classesDirs })
        dependsOn(tasks.classes)
    }
tasks.check { dependsOn(checkCoreBoundary) }
configurations
    .matching {
        it.name in
            setOf("compileClasspath", "runtimeClasspath", "testCompileClasspath", "testRuntimeClasspath")
    }.configureEach { resolutionStrategy.activateDependencyLocking() }
