plugins {
    id("io.papermc.paperweight.userdev")
    eclipse
}

eclipse {
    project {
        name = "Dynmap(Spigot-1.21)"
    }
}

description = "bukkit-helper-1.21.3"

repositories {
    mavenCentral()
    maven { url = uri("https://papermc.io/repo/repository/maven-public/") }
}

paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.REOBF_PRODUCTION

tasks.named<JavaCompile>("compileJava") {
    sourceCompatibility = "21"
    targetCompatibility = "21"
    options.release.set(21)
}

dependencies {
  implementation(project(":bukkit-helper"))
  implementation(project(":dynmap-api"))
  implementation(project(":DynmapCore", configuration = "shadow"))

  paperweight {
      paperDevBundle("1.21.3-R0.1-SNAPSHOT")
  }
}

tasks.assemble {
    dependsOn(tasks.reobfJar)
}
