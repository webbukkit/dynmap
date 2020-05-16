package org.dynmap.hdmap;

import org.dynmap.Color;
import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.Log;
import org.dynmap.MapManager;
import org.dynmap.utils.LightLevels;
import org.dynmap.utils.BlockStep;

public class LightLevelHDLighting extends DefaultHDLighting {
    private final Color[] lightlevelcolors = new Color[16];
    protected final boolean night_and_day;    /* If true, render both day (prefix+'-day') and night (prefix) tiles */
    private final boolean night;
    private final Color mincolor = new Color(0x40, 0x40, 0x40);
    private final Color maxcolor = new Color(0xFF, 0xFF, 0xFF);
    public LightLevelHDLighting(DynmapCore core, ConfigurationNode configuration) {
        super(core, configuration);
        grayscale = true;   // Force to grayscale
        blackandwhite = false;
        for (int i = 0; i < 16; i++) {
            lightlevelcolors[i] = configuration.getColor("color" + i, null);
        }
        night = configuration.getBoolean("night",  false);
        night_and_day = configuration.getBoolean("night-and-day", false);
    }
        
    /* Apply lighting to given pixel colors (1 outcolor if normal, 2 if night/day) */
    public void    applyLighting(HDPerspectiveState ps, HDShaderState ss, Color incolor, Color[] outcolor) {
        super.applyLighting(ps, ss, incolor, outcolor);    // Apply default lighting (outcolors will be grayscale)
        // Compute light levels
        LightLevels ll = ps.getCachedLightLevels(0);
        ps.getLightLevels(ll);
        if (outcolor.length == 1) {
            // Scale range between 25% and 100% intensity (we don't want blacks, since that will prevent color from showing
            outcolor[0].scaleColor(mincolor, maxcolor);
            int lightlevel = night ? ll.emitted : Math.max(ll.sky, ll.emitted);
            if (lightlevelcolors[lightlevel] != null) {
                outcolor[0].blendColor(lightlevelcolors[lightlevel]);
            }
        }
        else {
            // Scale range between 25% and 100% intensity (we don't want blacks, since that will prevent color from showing
            outcolor[0].scaleColor(mincolor, maxcolor);
            outcolor[1].scaleColor(mincolor, maxcolor);
            int daylightlevel = Math.max(ll.sky, ll.emitted);
            if (lightlevelcolors[ll.emitted] != null) {
                outcolor[0].blendColor(lightlevelcolors[ll.emitted]);
            }
            if (lightlevelcolors[daylightlevel] != null) {
                outcolor[1].blendColor(lightlevelcolors[daylightlevel]);
            }
        }
    }
    /* Test if night/day is enabled for this renderer */
    public boolean isNightAndDayEnabled() { return night_and_day; }
    
    /* Test if sky light level needed */
    public boolean isSkyLightLevelNeeded() { return true; }
    
    /* Test if emitted light level needed */
    public boolean isEmittedLightLevelNeeded() { return true; }    

    @Override
    public int[] getBrightnessTable(DynmapWorld world) {
        return null;
    }
}
