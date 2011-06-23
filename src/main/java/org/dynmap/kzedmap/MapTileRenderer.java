package org.dynmap.kzedmap;

import java.io.File;

import org.dynmap.utils.MapChunkCache;

import org.json.simple.JSONObject;

public interface MapTileRenderer {
    String getName();

    boolean render(MapChunkCache cache, KzedMapTile tile, File outputFile);

    void buildClientConfiguration(JSONObject worldObject);
    
    boolean isBiomeDataNeeded();
    boolean isRawBiomeDataNeeded();
    boolean isNightAndDayEnabled();
}
