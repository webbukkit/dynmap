# Dynmap - dynamic web maps for Minecraft servers

## How to build
Dynmap 3.x+ uses Gradle for building support for all platforms, with all resulting artifacts produced in the /targets directory

To build, run:
    ./gradlew clean build install
Or (on Windows):
    gradlew.bat clean build install
    
# What platforms are supported?
The following target platforms are supported:
- CraftBukkit/Spigot - via the Dynmap-<version>-spigot.jar plugin (supports MC v1.8.9 through v1.14.2)
- Forge v1.8.9 - via Dynmap-<version>-forge-1.8.9.jar mod
- Forge v1.9.4 - via Dynmap-<version>-forge-1.9.4.jar mod
- Forge v1.10.2 - via Dynmap-<version>-forge-1.10.2.jar mod
- Forge v1.11.2 - via Dynmap-<version>-forge-1.11.2.jar mod
- Forge v1.12.2 - via Dynmap-<version>-forge-1.12.2.jar mod
