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
    protected boolean blackandwhite;
    protected int blackthreshold;
    protected final Color graytone;
    protected final Color graytonedark;

    public DefaultHDLighting(DynmapCore core, ConfigurationNode configuration) {
        name = (String) configuration.get("name");
        grayscale = configuration.getBoolean("grayscale", false);
        graytone = configuration.getColor("graytone", "#FFFFFF");
        graytonedark = configuration.getColor("graytonedark", "#000000");
        blackandwhite = configuration.getBoolean("blackandwhite", false);
        if (blackandwhite) grayscale = false;
        blackthreshold = configuration.getInteger("blackthreshold",  0x40);
    }
    
    protected void checkGrayscale(Color[] outcolor) {
        if (grayscale) {
        	for (int i = 0; i < outcolor.length; i++) {
        		outcolor[i].setGrayscale();
        		outcolor[i].scaleColor(graytonedark,graytone);
        	}
        }
        else if (blackandwhite) {
        	for (int i = 0; i < outcolor.length; i++) {
        		outcolor[i].setGrayscale();
        		if (outcolor[i].getRed() > blackthreshold) {
        			outcolor[i].setColor(graytone);
        		}
        		else {
        			outcolor[i].setColor(graytonedark);
        		}
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
