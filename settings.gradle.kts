pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
  }
}
plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}
rootProject.name = "PvPEnhancer"
