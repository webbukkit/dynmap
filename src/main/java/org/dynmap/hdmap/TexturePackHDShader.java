package org.dynmap.hdmap;

import static org.dynmap.JSONUtils.s;
import org.bukkit.block.Biome;
import org.dynmap.Color;
import org.dynmap.ColorScheme;
import org.dynmap.ConfigurationNode;
import org.dynmap.Log;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.MapIterator;
import org.json.simple.JSONObject;

public class TexturePackHDShader implements HDShader {
    private String tpname;
    private String name;
    private TexturePack tp;
    
    public TexturePackHDShader(ConfigurationNode configuration) {
        tpname = configuration.getString("texturepack", "minecraft");
        name = configuration.getString("name", tpname);
        tp = TexturePack.getTexturePack(tpname);
        if(tp == null) {
            Log.severe("Error: shader '" + name + "' cannot load texture pack '" + tpname + "'");
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
        private Color color;
        protected MapIterator mapiter;
        protected HDMap map;
        private boolean air;
        
        private OurShaderState(MapIterator mapiter, HDMap map) {
            this.mapiter = mapiter;
            this.map = map;
            this.color = new Color();
        }
        /**
         * Get our shader
         */
        public HDShader getShader() {
            return TexturePackHDShader.this;
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
            return map.getLighting();
        }
        
        /**
         * Reset renderer state for new ray
         */
        public void reset(HDPerspectiveState ps) {
            color.setTransparent();
            air = true;
        }
        
        /**
         * Process next ray step - called for each block on route
         * @return true if ray is done, false if ray needs to continue
         */
        public boolean processBlock(HDPerspectiveState ps) {
            int blocktype = ps.getBlockTypeID();
            color.setRGBA(0, 0, 0, 255);

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
            c.setColor(color);
        }
        /**
         * Clean up state object - called after last ray completed
         */
        public void cleanup() {
        }
    }

    /**
     *  Get renderer state object for use rendering a tile
     * @param map - map being rendered
     * @param cache - chunk cache containing data for tile to be rendered
     * @param mapiter - iterator used when traversing rays in tile
     * @return state object to use for all rays in tile
     */
    public HDShaderState getStateInstance(HDMap map, MapChunkCache cache, MapIterator mapiter) {
        return new OurShaderState(mapiter, map);
    }
    
    /* Add shader's contributions to JSON for map object */
    public void addClientConfiguration(JSONObject mapObject) {
        s(mapObject, "shader", name);
    }
}
