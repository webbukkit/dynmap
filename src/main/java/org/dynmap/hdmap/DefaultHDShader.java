package org.dynmap.hdmap;

import static org.dynmap.JSONUtils.a;
import static org.dynmap.JSONUtils.s;

import java.io.File;
import java.util.HashSet;

import org.bukkit.block.Biome;
import org.dynmap.Color;
import org.dynmap.ColorScheme;
import org.dynmap.ConfigurationNode;
import org.dynmap.Log;
import org.dynmap.hdmap.HDMap.BlockStep;
import org.dynmap.kzedmap.KzedMapTile;
import org.dynmap.kzedmap.DefaultTileRenderer.BiomeColorOption;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.MapIterator;
import org.dynmap.utils.Vector3D;
import org.json.simple.JSONObject;

public class DefaultHDShader implements HDShader {
    private ConfigurationNode configuration;
    private String name;
    protected ColorScheme colorScheme;

    protected HashSet<Integer> highlightBlocks = new HashSet<Integer>();
    protected Color highlightColor = new Color(255, 0, 0);

    protected int   shadowscale[];  /* index=skylight level, value = 256 * scaling value */
    protected int   lightscale[];   /* scale skylight level (light = lightscale[skylight] */
    protected boolean night_and_day;    /* If true, render both day (prefix+'-day') and night (prefix) tiles */
    protected boolean transparency; /* Is transparency support active? */
    public enum BiomeColorOption {
        NONE, BIOME, TEMPERATURE, RAINFALL
    }
    protected BiomeColorOption biomecolored = BiomeColorOption.NONE; /* Use biome for coloring */
    
    public DefaultHDShader(ConfigurationNode configuration) {
        this.configuration = configuration;
        name = (String) configuration.get("name");
        double shadowweight = configuration.getDouble("shadowstrength", 0.0);
        if(shadowweight > 0.0) {
            shadowscale = new int[16];
            shadowscale[15] = 256;
            /* Normal brightness weight in MC is a 20% relative dropoff per step */
            for(int i = 14; i >= 0; i--) {
                double v = shadowscale[i+1] * (1.0 - (0.2 * shadowweight));
                shadowscale[i] = (int)v;
                if(shadowscale[i] > 256) shadowscale[i] = 256;
                if(shadowscale[i] < 0) shadowscale[i] = 0;
            }
        }
        int v = configuration.getInteger("ambientlight", -1);
        if(v >= 0) {
            lightscale = new int[16];
            for(int i = 0; i < 16; i++) {
                if(i < (15-v))
                    lightscale[i] = 0;
                else
                    lightscale[i] = i - (15-v);
            }
        }
        colorScheme = ColorScheme.getScheme(configuration.getString("colorscheme", "default"));
        night_and_day = configuration.getBoolean("night-and-day", false);
        transparency = configuration.getBoolean("transparency", true);  /* Default on */
        String biomeopt = configuration.getString("biomecolored", "none");
        if(biomeopt.equals("biome")) {
            biomecolored = BiomeColorOption.BIOME;
        }
        else if(biomeopt.equals("temperature")) {
            biomecolored = BiomeColorOption.TEMPERATURE;
        }
        else if(biomeopt.equals("rainfall")) {
            biomecolored = BiomeColorOption.RAINFALL;
        }
        else {
            biomecolored = BiomeColorOption.NONE;
        }
    }
    
    public boolean isBiomeDataNeeded() { return biomecolored == BiomeColorOption.BIOME; }
    public boolean isRawBiomeDataNeeded() { return (biomecolored == BiomeColorOption.RAINFALL) || (biomecolored == BiomeColorOption.TEMPERATURE); };
    public boolean isNightAndDayEnabled() { return night_and_day; }
    public boolean isSkyLightLevelNeeded() { return (lightscale != null); }
    public boolean isEmittedLightLevelNeeded() { return (shadowscale != null); }

    public String getName() { return name; }
    
    private class OurRendererState implements HDShaderState {
        private Color color = new Color();
        private Color daycolor;
        protected MapIterator mapiter;
        private Color tmpcolor = new Color();
        private Color tmpdaycolor = new Color();
        private int pixelodd;
        
        private OurRendererState(MapIterator mapiter) {
            this.mapiter = mapiter;
            if(night_and_day) {
                daycolor = new Color();
            }
        }
        /**
         * Reset renderer state for new ray
         */
        public void reset(int x, int y, Vector3D raystart, double scale) {
            color.setTransparent();
            if(daycolor != null)
                daycolor.setTransparent();
            pixelodd = (x & 0x3) + (y<<1);
        }
        protected Color[] getBlockColors(int blocktype, int blockdata) {
            if((blockdata != 0) && (colorScheme.datacolors[blocktype] != null))
                return colorScheme.datacolors[blocktype][blockdata];
            else
                return colorScheme.colors[blocktype];
        }
        /**
         * Process next ray step - called for each block on route
         * @param blocktype - block type of current block
         * @param blockdata - data nibble of current block
         * @param skylightlevel - sky light level of previous block (surface on current block)
         * @param emittedlightlevel - emitted light level of previous block (surface on current block)
         * @param laststep - direction of last step
         * @return true if ray is done, false if ray needs to continue
         */
        public boolean processBlock(int blocktype, int blockdata, int skylightlevel, int emittedlightlevel, HDMap.BlockStep laststep) {            
            if(blocktype == 0)
                return false;
            Color[] colors = getBlockColors(blocktype, blockdata);
            
            if (colors != null) {
                int seq;
                /* Figure out which color to use */
                if((laststep == BlockStep.X_PLUS) || (laststep == BlockStep.X_MINUS))
                    seq = 1;
                else if((laststep == BlockStep.Z_PLUS) || (laststep == BlockStep.Z_MINUS))
                    seq = 3;
                else if(((pixelodd + mapiter.getY()) & 0x03) == 0)
                    seq = 2;
                else
                    seq = 0;

                Color c = colors[seq];
                if (c.getAlpha() > 0) {
                    /* Handle light level, if needed */
                    int lightlevel = 15, lightlevel_day = 15;
                    if(shadowscale != null) {
                        lightlevel = lightlevel_day = skylightlevel;
                        if(lightscale != null)
                            lightlevel = lightscale[lightlevel];
                        if((lightlevel < 15) || (lightlevel_day < 15)) {
                            int emitted = emittedlightlevel;
                            lightlevel = Math.max(emitted, lightlevel);                                
                            lightlevel_day = Math.max(emitted, lightlevel_day);                                
                        }
                    }
                    /* Figure out our color, with lighting if needed */
                    tmpcolor.setColor(c);
                    if(lightlevel < 15) {
                        shadowColor(tmpcolor, lightlevel);
                    }
                    if(daycolor != null) {
                        if(lightlevel_day == lightlevel) {
                            tmpdaycolor.setColor(tmpcolor);
                        }
                        else {
                            tmpdaycolor.setColor(c);
                            if(lightlevel_day < 15) {
                                shadowColor(tmpdaycolor, lightlevel_day);
                            }
                        }
                    }
                    /* Blend color with accumulated color (weighted by alpha) */
                    if(!transparency) {  /* No transparency support */
                        color.setARGB(tmpcolor.getARGB() | 0xFF000000);
                        if(daycolor != null)
                            daycolor.setARGB(tmpdaycolor.getARGB() | 0xFF000000);
                        return true;    /* We're done */
                    }
                    /* If no previous color contribution, use new color */
                    else if(color.isTransparent()) {
                        color.setColor(tmpcolor);
                        if(daycolor != null)
                            daycolor.setColor(tmpdaycolor);
                        return (color.getAlpha() == 255);
                    }
                    /* Else, blend and generate new alpha */
                    else {
                        int alpha = color.getAlpha();
                        int alpha2 = tmpcolor.getAlpha() * (255-alpha) / 255;
                        int talpha = alpha + alpha2;
                        color.setRGBA((tmpcolor.getRed()*alpha2 + color.getRed()*alpha) / talpha,
                                      (tmpcolor.getGreen()*alpha2 + color.getGreen()*alpha) / talpha,
                                      (tmpcolor.getBlue()*alpha2 + color.getBlue()*alpha) / talpha, talpha);
                        if(daycolor != null)
                            daycolor.setRGBA((tmpdaycolor.getRed()*alpha2 + daycolor.getRed()*alpha) / talpha,
                                          (tmpdaycolor.getGreen()*alpha2 + daycolor.getGreen()*alpha) / talpha,
                                          (tmpdaycolor.getBlue()*alpha2 + daycolor.getBlue()*alpha) / talpha, talpha);
                        return (talpha >= 254);   /* If only one short, no meaningful contribution left */
                    }
                }
            }
            return false;
        }        
        /**
         * Ray ended - used to report that ray has exited map (called if renderer has not reported complete)
         */
        public void rayFinished() {
        }
        /**
         * Get result color - get resulting color for ray
         * @param c - object to store color value in
         * @param index - index of color to request (renderer specific - 0=default, 1=day for night/day renderer
         */
        public void getRayColor(Color c, int index) {
            if(index == 0)
                c.setColor(color);
            else if((index == 1) && (daycolor != null))
                c.setColor(daycolor);
        }
        /**
         * Clean up state object - called after last ray completed
         */
        public void cleanup() {
        }
        
        private final void shadowColor(Color c, int lightlevel) {
            int scale = shadowscale[lightlevel];
            if(scale < 256)
                c.setRGBA((c.getRed() * scale) >> 8, (c.getGreen() * scale) >> 8, 
                    (c.getBlue() * scale) >> 8, c.getAlpha());
        }
    }

    private class OurBiomeRendererState extends OurRendererState {
        private OurBiomeRendererState(MapIterator mapiter) {
            super(mapiter);
        }
        protected Color[] getBlockColors(int blocktype, int blockdata) {
            Biome bio = mapiter.getBiome();
            if(bio != null)
                return colorScheme.biomecolors[bio.ordinal()];
            return null;
        }
    }
    
    private class OurBiomeRainfallRendererState extends OurRendererState {
        private OurBiomeRainfallRendererState(MapIterator mapiter) {
            super(mapiter);
        }
        protected Color[] getBlockColors(int blocktype, int blockdata) {
            return colorScheme.getRainColor(mapiter.getRawBiomeRainfall());
        }
    }

    private class OurBiomeTempRendererState extends OurRendererState {
        private OurBiomeTempRendererState(MapIterator mapiter) {
            super(mapiter);
        }
        protected Color[] getBlockColors(int blocktype, int blockdata) {
            return colorScheme.getTempColor(mapiter.getRawBiomeTemperature());
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
        switch(biomecolored) {
            case NONE:
                return new OurRendererState(mapiter);
            case BIOME:
                return new OurBiomeRendererState(mapiter);
            case RAINFALL:
                return new OurBiomeRainfallRendererState(mapiter);
            case TEMPERATURE:
                return new OurBiomeTempRendererState(mapiter);
        }
        return null;
    }
    
    @Override
    public void buildClientConfiguration(JSONObject worldObject) {
        ConfigurationNode c = configuration;
        JSONObject o = new JSONObject();
        s(o, "type", "HDMapType");
        s(o, "name", c.getString("name"));
        s(o, "title", c.getString("title"));
        s(o, "icon", c.getString("icon"));
        s(o, "prefix", c.getString("prefix"));
        s(o, "background", c.getString("background"));
        s(o, "nightandday", c.getBoolean("night-and-day", false));
        s(o, "backgroundday", c.getString("backgroundday"));
        s(o, "backgroundnight", c.getString("backgroundnight"));
        a(worldObject, "maps", o);
    }
}
