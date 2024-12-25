import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    eclipse
}

eclipse {
    project {
        name = "Dynmap(DynmapCore)"
    }
}

description = "DynmapCore"

tasks.named<JavaCompile>("compileJava") {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
}

dependencies {
    implementation(project(":DynmapCoreAPI"))
    implementation("javax.servlet:javax.servlet-api:3.1")
    implementation("org.eclipse.jetty:jetty-server:9.4.26.v20200117")
    implementation("org.eclipse.jetty:jetty-servlet:9.4.26.v20200117")
    implementation("com.googlecode.json-simple:json-simple:1.1.1")
    implementation("org.yaml:snakeyaml:1.23")	// DON'T UPDATE - NEWER ONE TRIPS ON WINDOWS ENCODED FILES
    implementation("com.googlecode.owasp-java-html-sanitizer:owasp-java-html-sanitizer:20180219.1")
    implementation("org.postgresql:postgresql:42.2.18")
    implementation("io.github.linktosriram.s3lite:core:0.0.2-SNAPSHOT")
    implementation("io.github.linktosriram.s3lite:api:0.0.2-SNAPSHOT")
    implementation("io.github.linktosriram.s3lite:http-client-url-connection:0.0.2-SNAPSHOT")
    implementation("io.github.linktosriram.s3lite:http-client-spi:0.0.2-SNAPSHOT")
    implementation("io.github.linktosriram.s3lite:util:0.0.2-SNAPSHOT")
    implementation("jakarta.xml.bind:jakarta.xml.bind-api:3.0.1")
    implementation("com.sun.xml.bind:jaxb-impl:3.0.0")
}

tasks.named<Copy>("processResources") {
    filesMatching(listOf("core.yml",
        "lightings.txt",
        "perspectives.txt",
        "shaders.txt",
        "extracted/web/version.js",
        "extracted/web/index.html",
        "extracted/web/login.html")) {
        expand("buildNumber" to project.parent!!.ext["buildNumber"])
        expand("version" to project.version)
    }
}

tasks.named<ShadowJar>("shadowJar") {
    dependencies {
        include(dependency("com.googlecode.json-simple:json-simple:"))
        include(dependency("org.yaml:snakeyaml:"))
        include(dependency("com.googlecode.owasp-java-html-sanitizer:owasp-java-html-sanitizer:"))
        include(dependency("javax.servlet::"))
        include(dependency("org.eclipse.jetty::"))
        include(dependency("org.eclipse.jetty.orbit:javax.servlet:"))
        include(dependency("org.postgresql:postgresql:"))
        include(dependency("io.github.linktosriram.s3lite:core:"))
        include(dependency("io.github.linktosriram.s3lite:api:"))
        include(dependency("io.github.linktosriram.s3lite:http-client-url-connection:"))
        include(dependency("io.github.linktosriram.s3lite:http-client-spi:"))
        include(dependency("io.github.linktosriram.s3lite:util:"))
        include(dependency("jakarta.xml.bind::"))
        include(dependency("com.sun.xml.bind::"))
        include(dependency(":DynmapCoreAPI"))
        exclude("META-INF/maven/**")
        exclude("META-INF/services/**")
	}
	relocate("org.json.simple", "org.dynmap.json.simple")
	relocate("org.yaml.snakeyaml", "org.dynmap.snakeyaml")
	relocate("org.eclipse.jetty", "org.dynmap.jetty")
	relocate("org.owasp.html", "org.dynmap.org.owasp.html")
	relocate("javax.servlet", "org.dynmap.javax.servlet" )
	relocate("org.postgresql", "org.dynmap.org.postgresql")
	relocate("io.github.linktosriram.s3lite", "org.dynmap.s3lite")

    destinationDirectory.set(File("../target"))
    archiveClassifier.set("")
}

tasks.named("assemble").configure {
    dependsOn("shadowJar")
}
