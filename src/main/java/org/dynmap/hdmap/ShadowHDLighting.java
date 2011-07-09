package org.dynmap.hdmap;

import static org.dynmap.JSONUtils.s;

import org.dynmap.Color;
import org.dynmap.ConfigurationNode;
import org.dynmap.Log;
import org.json.simple.JSONObject;

public class ShadowHDLighting extends DefaultHDLighting {

    protected int   shadowscale[];  /* index=skylight level, value = 256 * scaling value */
    protected int   lightscale[];   /* scale skylight level (light = lightscale[skylight] */
    protected boolean night_and_day;    /* If true, render both day (prefix+'-day') and night (prefix) tiles */

    public ShadowHDLighting(ConfigurationNode configuration) {
        super(configuration);
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
        if((v >= 0) && (v < 15)) {
            lightscale = new int[16];
            for(int i = 0; i < 16; i++) {
                if(i < (15-v))
                    lightscale[i] = 0;
                else
                    lightscale[i] = i - (15-v);
            }
        }
        night_and_day = configuration.getBoolean("night-and-day", false);
        if(night_and_day) {
            if(lightscale == null) {
                Log.severe("night-and-day in lighting '" + getName() + "' requires ambientlight<15");
                night_and_day = false;
            }
        }
    }
    
    /* Apply lighting to given pixel colors (1 outcolor if normal, 2 if night/day) */
    public void    applyLighting(HDPerspectiveState ps, HDShaderState ss, Color incolor, Color[] outcolor) {
        int lightlevel = 15, lightlevel_day = 15;
        /* If processing for shadows, use sky light level as base lighting */
        if(shadowscale != null) {
            lightlevel = lightlevel_day = ps.getSkyLightLevel();
        }
        /* If ambient light, adjust base lighting for it */
        if(lightscale != null)
            lightlevel = lightscale[lightlevel];
        /* If we're below max, see if emitted light helps */
        if((lightlevel < 15) || (lightlevel_day < 15)) {
            int emitted = ps.getEmittedLightLevel();
            lightlevel = Math.max(emitted, lightlevel);                                
            lightlevel_day = Math.max(emitted, lightlevel_day);                                
        }
        /* Figure out our color, with lighting if needed */
        outcolor[0].setColor(incolor);
        if(lightlevel < 15) {
            shadowColor(outcolor[0], lightlevel);
        }
        if(outcolor.length > 1) {
            if(lightlevel_day == lightlevel) {
                outcolor[1].setColor(outcolor[0]);
            }
            else {
                outcolor[1].setColor(incolor);
                if(lightlevel_day < 15) {
                    shadowColor(outcolor[1], lightlevel_day);
                }
            }
        }
    }

    private final void shadowColor(Color c, int lightlevel) {
        int scale = shadowscale[lightlevel];
        if(scale < 256)
            c.setRGBA((c.getRed() * scale) >> 8, (c.getGreen() * scale) >> 8, 
                (c.getBlue() * scale) >> 8, c.getAlpha());
    }
    
    /* Test if night/day is enabled for this renderer */
    public boolean isNightAndDayEnabled() { return night_and_day; }
    
    /* Test if sky light level needed */
    public boolean isSkyLightLevelNeeded() { return (shadowscale != null); }
    
    /* Test if emitted light level needed */
    public boolean isEmittedLightLevelNeeded() { return (shadowscale != null) || (lightscale != null); }    

}
