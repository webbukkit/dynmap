package org.dynmap.hdmap;

import static org.dynmap.JSONUtils.s;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeSet;

import org.dynmap.Color;
import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapCore;
import org.dynmap.Log;
import org.dynmap.MapManager;
import org.dynmap.common.DynmapCommandSender;
import org.dynmap.exporter.OBJExport;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.utils.BlockStep;
import org.dynmap.utils.DynLongHashMap;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.MapIterator;
import org.json.simple.JSONObject;

// Shader for color coding by chunk data version
public class ChunkVersionHDShader implements HDShader {
    private final String name;
    
    private static class DataVersionMap {
    	int dataVersion;
    	String version;
    	Color defcolor;
    	DataVersionMap(int dv, String v, int c) {
    		dataVersion = dv;
    		version = v;
    		defcolor = new Color((c>>16)&0xFF, (c>>8)&0xFF, c&0xFF);

    	}
    };
    final static DataVersionMap[] versionmap = {
    	new DataVersionMap(0, "unknown", 0x202020),
    	new DataVersionMap(1519, "1.13.0", 0xF9E79F),
    	new DataVersionMap(1628, "1.13.1", 0xF4D03F),
    	new DataVersionMap(1631, "1.13.2", 0xD4AC0D),
    	new DataVersionMap(1952, "1.14.0", 0xABEBC6),
    	new DataVersionMap(1957, "1.14.1", 0x58D68D),
    	new DataVersionMap(1963, "1.14.2", 0x28B463),
    	new DataVersionMap(1968, "1.14.3", 0x239B56),
    	new DataVersionMap(1976, "1.14.4", 0x1D8348),
    	new DataVersionMap(2225, "1.15.0", 0xAED6F1),
    	new DataVersionMap(2227, "1.15.1", 0x5DADE2),
    	new DataVersionMap(2230, "1.15.2", 0x2E86C1),
    	new DataVersionMap(2566, "1.16.0", 0xD7BDE2),
    	new DataVersionMap(2567, "1.16.1", 0xC39BD3),
    	new DataVersionMap(2578, "1.16.2", 0xAF7AC5),
    	new DataVersionMap(2580, "1.16.3", 0x9B59B6),
    	new DataVersionMap(2584, "1.16.4", 0x884EA0),
    	new DataVersionMap(2586, "1.16.5", 0x76448A),
    	new DataVersionMap(2724, "1.17.0", 0xF5CBA7),
    	new DataVersionMap(2730, "1.17.1", 0xEB984E),
    	new DataVersionMap(2860, "1.18.0", 0xA3E4D7),
    	new DataVersionMap(2865, "1.18.1", 0x48C9B0),
    };
    final static Color unknown_color = new Color(255, 255, 255);
    
    private ArrayList<Integer> unknown_vers = new ArrayList<Integer>();
    	
    public ChunkVersionHDShader(DynmapCore core, ConfigurationNode configuration) {
        name = (String) configuration.get("name");
    }
    
    @Override
    public boolean isBiomeDataNeeded() { 
        return false; 
    }
    
    @Override
    public boolean isRawBiomeDataNeeded() { 
        return false; 
    }
    
    @Override
    public boolean isHightestBlockYDataNeeded() {
        return false;
    }

    @Override
    public boolean isBlockTypeDataNeeded() {
        return true;
    }

    @Override
    public boolean isSkyLightLevelNeeded() {
        return false;
    }

    @Override
    public boolean isEmittedLightLevelNeeded() {
        return false;
    }

    @Override
    public String getName() {
        return name;
    }
    
    private class OurShaderState implements HDShaderState {
        private Color color[];
        private Color c;
        protected HDMap map;
        private HDLighting lighting;
        final int[] lightingTable;
        
        private OurShaderState(MapIterator mapiter, HDMap map, MapChunkCache cache, int scale) {
            this.map = map;
            this.lighting = map.getLighting();
            if(lighting.isNightAndDayEnabled()) {
                color = new Color[] { new Color(), new Color() };
            }
            else {
                color = new Color[] { new Color() };
            }
            c = new Color();
            if (MapManager.mapman.useBrightnessTable()) {
                lightingTable = cache.getWorld().getBrightnessTable();
            }
            else {
                lightingTable = null;
            }
        }
        /**
         * Get our shader
         */
        public HDShader getShader() {
            return ChunkVersionHDShader.this;
        }

        /**
         * Get our map
         */
        public HDMap getMap() {
            return map;
        }
        
        /**
         * Get our lighting
         */
        public HDLighting getLighting() {
            return lighting;
        }
        
        /**
         * Reset renderer state for new ray
         */
        public void reset(HDPerspectiveState ps) {
            for(int i = 0; i < color.length; i++)
                color[i].setTransparent();
        }
        /**
         * Process next ray step - called for each block on route
         * @return true if ray is done, false if ray needs to continue
         */
        public boolean processBlock(HDPerspectiveState ps) {
            if (ps.getBlockState().isAir()) {
                return false;
            }
            int ver = ps.getMapIterator().getDataVersion();	// Get data version
            boolean match = false;
            // Find last record <= version
            for (int i = 0; i < versionmap.length; i++) {
            	if (ver <= versionmap[i].dataVersion) { 
                    c.setColor(versionmap[i].defcolor);
                    match = true;
                    break;
            	}
            }
            if (!match) {
        		c.setColor(unknown_color);
            	if (!unknown_vers.contains(ver)) {            		
            		Log.warning("Unknown chunk dataVersion: " + ver);
            		unknown_vers.add(ver);
            	}
            }
            /* Handle light level, if needed */
            lighting.applyLighting(ps, this, c, color);

            return true;
        }        
        /**
         * Ray ended - used to report that ray has exited map (called if renderer has not reported complete)
         */
        public void rayFinished(HDPerspectiveState ps) {
        }
        /**
         * Get result color - get resulting color for ray
         * @param c - object to store color value in
         * @param index - index of color to request (renderer specific - 0=default, 1=day for night/day renderer
         */
        public void getRayColor(Color c, int index) {
            c.setColor(color[index]);
        }
        /**
         * Clean up state object - called after last ray completed
         */
        public void cleanup() {
        }
        @Override
        public DynLongHashMap getCTMTextureCache() {
            return null;
        }
        @Override
        public int[] getLightingTable() {
            return lightingTable;
        }
        @Override
        public void setLastBlockState(DynmapBlockState new_lastbs) {
        }
    }

    /**
     *  Get renderer state object for use rendering a tile
     * @param map - map being rendered
     * @param cache - chunk cache containing data for tile to be rendered
     * @param mapiter - iterator used when traversing rays in tile
     * @param scale - scale of perspecitve
     * @return state object to use for all rays in tile
     */
    @Override
    public HDShaderState getStateInstance(HDMap map, MapChunkCache cache, MapIterator mapiter, int scale) {
        return new OurShaderState(mapiter, map, cache, scale);
    }
    
    /* Add shader's contributions to JSON for map object */
    public void addClientConfiguration(JSONObject mapObject) {
        s(mapObject, "shader", name);
    }
    @Override
    public void exportAsMaterialLibrary(DynmapCommandSender sender, OBJExport out) throws IOException {
        throw new IOException("Export unsupported");
    }
    private static final String[] nulllist = new String[0];
    @Override
    public String[] getCurrentBlockMaterials(DynmapBlockState blk, MapIterator mapiter, int[] txtidx, BlockStep[] steps) {
        return nulllist;
    }
}
