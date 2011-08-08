package org.dynmap.hdmap;

import static org.dynmap.JSONUtils.s;

import org.bukkit.World.Environment;
import org.dynmap.Color;
import org.dynmap.ConfigurationNode;
import org.dynmap.Log;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.MapIterator;
import org.json.simple.JSONObject;

public class TexturePackHDShader implements HDShader {
    private String tpname;
    private String name;
    private TexturePack tp;
    private boolean biome_shaded;
    
    public TexturePackHDShader(ConfigurationNode configuration) {
        tpname = configuration.getString("texturepack", "minecraft");
        name = configuration.getString("name", tpname);
        tp = TexturePack.getTexturePack(tpname);
        biome_shaded = configuration.getBoolean("biomeshaded", true);
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
        return biome_shaded; 
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
        private Color tmpcolor[];
        private Color c;
        protected MapIterator mapiter;
        protected HDMap map;
        private TexturePack scaledtp;
        private HDLighting lighting;
        private int lastblkid;
        private boolean do_biome_shading;
        
        private OurShaderState(MapIterator mapiter, HDMap map, MapChunkCache cache) {
            this.mapiter = mapiter;
            this.map = map;
            this.lighting = map.getLighting();
            if(lighting.isNightAndDayEnabled()) {
                color = new Color[] { new Color(), new Color() };
                tmpcolor = new Color[] { new Color(), new Color() };
            }
            else {
                color = new Color[] { new Color() };
                tmpcolor = new Color[] { new Color() };
            }
            c = new Color();
            scaledtp = tp.resampleTexturePack(map.getPerspective().getModelScale());
            /* Biome raw data only works on normal worlds at this point */
            do_biome_shading = biome_shaded && (cache.getWorld().getEnvironment() == Environment.NORMAL);
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
            return lighting;
        }
        
        /**
         * Reset renderer state for new ray
         */
        public void reset(HDPerspectiveState ps) {
            for(Color c: color) 
                c.setTransparent();
            lastblkid = 0;
        }
        
        /**
         * Process next ray step - called for each block on route
         * @return true if ray is done, false if ray needs to continue
         */
        public boolean processBlock(HDPerspectiveState ps) {
            int blocktype = ps.getBlockTypeID();
            int lastblocktype = lastblkid;
            lastblkid = blocktype;
            
            if(blocktype == 0) {
                return false;
            }
            
            /* Get color from textures */
            scaledtp.readColor(ps, mapiter, c, blocktype, lastblocktype, do_biome_shading);

            if (c.getAlpha() > 0) {
                int subalpha = ps.getSubmodelAlpha();
                /* Scale brightness depending upon face */
                switch(ps.getLastBlockStep()) {
                    case X_MINUS:
                    case X_PLUS:
                        /* 60% brightness */
                        c.blendColor(0xFFA0A0A0);
                        break;
                    case Y_MINUS:
                    case Y_PLUS:
                        /* 85% brightness for even, 90% for even*/
                        if((mapiter.getY() & 0x01) == 0) 
                            c.blendColor(0xFFD9D9D9);
                        else
                            c.blendColor(0xFFE6E6E6);
                        break;
                }
                /* Handle light level, if needed */
                lighting.applyLighting(ps, this, c, tmpcolor);
                /* If we got alpha from subblock model, use it instead if it is lower */
                /* (disable for now: weighting is wrong, as crosssection is 2D, not 3D based) */
//                if(subalpha >= 0) {
//                    for(Color clr : tmpcolor) {
//                    	int a = clr.getAlpha();
//                    	if(subalpha < a)
//                    		clr.setAlpha(subalpha);
//                    }
//                }
                /* If no previous color contribution, use new color */
                if(color[0].isTransparent()) {
                    for(int i = 0; i < color.length; i++)
                        color[i].setColor(tmpcolor[i]);
                    return (color[0].getAlpha() == 255);
                }
                /* Else, blend and generate new alpha */
                else {
                    int alpha = color[0].getAlpha();
                    int alpha2 = tmpcolor[0].getAlpha() * (255-alpha) / 255;
                    int talpha = alpha + alpha2;
                    if(talpha > 0)
                    	for(int i = 0; i < color.length; i++)
                    		color[i].setRGBA((tmpcolor[i].getRed()*alpha2 + color[i].getRed()*alpha) / talpha,
                                  (tmpcolor[i].getGreen()*alpha2 + color[i].getGreen()*alpha) / talpha,
                                  (tmpcolor[i].getBlue()*alpha2 + color[i].getBlue()*alpha) / talpha, talpha);
                    else
                    	for(int i = 0; i < color.length; i++)
                    		color[i].setTransparent();
                    	
                    return (talpha >= 254);   /* If only one short, no meaningful contribution left */
                }
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
            c.setColor(color[index]);
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
        return new OurShaderState(mapiter, map, cache);
    }
    
    /* Add shader's contributions to JSON for map object */
    public void addClientConfiguration(JSONObject mapObject) {
        s(mapObject, "shader", name);
    }
}
