package org.dynmap.hdmap;

import java.util.HashSet;

import org.dynmap.Color;
import org.dynmap.ColorScheme;
import org.dynmap.ConfigurationNode;
import org.dynmap.hdmap.DefaultHDShader.BiomeColorOption;
import org.json.simple.JSONObject;
import static org.dynmap.JSONUtils.s;

public class DefaultHDLighting implements HDLighting {
    private String name;

    public DefaultHDLighting(ConfigurationNode configuration) {
        name = (String) configuration.get("name");
    }
    
    /* Get lighting name */
    public String getName() { return name; }
    
    /* Apply lighting to given pixel colors (1 outcolor if normal, 2 if night/day) */
    public void    applyLighting(HDPerspectiveState ps, HDShaderState ss, Color incolor, Color[] outcolor) {
        for(Color oc: outcolor)
            oc.setColor(incolor);
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
}
