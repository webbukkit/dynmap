package org.dynmap.hdmap;

import org.dynmap.Color;
import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.json.simple.JSONObject;

import static org.dynmap.JSONUtils.s;

public class DefaultHDLighting implements HDLighting {
    private String name;
    protected boolean grayscale;
    protected final Color graytone;
    protected final Color graytonedark;

    public DefaultHDLighting(DynmapCore core, ConfigurationNode configuration) {
        name = (String) configuration.get("name");
        grayscale = configuration.getBoolean("grayscale", false);
        graytone = configuration.getColor("graytone", null);
        graytonedark = configuration.getColor("graytonedark", "#000000");
    }
    
    protected void checkGrayscale(Color[] outcolor) {
        if (grayscale) {
            outcolor[0].setGrayscale();
            if (graytone != null) outcolor[0].scaleColor(graytonedark,graytone);
            if (outcolor.length > 1) {
                outcolor[1].setGrayscale();
                if (graytone != null) outcolor[1].scaleColor(graytonedark, graytone);
            }
        }
    }

    /* Get lighting name */
    public String getName() { return name; }
    
    /* Apply lighting to given pixel colors (1 outcolor if normal, 2 if night/day) */
    public void    applyLighting(HDPerspectiveState ps, HDShaderState ss, Color incolor, Color[] outcolor) {
        for(int i = 0; i < outcolor.length; i++)
            outcolor[i].setColor(incolor);
        checkGrayscale(outcolor);
    }
    
    /* Test if Biome Data is needed for this renderer */
    public boolean isBiomeDataNeeded() { return false; }
    
    /* Test if raw biome temperature/rainfall data is needed */
    public boolean isRawBiomeDataNeeded() { return false; }
    
    /* Test if highest block Y data is needed */
    public boolean isHightestBlockYDataNeeded() { return false; }
    
    /* Tet if block type data needed */
    public boolean isBlockTypeDataNeeded() { return false; }
    
    /* Test if night/day is enabled for this renderer */
    public boolean isNightAndDayEnabled() { return false; }
    
    /* Test if sky light level needed */
    public boolean isSkyLightLevelNeeded() { return false; }
    
    /* Test if emitted light level needed */
    public boolean isEmittedLightLevelNeeded() { return false; }
    
    /* Add shader's contributions to JSON for map object */
    public void addClientConfiguration(JSONObject mapObject) {
        s(mapObject, "lighting", name);
        s(mapObject, "nightandday", isNightAndDayEnabled());
    }

    @Override
    public int[] getBrightnessTable(DynmapWorld world) {
        return null;
    }
}
