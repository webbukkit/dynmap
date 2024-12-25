import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.papermc.paperweight.userdev.attribute.Obfuscation

plugins {
    id("com.gradleup.shadow")
    eclipse
}

eclipse {
    project {
        name = "Dynmap(Spigot)"
    }
}

description = "Dynmap plugin for bukkit based servers"

var adapters = configurations.create("adapters") {
    description = "Adapters to include in the JAR"
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
    shouldResolveConsistentlyWith(configurations["runtimeClasspath"])
    attributes {
        attribute(Obfuscation.OBFUSCATION_ATTRIBUTE, objects.named(Obfuscation.OBFUSCATED))
    }
}

repositories {
    maven {
        url = uri("https://jitpack.io")
    }
    maven {
        url = uri("https://repo.codemc.org/repository/maven-releases/")
    }
    maven { url  = uri ("https://papermc.io/repo/repository/maven-public/") }
}

dependencies {
    compileOnly("org.bukkit:bukkit:1.10.2-R0.1-SNAPSHOT")
    compileOnly("com.nijikokun.bukkit:Permissions:3.1.6")  { isTransitive = false }
    compileOnly("me.lucko.luckperms:luckperms-api:4.3")  { isTransitive = false }
    compileOnly("net.luckperms:api:5.4")  { isTransitive = false }
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") { isTransitive = false }
    compileOnly("net.skinsrestorer:skinsrestorer-api:14.2.+")  { isTransitive = false }
    implementation(project(":dynmap-api"))  { isTransitive = false }
    implementation(project(":DynmapCore", configuration = "shadow"))  { isTransitive = false }
    compileOnly("ru.tehkode:PermissionsEx:1.19.1")  { isTransitive = false }
    compileOnly("de.bananaco:bPermissions:2.9.1")  { isTransitive = false }
    compileOnly("com.platymuus.bukkit.permissions:PermissionsBukkit:1.6")  { isTransitive = false }
    compileOnly("org.anjocaido:EssentialsGroupManager:2.10.1")  { isTransitive = false }
    implementation("org.bstats:bstats-bukkit:3.0.2")
    compileOnly("com.googlecode.json-simple:json-simple:1.1.1") { isTransitive = false }
    compileOnly("com.google.code.gson:gson:2.8.9") { isTransitive = false }

    implementation(project(":bukkit-helper"))

    project.project(":bukkit-helper:adapters").subprojects.forEach {
        "adapters"(project(it.path))
    }
}

tasks.named<Copy>("processResources") {
    val internalVersion = "${project.version}-${project.parent!!.ext["buildNumber"]}"
    inputs.property("internalVersion", internalVersion)
    filesMatching("plugin.yml") {
        expand("internalVersion" to internalVersion)
    }
}

/*
jar {
    classifier = 'unshaded'
}*/

tasks.named<ShadowJar>("shadowJar") {
    configurations.add(adapters)

    dependencies {
        include(dependency("org.bstats::"))
        include(dependency(":dynmap-api"))
        include(dependency(":DynmapCore"))
        include(dependency(":bukkit-helper"))

        project.project(":bukkit-helper:adapters").subprojects.forEach {
            include(dependency("${it.group}:${it.name}"))
        }
    }
    relocate("org.bstats", "org.dynmap.bstats")
//    destinationDir = file '../target'
//    archiveName = "Dynmap-${parent.version}-spigot.jar"
//    classifier = ''
}

tasks.named<ShadowJar>("shadowJar") {
    doLast {
        ant.withGroovyBuilder {
            "checksum"("file" to archiveFile.get().asFile)
        }
    }
}

tasks.named("assemble").configure {
    dependsOn("shadowJar")
}
