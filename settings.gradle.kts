pluginManagement {
  repositories {
    gradlePluginPortal()
    maven { url = uri("https://papermc.io/repo/repository/maven-public/") }
    maven { url = uri("https://maven.fabricmc.net/") }
  }
}

rootProject.name = "dynmap-common"

include(":spigot")

listOf(
  "113-2",
  "114-1",
  "115",
  "116",
  "116-2",
  "116-3",
  "116-4",
  "117",
  "118",
  "118-2",
  "119",
  "119-3",
  "119-4",
  "120",
  "120-2",
  "120-4",
  "120-5",
  "121",
  "121-3"
).forEach {
  include(":bukkit-helper:adapters:bukkit-helper-$it")
}

include(":bukkit-helper")
include(":dynmap-api")
include (":DynmapCore")
include (":DynmapCoreAPI")
include (":fabric-1.21")
include (":fabric-1.20.6")
include (":fabric-1.20.4")
include (":fabric-1.20.2")
include (":fabric-1.20")
include (":fabric-1.19.4")
include (":fabric-1.18.2")
include (":fabric-1.17.1")
include (":fabric-1.16.4")
include (":fabric-1.15.2")
include (":fabric-1.14.4")
include (":forge-1.21")
include (":forge-1.20.6")
include (":forge-1.20.2")
include (":forge-1.20")
include (":forge-1.19.3")
include (":forge-1.18.2")
include (":forge-1.17.1")
include (":forge-1.16.5")
include (":forge-1.15.2")
include (":forge-1.14.4")
