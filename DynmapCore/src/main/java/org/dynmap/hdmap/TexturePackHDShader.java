package org.dynmap.hdmap;

import static org.dynmap.JSONUtils.s;

import java.io.IOException;
import java.util.BitSet;
import java.util.List;

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

public class TexturePackHDShader implements HDShader {
    private final String tpname;
    private final String name;
    private TexturePack tp;
    private boolean did_tp_load = false;
    private final boolean biome_shaded;
    private final boolean bettergrass;
    private final int gridscale;
    private final DynmapCore core;
    private final BitSet hiddenids;
    
    public TexturePackHDShader(DynmapCore core, ConfigurationNode configuration) {
        tpname = configuration.getString("texturepack", "minecraft");
        name = configuration.getString("name", tpname);
        this.core = core;
        biome_shaded = configuration.getBoolean("biomeshaded", true);
        bettergrass = configuration.getBoolean("better-grass", MapManager.mapman.getBetterGrass());
        gridscale = configuration.getInteger("grid-scale", 0);
        List<Object> hidden = configuration.getList("hiddenids");
        if(hidden != null) {
            hiddenids = new BitSet();
            for(Object o : hidden) {
                if(o instanceof Integer) {
                    int v = ((Integer)o);
                    hiddenids.set(v);
                }
            }
        }
        else {
            hiddenids = null;
        }
    }
    
    private final TexturePack getTexturePack() {
        if (!did_tp_load) {
            tp = TexturePack.getTexturePack(this.core, this.tpname);
            if(tp == null) {
                Log.severe("Error: shader '" + name + "' cannot load texture pack '" + tpname + "'");
            }
            did_tp_load = true;
        }
        return tp;
    }
    
    @Override
    public boolean isBiomeDataNeeded() { 
        return biome_shaded; 
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
    
    class ShaderState implements HDShaderState {
        final private Color color[];
        final private Color tmpcolor[];
        final private Color c;
        final protected MapIterator mapiter;
        final protected HDMap map;
        final private TexturePack scaledtp;
        final private HDLighting lighting;
        protected DynmapBlockState lastblk;
        protected DynmapBlockState lastblkhit;
        final boolean do_biome_shading;
        final boolean do_better_grass;
        DynLongHashMap ctm_cache;
        final int[] lightingTable;
        
        protected ShaderState(MapIterator mapiter, HDMap map, MapChunkCache cache, int scale) {
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
            TexturePack tp = getTexturePack();
            if (tp != null)
                scaledtp = tp.resampleTexturePack(scale);
            else
                scaledtp = null;
            /* Biome raw data only works on normal worlds at this point */
            do_biome_shading = biome_shaded; // && (cache.getWorld().getEnvironment() == Environment.NORMAL);
            do_better_grass = bettergrass;
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
        @Override
        public HDShader getShader() {
            return TexturePackHDShader.this;
        }

        /**
         * Get our map
         */
        @Override
        public HDMap getMap() {
            return map;
        }
        
        /**
         * Get our lighting
         */
        @Override
        public HDLighting getLighting() {
            return lighting;
        }
        
        /**
         * Reset renderer state for new ray
         */
        @Override
        public void reset(HDPerspectiveState ps) {
            for(int i = 0; i < color.length; i++)
                color[i].setTransparent();
            setLastBlockState(DynmapBlockState.AIR);
            lastblkhit = DynmapBlockState.AIR;
        }
        
        /**
         * Process next ray step - called for each block on route
         * @return true if ray is done, false if ray needs to continue
         */
        public boolean processBlock(HDPerspectiveState ps) {
            DynmapBlockState blocktype = ps.getBlockState();
            if ((hiddenids != null) && hiddenids.get(blocktype.globalStateIndex)) {
                blocktype = DynmapBlockState.AIR;
            }
            
            if (blocktype.isAir()) {
            	lastblkhit = blocktype;
                return false;
            }
            
            DynmapBlockState lastblocktype = lastblk;
            
            /* Get color from textures */
            if (scaledtp != null) {
                scaledtp.readColor(ps, mapiter, c, blocktype, lastblocktype, ShaderState.this);
            }
        	lastblkhit = blocktype;

            if (c.getAlpha() > 0) {
                /* Scale brightness depending upon face */
            	if (ps.getShade()) {
	            	if (this.lightingTable != null) {	
	            		switch (ps.getLastBlockStep()) {
	                        case X_MINUS:
	                        case X_PLUS:
	                            /* 60% brightness */
	                            c.blendColor(0xFF999999);
	                            break;
	                        case Y_MINUS:
	                            // 95% for even
	                            if((mapiter.getY() & 0x01) == 0) {
	                                c.blendColor(0xFFF3F3F3);
	                            }
	                            break;
	                        case Y_PLUS:
	                            /* 50%*/
	                            c.blendColor(0xFF808080);
	                            break;
	                        case Z_MINUS:
	                        case Z_PLUS:
	                        default:
	                            /* 80%*/
	                            c.blendColor(0xFFCDCDCD);
	                            break;
	                	}
	                }
	                else {
	                    switch (ps.getLastBlockStep()) {
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
	                        default:
	                            break;
	                    }
	                }
            	}
                /* Handle light level, if needed */
                lighting.applyLighting(ps, this, c, tmpcolor);
                /* If grid scale, add it */
                if(gridscale > 0) {
                    int xx = mapiter.getX() % gridscale;
                    int zz = mapiter.getZ() % gridscale;
                    if(((xx == 0) && ((zz & 2) == 0)) || ((zz == 0) && ((xx & 2) == 0))) {
                        for(int i = 0; i < tmpcolor.length; i++) {
                            int v = tmpcolor[i].getARGB();
                            tmpcolor[i].setARGB((v & 0xFF000000) | ((v & 0xFEFEFE) >> 1) | 0x808080);
                        }
                    }
                }
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
            if (ctm_cache != null) {
                ctm_cache.clear();
                ctm_cache = null;
            }
        }
        @Override
        public DynLongHashMap getCTMTextureCache() {
            if (ctm_cache == null) {
                ctm_cache = new DynLongHashMap();
            }
            return ctm_cache;
        }
        @Override
        public int[] getLightingTable() {
            return lightingTable;
        }
        @Override
        public void setLastBlockState(DynmapBlockState new_lastbs) {
            lastblk = new_lastbs;
        }
        // Return last blockc with surface hit
        public DynmapBlockState getLastBlockHit() {
        	return lastblkhit;
        }
    }

    /**
     *  Get renderer state object for use rendering a tile
     * @param map - map being rendered
     * @param cache - chunk cache containing data for tile to be rendered
     * @param mapiter - iterator used when traversing rays in tile
     * @param scale - scale of perspective
     * @return state object to use for all rays in tile
     */
    public HDShaderState getStateInstance(HDMap map, MapChunkCache cache, MapIterator mapiter, int scale) {
        return new ShaderState(mapiter, map, cache, scale);
    }
    
    /* Add shader's contributions to JSON for map object */
    public void addClientConfiguration(JSONObject mapObject) {
        s(mapObject, "shader", name);
    }

    @Override
    public void exportAsMaterialLibrary(DynmapCommandSender sender, OBJExport out) throws IOException {
        if (tp == null) {
            getTexturePack();   // Make sure its loaded
        }
        if (tp != null) {
            tp.exportAsOBJMaterialLibrary(out, out.getBaseName());
            return;
        }
        throw new IOException("Export unsupported - invalid texture pack");
    }
    @Override
    public String[] getCurrentBlockMaterials(DynmapBlockState blk, MapIterator mapiter, int[] txtidx, BlockStep[] steps) {
        if (tp == null) {
            getTexturePack();   // Make sure its loaded
        }
        if (tp != null) {
            return tp.getCurrentBlockMaterials(blk, mapiter, txtidx, steps);
        }
        return new String[txtidx.length];
    }
}
