import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    eclipse
}

eclipse {
    project {
        name = "Dynmap(dynmap-api)"
    }
}

description = "dynmap-api"

tasks.named<JavaCompile>("compileJava") {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
}

dependencies {
    compileOnly("org.bukkit:bukkit:1.7.10-R0.1-SNAPSHOT")
    compileOnly(project(":DynmapCoreAPI"))
}

tasks.named<ShadowJar>("shadowJar") {
    dependencies {
        include(dependency(":DynmapCoreAPI"))
	}
    destinationDirectory.set(File("../target"))
    archiveClassifier.set("")
}

configure<PublishingExtension> {
    publications.withType<MavenPublication>() {
        from(components["java"])
    }
}
