package org.dynmap.hdmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.bukkit.World;
import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapChunk;
import org.dynmap.DynmapWorld;
import org.dynmap.Log;
import org.dynmap.MapManager;
import org.dynmap.MapTile;
import org.dynmap.MapType;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.MapIterator;

public class HDMapManager {
    public HashMap<String, HDShader> shaders = new HashMap<String, HDShader>();
    public HashMap<String, HDPerspective> perspectives = new HashMap<String, HDPerspective>();
    public HashMap<String, HDLighting> lightings = new HashMap<String, HDLighting>();
    public HashSet<HDMap> maps = new HashSet<HDMap>();
    public HashMap<String, ArrayList<HDMap>> maps_by_world_perspective = new HashMap<String, ArrayList<HDMap>>();
    
    public void loadHDShaders(ConfigurationNode shadercfg) {
        Log.verboseinfo("Loading shaders...");
        for(HDShader shader : shadercfg.<HDShader>createInstances("shaders", new Class<?>[0], new Object[0])) {
            if(shader.getName() == null) continue;
            if(shaders.containsKey(shader.getName())) {
                Log.severe("Duplicate shader name '" + shader.getName() + "' - shader ignored");
            }
            else {
                shaders.put(shader.getName(), shader);
            }
        }
        Log.info("Loaded " + shaders.size() + " shaders.");
    }

    public void loadHDPerspectives(ConfigurationNode perspectivecfg) {
        Log.verboseinfo("Loading perspectives...");
        for(HDPerspective perspective : perspectivecfg.<HDPerspective>createInstances("perspectives", new Class<?>[0], new Object[0])) {
            if(perspective.getName() == null) continue;
            if(perspectives.containsKey(perspective.getName())) {
                Log.severe("Duplicate perspective name '" + perspective.getName() + "' - perspective ignored");
            }
            else {
                perspectives.put(perspective.getName(), perspective);
            }
        }
        Log.info("Loaded " + perspectives.size() + " perspectives.");
    }
    
    public void loadHDLightings(ConfigurationNode lightingcfg) {
        Log.verboseinfo("Loading lightings...");
        for(HDLighting lighting : lightingcfg.<HDLighting>createInstances("lightings", new Class<?>[0], new Object[0])) {
            if(lighting.getName() == null) continue;
            if(lightings.containsKey(lighting.getName())) {
                Log.severe("Duplicate lighting name '" + lighting.getName() + "' - lighting ignored");
            }
            else {
                lightings.put(lighting.getName(), lighting);
            }
        }
        Log.info("Loaded " + lightings.size() + " lightings.");
    }

    /**
     * Initialize shader states for all shaders for given tile
     */
    public HDShaderState[] getShaderStateForTile(HDMapTile tile, MapChunkCache cache, MapIterator mapiter) {
        DynmapWorld w = MapManager.mapman.worldsLookup.get(tile.getWorld().getName());
        if(w == null) return new HDShaderState[0];
        ArrayList<HDShaderState> shaders = new ArrayList<HDShaderState>();
        for(MapType map : w.maps) {
            if(map instanceof HDMap) {
                HDMap hdmap = (HDMap)map;
                if(hdmap.getPerspective() == tile.perspective) {
                    shaders.add(hdmap.getShader().getStateInstance(hdmap, cache, mapiter));
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
        String w = t.getWorld().getName();
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
