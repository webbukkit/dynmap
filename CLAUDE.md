# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Dynmap is a dynamic web mapping plugin/mod for Minecraft servers. It's a multi-platform project supporting Spigot/PaperMC, Forge, and Fabric across multiple Minecraft versions (1.12.2 - 1.21.x).

## Build Commands

This project now spans **three separate Gradle builds**, split by Gradle-version compatibility:

```bash
# Main build: Spigot, all bukkit-helper-*, all fabric-*, DynmapCore/API (Gradle 9.5.1)
./gradlew setup build

# Build outputs go to /target directory

# Build specific module (for faster iteration, but NOT for PR submissions)
./gradlew :DynmapCore:build

# Run unit tests (DynmapCore only — JUnit 4)
./gradlew :DynmapCore:test

# Modern Forge (1.14.4 - 1.21.11): separate build, Gradle 8.14 - ForgeGradle does not support Gradle 9
cd forge-build
./gradlew setup build

# Forge 1.12.2 (requires JDK 8 - set JAVA_HOME accordingly)
cd oldgradle
./gradlew setup build
```

**Why three builds:** ForgeGradle (used by `forge-1.14.4` through `forge-1.21.11`) hard-rejects Gradle 9
("Versions Gradle 9.0 and newer are not supported yet"), so those 10 modules live in `forge-build/`
on Gradle 8.14 (mirroring the existing `oldgradle`/`forge-1.12.2` split). The main build moved to
Gradle 9.5.1 specifically so `bukkit-helper-26-2` (Paper/Spigot 26.2 support) can use
`paperweight-userdev`, which requires Gradle 9+.

**JDK Requirements:**
- Default: JDK 21 — run the main build's Gradle daemon on JDK 21, not JDK 25. Confirmed by testing: on JDK 25, `spigot:compileJava` fails with `cannot access NonNull ... class file for org.checkerframework.checker.nullness.qual.NonNull not found` (an unrelated LuckPerms-dependency/`-source 8` cross-compilation quirk specific to JDK 25's javac) - JDK 21 compiles this cleanly.
- `bukkit-helper-26-2` (Paper/Spigot 26.2): requires **JDK 25** to compile against real (unobfuscated) Mojang-mapped classes. This is handled automatically by that module's own Gradle toolchain block (`java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }`) as long as a JDK 25 install is present for Gradle to auto-detect (e.g. under `Program Files\Java`) - no manual `JAVA_HOME` juggling needed once both JDK 21 and JDK 25 are installed.
- `forge-build`: Gradle 8.14 cannot run its daemon on JDK 25 either - use JDK 21 (or any JDK ≤ ~23) there too.
- `fabric-26.2`: unlike `bukkit-helper-26-2`, Fabric Loom needs the Gradle **daemon itself** on JDK 25 for this specific module (`Minecraft 26.2 requires Java 25 but Gradle is using 21`) - a per-module toolchain isn't enough. Run `:fabric-26.2:*` tasks with JDK 25 as the default `java`/`JAVA_HOME`; other tasks in the same invocation (spigot, bukkit-helper-*) still need JDK 21 as noted above, so build them in separate invocations if you need both.
- Forge 1.12.2 (oldgradle): JDK 8 strictly required
- Runtime targets: JDK 8 (1.16-), JDK 16 (1.17.x), JDK 17 (1.18-1.20.4), JDK 21 (1.20.5-1.21.11), JDK 25 (26.2+)

**Build notes:**
- `gradle.properties` sets `org.gradle.parallel=false` and `org.gradle.daemon=false` — do not change these
- `snakeyaml` is pinned at 1.23 intentionally — newer versions break on Windows-encoded config files
- Shadow plugin is `com.gradleup.shadow` (main build, Gradle 9) — the older `io.github.goooler.shadow` is kept only in `forge-build/` (Gradle 8), since it crashes under Gradle 9's embedded Groovy 4
- **Shadow include/exclude gotcha (found via real server testing, 2026-07-19):** `com.gradleup.shadow`'s `dependency(...)` notation silently drops the old `'group::'` wildcard (empty artifact name AND version) instead of matching everything in that group - it just matches nothing, with no build error. This broke `DynmapCore`'s bundling of Jetty/javax.servlet/jakarta.xml.bind entirely (the whole embedded web server was missing from the jar) and `spigot`'s bundling of bstats, both silently, both only caught by actually loading the plugin on a real server (`NoClassDefFoundError`). Fixed by spelling out each artifact explicitly with a trailing colon (`'group:artifact:'`, empty version only - that form still works). **If you add a new `include(dependency('group::'))` anywhere in the main build, don't - it will silently do nothing; enumerate the actual artifacts instead**, and verify with `jar tf` on the built output that the relocated package (e.g. `org/dynmap/jetty/...`) actually has classes in it.
- As of Minecraft 26.1+, Mojang ships the server unobfuscated — there is no more "Spigot mappings" reobf layer, so `bukkit-helper-26-2` is written directly against real `net.minecraft.*` class/method names (see that module's source header comment) instead of the obfuscated calls seen in older `bukkit-helper-*` modules
- Fabric follows the same shift: Yarn mappings were discontinued as of 26.1 (nothing left to remap), so `fabric-26.2` uses plugin id `net.fabricmc.fabric-loom` (not `fabric-loom`), has **no `mappings` dependency at all**, uses plain `implementation`/`compileOnly` instead of `modImplementation`/`modCompileOnly`, and has no `remapJar` task - the plain `jar` task is the final artifact. Its own `com.gradleup.shadow`-provided `shadowJar` task is disabled (see build.gradle comment) since it isn't used and would otherwise blow past the 65535-zip-entry limit by bundling the full Fabric/Minecraft runtime classpath.

## Architecture

### Module Structure

**Core Shared Modules:**
- `DynmapCoreAPI/` - Stable public API for external plugins/mods (markers, mod support, rendering). Published to `repo.mikeprimm.com`. The `org.dynmap.renderer` package here defines `DynmapBlockState` — the central block state abstraction used everywhere.
- `DynmapCore/` - Internal shared implementation (NOT stable - subject to breaking changes)
- `dynmap-api/` - Bukkit-specific public API

**Platform Implementations:**
- `spigot/` - Bukkit/PaperMC implementation (`DynmapPlugin.java`); dispatches to the matching `bukkit-helper-*` at runtime via reflection (`Helper.java`) based on `Server#getVersion()` (legacy `1.x` servers) or `Server#getMinecraftVersion()` (26.x+ servers, called reflectively since it postdates spigot's own ancient compile-time Bukkit API)
- `bukkit-helper-*` - Version-specific NMS code (one per MC version: 1.13-1.21, plus `26-2` for Paper/Spigot 26.2)
- `fabric-*` - Fabric mod implementations (1.14.4-1.21.11, plus `26.2`)
- `forge-*` - Forge mod implementations (1.14.4-1.21.x), built from `forge-build/` (separate Gradle 8.14 build - see Build Commands); `forge-1.12.2` lives in `oldgradle/`/`oldbuilds/` instead

### Dependency Flow
```
External Plugins/Mods
    ↓
DynmapCoreAPI (stable, published to repo.mikeprimm.com)
    ↓
DynmapCore (internal, unstable)
    ↓
Platform-specific modules (Spigot, Fabric, Forge)
```

### Key Components in DynmapCore

- `DynmapCore.java` — Main coordination hub (~3,100 lines); bootstrapped by each platform
- `MapManager.java` — Tile rendering orchestration; owns the render thread pool and `FullWorldRenderState` queue
- `hdmap/` — HD map rendering pipeline:
  - `IsoHDPerspective` — Isometric raytrace engine (the hot rendering path)
  - `HDBlockModels` / `HDScaledBlockModels` — Block geometry (patch/volumetric/scaled models)
  - `TexturePack` / `TexturePackLoader` — Texture resolution from resource packs
  - `hdmap/renderer/` — Custom block renderers (stairs, fences, doors, etc.) implementing `CustomRenderer`
  - Shaders (`DefaultHDShader`, `CaveHDShader`, `TopoHDShader`, etc.) — post-process pixel color
  - Lighting (`DefaultHDLighting`, `ShadowHDLighting`, etc.) — light level calculation
- `storage/` — Storage backends (FileTree, MySQL, MariaDB, PostgreSQL, SQLite, MSSQL, AWS S3)
- `web/` — Embedded Jetty 9 server with custom HTTP routing (no standard servlet container)
- `markers/impl/` — Full marker system implementation; public interface is in `DynmapCoreAPI`
- `utils/MapChunkCache` + `utils/MapIterator` — Abstract interfaces that each platform implements to feed world data into the renderer

### Platform Integration Pattern

Each platform module (Spigot `bukkit-helper-*`, Fabric, Forge) must implement:
- `MapChunkCache` — Loads and caches chunk data for a tile's required chunks
- `MapIterator` — Block-by-block iteration over the loaded chunk cache
- A platform entry point (e.g., `DynmapPlugin` for Spigot) that bootstraps `DynmapCore`

The `bukkit-helper-*` modules contain version-specific NMS code; `spigot/` delegates to the appropriate helper at runtime via reflection.

## Testing

Unit tests exist in `DynmapCore/src/test/` (JUnit 4) covering `Matrix3D`, `Vector3D`, `IpAddressMatcher`, `DynIntHashMap`, and `BufferInputStream`. Run with `./gradlew :DynmapCore:test`.

Full verification requires:
1. Building all platforms: `./gradlew setup build` AND `cd oldgradle && ./gradlew setup build`
2. Manual testing on target Minecraft server platforms

## Critical Contribution Rules

**PRs must build and test on ALL platforms including oldgradle. Changes to DynmapCore/DynmapCoreAPI require testing on all platforms.**

- **Java 8 compatibility required** — Code must compile and run on Java 8
- **Java only** — No Kotlin, Scala, or other JVM languages
- **No dependency updates** — Library versions are tied to platform compatibility
- **No platform-specific code** — Must work on Windows, Linux (x86/ARM), macOS, Docker
- **Small PRs only** — One feature per PR, no style/formatting changes
- **No mod-specific code** — Use Dynmap APIs instead; external mods should depend on DynmapCoreAPI
- **Apache License v2** — All code must be compatible
- **DynmapCoreAPI is the only stable API** — Do not add external dependencies on DynmapCore internals
