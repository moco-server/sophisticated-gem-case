pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.neoforged.net/releases")
    }
}
rootProject.name = "minecraft-mod-template"
include("core", "neoforge")
