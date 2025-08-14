plugins { java }
group = "com.example"
version = "2.0.1"

repositories {
  maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
  mavenCentral()
}

dependencies {
  compileOnly("org.spigotmc:spigot-api:1.21-R0.1-SNAPSHOT")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}
tasks.withType<JavaCompile> {
  options.encoding = "UTF-8"
  options.release.set(21)
}
