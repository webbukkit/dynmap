package org.dynmap.hdmap;

import static org.dynmap.JSONUtils.s;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

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
public class ChunkStatusHDShader implements HDShader {
    private final String name;
    
    private static class ChunkStatusMap {
    	Color defcolor;
    	ChunkStatusMap(String s, int c) {
    		defcolor = new Color((c>>16)&0xFF, (c>>8)&0xFF, c&0xFF);
    		statusmap.put(s, this);
    	}
    };
    private static HashMap<String, ChunkStatusMap> statusmap = new HashMap<String, ChunkStatusMap>();
    
    private static ChunkStatusMap[] statusvals = {
		new ChunkStatusMap("empty", 0xFF0000),
		new ChunkStatusMap("structure_starts", 0xFF1493),
		new ChunkStatusMap("structure_references", 0xFF7F50),
		new ChunkStatusMap("biomes", 0xFFA500),
		new ChunkStatusMap("noise", 0xFFD700),
		new ChunkStatusMap("surface", 0xFFFF00),
		new ChunkStatusMap("carvers", 0xFFEFD5),
		new ChunkStatusMap("liquid_carvers", 0xF0E68C),
		new ChunkStatusMap("features", 0xBDB76B),
		new ChunkStatusMap("light", 0xDDA0DD),
		new ChunkStatusMap("spawn", 0xFF00FF),
		new ChunkStatusMap("heightmaps", 0x9370DB),
		new ChunkStatusMap("full", 0x32CD32),
    };

    final static Color unknown_color = new Color(255, 255, 255);
    
    private ArrayList<String> unknown_state = new ArrayList<String>();
    	
    public ChunkStatusHDShader(DynmapCore core, ConfigurationNode configuration) {
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
            return ChunkStatusHDShader.this;
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
            String cs = ps.getMapIterator().getChunkStatus();	// Get data version
            
            ChunkStatusMap csm = statusmap.get(cs);
            if (csm != null) {
                c.setColor(csm.defcolor);
            }
            else {
        		c.setColor(unknown_color);
            	if (!unknown_state.contains(cs)) {            		
            		Log.warning("Unknown chunk status: " + cs);
            		unknown_state.add(cs);
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
