package org.dynmap.hdmap;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.Log;
import org.dynmap.MapManager;
import org.dynmap.MapType;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.MapIterator;

public class HDMapManager {
    public HashMap<String, HDShader> shaders = new HashMap<String, HDShader>();
    public HashMap<String, HDPerspective> perspectives = new HashMap<String, HDPerspective>();
    public HashMap<String, HDLighting> lightings = new HashMap<String, HDLighting>();
    public HashSet<HDMap> maps = new HashSet<HDMap>();
    public HashMap<String, ArrayList<HDMap>> maps_by_world_perspective = new HashMap<String, ArrayList<HDMap>>();
 
    public static boolean waterlightingfix;
    public static boolean biomeshadingfix;

    public void loadHDShaders(DynmapCore core) {
        Log.verboseinfo("Loading shaders...");
        /* Update mappings, if needed */
        TexturePack.handleBlockAlias();

        File f = new File(core.getDataFolder(), "shaders.txt");
        if(!core.updateUsingDefaultResource("/shaders.txt", f, "shaders")) {
            return;
        }
        ConfigurationNode shadercfg = new ConfigurationNode(f);
        shadercfg.load();
        
        for(HDShader shader : shadercfg.<HDShader>createInstances("shaders", new Class<?>[] { DynmapCore.class }, new Object[] { core })) {
            if(shader.getName() == null) continue;
            shaders.put(shader.getName(), shader);
        }
        /* Load custom shaders, if file is defined - or create empty one if not */
        f = new File(core.getDataFolder(), "custom-shaders.txt");
        core.createDefaultFileFromResource("/custom-shaders.txt", f);
        if(f.exists()) {
            ConfigurationNode customshadercfg = new ConfigurationNode(f);
            customshadercfg.load();
            for(HDShader shader : customshadercfg.<HDShader>createInstances("shaders", new Class<?>[] { DynmapCore.class }, new Object[] { core })) {
                if(shader.getName() == null) continue;
                shaders.put(shader.getName(), shader);
            }
        }
        Log.info("Loaded " + shaders.size() + " shaders.");
    }

    public void loadHDPerspectives(DynmapCore core) {
        Log.verboseinfo("Loading perspectives...");
        // Update mappings, if needed
        HDBlockModels.handleBlockAlias();
        
        File f = new File(core.getDataFolder(), "perspectives.txt");
        if(!core.updateUsingDefaultResource("/perspectives.txt", f, "perspectives")) {
            return;
        }
        ConfigurationNode perspectivecfg = new ConfigurationNode(f);
        perspectivecfg.load();
        for(HDPerspective perspective : perspectivecfg.<HDPerspective>createInstances("perspectives", new Class<?>[] { DynmapCore.class }, new Object[] { core })) {
            if(perspective.getName() == null) continue;
            perspectives.put(perspective.getName(), perspective);
        }
        /* Load custom perspectives, if file is defined - or create empty one if not */
        f = new File(core.getDataFolder(), "custom-perspectives.txt");
        core.createDefaultFileFromResource("/custom-perspectives.txt", f);
        if(f.exists()) {
            perspectivecfg = new ConfigurationNode(f);
            perspectivecfg.load();
            for(HDPerspective perspective : perspectivecfg.<HDPerspective>createInstances("perspectives", new Class<?>[] { DynmapCore.class }, new Object[] { core })) {
                if(perspective.getName() == null) continue;
                perspectives.put(perspective.getName(), perspective);
            }
        }
        Log.info("Loaded " + perspectives.size() + " perspectives.");
    }
    
    public void loadHDLightings(DynmapCore core) {
        Log.verboseinfo("Loading lightings...");
        File f = new File(core.getDataFolder(), "lightings.txt");
        if(!core.updateUsingDefaultResource("/lightings.txt", f, "lightings")) {
            return;
        }
        ConfigurationNode lightingcfg = new ConfigurationNode(f);
        lightingcfg.load();

        for(HDLighting lighting : lightingcfg.<HDLighting>createInstances("lightings", new Class<?>[] { DynmapCore.class }, new Object[] { core })) {
            if(lighting.getName() == null) continue;
            lightings.put(lighting.getName(), lighting);
        }
        /* Load custom lightings, if file is defined - or create empty one if not */
        f = new File(core.getDataFolder(), "custom-lightings.txt");
        core.createDefaultFileFromResource("/custom-lightings.txt", f);
        if(f.exists()) {
            lightingcfg = new ConfigurationNode(f);
            lightingcfg.load();
            for(HDLighting lighting : lightingcfg.<HDLighting>createInstances("lightings", new Class<?>[] { DynmapCore.class }, new Object[] { core })) {
                if(lighting.getName() == null) continue;
                lightings.put(lighting.getName(), lighting);
            }
        }
        Log.info("Loaded " + lightings.size() + " lightings.");
    }

    /**
     * Initialize shader states for all shaders for given tile
     * 
     * @param tile - tile to init
     * @param cache - chunk cache
     * @param mapiter - map iterator
     * @param mapname - map name
     * @param scale - map scale
     * @return array of shader states for all associated shaders
     */
    public HDShaderState[] getShaderStateForTile(HDMapTile tile, MapChunkCache cache, MapIterator mapiter, String mapname, int scale) {
        DynmapWorld w = MapManager.mapman.worldsLookup.get(tile.getDynmapWorld().getName());
        if(w == null) {
            return new HDShaderState[0];
        }
        ArrayList<HDShaderState> shaders = new ArrayList<HDShaderState>();
        for(MapType map : w.maps) {
            if(map instanceof HDMap) {
                HDMap hdmap = (HDMap)map;
                if((hdmap.getPerspective() == tile.perspective) && (hdmap.getBoostZoom() == tile.boostzoom)) {
                    /* If limited to one map, and this isn't it, skip */
                    if((mapname != null) && (!hdmap.getName().equals(mapname)))
                        continue;
                    shaders.add(hdmap.getShader().getStateInstance(hdmap, cache, mapiter, scale));
                }
            }
        }
        return shaders.toArray(new HDShaderState[shaders.size()]);
    }
    
    private static final int BIOMEDATAFLAG = 0;
    private static final int HIGHESTZFLAG = 1;
    private static final int RAWBIOMEFLAG = 2;
    private static final int BLOCKTYPEFLAG = 3;
    
    public boolean isBiomeDataNeeded(HDMapTile t) { 
        return getCachedFlags(t)[BIOMEDATAFLAG];
    }
    
    public boolean isHightestBlockYDataNeeded(HDMapTile t) {
        return getCachedFlags(t)[HIGHESTZFLAG];
    }
    
    public boolean isRawBiomeDataNeeded(HDMapTile t) { 
        return getCachedFlags(t)[RAWBIOMEFLAG];
    }
    
    public boolean isBlockTypeDataNeeded(HDMapTile t) {
        return getCachedFlags(t)[BLOCKTYPEFLAG];
    }
    
    private HashMap<String, boolean[]> cached_data_flags_by_world_perspective = new HashMap<String, boolean[]>();
    
    private boolean[] getCachedFlags(HDMapTile t) {
        String w = t.getDynmapWorld().getName();
        String k = w + "/" + t.perspective.getName();
        boolean[] flags = cached_data_flags_by_world_perspective.get(k);
        if(flags != null)
            return flags;
        flags = new boolean[4];
        cached_data_flags_by_world_perspective.put(k, flags);
        DynmapWorld dw = MapManager.mapman.worldsLookup.get(w);
        if(dw == null) return flags;

        for(MapType map : dw.maps) {
            if(map instanceof HDMap) {
                HDMap hdmap = (HDMap)map;
                if(hdmap.getPerspective() == t.perspective) {
                    HDShader sh = hdmap.getShader();
                    HDLighting lt = hdmap.getLighting();
                    flags[BIOMEDATAFLAG] |= sh.isBiomeDataNeeded() | lt.isBiomeDataNeeded();
                    flags[HIGHESTZFLAG] |= sh.isHightestBlockYDataNeeded() | lt.isHightestBlockYDataNeeded();
                    flags[RAWBIOMEFLAG] |= sh.isRawBiomeDataNeeded() | lt.isRawBiomeDataNeeded();
                    flags[BLOCKTYPEFLAG] |= sh.isBlockTypeDataNeeded() | lt.isBlockTypeDataNeeded();
                }
            }
        }
        return flags;
    }
}
