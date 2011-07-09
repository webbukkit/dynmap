package org.dynmap.hdmap;

import java.io.File;

import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.MapIterator;

import org.json.simple.JSONObject;

public interface HDShader {
    /* Get shader name */
    String getName();
    /**
     *  Get renderer state object for use rendering a tile
     * @param map - map being rendered
     * @param cache - chunk cache containing data for tile to be rendered
     * @param mapiter - iterator used when traversing rays in tile
     * @return state object to use for all rays in tile
     */
    HDShaderState getStateInstance(HDMap map, MapChunkCache cache, MapIterator mapiter);
    /* Test if Biome Data is needed for this renderer */
    boolean isBiomeDataNeeded();
    /* Test if raw biome temperature/rainfall data is needed */
    boolean isRawBiomeDataNeeded();
    /* Test if highest block Y data is needed */
    boolean isHightestBlockYDataNeeded();
    /* Tet if block type data needed */
    boolean isBlockTypeDataNeeded();
    /* Test if sky light level needed */
    boolean isSkyLightLevelNeeded();
    /* Test if emitted light level needed */
    boolean isEmittedLightLevelNeeded();
    /* Add shader's contributions to JSON for map object */
    void addClientConfiguration(JSONObject mapObject);

}
