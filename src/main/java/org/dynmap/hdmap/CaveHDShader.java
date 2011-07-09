package org.dynmap.hdmap;

import static org.dynmap.JSONUtils.s;
import org.bukkit.block.Biome;
import org.dynmap.Color;
import org.dynmap.ColorScheme;
import org.dynmap.ConfigurationNode;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.MapIterator;
import org.json.simple.JSONObject;

public class CaveHDShader implements HDShader {
    private String name;

    
    public CaveHDShader(ConfigurationNode configuration) {
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
            return CaveHDShader.this;
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
            switch (blocktype) {
                case 0:
                case 20:
                case 18:
                case 17:
                case 78:
                case 79:
                    break;
                default:
                    air = false;
                    return false;
            }
            if (!air) {
                int cr, cg, cb;
                int mult = 256;

                if (mapiter.getY() < 64) {
                    cr = 0;
                    cg = 64 + mapiter.getY() * 3;
                    cb = 255 - mapiter.getY() * 4;
                } else {
                    cr = (mapiter.getY() - 64) * 4;
                    cg = 255;
                    cb = 0;
                }
                /* Figure out which color to use */
                switch(ps.getLastBlockStep()) {
                    case X_PLUS:
                    case X_MINUS:
                        mult = 224;
                        break;
                    case Z_PLUS:
                    case Z_MINUS:
                        mult = 256;
                        break;
                    default:
                        mult = 160;
                        break;
                }
                cr = cr * mult / 256;
                cg = cg * mult / 256;
                cb = cb * mult / 256;

                color.setRGBA(cr, cg, cb, 255);
                return true;
            }
            return false;
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
