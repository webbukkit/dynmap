plugins {
    eclipse
}

eclipse {
    project {
        name = "Dynmap(DynmapCoreAPI)"
    }
}

tasks.named<JavaCompile>("compileJava") {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
}

description = "DynmapCoreAPI"

tasks.withType<Jar>() {
    destinationDirectory.set(File("../target"))
}

configure<PublishingExtension> {
    publications.withType<MavenPublication>() {
        from(components["java"])
    }
}
