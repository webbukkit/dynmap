package org.dynmap.fabric_1_16_2;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.dynmap.DynmapCore;
import org.dynmap.Log;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DynmapMod implements ModInitializer {
    private static final String MODID = "dynmap";
    private static final ModContainer MOD_CONTAINER = FabricLoader.getInstance().getModContainer(MODID)
            .orElseThrow(() -> new RuntimeException("Failed to get mod container: " + MODID));
    // The instance of your mod that Fabric uses.
    public static DynmapMod instance;

    public static DynmapPlugin plugin;
    public static File jarfile;
    public static String ver;
    public static boolean useforcedchunks;

    @Override
    public void onInitialize() {
        instance = this;

        Path path = MOD_CONTAINER.getRootPath();
        try {
            jarfile = new File(DynmapCore.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            Log.severe("Unable to get DynmapCore jar path", e);
        }

        if (path.getFileSystem().provider().getScheme().equals("jar")) {
            path = Paths.get(path.getFileSystem().toString());
            jarfile = path.toFile();
        }

        ver = MOD_CONTAINER.getMetadata().getVersion().getFriendlyString();

        Log.setLogger(new FabricLogger());
        org.dynmap.modsupport.ModSupportImpl.init();

        // Initialize the plugin, we will enable it fully when the server starts.
        plugin = new DynmapPlugin();
    }
}
