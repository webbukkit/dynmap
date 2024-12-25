plugins {
    id("com.gradleup.shadow") version "8.3.5"
    id("java")
    id("maven-publish")
    id("io.papermc.paperweight.userdev") version "1.7.5" apply false
    eclipse
}

eclipse {
    project {
        name = "Dynmap"
    }
}

allprojects {
    repositories {
        mavenLocal()
        mavenCentral()
        maven { url = uri("https://libraries.minecraft.net/") }
        maven { url = uri("https://oss.sonatype.org/content/repositories/releases") }
        maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }
        maven { url = uri("https://repo.mikeprimm.com") }
        maven { url = uri("https://repo.maven.apache.org/maven2") }
        maven { url = uri("https://papermc.io/repo/repository/maven-public/") }
        maven { url = uri("https://repo.codemc.org/repository/maven-public/") }
    }

    apply(plugin = "java")

    group = "us.dynmap"
    version = "3.8-beta-1"
}

ext["buildNumber"] = System.getenv("BUILD_NUMBER") ?: "Dev"

subprojects {
    apply(plugin = "com.gradleup.shadow")
    apply(plugin = "java")
    apply(plugin = "maven-publish")

    java {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}

tasks.named<Delete>("clean") {
    delete("target")
}

tasks.register("setupCIWorkspace")