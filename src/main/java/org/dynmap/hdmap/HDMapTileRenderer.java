package org.dynmap.hdmap;

import java.io.File;

import org.dynmap.utils.MapChunkCache;

import org.json.simple.JSONObject;

public interface HDMapTileRenderer {
    String getName();

    boolean render(MapChunkCache cache, HDMapTile tile, File outputFile);

    void buildClientConfiguration(JSONObject worldObject);
    
    boolean isBiomeDataNeeded();
    boolean isRawBiomeDataNeeded();
    boolean isNightAndDayEnabled();
}
