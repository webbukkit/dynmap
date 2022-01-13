# Dynmap-fabric

## Design

This mod makes use of Fabric's child mod feature. Specifically:

- `fabric/build.gradle` pulls in `fabric-<version>` mods using the `include`
  directive. The mods are included as Jar-in-Jar inside `META-INF/jars`.
- Each of the `fabric-<version>` mods depends on `fabric` project's
  `namedElements` configuration. This is the Fabric equivalent of Gradle's usual
  `apiElements` configuration but pointing at the named jar. See Loom 0.10
  [release page](https://github.com/FabricMC/fabric-loom/releases/tag/0.10)
  for more details - or ask modmuss50 directly on the Fabric discord.
- When the main `fabric` mod is loaded by Fabric Loader, all the child mods are
  scanned and only the one matching the running Minecraft version is loaded.
- When running in a development environment, the child mod's `fabric.mod.json`
  explicitly lists the parent `fabric` mod, ensuring it also gets loaded.

## Development

Use the `fabric-<version>:runServer` Gradle task to start/debug the mod.

The recommended IDE for developing this mod is IntelliJ IDEA with MinecraftDev
plugin. Using any other IDE is at your own peril - while you *can* get all the
debug features running (run/debug configurations, decompiled sources, etc.)
it requires extra manual steps that you have to perform on your own.

If you figure out the specific steps for running the mod under other IDEs,
please document it here.
