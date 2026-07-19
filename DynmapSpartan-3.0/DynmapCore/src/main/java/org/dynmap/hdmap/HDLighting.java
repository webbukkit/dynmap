package org.dynmap.hdmap;

import org.dynmap.Color;
import org.dynmap.DynmapWorld;
import org.json.simple.JSONObject;

public interface HDLighting {
    /* Get lighting name */
    String getName();
    /* Apply lighting to given pixel colors (1 outcolor if normal, 2 if night/day) */
    void    applyLighting(HDPerspectiveState ps, HDShaderState ss, Color incolor, Color[] outcolor);
    /* Test if Biome Data is needed for this renderer */
    boolean isBiomeDataNeeded();
    /* Test if raw biome temperature/rainfall data is needed */
    boolean isRawBiomeDataNeeded();
    /* Test if highest block Y data is needed */
    boolean isHightestBlockYDataNeeded();
    /* Tet if block type data needed */
    boolean isBlockTypeDataNeeded();
    /* Test if night/day is enabled for this renderer */
    boolean isNightAndDayEnabled();
    /* Test if sky light level needed */
    boolean isSkyLightLevelNeeded();
    /* Test if emitted light level needed */
    boolean isEmittedLightLevelNeeded();
    /* Add shader's contributions to JSON for map object */
    void addClientConfiguration(JSONObject mapObject);
    /* Get brightness table for given world */
    int[] getBrightnessTable(DynmapWorld world);
}
