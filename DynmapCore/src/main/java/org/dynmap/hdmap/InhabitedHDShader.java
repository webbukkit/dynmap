package org.dynmap.hdmap;

import static org.dynmap.JSONUtils.s;

import java.io.IOException;
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

public class InhabitedHDShader implements HDShader {
    private final String name;
    private final long filllevel[]; /* Values for colors */
    private final Color fillcolor[];
    
    private Color readColor(String id, ConfigurationNode cfg) {
        String lclr = cfg.getString(id, null);
        if((lclr != null) && (lclr.startsWith("#"))) {
            try {
                int c = Integer.parseInt(lclr.substring(1), 16);
                return new Color((c>>16)&0xFF, (c>>8)&0xFF, c&0xFF);
            } catch (NumberFormatException nfx) {
                Log.severe("Invalid color value: " + lclr + " for '" + id + "'");
            }
        }
        return null;
    }
    public InhabitedHDShader(DynmapCore core, ConfigurationNode configuration) {
        name = (String) configuration.get("name");
        HashMap<Long, Color> map = new HashMap<Long, Color>();
        for (String key : configuration.keySet()) {
            if (key.startsWith("color")) {
                try {
                    long val = Long.parseLong(key.substring(5));
                    Color clr = readColor(key, configuration);
                    map.put(val, clr);
                } catch (NumberFormatException nfx) {
                }
            }
        }
        TreeSet<Long> keys = new TreeSet<Long>(map.keySet());
        filllevel = new long[keys.size()];
        fillcolor = new Color[keys.size()];
        int idx = 0;
        for (Long k : keys) {
            filllevel[idx] = k;
            fillcolor[idx] = map.get(k);
            idx++;
        }
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
            return InhabitedHDShader.this;
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
            long ts = ps.getMapIterator().getInhabitedTicks() / 1200;   // Get time, in minutes
            // Find top of range
            boolean match = false;
            for (int i = 0; i < filllevel.length; i++) {
                if (ts < filllevel[i]) {    // Found it
                    if (i > 0) {    // Middle?  Interpolate
                        int interp = (int) ((256 * (ts - filllevel[i-1])) / (filllevel[i] - filllevel[i-1]));
                        int red = (interp * fillcolor[i].getRed()) + ((256 - interp) * fillcolor[i-1].getRed());
                        int green = (interp * fillcolor[i].getGreen()) + ((256 - interp) * fillcolor[i-1].getGreen());
                        int blue = (interp * fillcolor[i].getBlue()) + ((256 - interp) * fillcolor[i-1].getBlue());
                        c.setRGBA(red / 256, green / 256, blue / 256, 255);
                    }
                    else {  // Else, use color
                        c.setColor(fillcolor[i]);
                    }
                    match = true;
                    break;
                }
            }
            if (!match) {
                c.setColor(fillcolor[fillcolor.length-1]);
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
