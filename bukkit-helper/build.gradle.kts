plugins {
    eclipse
}

eclipse {
    project {
        name = "Dynmap(Spigot-Common)"
    }
}

description = "bukkit-helper"

tasks.named<JavaCompile>("compileJava") {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
}

dependencies {
  implementation(project(":dynmap-api"))
  implementation(project(":DynmapCore", configuration = "shadow"))
  compileOnly("org.bukkit:bukkit:1.10.2-R0.1-SNAPSHOT")
  compileOnly("com.google.code.gson:gson:2.8.9")
}
